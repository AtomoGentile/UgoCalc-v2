package com.ugocalc.app.data.remote.ai

import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.client.MalformedJsonException
import com.ugocalc.app.data.remote.ai.client.RateLimitException
import com.ugocalc.app.data.remote.ai.client.ServiceUnavailableException
import com.ugocalc.app.data.remote.ai.client.buildHttpErrorException
import com.ugocalc.app.data.remote.ai.client.extractAnthropicText
import com.ugocalc.app.data.remote.ai.client.extractBedrockConverseText
import com.ugocalc.app.data.remote.ai.client.extractGeminiText
import com.ugocalc.app.data.remote.ai.client.extractOpenAiText
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsClient
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AiCallExecutorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── extractJsonPayload ─────────────────────────────────────────────────────

    @Test
    fun `extractJsonPayload returns pure JSON unchanged`() {
        val raw = """{"alimenti":[{"nome":"Pasta","peso_stimato_g":120}]}"""
        assertEquals(raw, AiCallExecutor.extractJsonPayload(raw))
    }

    @Test
    fun `extractJsonPayload extracts inner fenced json block with surrounding prose`() {
        val raw = """
            Ecco il risultato dell'analisi nutrizionale:
            ```json
            {"alimenti": [{"nome": "Pizza margherita", "peso_stimato_g": 280}]}
            ```
            Spero che questi dati ti siano utili!
        """.trimIndent()
        assertEquals(
            """{"alimenti": [{"nome": "Pizza margherita", "peso_stimato_g": 280}]}""",
            AiCallExecutor.extractJsonPayload(raw),
        )
    }

    @Test
    fun `extractJsonPayload extracts balanced JSON object with prose and braces inside quoted strings`() {
        val raw = """
            Risultato analisi: {"note": "test con } parentesi e \"virgolette } escapate\" \\\"ok\\\"", "items": [1, 2]} fine risposta!
        """.trimIndent()
        assertEquals(
            """{"note": "test con } parentesi e \"virgolette } escapate\" \\\"ok\\\"", "items": [1, 2]}""",
            AiCallExecutor.extractJsonPayload(raw),
        )
    }

    @Test
    fun `extractJsonPayload extracts balanced JSON array when root is array`() {
        val raw = """Ecco l'elenco richiesto: [{"nome": "Mela [rossa]"}, {"nome": "Pera"}] grazie."""
        assertEquals(
            """[{"nome": "Mela [rossa]"}, {"nome": "Pera"}]""",
            AiCallExecutor.extractJsonPayload(raw),
        )
    }

    @Test
    fun `extractJsonPayload handles raw JSON containing triple backticks inside a string value`() {
        val raw = """{"note": "esempio ```codice``` inline", "ok": true}"""
        assertEquals(raw, AiCallExecutor.extractJsonPayload(raw))
    }

    // ── detectImageMimeType ────────────────────────────────────────────────────

    @Test
    fun `detectImageMimeType identifies PNG, JPEG, WEBP and falls back cleanly`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val webp = "RIFF1234WEBP".toByteArray(Charsets.US_ASCII)
        val unknown = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        assertEquals("image/png", AiCallExecutor.detectImageMimeType(png, "image/jpeg"))
        assertEquals("image/jpeg", AiCallExecutor.detectImageMimeType(jpeg, "image/png"))
        assertEquals("image/webp", AiCallExecutor.detectImageMimeType(webp, "image/jpeg"))
        assertEquals("image/png", AiCallExecutor.detectImageMimeType(unknown, "image/png"))
        assertEquals("image/jpeg", AiCallExecutor.detectImageMimeType(unknown, ""))
    }

    // ── AiClients response extractors & HTTP errors ────────────────────────────

    @Test
    fun `extractGeminiText skips thought parts and joins non-thought text parts`() {
        val response = """
            {
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [
                      {"text": "Ragionamento interno del modello...", "thought": true},
                      {"text": "{\"alimenti\":" },
                      {"text": "[{\"nome\":\"Riso\"}]}"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val extracted = extractGeminiText(json, response, "Gemini")
        assertEquals("{\"alimenti\":\n[{\"nome\":\"Riso\"}]}", extracted)
    }

    @Test
    fun `extractGeminiText falls back to thought text if no non-thought text exists`() {
        val response = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "{\"ok\":true}", "thought": true}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("{\"ok\":true}", extractGeminiText(json, response, "Gemini"))
    }

    @Test
    fun `extractGeminiText throws clear Italian error on promptFeedback blockReason`() {
        val response = """
            {
              "candidates": [],
              "promptFeedback": { "blockReason": "SAFETY" }
            }
        """.trimIndent()
        try {
            extractGeminiText(json, response, "Gemini")
            fail("Expected AiCallException")
        } catch (e: AiCallException) {
            assertTrue(e.message!!.contains("SAFETY"))
            assertTrue(e.message!!.contains("bloccata", ignoreCase = true))
        }
    }

    @Test
    fun `extractAnthropicText skips thinking and tool blocks and picks JSON text block`() {
        val response = """
            {
              "content": [
                {"type": "thinking", "thinking": "Let me look up values..."},
                {"type": "text", "text": "Cerco i valori nutrizionali sul web."},
                {"type": "server_tool_use", "id": "t1", "name": "web_search"},
                {"type": "web_search_tool_result", "tool_use_id": "t1"},
                {"type": "text", "text": "{\"alimento\":\"Biscotti\",\"cho_per_100g\":70.0}"}
              ]
            }
        """.trimIndent()
        assertEquals(
            """{"alimento":"Biscotti","cho_per_100g":70.0}""",
            extractAnthropicText(json, response, "Anthropic"),
        )
    }

    @Test
    fun `extractBedrockConverseText skips reasoning blocks and extracts JSON text`() {
        val response = """
            {
              "output": {
                "message": {
                  "content": [
                    {"reasoningContent": {"reasoningText": {"text": "thinking..."}}},
                    {"text": "{\"alimenti\": []}"}
                  ]
                }
              }
            }
        """.trimIndent()
        assertEquals("""{"alimenti": []}""", extractBedrockConverseText(json, response))
    }

    @Test
    fun `extractors handle explicit JsonNull fields safely without crashing`() {
        // OpenAI content: null must throw AiCallException instead of returning literal "null"
        try {
            extractOpenAiText(json, """{"choices":[{"message":{"content":null}}]}""", "OpenAI")
            fail("Expected AiCallException for OpenAI null content")
        } catch (e: AiCallException) {
            assertTrue(e.message!!.contains("priva di contenuto", ignoreCase = true))
        }

        // Gemini promptFeedback: null & candidates: null must throw AiCallException cleanly
        try {
            extractGeminiText(json, """{"promptFeedback":null,"candidates":null}""", "Gemini")
            fail("Expected AiCallException for Gemini null candidates")
        } catch (_: AiCallException) {
            // expected
        }

        // Anthropic content: null must throw AiCallException cleanly
        try {
            extractAnthropicText(json, """{"content":null}""", "Anthropic")
            fail("Expected AiCallException for Anthropic null content")
        } catch (_: AiCallException) {
            // expected
        }
    }

    @Test
    fun `OpenFoodFactsClient safely handles explicit JsonNull product, products and nutriments`() {
        val client = OpenFoodFactsClient(OkHttpClient(), json)
        assertNull(client.parseProductResponse("""{"status":1,"product":null}"""))
        assertNull(client.parseProductResponse("""{"status":1,"product":{"product_name":"Test","nutriments":null}}"""))
        assertEquals(emptyList<Any>(), client.parseSearchResponse("""{"products":null}"""))
        assertEquals(
            emptyList<Any>(),
            client.parseSearchResponse("""{"products":[null,{"product_name":null,"nutriments":null}]}"""),
        )
    }

    @Test
    fun `buildHttpErrorException produces friendly Italian messages and proper exception types`() {
        val e401 = buildHttpErrorException("OpenAI", 401, """{"error":{"message":"Incorrect API key"}}""", json)
        assertEquals(AiCallException::class.java, e401::class.java)
        assertTrue(e401.message!!.contains("Chiave API", ignoreCase = true))
        assertTrue(e401.message!!.contains("Incorrect API key"))

        val e404 = buildHttpErrorException("Anthropic", 404, """{"error":{"message":"model not found"}}""", json)
        assertEquals(AiCallException::class.java, e404::class.java)
        assertTrue(e404.message!!.contains("Modello non disponibile", ignoreCase = true))

        val e429 = buildHttpErrorException("Gemini", 429, """{"error":{"message":"quota exceeded"}}""", json)
        assertTrue(e429 is RateLimitException)
        assertTrue(e429.message!!.contains("429"))

        val e503 = buildHttpErrorException("Gemini", 503, "overloaded", json)
        assertTrue(e503 is ServiceUnavailableException)
        assertTrue(e503.message!!.contains("503"))
    }

    // ── Production executeWithRetry behavior ───────────────────────────────────

    @Test
    fun `defaultRetryDelayMs returns positive backoff for transient error types`() {
        assertTrue(AiCallExecutor.defaultRetryDelayMs(0, MalformedJsonException("bad")) > 0L)
        assertTrue(AiCallExecutor.defaultRetryDelayMs(0, ServiceUnavailableException("503")) > 0L)
        assertTrue(AiCallExecutor.defaultRetryDelayMs(1, RateLimitException("429")) > AiCallExecutor.defaultRetryDelayMs(0, RateLimitException("429")))
        assertTrue(AiCallExecutor.defaultRetryDelayMs(1, SocketTimeoutException("timeout")) > AiCallExecutor.defaultRetryDelayMs(0, SocketTimeoutException("timeout")))
    }

    @Test
    fun `executeWithRetry succeeds after transient SocketTimeoutException and IOException`() = runBlocking {
        var attempts = 0
        val value = AiCallExecutor.executeWithRetry(maxAttempts = 3, delayProvider = { _, _ -> 0L }) {
            attempts++
            if (attempts == 1) throw SocketTimeoutException("read timed out")
            if (attempts == 2) throw IOException("connection reset")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(3, attempts)
    }

    @Test
    fun `executeWithRetry wraps exhausted IOException into readable AiCallException`() = runBlocking {
        var attempts = 0
        try {
            AiCallExecutor.executeWithRetry(maxAttempts = 3, delayProvider = { _, _ -> 0L }) {
                attempts++
                throw SocketTimeoutException("connect timed out")
            }
            fail("Expected AiCallException")
        } catch (e: AiCallException) {
            assertEquals(3, attempts)
            assertTrue(e.message!!.contains("timeout", ignoreCase = true))
        }
    }

    @Test
    fun `executeWithRetry does NOT retry non-transient AiCallException (HTTP 400, 401, 403, 404)`() = runBlocking {
        for (code in listOf(400, 401, 403, 404)) {
            var attempts = 0
            try {
                AiCallExecutor.executeWithRetry(maxAttempts = 3, delayProvider = { _, _ -> 0L }) {
                    attempts++
                    throw buildHttpErrorException("OpenAI", code, "client error $code", json)
                }
                fail("Expected AiCallException for HTTP $code")
            } catch (_: AiCallException) {
                assertEquals("HTTP $code must not be retried", 1, attempts)
            }
        }
    }

    @Test
    fun `executeWithRetry never swallows CancellationException`() = runBlocking {
        var attempts = 0
        try {
            AiCallExecutor.executeWithRetry(maxAttempts = 3, delayProvider = { _, _ -> 0L }) {
                attempts++
                throw CancellationException("user cancelled")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }

    // ── AiModelCatalog dynamic updates & catalogVersion ────────────────────────

    @Test
    fun `AiModelCatalog defaults cover Anthropic, OpenAI, Gemini and Vertex AI and increment catalogVersion on updates`() {
        val initialVersion = AiModelCatalog.catalogVersion.value
        try {
            assertTrue(AiModelCatalog.ALL.any { it.provider == AiProviderType.ANTHROPIC && it.id == "claude-sonnet-4-5-20250929" })
            assertTrue(AiModelCatalog.ALL.any { it.provider == AiProviderType.OPENAI_API && it.id == "gpt-4o" })
            assertTrue(AiModelCatalog.ALL.any { it.provider == AiProviderType.GEMINI && it.id == "gemini-2.5-flash" })
            assertTrue(AiModelCatalog.ALL.any { it.provider == AiProviderType.VERTEX_AI && it.id == "gemini-2.5-pro" })

            AiModelCatalog.setOpenAiModels(listOf("gpt-4.1-mini"))
            assertEquals(initialVersion + 1, AiModelCatalog.catalogVersion.value)
            assertEquals(
                listOf("gpt-4.1-mini"),
                AiModelCatalog.ALL.filter { it.provider == AiProviderType.OPENAI_API }.map { it.id },
            )

            AiModelCatalog.setAnthropicModels(listOf("claude-sonnet-4-5-20250929"))
            assertEquals(initialVersion + 2, AiModelCatalog.catalogVersion.value)

            AiModelCatalog.clearOpenAiModels()
            AiModelCatalog.clearAnthropicModels()
            assertEquals(initialVersion + 4, AiModelCatalog.catalogVersion.value)
            assertTrue(AiModelCatalog.ALL.any { it.provider == AiProviderType.OPENAI_API && it.id == "gpt-4o" })
        } finally {
            AiModelCatalog.clearOpenAiModels()
            AiModelCatalog.clearAnthropicModels()
            AiModelCatalog.clearGeminiModels()
        }
    }
}
