package de.bolzwerk.tagwerk.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NoteStatus { OPEN, DONE }

enum class NoteSource { DICTATED, TYPED, DIARY }

/** Verarbeitungsstand einer Notiz in der KI-Pipeline. */
enum class ProcessingState { PENDING_TRANSCRIPTION, PENDING_CATEGORIZATION, DONE, FAILED }

enum class AssignedBy { USER, AI }

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val createdBy: AssignedBy,
    val createdAt: Long,
)

@Entity(tableName = "audio_recordings")
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Dateiname im App-internen Audioverzeichnis (verschlüsselt). */
    val fileName: String,
    val durationMs: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = AudioRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["audioRecordingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("audioRecordingId"), Index("status")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Originaltext (Transkript bzw. getippter Text) – wird von der KI nie verändert. */
    val text: String,
    val status: NoteStatus,
    val source: NoteSource,
    val dueDate: Long? = null,
    val audioRecordingId: Long? = null,
    val processingState: ProcessingState,
    val processingError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "note_categories",
    primaryKeys = ["noteId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class NoteCategoryEntity(
    val noteId: Long,
    val categoryId: Long,
    val assignedBy: AssignedBy,
    /** false = KI-Vorschlag, der noch bestätigt werden muss. */
    val confirmed: Boolean,
)

@Entity(tableName = "usage_log")
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String,
    val model: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val audioSeconds: Double = 0.0,
    val createdAt: Long,
)

/** Ergebnis eines Joins zwischen Zuordnung und Kategorie. */
data class NoteCategoryRow(
    val noteId: Long,
    val categoryId: Long,
    val name: String,
    val colorIndex: Int,
    val assignedBy: AssignedBy,
    val confirmed: Boolean,
)

data class CategoryWithCount(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    @ColumnInfo(name = "noteCount") val noteCount: Int,
)

data class UsageSummary(
    val audioSeconds: Double?,
    val inputTokens: Long?,
    val outputTokens: Long?,
)
