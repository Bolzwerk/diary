package de.bolzwerk.tagwerk.ui.record

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.TagwerkApp
import de.bolzwerk.tagwerk.audio.AudioRecorder
import de.bolzwerk.tagwerk.data.repo.NoteItem
import de.bolzwerk.tagwerk.ui.components.WAVEFORM_BARS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RecordingUiState {
    data object Idle : RecordingUiState

    data class Recording(
        val elapsedMs: Long,
        val levels: List<Float>,
        val paused: Boolean,
        /** true, solange der Button gedrückt gehalten wird (Push-to-talk). */
        val holding: Boolean,
    ) : RecordingUiState

    data class Saved(val noteId: Long, val offline: Boolean) : RecordingUiState

    data class Error(val message: String) : RecordingUiState
}

/**
 * Steuert die Aufnahme einer Notiz. Ein kurzer Tipp startet die Aufnahme (beenden über „Fertig“),
 * langes Halten nimmt auf, bis losgelassen wird.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as TagwerkApp).container
    private val recorder = AudioRecorder(application)

    private val _state = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    /** Die zuletzt gespeicherte Notiz, damit das Ergebnis live im Aufnahme-Panel erscheint. */
    val savedNote: StateFlow<NoteItem?> = _state
        .map { (it as? RecordingUiState.Saved)?.noteId }
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.noteRepository.observeNote(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var tickerJob: Job? = null
    private var pressStartedAt = 0L
    private var startedByThisPress = false

    fun onPress() {
        when (_state.value) {
            is RecordingUiState.Recording -> startedByThisPress = false
            else -> {
                startedByThisPress = start()
                pressStartedAt = SystemClock.elapsedRealtime()
                if (startedByThisPress) updateRecording { it.copy(holding = true) }
            }
        }
    }

    fun onRelease() {
        val current = _state.value as? RecordingUiState.Recording ?: return
        if (startedByThisPress) {
            val heldMs = SystemClock.elapsedRealtime() - pressStartedAt
            if (heldMs >= HOLD_THRESHOLD_MS) finish() else _state.value = current.copy(holding = false)
        } else {
            finish()
        }
        startedByThisPress = false
    }

    fun togglePause() {
        val current = _state.value as? RecordingUiState.Recording ?: return
        if (current.paused) recorder.resume() else recorder.pause()
        _state.value = current.copy(paused = !current.paused)
    }

    fun cancel() {
        tickerJob?.cancel()
        recorder.cancel()
        _state.value = RecordingUiState.Idle
    }

    fun finish() {
        tickerJob?.cancel()
        val result = recorder.stop()
        if (result == null) {
            _state.value = RecordingUiState.Error("Aufnahme zu kurz oder fehlgeschlagen")
            return
        }
        val (file, durationMs) = result
        if (durationMs < MIN_DURATION_MS) {
            file.delete()
            _state.value = RecordingUiState.Error("Aufnahme zu kurz – zum Diktieren tippen oder gedrückt halten")
            return
        }
        viewModelScope.launch {
            val id = container.noteRepository.createDictatedNote(file, durationMs)
            _state.value = RecordingUiState.Saved(id, offline = !isOnline())
        }
    }

    fun dismiss() {
        if (_state.value is RecordingUiState.Recording) return
        _state.value = RecordingUiState.Idle
    }

    private fun start(): Boolean {
        return try {
            recorder.start()
            _state.value = RecordingUiState.Recording(0, emptyList(), paused = false, holding = false)
            tickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    updateRecording {
                        val level = if (it.paused) 0f else recorder.amplitude()
                        it.copy(elapsedMs = recorder.elapsedMs(), levels = (it.levels + level).takeLast(WAVEFORM_BARS))
                    }
                }
            }
            true
        } catch (e: Exception) {
            _state.value = RecordingUiState.Error("Mikrofon nicht verfügbar: ${e.message}")
            false
        }
    }

    private inline fun updateRecording(transform: (RecordingUiState.Recording) -> RecordingUiState.Recording) {
        val current = _state.value as? RecordingUiState.Recording ?: return
        _state.value = transform(current)
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        recorder.cancel()
    }

    private companion object {
        const val HOLD_THRESHOLD_MS = 500L
        const val MIN_DURATION_MS = 700L
        const val TICK_MS = 80L
    }
}
