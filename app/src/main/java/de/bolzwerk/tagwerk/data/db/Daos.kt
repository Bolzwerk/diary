package de.bolzwerk.tagwerk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM notes WHERE processingState IN ('PENDING_TRANSCRIPTION', 'PENDING_CATEGORIZATION')")
    suspend fun pending(): List<NoteEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<CategoryEntity>

    @Query(
        """
        SELECT c.id, c.name, c.colorIndex, COUNT(nc.noteId) AS noteCount
        FROM categories c LEFT JOIN note_categories nc ON nc.categoryId = c.id
        GROUP BY c.id ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM note_categories WHERE categoryId = :id")
    suspend fun usageCount(id: Long): Int
}

@Dao
interface NoteCategoryDao {
    @Query(
        """
        SELECT nc.noteId, nc.categoryId, c.name, c.colorIndex, nc.assignedBy, nc.confirmed
        FROM note_categories nc JOIN categories c ON c.id = nc.categoryId
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<NoteCategoryRow>>

    @Query("SELECT * FROM note_categories WHERE noteId = :noteId")
    suspend fun forNote(noteId: Long): List<NoteCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: NoteCategoryEntity)

    @Query("UPDATE note_categories SET confirmed = 1 WHERE noteId = :noteId AND categoryId = :categoryId")
    suspend fun confirm(noteId: Long, categoryId: Long)

    @Query("DELETE FROM note_categories WHERE noteId = :noteId AND categoryId = :categoryId")
    suspend fun remove(noteId: Long, categoryId: Long)

    @Query("DELETE FROM note_categories WHERE noteId = :noteId AND assignedBy = 'AI' AND confirmed = 0")
    suspend fun removeUnconfirmedSuggestions(noteId: Long)

    /** Verschiebt alle Zuordnungen einer Kategorie auf eine andere (Zusammenführen). */
    @Transaction
    suspend fun moveAll(fromCategoryId: Long, toCategoryId: Long) {
        for (link in forCategory(fromCategoryId)) {
            upsert(link.copy(categoryId = toCategoryId))
        }
    }

    @Query("SELECT * FROM note_categories WHERE categoryId = :categoryId")
    suspend fun forCategory(categoryId: Long): List<NoteCategoryEntity>
}

@Dao
interface AudioDao {
    @Insert
    suspend fun insert(recording: AudioRecordingEntity): Long

    @Query("SELECT * FROM audio_recordings WHERE id = :id")
    suspend fun get(id: Long): AudioRecordingEntity?

    @Query("SELECT * FROM audio_recordings WHERE createdAt < :before")
    suspend fun olderThan(before: Long): List<AudioRecordingEntity>

    @Query("DELETE FROM audio_recordings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(entry: UsageLogEntity)

    @Query(
        """
        SELECT SUM(audioSeconds) AS audioSeconds, SUM(inputTokens) AS inputTokens, SUM(outputTokens) AS outputTokens
        FROM usage_log WHERE createdAt >= :since
        """
    )
    fun observeSummary(since: Long): Flow<UsageSummary>
}
