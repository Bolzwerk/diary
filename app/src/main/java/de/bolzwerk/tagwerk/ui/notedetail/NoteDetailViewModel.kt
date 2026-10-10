package de.bolzwerk.tagwerk.ui.notedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.audio.AudioPlayer
import de.bolzwerk.tagwerk.data.db.CategoryEntity
import de.bolzwerk.tagwerk.data.db.NoteStatus
import de.bolzwerk.tagwerk.data.repo.NoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteDetailViewModel(private val container: AppContainer, private val noteId: Long) : ViewModel() {

    private val repository = container.noteRepository

    val note: StateFlow<NoteItem?> = repository.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allCategories: StateFlow<List<CategoryEntity>> = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
        private set
    var text by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var deleted by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set

    // Zuletzt aus der Datenbank übernommene Werte – damit KI-Ergebnisse (Transkript, Titel)
    // in die Eingabefelder fließen, solange der Nutzer sie nicht selbst geändert hat.
    private var syncedTitle = ""
    private var syncedText = ""
    private var saveJob: Job? = null
    private val player = AudioPlayer(onFinished = { isPlaying = false })

    init {
        viewModelScope.launch {
            note.collect { item ->
                if (item == null) return@collect
                if (!loaded || title == syncedTitle) title = item.title
                if (!loaded || text == syncedText) text = item.text
                syncedTitle = item.title
                syncedText = item.text
                loaded = true
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value
        scheduleSave()
    }

    fun onTextChange(value: String) {
        text = value
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            save()
        }
    }

    private suspend fun save() {
        if (!loaded || deleted) return
        syncedTitle = title
        syncedText = text
        repository.updateContent(noteId, title, text)
    }

    fun toggleDone() = viewModelScope.launch {
        val current = note.value ?: return@launch
        repository.setStatus(noteId, if (current.status == NoteStatus.DONE) NoteStatus.OPEN else NoteStatus.DONE)
    }

    fun confirmCategory(categoryId: Long) = viewModelScope.launch { repository.confirmCategory(noteId, categoryId) }

    fun removeCategory(categoryId: Long) = viewModelScope.launch { repository.removeCategory(noteId, categoryId) }

    fun addCategory(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repository.addCategory(noteId, name)
    }

    fun recategorize() = viewModelScope.launch {
        save()
        repository.recategorize(noteId)
    }

    fun retry() = viewModelScope.launch {
        save()
        repository.retryProcessing(noteId)
    }

    fun delete() = viewModelScope.launch {
        saveJob?.cancel()
        deleted = true
        player.stop()
        repository.delete(noteId)
    }

    fun togglePlayback() {
        if (isPlaying) {
            player.stop()
            isPlaying = false
            return
        }
        viewModelScope.launch {
            try {
                val fileName = repository.audioFileName(noteId) ?: return@launch
                val file = withContext(Dispatchers.IO) { container.audioStore.decryptForPlayback(fileName) }
                player.play(file)
                isPlaying = true
                playbackError = null
            } catch (e: Exception) {
                playbackError = "Aufnahme kann nicht abgespielt werden"
            }
        }
    }

    override fun onCleared() {
        player.stop()
        if (saveJob?.isActive == true && !deleted) {
            val title = title
            val text = text
            container.appScope.launch { repository.updateContent(noteId, title, text) }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 600L
    }
}
