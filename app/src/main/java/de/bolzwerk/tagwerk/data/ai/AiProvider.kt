package de.bolzwerk.tagwerk.data.ai

/** Basisadresse und Schlüssel eines OpenAI-kompatiblen Endpunkts. */
data class AiEndpoint(val baseUrl: String, val apiKey: String)

/**
 * Verbindungsdaten. Für die Transkription kann ein eigener Endpunkt hinterlegt werden
 * (z. B. wenn der Chat-Server keine Audio-Schnittstelle anbietet); leer = gleicher Endpunkt.
 */
data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val transcriptionModel: String,
    val chatModel: String,
    val transcriptionBaseUrl: String = "",
    val transcriptionApiKey: String = "",
) {
    val chatEndpoint: AiEndpoint get() = AiEndpoint(baseUrl, apiKey)

    val transcriptionEndpoint: AiEndpoint
        get() = AiEndpoint(
            baseUrl = transcriptionBaseUrl.ifBlank { baseUrl },
            apiKey = transcriptionApiKey.ifBlank { apiKey },
        )
}

/** Ergebnis eines Verbindungstests – je geprüfter Funktion ein Eintrag. */
data class ConnectionCheck(val name: String, val ok: Boolean, val message: String)

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
    enum class Kind { NOT_CONFIGURED, AUTH, NOT_FOUND, NETWORK, RATE_LIMIT, SERVER, BAD_REQUEST, INVALID_RESPONSE }

    /** Ob ein späterer erneuter Versuch sinnvoll ist (z. B. Netzwerkfehler). */
    val retryable: Boolean get() = kind == Kind.NETWORK || kind == Kind.RATE_LIMIT || kind == Kind.SERVER
}

/** Abstraktion der KI-Dienste, damit Anbieter und Tests austauschbar sind. */
interface AiProvider {
    suspend fun transcribe(config: AiConfig, audio: ByteArray, fileName: String, glossary: String): TranscriptionResult

    suspend fun categorizeNote(config: AiConfig, noteText: String, existingCategories: List<String>): CategorizationResult

    /** Prüft Chat- und Transkriptions-Endpunkt einzeln (Erreichbarkeit, Pfad, API-Key). */
    suspend fun testConnection(config: AiConfig): List<ConnectionCheck>
}
