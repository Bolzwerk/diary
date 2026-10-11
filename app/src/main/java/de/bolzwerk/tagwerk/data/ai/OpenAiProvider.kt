package de.bolzwerk.tagwerk.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Anbindung an die OpenAI-API bzw. einen kompatiblen Endpunkt (eigene Base-URL). */
class OpenAiProvider(
    private val client: OkHttpClient = defaultClient(),
) : AiProvider {

    override suspend fun transcribe(config: AiConfig, audio: ByteArray, fileName: String, glossary: String): TranscriptionResult {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.transcriptionModel)
            .addFormDataPart("language", "de")
            .addFormDataPart("response_format", "json")
            .apply { if (glossary.isNotBlank()) addFormDataPart("prompt", "Begriffe und Namen: ${glossary.trim()}") }
            .addFormDataPart("file", fileName, audio.toRequestBody("audio/mp4".toMediaType()))
            .build()
        val raw = send(config.transcriptionEndpoint, "audio/transcriptions") { post(body) }
        val parsed = decode<TranscriptionResponse>(raw)
        return TranscriptionResult(
            text = parsed.text.trim(),
            usage = TokenUsage(parsed.usage?.inputTokens ?: 0, parsed.usage?.outputTokens ?: 0),
        )
    }

    override suspend fun categorizeNote(config: AiConfig, noteText: String, existingCategories: List<String>): CategorizationResult {
        val completion = chatJson(
            config = config,
            system = Prompts.categorizationSystem(existingCategories),
            user = Prompts.categorizationUser(noteText),
            schemaName = "note_categorization",
            schema = Prompts.categorizationSchema,
        )
        return parseCategorization(completion.content, completion.usage)
    }

    override suspend fun testConnection(config: AiConfig): List<ConnectionCheck> {
        val chat = try {
            val body = buildJsonObject {
                put("model", config.chatModel)
                putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "Antworte nur mit: OK") } }
            }
            parseChatResult(send(config.chatEndpoint, "chat/completions") { post(body.toString().toRequestBody(JSON_MEDIA_TYPE)) })
            ConnectionCheck("Text (${config.chatModel})", ok = true, message = "funktioniert")
        } catch (e: AiException) {
            ConnectionCheck("Text (${config.chatModel})", ok = false, message = e.message.orEmpty())
        }

        val transcription = try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", config.transcriptionModel)
                .addFormDataPart("language", "de")
                .addFormDataPart("file", "test.wav", silentWav().toRequestBody("audio/wav".toMediaType()))
                .build()
            send(config.transcriptionEndpoint, "audio/transcriptions") { post(body) }
            ConnectionCheck("Transkription (${config.transcriptionModel})", ok = true, message = "funktioniert")
        } catch (e: AiException) {
            if (e.kind == AiException.Kind.BAD_REQUEST) {
                // Der Endpunkt existiert, lehnt nur das stille Testaudio ab – für echte Aufnahmen reicht das.
                ConnectionCheck("Transkription (${config.transcriptionModel})", ok = true, message = "erreichbar (${e.message})")
            } else {
                ConnectionCheck("Transkription (${config.transcriptionModel})", ok = false, message = e.message.orEmpty())
            }
        }
        return listOf(chat, transcription)
    }

    /**
     * Fordert eine JSON-Antwort an. Zuerst mit striktem JSON-Schema (Structured Outputs);
     * lehnt der Endpunkt das ab, folgen einfachere Varianten für kompatible Server.
     */
    private suspend fun chatJson(
        config: AiConfig,
        system: String,
        user: String,
        schemaName: String,
        schema: JsonObject,
    ): ChatResult {
        var lastError: AiException? = null
        for (mode in ResponseFormatMode.entries) {
            val systemPrompt = if (mode == ResponseFormatMode.JSON_SCHEMA) system else "$system\n\nJSON-Schema der Antwort:\n$schema"
            val body = buildJsonObject {
                put("model", config.chatModel)
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", systemPrompt) }
                    addJsonObject { put("role", "user"); put("content", user) }
                }
                when (mode) {
                    ResponseFormatMode.JSON_SCHEMA -> putJsonObject("response_format") {
                        put("type", "json_schema")
                        putJsonObject("json_schema") {
                            put("name", schemaName)
                            put("strict", true)
                            put("schema", schema)
                        }
                    }
                    ResponseFormatMode.JSON_OBJECT -> putJsonObject("response_format") { put("type", "json_object") }
                    ResponseFormatMode.PLAIN -> Unit
                }
            }
            try {
                val raw = send(config.chatEndpoint, "chat/completions") { post(body.toString().toRequestBody(JSON_MEDIA_TYPE)) }
                return parseChatResult(raw)
            } catch (e: AiException) {
                if (e.kind != AiException.Kind.BAD_REQUEST) throw e
                lastError = e
            }
        }
        throw lastError ?: AiException(AiException.Kind.BAD_REQUEST, "Anfrage abgelehnt")
    }

    /**
     * Sendet eine Anfrage an `{baseUrl}/{path}`. Antwortet der Server mit 404/405 und enthält die
     * Base-URL keine Versionsangabe, wird automatisch `{baseUrl}/v1/{path}` versucht – ein häufiger
     * Einrichtungsfehler bei eigenen Servern.
     */
    private suspend fun send(endpoint: AiEndpoint, path: String, configure: Request.Builder.() -> Request.Builder): String {
        if (endpoint.baseUrl.isBlank() || endpoint.apiKey.isBlank()) {
            throw AiException(AiException.Kind.NOT_CONFIGURED, "KI-Verbindung ist nicht eingerichtet")
        }
        val candidates = baseUrlCandidates(endpoint.baseUrl)
        var notFound: AiException? = null
        for (base in candidates) {
            val url = runCatching { buildUrl(base, path) }.getOrElse {
                throw AiException(AiException.Kind.NOT_CONFIGURED, "Ungültige Base-URL: ${endpoint.baseUrl}")
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer ${endpoint.apiKey}").configure().build()
            try {
                return execute(request)
            } catch (e: AiException) {
                if (e.kind != AiException.Kind.NOT_FOUND) throw e
                if (notFound == null) notFound = e
            }
        }
        throw notFound!!
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException(AiException.Kind.NETWORK, "Keine Verbindung zu ${displayUrl(request)}: ${e.message}", e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw httpError(it.code, text, "${request.method} ${displayUrl(request)}")
            text
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        private val VERSION_SUFFIX = Regex("/v\\d+[a-z0-9]*$", RegexOption.IGNORE_CASE)

        /** Base-URLs, die nacheinander probiert werden: wie eingegeben, ohne Versionsangabe zusätzlich mit `/v1`. */
        internal fun baseUrlCandidates(baseUrl: String): List<String> {
            val base = baseUrl.trim().trimEnd('/')
            return if (VERSION_SUFFIX.containsMatchIn(base)) listOf(base) else listOf(base, "$base/v1")
        }

        /** URL ohne Query-Parameter für Fehlermeldungen. */
        private fun displayUrl(request: Request): String =
            request.url.newBuilder().query(null).build().toString()

        /** Kurzes stilles WAV (16 kHz, mono, 16 Bit) für den Verbindungstest der Transkription. */
        internal fun silentWav(durationMs: Int = 500): ByteArray {
            val sampleRate = 16_000
            val dataSize = sampleRate * durationMs / 1000 * 2
            val buffer = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            buffer.put("data".toByteArray()).putInt(dataSize)
            return buffer.array()
        }

        /** Verbindet Base-URL und Pfad, unabhängig von abschließenden Schrägstrichen. */
        fun buildUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://"))
            return "$base/${path.trimStart('/')}"
        }

        internal fun httpError(code: Int, body: String, target: String = ""): AiException {
            val detail = (runCatching { json.decodeFromString<ErrorEnvelope>(body).error?.message }.getOrNull()
                ?: htmlToText(body)).take(200)
            val kind = when (code) {
                401, 403 -> AiException.Kind.AUTH
                404, 405 -> AiException.Kind.NOT_FOUND
                429 -> AiException.Kind.RATE_LIMIT
                400, 413, 415, 422 -> AiException.Kind.BAD_REQUEST
                else -> AiException.Kind.SERVER
            }
            val prefix = when (kind) {
                AiException.Kind.AUTH -> "API-Key ungültig oder keine Berechtigung"
                AiException.Kind.NOT_FOUND -> "Endpunkt nicht gefunden – Base-URL prüfen (endet meist auf /v1) " +
                    "bzw. ob der Server diese Funktion anbietet"
                AiException.Kind.RATE_LIMIT -> "Limit erreicht – später erneut versuchen"
                AiException.Kind.BAD_REQUEST -> "Anfrage abgelehnt"
                else -> "Fehler beim KI-Dienst"
            }
            return AiException(
                kind,
                buildString {
                    append("$prefix (HTTP $code")
                    if (target.isNotBlank()) append(", $target")
                    append(")")
                    if (detail.isNotBlank()) append(": $detail")
                },
            )
        }

        /** Reduziert HTML-Fehlerseiten (z. B. Express „Cannot POST …“) auf ihren Text. */
        private fun htmlToText(body: String): String =
            body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

        internal fun parseChatResult(raw: String): ChatResult {
            val response = decode<ChatCompletionResponse>(raw)
            val message = response.choices.firstOrNull()?.message
                ?: throw AiException(AiException.Kind.INVALID_RESPONSE, "Leere Antwort vom KI-Dienst")
            message.refusal?.let { throw AiException(AiException.Kind.INVALID_RESPONSE, "KI hat abgelehnt: $it") }
            val content = message.content
                ?: throw AiException(AiException.Kind.INVALID_RESPONSE, "Antwort enthält keinen Text")
            return ChatResult(
                content = content,
                usage = TokenUsage(response.usage?.promptTokens ?: 0, response.usage?.completionTokens ?: 0),
            )
        }

        internal fun parseCategorization(content: String, usage: TokenUsage = TokenUsage()): CategorizationResult {
            val parsed = decode<CategorizationJson>(stripCodeFence(content))
            return CategorizationResult(
                title = parsed.title.trim().trim('"', '„', '“').removeSuffix("."),
                categories = parsed.categories.map { SuggestedCategory(it.name, it.isNew) },
                usage = usage,
            )
        }

        /** Entfernt ```json ... ``` – manche kompatible Server liefern JSON in Markdown verpackt. */
        internal fun stripCodeFence(content: String): String {
            val trimmed = content.trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed.substringAfter('\n').substringBeforeLast("```").trim()
        }

        private inline fun <reified T> decode(raw: String): T = try {
            json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            throw AiException(AiException.Kind.INVALID_RESPONSE, "Unerwartete Antwort vom KI-Dienst", e)
        }
    }

    private enum class ResponseFormatMode { JSON_SCHEMA, JSON_OBJECT, PLAIN }

    internal data class ChatResult(val content: String, val usage: TokenUsage)

    @Serializable
    internal data class TranscriptionResponse(val text: String, val usage: TranscriptionUsage? = null)

    @Serializable
    internal data class TranscriptionUsage(
        @SerialName("input_tokens") val inputTokens: Int? = null,
        @SerialName("output_tokens") val outputTokens: Int? = null,
    )

    @Serializable
    internal data class ChatCompletionResponse(val choices: List<Choice> = emptyList(), val usage: ChatUsage? = null)

    @Serializable
    internal data class Choice(val message: Message? = null)

    @Serializable
    internal data class Message(val content: String? = null, val refusal: String? = null)

    @Serializable
    internal data class ChatUsage(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
    )

    @Serializable
    internal data class CategorizationJson(val title: String = "", val categories: List<CategoryJson> = emptyList())

    @Serializable
    internal data class CategoryJson(val name: String, @SerialName("is_new") val isNew: Boolean = false)

    @Serializable
    internal data class ModelList(val data: List<JsonObject> = emptyList())

    @Serializable
    internal data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    internal data class ErrorBody(val message: String? = null)
}
