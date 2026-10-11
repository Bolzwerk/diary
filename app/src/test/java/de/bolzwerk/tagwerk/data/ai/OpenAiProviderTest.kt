package de.bolzwerk.tagwerk.data.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAiProviderTest {

    private lateinit var server: MockWebServer
    private val provider = OpenAiProvider()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = AiConfig(
        baseUrl = server.url("/v1/").toString(),
        apiKey = "sk-test",
        transcriptionModel = "whisper-1",
        chatModel = "gpt-test",
    )

    private fun chatResponse(content: String) = MockResponse().setBody(
        """{"choices":[{"message":{"content":${Json.encodeToString(String.serializer(), content)}}}],"usage":{"prompt_tokens":12,"completion_tokens":5}}"""
    )

    @Test
    fun `transcribe posts multipart with model and language`() = runTest {
        server.enqueue(MockResponse().setBody("""{"text":"  Reifenwechsel buchen  "}"""))

        val result = provider.transcribe(config(), byteArrayOf(1, 2, 3), "a.m4a", glossary = "Tagwerk")

        assertEquals("Reifenwechsel buchen", result.text)
        val request = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("whisper-1"))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("Begriffe und Namen: Tagwerk"))
    }

    @Test
    fun `categorize uses json schema and parses result`() = runTest {
        server.enqueue(chatResponse("""{"title":"Reifen wechseln.","categories":[{"name":"Auto","is_new":true}]}"""))

        val result = provider.categorizeNote(config(), "Reifenwechsel bis Ende Oktober buchen", listOf("Arbeit"))

        assertEquals("Reifen wechseln", result.title)
        assertEquals(listOf(SuggestedCategory("Auto", isNew = true)), result.categories)
        assertEquals(TokenUsage(12, 5), result.usage)
        val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("gpt-test", sent["model"]!!.jsonPrimitive.content)
        assertEquals("json_schema", sent["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `falls back to json_object when schema is rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"response_format not supported"}}"""))
        server.enqueue(chatResponse("```json\n{\"title\":\"Einkauf\",\"categories\":[]}\n```"))

        val result = provider.categorizeNote(config(), "Milch kaufen", emptyList())

        assertEquals("Einkauf", result.title)
        server.takeRequest()
        val second = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("json_object", second["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `auth errors are not retryable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key"}}"""))
        try {
            provider.transcribe(config(), byteArrayOf(1), "a.m4a", "")
            fail("Exception erwartet")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.AUTH, e.kind)
            assertTrue(!e.retryable)
            assertTrue(e.message!!.contains("Incorrect API key"))
        }
    }

    @Test
    fun `server errors are retryable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            provider.transcribe(config(), byteArrayOf(1), "a.m4a", "")
            fail("Exception erwartet")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.SERVER, e.kind)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun `missing v1 in base url is retried automatically`() = runTest {
        server.enqueue(expressNotFound("/audio/transcriptions"))
        server.enqueue(MockResponse().setBody("""{"text":"Hallo"}"""))

        val result = provider.transcribe(config().copy(baseUrl = server.url("/").toString()), byteArrayOf(1), "a.m4a", "")

        assertEquals("Hallo", result.text)
        assertEquals("/audio/transcriptions", server.takeRequest().path)
        assertEquals("/v1/audio/transcriptions", server.takeRequest().path)
    }

    @Test
    fun `express 404 is reported with url and readable text`() = runTest {
        server.enqueue(expressNotFound("/v1/audio/transcriptions"))
        try {
            provider.transcribe(config(), byteArrayOf(1), "a.m4a", "")
            fail("Exception erwartet")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.NOT_FOUND, e.kind)
            assertTrue(!e.retryable)
            val message = e.message!!
            assertTrue(message, message.contains("POST " + server.url("/v1/audio/transcriptions")))
            assertTrue(message, message.contains("Cannot POST /v1/audio/transcriptions"))
            assertTrue(message, !message.contains("<pre>"))
        }
        // Base-URL enthält bereits /v1 → kein zweiter Versuch
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `separate transcription endpoint and key are used`() = runTest {
        val audioServer = MockWebServer().apply { start() }
        try {
            audioServer.enqueue(MockResponse().setBody("""{"text":"Hallo"}"""))
            val cfg = config().copy(transcriptionBaseUrl = audioServer.url("/v1").toString(), transcriptionApiKey = "sk-audio")

            provider.transcribe(cfg, byteArrayOf(1), "a.m4a", "")

            val request = audioServer.takeRequest()
            assertEquals("/v1/audio/transcriptions", request.path)
            assertEquals("Bearer sk-audio", request.getHeader("Authorization"))
            assertEquals(0, server.requestCount)
        } finally {
            audioServer.shutdown()
        }
    }

    @Test
    fun `connection test reports chat and transcription separately`() = runTest {
        server.enqueue(chatResponse("OK"))
        server.enqueue(expressNotFound("/v1/audio/transcriptions"))

        val checks = provider.testConnection(config())

        assertEquals(2, checks.size)
        assertTrue(checks[0].ok)
        assertTrue(!checks[1].ok)
        assertTrue(checks[1].message.contains("Endpunkt nicht gefunden"))
    }

    @Test
    fun `connection test treats rejected test audio as reachable`() = runTest {
        server.enqueue(chatResponse("OK"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Audio file is too short"}}"""))

        val checks = provider.testConnection(config())

        assertTrue(checks.all { it.ok })
    }

    @Test
    fun `listModels returns ids`() = runTest {
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"gpt-4o-mini","owned_by":"x"},{"id":"whisper-1"}]}"""))

        val models = provider.listModels(config().chatEndpoint)

        assertEquals(listOf("gpt-4o-mini", "whisper-1"), models)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun `silent wav has valid header`() {
        val wav = OpenAiProvider.silentWav(500)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44 + 16_000, wav.size)
    }

    @Test
    fun `base url candidates add v1 only when no version is present`() {
        assertEquals(listOf("https://x.de", "https://x.de/v1"), OpenAiProvider.baseUrlCandidates("https://x.de/"))
        assertEquals(listOf("https://x.de/api", "https://x.de/api/v1"), OpenAiProvider.baseUrlCandidates("https://x.de/api"))
        assertEquals(listOf("https://x.de/v1"), OpenAiProvider.baseUrlCandidates("https://x.de/v1/"))
        assertEquals(listOf("https://x.de/v1beta"), OpenAiProvider.baseUrlCandidates("https://x.de/v1beta"))
    }

    private fun expressNotFound(path: String) = MockResponse()
        .setResponseCode(404)
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!DOCTYPE html><html><head><title>Error</title></head><body><pre>Cannot POST $path</pre></body></html>")

    @Test
    fun `missing configuration is reported`() = runTest {
        try {
            provider.transcribe(config().copy(apiKey = ""), byteArrayOf(1), "a.m4a", "")
            fail("Exception erwartet")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.NOT_CONFIGURED, e.kind)
        }
    }

    @Test
    fun `buildUrl tolerates trailing slashes`() {
        assertEquals("https://api.openai.com/v1/models", OpenAiProvider.buildUrl("https://api.openai.com/v1/", "/models"))
        assertEquals("http://10.0.0.2:8080/v1/chat/completions", OpenAiProvider.buildUrl(" http://10.0.0.2:8080/v1 ", "chat/completions"))
    }

    @Test
    fun `stripCodeFence leaves plain json untouched`() {
        assertEquals("{\"a\":1}", OpenAiProvider.stripCodeFence("  {\"a\":1} "))
        assertEquals("{\"a\":1}", OpenAiProvider.stripCodeFence("```json\n{\"a\":1}\n```"))
    }
}
