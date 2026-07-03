package com.ugocalc.app.data.remote.ai

import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.client.AiTextCompletionClient
import com.ugocalc.app.data.remote.ai.client.ImagePart
import com.ugocalc.app.data.remote.ai.client.MalformedJsonException
import com.ugocalc.app.data.remote.ai.client.ServiceUnavailableException
import com.ugocalc.app.data.remote.ai.client.AnthropicCompletionClient
import com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient
import com.ugocalc.app.data.remote.ai.client.OpenAiCompletionClient
import com.ugocalc.app.data.remote.ai.dto.MatchingDto
import com.ugocalc.app.data.remote.ai.dto.NormalizationDto
import com.ugocalc.app.data.remote.ai.dto.OnlineFoodResearchDto
import com.ugocalc.app.data.remote.ai.dto.VisionAnalysisDto
import com.ugocalc.app.data.remote.ai.dto.toDomain
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.NormalizationResult
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.VisionAnalysisResult
import com.ugocalc.app.domain.prompts.PromptTemplates
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto unico di accesso ai 3 Passaggi AI (§3, §4, §5). Risolve il client del provider giusto,
 * recupera la API key salvata, invia il prompt previsto dal documento e decodifica la risposta nel
 * DTO corrispondente. Qualunque errore (rete, HTTP, JSON malformato/non conforme allo schema)
 * viene normalizzato in [AiCallException], così l'use case chiamante può applicare la stessa
 * gestione del fallimento per tutti e tre i provider (§3 "Gestione del fallimento").
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val openAiClient: OpenAiCompletionClient,
    private val anthropicClient: AnthropicCompletionClient,
    private val geminiClient: GeminiCompletionClient,
    private val apiKeyStore: SecureApiKeyStore,
    private val json: Json,
) {

    private fun clientFor(provider: AiProviderType): AiTextCompletionClient = when (provider) {
        AiProviderType.OPENAI -> openAiClient
        AiProviderType.ANTHROPIC -> anthropicClient
        AiProviderType.GEMINI -> geminiClient
    }

    private fun apiKeyFor(model: AiModel): String =
        apiKeyStore.getApiKey(model.provider)
            ?: throw AiCallException("Nessuna API key configurata per ${model.displayName}")

    /** Rimuove eventuali fence markdown (```json ... ```) che alcuni modelli aggiungono nonostante il prompt lo vieti. */
    private fun stripMarkdownFences(raw: String): String =
        raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    suspend fun runVisionAnalysis(model: AiModel, images: List<ByteArray>, imageMimeType: String): VisionAnalysisResult {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        val imageParts = images.map { bytes ->
            ImagePart(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), imageMimeType)
        }
        return withRetry {
            val raw = client.completeJson(
                apiKey = apiKey,
                modelId = model.id,
                userText = PromptTemplates.VISION_PROMPT,
                images = imageParts,
            )
            decodeDto<VisionAnalysisDto>(raw).toDomain()
        }
    }

    suspend fun runTableNormalization(model: AiModel, extractedText: String): NormalizationResult {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        return withRetry {
            val raw = client.completeJson(
                apiKey = apiKey,
                modelId = model.id,
                userText = PromptTemplates.normalizationPrompt(extractedText),
            )
            decodeDto<NormalizationDto>(raw).toDomain()
        }
    }

    suspend fun runMatching(model: AiModel, listaAlimentiJson: String, tabellaNormalizzataJson: String): MatchingResult {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        return withRetry {
            val raw = client.completeJson(
                apiKey = apiKey,
                modelId = model.id,
                userText = PromptTemplates.matchingPrompt(listaAlimentiJson, tabellaNormalizzataJson),
            )
            decodeDto<MatchingDto>(raw).toDomain()
        }
    }

    /** Ricerca online per un alimento assente dalle tabelle dell'utente (oggi funzionante solo con Gemini, vedi [com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient]). */
    suspend fun runOnlineFoodResearch(model: AiModel, foodName: String): OnlineFoodResearchResult {
        val client = clientFor(model.provider)
        val apiKey = apiKeyFor(model)
        return withRetry {
            val raw = client.searchOnline(
                apiKey = apiKey,
                modelId = model.id,
                userText = PromptTemplates.onlineFoodResearchPrompt(foodName),
            )
            decodeDto<OnlineFoodResearchDto>(raw).toDomain()
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
    private suspend fun <T> withRetry(maxAttempts: Int = 2, block: suspend () -> T): T {
        var lastError: AiCallException? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: MalformedJsonException) {
                lastError = e
                if (attempt < maxAttempts - 1) delay(1500)
            } catch (e: ServiceUnavailableException) {
                lastError = e
                if (attempt < maxAttempts - 1) delay(3000)
            }
        }
        throw lastError!!
    }

    private inline fun <reified T> decodeDto(raw: String): T {
        val cleaned = stripMarkdownFences(raw)
        return try {
            json.decodeFromString(cleaned)
        } catch (e: SerializationException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        } catch (e: IllegalArgumentException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        }
    }

    /** Include un estratto della risposta grezza: senza, un fallimento di parsing è indiagnosticabile a distanza. */
    private fun malformedJsonMessage(cleaned: String, cause: Exception): String {
        val preview = cleaned.take(300).ifBlank { "(risposta vuota)" }
        return "JSON malformato nella risposta del modello (${cause.message}). Risposta ricevuta: $preview"
    }
}
