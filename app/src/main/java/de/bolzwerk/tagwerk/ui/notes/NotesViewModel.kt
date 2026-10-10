package de.bolzwerk.tagwerk.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.data.db.CategoryEntity
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.repo.NoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusFilter(val label: String) { OPEN("Offen"), DONE("Erledigt"), ALL("Alle") }

data class NotesFilter(
    val status: StatusFilter = StatusFilter.OPEN,
    val categoryId: Long? = null,
    val query: String = "",
)

data class NotesUiState(
    val loading: Boolean = true,
    val notes: List<NoteItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val filter: NotesFilter = NotesFilter(),
)

class NotesViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.noteRepository
    private val filter = MutableStateFlow(NotesFilter())
    /** Notizen, deren Löschen noch rückgängig gemacht werden kann – bis dahin ausgeblendet. */
    private val pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<NotesUiState> = combine(
        repository.observeNotes(),
        container.categoryRepository.observeAll(),
        filter,
        pendingDeletes,
    ) { notes, categories, filter, hidden ->
        NotesUiState(
            loading = false,
            notes = notes.filter { it.id !in hidden && matches(it, filter) },
            categories = categories,
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setStatusFilter(status: StatusFilter) = filter.update { it.copy(status = status) }

    fun setCategoryFilter(categoryId: Long?) = filter.update { it.copy(categoryId = categoryId) }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun toggleDone(note: NoteItem) = viewModelScope.launch {
        repository.setStatus(note.id, if (note.status == NoteStatus.DONE) NoteStatus.OPEN else NoteStatus.DONE)
    }

    fun requestDelete(id: Long) = pendingDeletes.update { it + id }

    fun undoDelete(id: Long) = pendingDeletes.update { it - id }

    fun commitDelete(id: Long) = viewModelScope.launch {
        repository.delete(id)
        pendingDeletes.update { it - id }
    }

    override fun onCleared() {
        // Noch nicht bestätigte Löschungen beim Verlassen ausführen.
        val ids = pendingDeletes.value
        if (ids.isNotEmpty()) {
            container.appScope.launch { ids.forEach { repository.delete(it) } }
        }
    }

    private fun matches(note: NoteItem, filter: NotesFilter): Boolean {
        val statusOk = when (filter.status) {
            StatusFilter.OPEN -> note.status == NoteStatus.OPEN
            StatusFilter.DONE -> note.status == NoteStatus.DONE
            StatusFilter.ALL -> true
        }
        val categoryOk = filter.categoryId == null || note.categories.any { it.id == filter.categoryId }
        val query = filter.query.trim()
        val queryOk = query.isEmpty() ||
            note.title.contains(query, ignoreCase = true) ||
            note.text.contains(query, ignoreCase = true) ||
            note.categories.any { it.name.contains(query, ignoreCase = true) }
        return statusOk && categoryOk && queryOk
    }
}
