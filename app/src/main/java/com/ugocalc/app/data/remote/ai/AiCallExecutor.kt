package com.ugocalc.app.data.remote.ai

import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.client.AiTextCompletionClient
import com.ugocalc.app.data.remote.ai.client.AnthropicCompletionClient
import com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient
import com.ugocalc.app.data.remote.ai.client.ImagePart
import com.ugocalc.app.data.remote.ai.client.MalformedJsonException
import com.ugocalc.app.data.remote.ai.client.OpenAiCompletionClient
import com.ugocalc.app.data.remote.ai.client.ServiceUnavailableException
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiProviderType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Infrastruttura HTTP-agnostica-dal-prompt condivisa dai 3 Passaggi AI della V1 ([AiOrchestrator])
 * e dai moduli IA della V2 (es. M1 — Percezione unificata): risolve client/API key per provider,
 * invia il testo (ed eventuali immagini), ritenta sugli errori transitori noti e decodifica la
 * risposta nel tipo richiesto. Non conosce prompt o DTO specifici di nessun passaggio: quello resta
 * responsabilità del chiamante (§3 "Provider intercambiabili dietro contratti JSON neutrali").
 */
@Singleton
class AiCallExecutor @Inject constructor(
    private val openAiClient: OpenAiCompletionClient,
    private val anthropicClient: AnthropicCompletionClient,
    private val geminiClient: GeminiCompletionClient,
    private val apiKeyStore: SecureApiKeyStore,
    internal val json: Json,
) {

    internal fun clientFor(provider: AiProviderType): AiTextCompletionClient = when (provider) {
        AiProviderType.OPENAI, AiProviderType.OPENAI_API -> openAiClient
        AiProviderType.ANTHROPIC -> anthropicClient
        AiProviderType.GEMINI -> geminiClient
    }

    internal fun apiKeyFor(model: AiModel): String =
        apiKeyStore.getApiKey(model.provider)
            ?: throw AiCallException("Nessuna API key configurata per ${model.displayName}")

    /** Codifica immagini grezze in [ImagePart] pronte per [completeJson], come già fatto in V1. */
    fun encodeImages(images: List<ByteArray>, mimeType: String): List<ImagePart> =
        images.map { bytes -> ImagePart(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), mimeType) }

    /** Chiama [AiTextCompletionClient.completeJson] con retry e decodifica la risposta in [T]. */
    internal suspend inline fun <reified T> completeJson(
        model: AiModel,
        userText: String,
        images: List<ImagePart> = emptyList(),
    ): T {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        return withRetry {
            val raw = client.completeJson(apiKey = apiKey, modelId = model.id, userText = userText, images = images)
            decodeDto(raw)
        }
    }

    /** Chiama [AiTextCompletionClient.searchOnline] con retry e decodifica la risposta in [T]. */
    internal suspend inline fun <reified T> searchOnline(model: AiModel, userText: String): T {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        return withRetry {
            val raw = client.searchOnline(apiKey = apiKey, modelId = model.id, userText = userText)
            decodeDto(raw)
        }
    }

    /**
     * Ritenta SOLO su due categorie di errore note come transitorie/non deterministiche:
     * - [MalformedJsonException]: i modelli generano occasionalmente JSON non valido su liste
     *   lunghe (es. escaping sbagliato di una virgoletta) — un secondo tentativo spesso basta.
     * - [ServiceUnavailableException] (HTTP 503): sovraccarico temporaneo lato provider, non un
     *   problema della richiesta — un backoff più lungo (3s) spesso è sufficiente.
     * Qualunque altro [AiCallException] (es. 429 quota esaurita, chiave mancante) NON va ripetuto
     * subito: peggiorerebbe un eventuale blocco lato provider.
     */
    internal suspend inline fun <T> withRetry(maxAttempts: Int = 2, crossinline block: suspend () -> T): T {
        var lastError: AiCallException? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: MalformedJsonException) {
                lastError = e
                if (attempt < (maxAttempts - 1)) delay(1500.milliseconds)
            } catch (e: ServiceUnavailableException) {
                lastError = e
                if (attempt < (maxAttempts - 1)) delay(3000.milliseconds)
            }
        }
        throw lastError!!
    }

    internal inline fun <reified T> decodeDto(raw: String): T {
        val cleaned = stripMarkdownFences(raw)
        return try {
            json.decodeFromString(cleaned)
        } catch (e: SerializationException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        } catch (e: IllegalArgumentException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        }
    }

    /** Rimuove eventuali fence markdown (```json ... ```) che alcuni modelli aggiungono nonostante il prompt lo vieti. */
    internal fun stripMarkdownFences(raw: String): String =
        raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    /** Include un estratto della risposta grezza: senza, un fallimento di parsing è indiagnosticabile a distanza. */
    internal fun malformedJsonMessage(cleaned: String, cause: Exception): String {
        val preview = cleaned.take(300).ifBlank { "(risposta vuota)" }
        return "JSON malformato nella risposta del modello (${cause.message}). Risposta ricevuta: $preview"
    }
}
