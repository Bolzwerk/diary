package de.bolzwerk.tagwerk.domain

import de.bolzwerk.tagwerk.audio.AudioStore
import de.bolzwerk.tagwerk.data.ai.AiConfig
import de.bolzwerk.tagwerk.data.ai.AiException
import de.bolzwerk.tagwerk.data.ai.AiProvider
import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.NoteCategoryEntity
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.db.TagwerkDatabase
import de.bolzwerk.tagwerk.data.db.UsageLogEntity
import de.bolzwerk.tagwerk.data.repo.CategoryRepository
import de.bolzwerk.tagwerk.data.settings.AudioRetention
import de.bolzwerk.tagwerk.data.settings.CategorizationMode
import de.bolzwerk.tagwerk.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException

/** KI-Pipeline einer Notiz: Transkription → Titel & Kategorien. Wird vom Hintergrund-Worker aufgerufen. */
class NoteProcessor(
    private val db: TagwerkDatabase,
    private val ai: AiProvider,
    private val settings: SettingsRepository,
    private val categories: CategoryRepository,
    private val audioStore: AudioStore,
) {
    enum class Outcome { DONE, RETRY, FAILED }

    private val noteDao = db.noteDao()
    private val audioDao = db.audioDao()
    private val linkDao = db.noteCategoryDao()
    private val usageDao = db.usageDao()

    suspend fun process(noteId: Long, isLastAttempt: Boolean): Outcome {
        val current = settings.current
        if (!current.isAiConfigured) {
            setError(noteId, "Warte auf Einrichtung der KI-Verbindung (Einstellungen)")
            // Nach dem Speichern der Einstellungen werden wartende Notizen neu eingeplant.
            return Outcome.DONE
        }
        val config = current.toAiConfig()
        return try {
            transcribeIfNeeded(noteId, config)
            categorizeIfNeeded(noteId, config)
            Outcome.DONE
        } catch (e: AiException) {
            if (e.retryable && !isLastAttempt) {
                setError(noteId, e.message)
                Outcome.RETRY
            } else {
                markFailed(noteId, e.message ?: "Unbekannter Fehler")
                Outcome.FAILED
            }
        } catch (e: CancellationException) {
            throw e // Worker wurde abgebrochen (z. B. neu eingeplant) – kein Fehler der Notiz
        } catch (e: Exception) {
            markFailed(noteId, "Verarbeitung fehlgeschlagen: ${e.message}")
            Outcome.FAILED
        }
    }

    private suspend fun transcribeIfNeeded(noteId: Long, config: AiConfig) {
        val note = noteDao.get(noteId) ?: return
        if (note.processingState != ProcessingState.PENDING_TRANSCRIPTION) return
        val recording = note.audioRecordingId?.let { audioDao.get(it) }
        if (recording == null) {
            noteDao.update(note.copy(processingState = ProcessingState.PENDING_CATEGORIZATION))
            return
        }
        val audio = audioStore.read(recording.fileName)
        val result = ai.transcribe(config, audio, "aufnahme.m4a", settings.current.glossary)
        usageDao.insert(
            UsageLogEntity(
                operation = "transcription",
                model = config.transcriptionModel,
                inputTokens = result.usage.inputTokens,
                outputTokens = result.usage.outputTokens,
                audioSeconds = recording.durationMs / 1000.0,
                createdAt = System.currentTimeMillis(),
            )
        )
        val latest = noteDao.get(noteId) ?: return
        if (result.text.isBlank()) {
            noteDao.update(latest.copy(processingState = ProcessingState.FAILED, processingError = "Keine Sprache erkannt"))
            return
        }
        // Hat der Nutzer in der Zwischenzeit selbst Text eingegeben, wird das Transkript angehängt statt überschrieben.
        val text = if (latest.text.isBlank()) result.text else "${latest.text}\n\n${result.text}"
        noteDao.update(
            latest.copy(
                text = text,
                processingState = ProcessingState.PENDING_CATEGORIZATION,
                processingError = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (settings.current.audioRetention == AudioRetention.DELETE_AFTER_TRANSCRIPTION) {
            audioDao.delete(recording.id)
            audioStore.delete(recording.fileName)
        }
    }

    private suspend fun categorizeIfNeeded(noteId: Long, config: AiConfig) {
        val note = noteDao.get(noteId) ?: return
        if (note.processingState != ProcessingState.PENDING_CATEGORIZATION) return
        if (note.text.isBlank()) {
            noteDao.update(note.copy(processingState = ProcessingState.DONE, processingError = null))
            return
        }
        val existing = categories.all()
        val result = ai.categorizeNote(config, note.text, existing.map { it.name })
        usageDao.insert(
            UsageLogEntity(
                operation = "categorization",
                model = config.chatModel,
                inputTokens = result.usage.inputTokens,
                outputTokens = result.usage.outputTokens,
                createdAt = System.currentTimeMillis(),
            )
        )

        val links = linkDao.forNote(noteId)
        // Hat der Nutzer bereits selbst Kategorien vergeben, respektieren wir das.
        if (links.none { it.assignedBy == AssignedBy.USER }) {
            val confirmed = settings.current.categorizationMode == CategorizationMode.ASSIGN
            val matches = CategoryMatcher.resolve(result.categories, existing.map { CategoryMatcher.Existing(it.id, it.name) })
            for (match in matches) {
                val categoryId = when (match) {
                    is CategoryMatcher.Match.ExistingCategory -> match.id
                    is CategoryMatcher.Match.NewCategory -> categories.findOrCreate(match.name, AssignedBy.AI).id
                }
                if (links.none { it.categoryId == categoryId }) {
                    linkDao.upsert(NoteCategoryEntity(noteId, categoryId, AssignedBy.AI, confirmed))
                }
            }
        }

        val latest = noteDao.get(noteId) ?: return
        noteDao.update(
            latest.copy(
                title = latest.title.ifBlank { result.title },
                processingState = ProcessingState.DONE,
                processingError = null,
            )
        )
    }

    private suspend fun setError(noteId: Long, message: String?) {
        val note = noteDao.get(noteId) ?: return
        noteDao.update(note.copy(processingError = message))
    }

    private suspend fun markFailed(noteId: Long, message: String) {
        val note = noteDao.get(noteId) ?: return
        noteDao.update(note.copy(processingState = ProcessingState.FAILED, processingError = message))
    }
}
