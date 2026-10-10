package de.bolzwerk.tagwerk.data.repo

import androidx.room.withTransaction
import de.bolzwerk.tagwerk.data.db.AssignedBy
import de.bolzwerk.tagwerk.data.db.CategoryEntity
import de.bolzwerk.tagwerk.data.db.CategoryWithCount
import de.bolzwerk.tagwerk.data.db.TagwerkDatabase
import de.bolzwerk.tagwerk.domain.CategoryColors
import de.bolzwerk.tagwerk.domain.CategoryMatcher
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val db: TagwerkDatabase) {

    private val categoryDao = db.categoryDao()
    private val linkDao = db.noteCategoryDao()

    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeWithCounts(): Flow<List<CategoryWithCount>> = categoryDao.observeWithCounts()

    suspend fun all(): List<CategoryEntity> = categoryDao.all()

    /** Liefert die Kategorie mit diesem Namen (unabhängig von Schreibweise) oder legt sie an. */
    suspend fun findOrCreate(name: String, createdBy: AssignedBy): CategoryEntity = db.withTransaction {
        val existing = categoryDao.all()
        existing.firstOrNull { CategoryMatcher.normalize(it.name) == CategoryMatcher.normalize(name) }?.let { return@withTransaction it }
        val entity = CategoryEntity(
            name = name.trim(),
            colorIndex = CategoryColors.nextIndex(existing.map { it.colorIndex }),
            createdBy = createdBy,
            createdAt = System.currentTimeMillis(),
        )
        entity.copy(id = categoryDao.insert(entity))
    }

    /**
     * Benennt eine Kategorie um. Existiert der neue Name bereits, werden beide zusammengeführt
     * (alle Notizen wandern in die vorhandene Kategorie).
     */
    suspend fun rename(id: Long, newName: String) = db.withTransaction {
        val category = categoryDao.get(id) ?: return@withTransaction
        val name = newName.trim()
        if (name.isEmpty()) return@withTransaction
        val target = categoryDao.all().firstOrNull {
            it.id != id && CategoryMatcher.normalize(it.name) == CategoryMatcher.normalize(name)
        }
        if (target != null) {
            linkDao.moveAll(fromCategoryId = id, toCategoryId = target.id)
            categoryDao.delete(id)
        } else {
            categoryDao.update(category.copy(name = name, createdBy = AssignedBy.USER))
        }
    }

    suspend fun setColor(id: Long, colorIndex: Int) {
        val category = categoryDao.get(id) ?: return
        categoryDao.update(category.copy(colorIndex = colorIndex))
    }

    suspend fun delete(id: Long) = categoryDao.delete(id)

    /** Entfernt eine von der KI angelegte Kategorie wieder, wenn sie nirgends mehr verwendet wird. */
    suspend fun deleteIfUnusedAiCategory(id: Long) {
        val category = categoryDao.get(id) ?: return
        if (category.createdBy == AssignedBy.AI && categoryDao.usageCount(id) == 0) categoryDao.delete(id)
    }
}
