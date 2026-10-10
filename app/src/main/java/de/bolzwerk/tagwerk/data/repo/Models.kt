package de.bolzwerk.tagwerk.data.repo

import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.NoteEntity
import de.bolzwerk.tagwerk.data.db.NoteSource
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState

data class NoteCategory(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val assignedBy: AssignedBy,
    val confirmed: Boolean,
) {
    val isSuggestion: Boolean get() = !confirmed
}

/** Notiz inklusive Kategorien, wie sie die Oberfläche braucht. */
data class NoteItem(
    val id: Long,
    val title: String,
    val text: String,
    val status: NoteStatus,
    val source: NoteSource,
    val hasAudio: Boolean,
    val audioRecordingId: Long?,
    val processingState: ProcessingState,
    val processingError: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val categories: List<NoteCategory>,
) {
    val isProcessing: Boolean
        get() = processingState == ProcessingState.PENDING_TRANSCRIPTION || processingState == ProcessingState.PENDING_CATEGORIZATION

    val hasSuggestions: Boolean get() = categories.any { it.isSuggestion }

    /** Titel für die Anzeige – fällt auf den Textanfang zurück, solange die KI noch keinen Titel vergeben hat. */
    val displayTitle: String
        get() = title.ifBlank {
            when {
                processingState == ProcessingState.PENDING_TRANSCRIPTION -> "Wird transkribiert …"
                text.isBlank() -> "Leere Notiz"
                else -> text.lineSequence().first().take(60)
            }
        }

    companion object {
        fun from(note: NoteEntity, categories: List<NoteCategory>) = NoteItem(
            id = note.id,
            title = note.title,
            text = note.text,
            status = note.status,
            source = note.source,
            hasAudio = note.audioRecordingId != null,
            audioRecordingId = note.audioRecordingId,
            processingState = note.processingState,
            processingError = note.processingError,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            categories = categories,
        )
    }
}
