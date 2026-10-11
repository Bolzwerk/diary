package de.bolzwerk.tagwerk.data.settings

import android.content.Context
import android.content.SharedPreferences
import de.bolzwerk.tagwerk.core.security.SecretStore
import de.bolzwerk.tagwerk.data.ai.AiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CategorizationMode { ASSIGN, SUGGEST }

/** Wie lange Originalaufnahmen aufbewahrt werden. `null` = unbegrenzt, 0 = direkt nach der Transkription löschen. */
enum class AudioRetention(val days: Int?) { DELETE_AFTER_TRANSCRIPTION(0), DAYS_30(30), DAYS_90(90), FOREVER(null) }

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val chatModel: String = DEFAULT_CHAT_MODEL,
    /** Optional: eigener Endpunkt nur für die Transkription; leer = wie oben. */
    val transcriptionBaseUrl: String = "",
    val transcriptionApiKey: String = "",
    val glossary: String = "",
    val categorizationMode: CategorizationMode = CategorizationMode.ASSIGN,
    val audioRetention: AudioRetention = AudioRetention.DAYS_30,
) {
    val isAiConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    fun toAiConfig() = AiConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        transcriptionModel = transcriptionModel,
        chatModel = chatModel,
        transcriptionBaseUrl = transcriptionBaseUrl,
        transcriptionApiKey = transcriptionApiKey,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        const val DEFAULT_CHAT_MODEL = "gpt-4o-mini"
    }
}

/** Einstellungen; der API-Key wird verschlüsselt im [SecretStore] abgelegt. */
class SettingsRepository(context: Context, private val secrets: SecretStore) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(state.value)
        prefs.edit()
            .putString(KEY_BASE_URL, updated.baseUrl.trim())
            .putString(KEY_TRANSCRIPTION_MODEL, updated.transcriptionModel.trim())
            .putString(KEY_CHAT_MODEL, updated.chatModel.trim())
            .putString(KEY_TRANSCRIPTION_BASE_URL, updated.transcriptionBaseUrl.trim())
            .putString(KEY_GLOSSARY, updated.glossary)
            .putString(KEY_CATEGORIZATION_MODE, updated.categorizationMode.name)
            .putString(KEY_AUDIO_RETENTION, updated.audioRetention.name)
            .apply()
        if (updated.apiKey != state.value.apiKey) secrets.putString(KEY_API_KEY, updated.apiKey.trim())
        if (updated.transcriptionApiKey != state.value.transcriptionApiKey) {
            secrets.putString(KEY_TRANSCRIPTION_API_KEY, updated.transcriptionApiKey.trim())
        }
        state.value = updated.copy(
            apiKey = updated.apiKey.trim(),
            baseUrl = updated.baseUrl.trim(),
            transcriptionModel = updated.transcriptionModel.trim(),
            chatModel = updated.chatModel.trim(),
            transcriptionBaseUrl = updated.transcriptionBaseUrl.trim(),
            transcriptionApiKey = updated.transcriptionApiKey.trim(),
        )
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            baseUrl = prefs.getString(KEY_BASE_URL, null) ?: defaults.baseUrl,
            apiKey = runCatching { secrets.getString(KEY_API_KEY) }.getOrNull().orEmpty(),
            transcriptionModel = prefs.getString(KEY_TRANSCRIPTION_MODEL, null) ?: defaults.transcriptionModel,
            chatModel = prefs.getString(KEY_CHAT_MODEL, null) ?: defaults.chatModel,
            transcriptionBaseUrl = prefs.getString(KEY_TRANSCRIPTION_BASE_URL, null).orEmpty(),
            transcriptionApiKey = runCatching { secrets.getString(KEY_TRANSCRIPTION_API_KEY) }.getOrNull().orEmpty(),
            glossary = prefs.getString(KEY_GLOSSARY, null).orEmpty(),
            categorizationMode = enumOrDefault(prefs.getString(KEY_CATEGORIZATION_MODE, null), defaults.categorizationMode),
            audioRetention = enumOrDefault(prefs.getString(KEY_AUDIO_RETENTION, null), defaults.audioRetention),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_TRANSCRIPTION_MODEL = "transcription_model"
        const val KEY_CHAT_MODEL = "chat_model"
        const val KEY_TRANSCRIPTION_BASE_URL = "transcription_base_url"
        const val KEY_TRANSCRIPTION_API_KEY = "transcription_api_key"
        const val KEY_GLOSSARY = "glossary"
        const val KEY_CATEGORIZATION_MODE = "categorization_mode"
        const val KEY_AUDIO_RETENTION = "audio_retention"
    }
}
