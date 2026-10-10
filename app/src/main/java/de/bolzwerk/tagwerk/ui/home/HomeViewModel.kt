package de.bolzwerk.tagwerk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.db.ProcessingState
import de.bolzwerk.tagwerk.data.repo.NoteItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val aiConfigured: Boolean = true,
    val toConfirm: List<NoteItem> = emptyList(),
    val openNotes: List<NoteItem> = emptyList(),
    val openCount: Int = 0,
    val processingCount: Int = 0,
    val failedCount: Int = 0,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val notes = container.noteRepository

    val state: StateFlow<HomeUiState> = combine(
        notes.observeNotes(),
        container.settingsRepository.settings,
    ) { all, settings ->
        val open = all.filter { it.status == NoteStatus.OPEN }
        HomeUiState(
            loading = false,
            aiConfigured = settings.isAiConfigured,
            toConfirm = all.filter { it.hasSuggestions }.take(MAX_SUGGESTIONS),
            openNotes = open.take(MAX_OPEN),
            openCount = open.size,
            processingCount = all.count { it.isProcessing },
            failedCount = all.count { it.processingState == ProcessingState.FAILED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleDone(note: NoteItem) = viewModelScope.launch {
        notes.setStatus(note.id, if (note.status == NoteStatus.DONE) NoteStatus.OPEN else NoteStatus.DONE)
    }

    fun confirmCategory(noteId: Long, categoryId: Long) = viewModelScope.launch { notes.confirmCategory(noteId, categoryId) }

    fun removeCategory(noteId: Long, categoryId: Long) = viewModelScope.launch { notes.removeCategory(noteId, categoryId) }

    private companion object {
        const val MAX_SUGGESTIONS = 5
        const val MAX_OPEN = 6
    }
}
