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
        val request = requestBuilder(config, "audio/transcriptions").post(body).build()
        val raw = execute(request)
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

    override suspend fun testConnection(config: AiConfig): String {
        val request = requestBuilder(config, "models").get().build()
        val raw = execute(request)
        val count = runCatching { json.decodeFromString<ModelList>(raw).data.size }.getOrNull()
        return if (count != null) "Verbindung erfolgreich ($count Modelle verfügbar)" else "Verbindung erfolgreich"
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
            val request = requestBuilder(config, "chat/completions")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            try {
                val raw = execute(request)
                return parseChatResult(raw)
            } catch (e: AiException) {
                if (e.kind != AiException.Kind.BAD_REQUEST) throw e
                lastError = e
            }
        }
        throw lastError ?: AiException(AiException.Kind.BAD_REQUEST, "Anfrage abgelehnt")
    }

    private fun requestBuilder(config: AiConfig, path: String): Request.Builder {
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            throw AiException(AiException.Kind.NOT_CONFIGURED, "KI-Verbindung ist nicht eingerichtet")
        }
        val url = runCatching { buildUrl(config.baseUrl, path) }.getOrElse {
            throw AiException(AiException.Kind.NOT_CONFIGURED, "Ungültige Base-URL: ${config.baseUrl}")
        }
        return Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}")
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException(AiException.Kind.NETWORK, "Keine Verbindung zum KI-Dienst: ${e.message}", e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw httpError(it.code, text)
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

        /** Verbindet Base-URL und Pfad, unabhängig von abschließenden Schrägstrichen. */
        fun buildUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://"))
            return "$base/${path.trimStart('/')}"
        }

        internal fun httpError(code: Int, body: String): AiException {
            val detail = runCatching { json.decodeFromString<ErrorEnvelope>(body).error?.message }.getOrNull()
                ?: body.take(200)
            val kind = when (code) {
                401, 403 -> AiException.Kind.AUTH
                429 -> AiException.Kind.RATE_LIMIT
                400, 404, 415, 422 -> AiException.Kind.BAD_REQUEST
                in 500..599 -> AiException.Kind.SERVER
                else -> AiException.Kind.SERVER
            }
            val prefix = when (kind) {
                AiException.Kind.AUTH -> "API-Key ungültig oder keine Berechtigung"
                AiException.Kind.RATE_LIMIT -> "Limit erreicht – später erneut versuchen"
                AiException.Kind.BAD_REQUEST -> "Anfrage abgelehnt"
                else -> "Fehler beim KI-Dienst"
            }
            return AiException(kind, "$prefix (HTTP $code)${if (detail.isNotBlank()) ": $detail" else ""}")
        }

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
