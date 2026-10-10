package de.bolzwerk.tagwerk.data.ai

/** Verbindungsdaten für einen OpenAI-kompatiblen Endpunkt. */
data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val transcriptionModel: String,
    val chatModel: String,
)

data class TokenUsage(val inputTokens: Int = 0, val outputTokens: Int = 0)

data class TranscriptionResult(val text: String, val usage: TokenUsage = TokenUsage())

data class SuggestedCategory(val name: String, val isNew: Boolean)

data class CategorizationResult(
    val title: String,
    val categories: List<SuggestedCategory>,
    val usage: TokenUsage = TokenUsage(),
)

class AiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { NOT_CONFIGURED, AUTH, NETWORK, RATE_LIMIT, SERVER, BAD_REQUEST, INVALID_RESPONSE }

    /** Ob ein späterer erneuter Versuch sinnvoll ist (z. B. Netzwerkfehler). */
    val retryable: Boolean get() = kind == Kind.NETWORK || kind == Kind.RATE_LIMIT || kind == Kind.SERVER
}

/** Abstraktion der KI-Dienste, damit Anbieter und Tests austauschbar sind. */
interface AiProvider {
    suspend fun transcribe(config: AiConfig, audio: ByteArray, fileName: String, glossary: String): TranscriptionResult

    suspend fun categorizeNote(config: AiConfig, noteText: String, existingCategories: List<String>): CategorizationResult

    /** Prüft Erreichbarkeit und API-Key; liefert bei Erfolg eine kurze Beschreibung. */
    suspend fun testConnection(config: AiConfig): String
}
