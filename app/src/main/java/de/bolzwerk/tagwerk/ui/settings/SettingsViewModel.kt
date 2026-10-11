package de.bolzwerk.tagwerk.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bolzwerk.tagwerk.AppContainer
import de.bolzwerk.tagwerk.data.ai.ConnectionCheck
import de.bolzwerk.tagwerk.data.db.UsageSummary
import de.bolzwerk.tagwerk.data.settings.AppSettings
import de.bolzwerk.tagwerk.data.settings.AudioRetention
import de.bolzwerk.tagwerk.data.settings.CategorizationMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data class Done(val checks: List<ConnectionCheck>) : ConnectionTest
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.settingsRepository

    /** Bearbeitungsstand; wird erst mit „Speichern“ übernommen. */
    var draft by mutableStateOf(repository.current)
        private set
    var connectionTest by mutableStateOf<ConnectionTest>(ConnectionTest.Idle)
        private set
    var savedMessage by mutableStateOf<String?>(null)
        private set

    val usage: StateFlow<UsageSummary> = container.database.usageDao()
        .observeSummary(startOfMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageSummary(0.0, 0, 0))

    val hasUnsavedChanges: Boolean get() = draft != repository.current

    fun edit(transform: (AppSettings) -> AppSettings) {
        draft = transform(draft)
        savedMessage = null
    }

    fun setCategorizationMode(mode: CategorizationMode) {
        edit { it.copy(categorizationMode = mode) }
        save()
    }

    fun setAudioRetention(retention: AudioRetention) {
        edit { it.copy(audioRetention = retention) }
        save()
    }

    fun save() {
        val before = repository.current
        repository.update { draft }
        draft = repository.current
        savedMessage = "Gespeichert"
        val aiChanged = before.toAiConfig() != draft.toAiConfig()
        if (aiChanged && draft.isAiConfigured) {
            viewModelScope.launch {
                container.noteRepository.reschedulePending(replace = true)
                container.noteRepository.retryAllFailed()
            }
        }
    }

    fun saveAndTest() {
        save()
        val settings = repository.current
        connectionTest = ConnectionTest.Running
        viewModelScope.launch {
            connectionTest = ConnectionTest.Done(container.aiProvider.testConnection(settings.toAiConfig()))
        }
    }

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
