package de.bolzwerk.tagwerk.data.repo

import androidx.room.withTransaction
import de.bolzwerk.tagwerk.audio.AudioStore
import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.AudioRecordingEntity
import de.bolzwerk.tagwerk.data.db.NoteCategoryEntity
import de.bolzwerk.tagwerk.data.db.NoteCategoryRow
import de.bolzwerk.tagwerk.data.db.NoteEntity
import de.bolzwerk.tagwerk.data.db.NoteSource
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.db.TagwerkDatabase
import de.bolzwerk.tagwerk.work.WorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

class NoteRepository(
    private val db: TagwerkDatabase,
    private val audioStore: AudioStore,
    private val categories: CategoryRepository,
    private val scheduler: WorkScheduler,
) {
    private val noteDao = db.noteDao()
    private val linkDao = db.noteCategoryDao()
    private val audioDao = db.audioDao()

    fun observeNotes(): Flow<List<NoteItem>> =
        combine(noteDao.observeAll(), linkDao.observeAll()) { notes, links ->
            val byNote = links.groupBy { it.noteId }
            notes.map { NoteItem.from(it, byNote[it.id].orEmpty().map(::toCategory)) }
        }

    fun observeNote(id: Long): Flow<NoteItem?> =
        combine(noteDao.observe(id), linkDao.observeAll().map { rows -> rows.filter { it.noteId == id } }) { note, links ->
            note?.let { NoteItem.from(it, links.map(::toCategory)) }
        }

    /** Legt eine diktierte Notiz an; Transkription und Kategorisierung laufen im Hintergrund. */
    suspend fun createDictatedNote(recording: File, durationMs: Long): Long {
        val fileName = audioStore.importRecording(recording)
        val now = System.currentTimeMillis()
        val id = db.withTransaction {
            val audioId = audioDao.insert(AudioRecordingEntity(fileName = fileName, durationMs = durationMs, createdAt = now))
            noteDao.insert(
                NoteEntity(
                    title = "",
                    text = "",
                    status = NoteStatus.OPEN,
                    source = NoteSource.DICTATED,
                    audioRecordingId = audioId,
                    processingState = ProcessingState.PENDING_TRANSCRIPTION,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        scheduler.enqueueProcessing(id)
        return id
    }

    suspend fun createTypedNote(title: String, text: String): Long {
        val now = System.currentTimeMillis()
        val id = noteDao.insert(
            NoteEntity(
                title = title.trim(),
                text = text.trim(),
                status = NoteStatus.OPEN,
                source = NoteSource.TYPED,
                processingState = ProcessingState.PENDING_CATEGORIZATION,
                createdAt = now,
                updatedAt = now,
            )
        )
        scheduler.enqueueProcessing(id)
        return id
    }

    suspend fun updateContent(id: Long, title: String, text: String) {
        val note = noteDao.get(id) ?: return
        if (note.title == title && note.text == text) return
        noteDao.update(note.copy(title = title, text = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: Long, status: NoteStatus) {
        val note = noteDao.get(id) ?: return
        val now = System.currentTimeMillis()
        noteDao.update(
            note.copy(
                status = status,
                completedAt = if (status == NoteStatus.DONE) now else null,
                updatedAt = now,
            )
        )
    }

    suspend fun delete(id: Long) {
        val note = noteDao.get(id) ?: return
        val linkedCategories = linkDao.forNote(id).map { it.categoryId }
        val audio = note.audioRecordingId?.let { audioDao.get(it) }
        db.withTransaction {
            noteDao.delete(id)
            audio?.let { audioDao.delete(it.id) }
        }
        audio?.let { audioStore.delete(it.fileName) }
        linkedCategories.forEach { categories.deleteIfUnusedAiCategory(it) }
    }

    suspend fun confirmCategory(noteId: Long, categoryId: Long) = linkDao.confirm(noteId, categoryId)

    suspend fun removeCategory(noteId: Long, categoryId: Long) {
        linkDao.remove(noteId, categoryId)
        categories.deleteIfUnusedAiCategory(categoryId)
    }

    suspend fun addCategory(noteId: Long, categoryName: String) {
        val category = categories.findOrCreate(categoryName, AssignedBy.USER)
        linkDao.upsert(NoteCategoryEntity(noteId, category.id, AssignedBy.USER, confirmed = true))
    }

    /** Verwirft offene KI-Vorschläge und lässt die KI Titel/Kategorien neu bestimmen. */
    suspend fun recategorize(id: Long) {
        val note = noteDao.get(id) ?: return
        val suggestions = linkDao.forNote(id).filter { !it.confirmed }.map { it.categoryId }
        linkDao.removeUnconfirmedSuggestions(id)
        suggestions.forEach { categories.deleteIfUnusedAiCategory(it) }
        noteDao.update(note.copy(processingState = ProcessingState.PENDING_CATEGORIZATION, processingError = null))
        scheduler.enqueueProcessing(id, replace = true)
    }

    /** Startet eine fehlgeschlagene Verarbeitung erneut an der passenden Stelle. */
    suspend fun retryProcessing(id: Long) {
        val note = noteDao.get(id) ?: return
        val state = if (note.text.isBlank() && note.audioRecordingId != null) {
            ProcessingState.PENDING_TRANSCRIPTION
        } else {
            ProcessingState.PENDING_CATEGORIZATION
        }
        noteDao.update(note.copy(processingState = state, processingError = null))
        scheduler.enqueueProcessing(id, replace = true)
    }

    /**
     * Plant alle noch nicht verarbeiteten Notizen erneut ein. Beim App-Start ohne [replace], damit bereits
     * laufende Verarbeitung nicht abgebrochen wird; nach geänderten KI-Einstellungen mit [replace],
     * damit wartende Wiederholungen sofort starten.
     */
    suspend fun reschedulePending(replace: Boolean) {
        noteDao.pending().forEach { scheduler.enqueueProcessing(it.id, replace = replace) }
    }

    suspend fun audioFileName(noteId: Long): String? {
        val note = noteDao.get(noteId) ?: return null
        return note.audioRecordingId?.let { audioDao.get(it)?.fileName }
    }

    private fun toCategory(row: NoteCategoryRow) = NoteCategory(
        id = row.categoryId,
        name = row.name,
        colorIndex = row.colorIndex,
        assignedBy = row.assignedBy,
        confirmed = row.confirmed,
    )
}
