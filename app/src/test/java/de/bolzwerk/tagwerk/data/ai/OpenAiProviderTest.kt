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
            provider.testConnection(config())
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
            provider.testConnection(config())
            fail("Exception erwartet")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.SERVER, e.kind)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun `missing configuration is reported`() = runTest {
        try {
            provider.testConnection(config().copy(apiKey = ""))
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
