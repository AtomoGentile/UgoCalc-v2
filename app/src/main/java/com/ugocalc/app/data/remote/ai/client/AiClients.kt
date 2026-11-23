package com.ugocalc.app.data.remote.ai.client

import com.ugocalc.app.domain.model.AiEffort

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

open class AiCallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Sottotipo specifico per JSON sintatticamente non valido nella risposta del modello (vedi AiOrchestrator.withRetry: solo questo va ritentato, un HTTP/rate-limit non va ripetuto subito). */
class MalformedJsonException(message: String, cause: Throwable? = null) : AiCallException(message, cause)

/**
 * Sottotipo specifico per HTTP 500/502/503/504 ("servizio temporaneamente non disponibile"): a differenza di
 * un 429 (quota esaurita, ripetere subito peggiora la situazione) è quasi sempre un sovraccarico
 * transitorio lato provider — vedi AiCallExecutor.withRetry, che ritenta anche su questo.
 */
class ServiceUnavailableException(message: String, cause: Throwable? = null) : AiCallException(message, cause)

/** Sottotipo specifico per HTTP 429 ("troppe richieste"): quota esaurita o rate limit temporaneo. */
class RateLimitException(message: String, cause: Throwable? = null) : AiCallException(message, cause)

/**
 * Traduce un codice di errore HTTP in un messaggio italiano comprensibile per l'utente,
 * preservando un breve estratto diagnostico dal body del provider.
 */
internal fun buildHttpErrorException(
    providerLabel: String,
    statusCode: Int,
    bodyString: String,
    json: Json,
): AiCallException {
    val detail = extractErrorDetail(bodyString, json)
    val baseMessage = when (statusCode) {
        401, 403 -> "Chiave API o permessi non validi per $providerLabel (HTTP $statusCode). Verifica le credenziali nelle Impostazioni."
        404 -> "Modello non disponibile o non trovato su $providerLabel (HTTP 404). Aggiorna la lista modelli nelle Impostazioni."
        429 -> "Limite di richieste o quota raggiunta su $providerLabel (HTTP 429). Riprova tra qualche secondo."
        500, 502, 503, 504 -> "Servizio $providerLabel temporaneamente occupato o non disponibile (HTTP $statusCode). Riprova tra poco."
        400 -> "Richiesta non accettata da $providerLabel (HTTP 400)."
        else -> "Chiamata $providerLabel fallita (HTTP $statusCode)."
    }
    val fullMessage = if (detail.isNotBlank()) "$baseMessage Dettaglio: $detail" else baseMessage
    return when (statusCode) {
        429 -> RateLimitException(fullMessage)
        500, 502, 503, 504 -> ServiceUnavailableException(fullMessage)
        else -> AiCallException(fullMessage)
    }
}

private fun extractErrorDetail(bodyString: String, json: Json): String {
    val trimmed = bodyString.trim()
    if (trimmed.isEmpty()) return ""
    val extracted = try {
        val root = json.parseToJsonElement(trimmed) as? JsonObject
        val errorObj = root?.get("error") as? JsonObject
        (errorObj?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: (root?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: (root?.get("detail") as? JsonPrimitive)?.contentOrNull
            ?: (root?.get("error") as? JsonPrimitive)?.contentOrNull
    } catch (_: Exception) {
        null
    }
    val raw = (extracted ?: trimmed).replace(Regex("\\s+"), " ").trim()
    return if (raw.length > 180) raw.take(177) + "..." else raw
}

/**
 * Estrae il testo utile da una risposta OpenAI-compatibile (`choices[0].message.content`),
 * gestendo in modo sicuro `JsonNull`, campi mancanti o array di blocchi testuali.
 */
internal fun extractOpenAiText(
    json: Json,
    rawResponseBody: String,
    providerLabel: String,
): String {
    val root = try {
        json.parseToJsonElement(rawResponseBody) as? JsonObject
    } catch (e: Exception) {
        throw AiCallException("Risposta $providerLabel non valida (JSON malformato)", e)
    } ?: throw AiCallException("Risposta $providerLabel non valida (JSON malformato)")

    val firstChoice = (root["choices"] as? JsonArray)?.getOrNull(0) as? JsonObject
    val message = firstChoice?.get("message") as? JsonObject
    val content = message?.get("content")
    val text = when (content) {
        is JsonPrimitive -> content.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> {
            val blocks = content.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "text") return@mapNotNull null
                (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }
            if (blocks.isEmpty()) null else selectPreferredTextBlock(blocks)
        }
        else -> null
    }
    return text ?: throw AiCallException("Risposta $providerLabel priva di contenuto testuale")
}

/**
 * Estrae il testo utile da una risposta Gemini / Vertex AI iterando tutte le `parts`:
 * ignora le parti di ragionamento interno (`thought == true`), unisce le parti testuali non-thought
 * (con fallback a tutte le parti testuali se vuote), e segnala chiaramente eventuali blocchi
 * `promptFeedback.blockReason` o `finishReason`.
 */
internal fun extractGeminiText(
    json: Json,
    rawResponseBody: String,
    providerLabel: String,
): String {
    val root = try {
        json.parseToJsonElement(rawResponseBody) as? JsonObject
    } catch (e: Exception) {
        throw AiCallException("Risposta $providerLabel non valida (JSON malformato)", e)
    } ?: throw AiCallException("Risposta $providerLabel non valida (JSON malformato)")

    val promptBlockReason = (root["promptFeedback"] as? JsonObject)
        ?.let { it["blockReason"] as? JsonPrimitive }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

    val candidates = root["candidates"] as? JsonArray
    if (candidates == null || candidates.isEmpty()) {
        if (promptBlockReason != null) {
            throw AiCallException("Richiesta bloccata da $providerLabel (motivo: $promptBlockReason). Riprova con un'altra foto o modello.")
        }
        throw AiCallException("Risposta $providerLabel priva di candidati utilizzabili.")
    }

    val candidate = candidates[0] as? JsonObject
        ?: throw AiCallException("Risposta $providerLabel non valida (candidato malformato).")
    val finishReason = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
    val nonThoughtTexts = mutableListOf<String>()
    val allTexts = mutableListOf<String>()

    for (part in parts) {
        val partObj = part as? JsonObject ?: continue
        val text = (partObj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        allTexts += text
        val isThought = (partObj["thought"] as? JsonPrimitive)?.booleanOrNull == true
        if (!isThought) {
            nonThoughtTexts += text
        }
    }

    val resolved = nonThoughtTexts.joinToString("\n").trim()
        .ifBlank { allTexts.joinToString("\n").trim() }

    if (resolved.isNotBlank()) return resolved

    if (finishReason != null && !finishReason.equals("STOP", ignoreCase = true)) {
        throw AiCallException("Risposta $providerLabel interrotta senza testo (motivo: $finishReason).")
    }
    if (promptBlockReason != null) {
        throw AiCallException("Richiesta bloccata da $providerLabel (motivo: $promptBlockReason).")
    }
    throw AiCallException("Risposta $providerLabel priva di contenuto testuale.")
}

/**
 * Estrae il testo utile da una risposta Anthropic / Claude (diretta, Bedrock Mantle o Bedrock IAM):
 * filtra solo i blocchi con `type == "text"` (ignorando `thinking`, `redacted_thinking`,
 * `server_tool_use`, `web_search_tool_result`), preferendo l'ultimo blocco testuale contenente
 * un payload JSON (`{...}` o `[...]`), oppure unendo i blocchi se il JSON è spezzato, altrimenti
 * l'ultimo blocco testuale non vuoto.
 */
internal fun extractAnthropicText(
    json: Json,
    rawResponseBody: String,
    providerLabel: String,
): String {
    val root = try {
        json.parseToJsonElement(rawResponseBody) as? JsonObject
    } catch (e: Exception) {
        throw AiCallException("Risposta $providerLabel non valida (JSON malformato)", e)
    } ?: throw AiCallException("Risposta $providerLabel non valida (JSON malformato)")

    val contentBlocks = (root["content"] as? JsonArray).orEmpty()
    val textBlocks = contentBlocks.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "text") return@mapNotNull null
        (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    if (textBlocks.isEmpty()) {
        val stopReason = (root["stop_reason"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (stopReason != null && stopReason != "end_turn") {
            throw AiCallException("Risposta $providerLabel priva di contenuto testuale (stop_reason: $stopReason).")
        }
        throw AiCallException("Risposta $providerLabel priva di contenuto testuale.")
    }

    return selectPreferredTextBlock(textBlocks)
}

/**
 * Estrae il testo utile da una risposta Amazon Bedrock Converse API iterando tutti i blocchi
 * `output.message.content` (ignorando blocchi `reasoningContent`, `toolUse`, ecc.).
 */
internal fun extractBedrockConverseText(
    json: Json,
    rawResponseBody: String,
): String {
    val root = try {
        json.parseToJsonElement(rawResponseBody) as? JsonObject
    } catch (e: Exception) {
        throw AiCallException("Risposta Bedrock Converse IAM non valida (JSON malformato)", e)
    } ?: throw AiCallException("Risposta Bedrock Converse IAM non valida (JSON malformato)")

    val contentBlocks = (((root["output"] as? JsonObject)
        ?.get("message") as? JsonObject)
        ?.get("content") as? JsonArray)
        .orEmpty()

    val textBlocks = contentBlocks.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    if (textBlocks.isEmpty()) {
        val stopReason = (root["stopReason"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (stopReason != null && stopReason != "end_turn") {
            throw AiCallException("Risposta Bedrock Converse IAM priva di contenuto testuale (stopReason: $stopReason).")
        }
        throw AiCallException("Risposta Bedrock Converse IAM priva di contenuto testuale.")
    }

    return selectPreferredTextBlock(textBlocks)
}

private fun selectPreferredTextBlock(textBlocks: List<String>): String {
    if (textBlocks.size == 1) return textBlocks[0]
    textBlocks.lastOrNull { (it.contains('{') && it.contains('}')) || (it.contains('[') && it.contains(']')) }
        ?.let { return it }
    val joined = textBlocks.joinToString("\n")
    if ((joined.contains('{') && joined.contains('}')) || (joined.contains('[') && joined.contains(']'))) {
        return joined
    }
    return textBlocks.lastOrNull { it.contains('{') || it.contains('[') }
        ?: textBlocks.last()
}

/** Una singola immagine allegata a una chiamata AI (già codificata in base64). */
data class ImagePart(val base64: String, val mimeType: String)

interface AiTextCompletionClient {
    suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart> = emptyList(),
        effort: AiEffort = AiEffort.MEDIUM,
    ): String

    /**
     * Ricerca online (con accesso al web) per un alimento assente dalle tabelle dell'utente: a
     * differenza di [completeJson], che interpreta solo dati già forniti nel prompt, qui il
     * modello deve poter cercare sul web. Nessuna implementazione di default: ogni provider deve
     * dichiarare esplicitamente se e come la supporta. Gemini e Anthropic la implementano
     * davvero (quest'ultima mai verificata con una chiave reale); OpenAI diretto non implementa
     * qui la ricerca web.
     */
    suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String
}

/**
 * Chiama l'API OpenAI Chat Completions ufficiale (`api.openai.com/v1/chat/completions`) con la
 * chiave API diretta configurata dall'utente per `AiProviderType.OPENAI_API`.
 */
@Singleton
class OpenAiCompletionClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(buildRequestBody(modelId, userText, images, effort).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("OpenAI", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject { put("type", "text"); put("text", userText) }
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.base64}") }
                        }
                    }
                }
            }
        }
        putJsonObject("response_format") { put("type", "json_object") }
        val lower = modelId.lowercase()
        if ("o1" in lower || "o3" in lower || "o4" in lower || "gpt-5" in lower) {
            put("reasoning_effort", when (effort) { AiEffort.LOW -> "low"; AiEffort.MEDIUM -> "medium"; AiEffort.HIGH -> "high" })
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractOpenAiText(json, rawResponseBody, "OpenAI")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String {
        throw AiCallException("Ricerca online non disponibile: il client OpenAI non supporta la ricerca web.")
    }
}

/**
 * Chiama l'API Anthropic Messages (api.anthropic.com) — prima era uno stub finto (`"{}"`) mai
 * implementato per davvero. NON verificata con una chiave reale (vedi memoria utente: minorenne,
 * non può attivare da solo un account Anthropic con fatturazione): la forma della richiesta segue
 * la documentazione pubblica dell'API al momento della scrittura, ma va confermata alla prima
 * chiave vera disponibile.
 */
@Singleton
class AnthropicCompletionClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_API_VERSION)
            .post(buildRequestBody(modelId, userText, images, effort).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Anthropic", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("model", modelId)
        val lower = modelId.lowercase()
        val supportsThinking = "3-7" in lower || "3.7" in lower || "-4" in lower || "4-" in lower
        val thinkingBudget = if (supportsThinking && effort != AiEffort.LOW) {
            if (effort == AiEffort.HIGH) 4096 else 1536
        } else 0
        // A differenza di OpenAI/Gemini, dove è opzionale, Claude richiede max_tokens obbligatorio (> budget_tokens se thinking attivo).
        put("max_tokens", MAX_OUTPUT_TOKENS + thinkingBudget)
        if (thinkingBudget > 0) {
            putJsonObject("thinking") {
                put("type", "enabled")
                put("budget_tokens", thinkingBudget)
            }
        }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractAnthropicText(json, rawResponseBody, "Anthropic")

    /**
     * Usa il tool di ricerca web nativo di Claude (eseguito lato server, come il Grounding di
     * Gemini): il modello decide da sé quando cercare, la risposta finale include già il testo
     * con le informazioni trovate. Stessa API key già usata per Visione/Normalizzazione/Matching.
     */
    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_API_VERSION)
            .post(buildSearchRequestBody(modelId, userText).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Anthropic (ricerca online)", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildSearchRequestBody(modelId: String, userText: String): JsonObject = buildJsonObject {
        put("model", modelId)
        put("max_tokens", MAX_OUTPUT_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
        putJsonArray("tools") {
            addJsonObject {
                put("type", "web_search_20250305")
                put("name", "web_search")
                put("max_uses", 3)
            }
        }
    }

    companion object {
        private const val ANTHROPIC_API_VERSION = "2023-06-01"

        /**
         * Tetto per UNA risposta (non un limite giornaliero, quello dipende dal piano
         * dell'account Anthropic). 16384 invece di 8192: con MAX_CHARS_PER_CHUNK a 24.000 caratteri
         * (vedi DocumentTextExtractor) un chunk molto denso di righe durante la normalizzazione
         * tabelle potrebbe generare un JSON di risposta più lungo di 8192 token, troncandosi a metà.
         */
        private const val MAX_OUTPUT_TOKENS = 16384
    }
}

/**
 * Chiama Claude tramite AWS Bedrock Mantle (endpoint Anthropic-compatibile).
 * Endpoint: POST https://bedrock-mantle.{region}.api.aws/anthropic/v1/messages
 * Auth: x-api-key (Mantle API key) + anthropic-workspace-id (project ID)
 *
 * Il parametro apiKey arriva nel formato "mantleApiKey|projectId|region".
 */
@Singleton
class BedrockMantleClaudeClient @Inject constructor(
    @Named("no_auth") private val okHttpClientNoAuth: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (mantleKey, projectId, region) = parseCredentials(apiKey)
        val bodyBytes = buildRequestBody(modelId, userText, images).toString().toByteArray()
        val request = Request.Builder()
            .url("https://bedrock-mantle.$region.api.aws/anthropic/v1/messages")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $mantleKey")
            .header("anthropic-workspace-id", projectId)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(bodyBytes.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClientNoAuth.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Bedrock Mantle (Claude)", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("model", modelId)
        put("max_tokens", MAX_OUTPUT_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractAnthropicText(json, rawResponseBody, "Bedrock Mantle (Claude)")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String =
        completeJson(apiKey, modelId, userText)

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MAX_OUTPUT_TOKENS = 16384

        fun parseCredentials(apiKey: String): Triple<String, String, String> {
            val parts = apiKey.split('|')
            require(parts.size == 3) {
                "Credenziali Bedrock Mantle non valide: formato atteso 'mantleApiKey|projectId|region'"
            }
            return Triple(parts[0], parts[1], parts[2])
        }
    }
}

/**
 * Chiama GPT tramite AWS Bedrock Mantle (endpoint OpenAI-compatibile).
 * Endpoint: POST https://bedrock-mantle.{region}.api.aws/v1/chat/completions
 * Auth: Authorization: Bearer (Mantle API key) + OpenAI-Project (project ID)
 *
 * Il parametro apiKey arriva nel formato "mantleApiKey|projectId|region".
 */
@Singleton
class BedrockMantleGptClient @Inject constructor(
    @Named("no_auth") private val okHttpClientNoAuth: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (mantleKey, projectId, region) = BedrockMantleClaudeClient.parseCredentials(apiKey)
        val bodyBytes = buildRequestBody(modelId, userText, images).toString().toByteArray()
        val request = Request.Builder()
            .url("https://bedrock-mantle.$region.api.aws/v1/chat/completions")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $mantleKey")
            .header("OpenAI-Project", projectId.ifBlank { "default" })
            .post(bodyBytes.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClientNoAuth.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Bedrock Mantle (GPT)", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.base64}") }
                        }
                    }
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
        put("max_tokens", MAX_OUTPUT_TOKENS)
    }

    private fun extractText(rawResponseBody: String): String =
        extractOpenAiText(json, rawResponseBody, "Bedrock Mantle (GPT)")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String {
        throw AiCallException("Ricerca online non disponibile per GPT su Bedrock Mantle.")
    }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 16384
    }
}

/**
 * Chiama Claude tramite AWS Bedrock Runtime diretto con SigV4 (Access Key + Secret Key).
 * Endpoint: POST https://bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
 * Auth: AWS Signature Version 4 (servizio bedrock-runtime)
 *
 * Il parametro apiKey arriva nel formato "accessKey|secretKey|region".
 */
@Singleton
class BedrockIamClaudeClient @Inject constructor(
    @Named("no_auth") private val okHttpClientNoAuth: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (accessKey, secretKey, configuredRegion) = parseCredentials(apiKey)
        val bodyBytes = buildRequestBody(modelId, userText, images, effort).toString().toByteArray()
        val candidates = generateCandidateInvocations(modelId, configuredRegion)

        var lastException: Exception? = null
        for ((candId, candRegion) in candidates) {
            val url = "https://bedrock-runtime.$candRegion.amazonaws.com/model/${java.net.URLEncoder.encode(candId, "UTF-8")}/invoke"
            val baseRequest = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                .build()
            val signedRequest = AwsSigV4Signer.sign(baseRequest, accessKey, secretKey, candRegion, bodyBytes)

            try {
                okHttpClientNoAuth.newCall(signedRequest).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        android.util.Log.d("BedrockIAM", "Claude inference succeeded: model=$candId in region=$candRegion")
                        return@withContext extractText(bodyString)
                    }

                    if (response.code == 400 && (bodyString.contains("model identifier", ignoreCase = true) || bodyString.contains("ValidationException", ignoreCase = true) || bodyString.contains("throughput", ignoreCase = true))) {
                        android.util.Log.w("BedrockIAM", "Claude candidate $candId in $candRegion failed (400), trying next...")
                        lastException = buildHttpErrorException("Bedrock Runtime IAM (Claude)", response.code, bodyString, json)
                        return@use
                    }

                    throw buildHttpErrorException("Bedrock Runtime IAM (Claude)", response.code, bodyString, json)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServiceUnavailableException) {
                throw e
            } catch (e: RateLimitException) {
                throw e
            } catch (e: Exception) {
                if (e is AiCallException && (e.message?.contains("HTTP 401") == true || e.message?.contains("HTTP 403") == true)) {
                    throw e
                }
                lastException = e
            }
        }
        throw lastException ?: AiCallException("Chiamata Bedrock Runtime IAM (Claude) fallita per il modello $modelId")
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("anthropic_version", "bedrock-2023-05-31")
        put("max_tokens", MAX_OUTPUT_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractAnthropicText(json, rawResponseBody, "Bedrock Runtime IAM (Claude)")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String =
        completeJson(apiKey, modelId, userText)

    companion object {
        private const val MAX_OUTPUT_TOKENS = 16384

        fun parseCredentials(apiKey: String): Triple<String, String, String> {
            val parts = apiKey.split('|')
            require(parts.size == 3) {
                "Credenziali Bedrock IAM non valide: formato atteso 'accessKey|secretKey|region'"
            }
            return Triple(parts[0], parts[1], parts[2])
        }

        /**
         * Bedrock Runtime on-demand throughput requires a cross-region inference profile ID, not
         * a bare model ID. The profile prefix is derived from the region: us-east-* / us-west-* →
         * "us.", eu-* → "eu.", ap-* → "apac.". If modelId is already an ARN or already prefixed,
         * it is returned unchanged.
         *
         * Automatically sanitizes invalid non-numeric suffixes (e.g., ":256k") without altering
         * valid Bedrock model version identifiers (e.g. ":0", ":1", "-v1:0").
         */
        fun resolveInferenceProfileId(modelId: String, region: String): String {
            // If already an ARN or already has a cross-region/system profile prefix, return directly
            if (modelId.startsWith("arn:aws:bedrock:") ||
                modelId.startsWith("us.") ||
                modelId.startsWith("eu.") ||
                modelId.startsWith("apac.") ||
                modelId.startsWith("global.")
            ) {
                return modelId
            }

            // Do not add cross-region prefixes to regional-only models that don't support them
            if (modelId.startsWith("amazon.titan")) {
                return modelId
            }

            // Sanitize: only remove non-numeric context-length suffixes like ":256k" if present
            val sanitized = modelId.replace(Regex(":[a-zA-Z].*$"), "")

            // Models available exclusively via the US cross-region profile
            if (sanitized.contains("deepseek", ignoreCase = true) ||
                sanitized.contains("qwen", ignoreCase = true) ||
                sanitized.contains("cohere", ignoreCase = true) ||
                sanitized.contains("ai21", ignoreCase = true) ||
                sanitized.contains("moonshot", ignoreCase = true) ||
                sanitized.contains("kimi", ignoreCase = true) ||
                sanitized.contains("zhipu", ignoreCase = true) ||
                sanitized.contains("minimax", ignoreCase = true)
            ) {
                return if (sanitized.startsWith("us.")) sanitized else "us.$sanitized"
            }

            // Nova models are available in us. and apac.
            if (sanitized.contains("nova", ignoreCase = true) || sanitized.startsWith("amazon.nova")) {
                val prefix = if (region.startsWith("ap-")) "apac." else "us."
                return if (sanitized.startsWith(prefix)) sanitized else "$prefix$sanitized"
            }

            val prefix = when {
                region.startsWith("us-") -> "us."
                region.startsWith("eu-") -> "eu."
                region.startsWith("ap-") -> "apac."
                else -> "us."
            }
            return if (sanitized.startsWith(prefix)) sanitized else "$prefix$sanitized"
        }

        /**
         * Resolves the AWS region where the inference request must be sent and signed.
         * System inference profiles (e.g. us.*, eu.*, apac.*) MUST be sent to their corresponding
         * regional endpoint (us-east-1 for us.*, eu-central-1 for eu.*, ap-northeast-1 for apac.*)
         * and signed with that exact region in AWS SigV4.
         */
        fun resolveTargetRegion(resolvedId: String, configuredRegion: String): String {
            return when {
                resolvedId.startsWith("us.") -> if (configuredRegion.startsWith("us-")) configuredRegion else "us-east-1"
                resolvedId.startsWith("eu.") -> if (configuredRegion.startsWith("eu-")) configuredRegion else "eu-central-1"
                resolvedId.startsWith("apac.") -> if (configuredRegion.startsWith("ap-")) configuredRegion else "ap-northeast-1"
                else -> configuredRegion
            }
        }

        /**
         * Generates all viable (modelId, region) candidate pairs to attempt in sequence if Bedrock
         * returns a 400 ValidationException on the primary profile ID.
         */
        fun generateCandidateInvocations(modelId: String, configuredRegion: String): List<Pair<String, String>> {
            val candidates = mutableListOf<Pair<String, String>>()

            val primaryResolvedId = resolveInferenceProfileId(modelId, configuredRegion)
            val primaryTargetRegion = resolveTargetRegion(primaryResolvedId, configuredRegion)
            candidates.add(primaryResolvedId to primaryTargetRegion)

            val sanitized = modelId.replace(Regex(":[a-zA-Z].*$"), "")

            // DeepSeek specific variations (R1, V3, V3.1, V3.2)
            if (sanitized.contains("deepseek", ignoreCase = true)) {
                candidates.add("us.deepseek.r1-v1:0" to "us-east-1")
                candidates.add("deepseek.r1-v1:0" to "us-east-1")
                candidates.add("deepseek.v3.2" to "us-east-1")
                candidates.add("us.deepseek.v3.2" to "us-east-1")
                candidates.add("deepseek.v3.2:0" to "us-east-1")
                candidates.add("us.deepseek.v3.2:0" to "us-east-1")
                candidates.add("deepseek.v3-v1:0" to "us-east-1")
                candidates.add("us.deepseek.v3-v1:0" to "us-east-1")
                candidates.add("deepseek.r1-v1:0" to configuredRegion)
                candidates.add("deepseek.v3.2" to configuredRegion)
            }

            // Bare model in configured region and in us-east-1
            candidates.add(sanitized to configuredRegion)
            candidates.add(sanitized to "us-east-1")

            // With us. prefix in us-east-1
            if (!sanitized.startsWith("us.")) {
                candidates.add("us.$sanitized" to "us-east-1")
            } else {
                candidates.add(sanitized.removePrefix("us.") to configuredRegion)
            }

            // With :0 if no colon present
            if (!sanitized.contains(":")) {
                candidates.add("$sanitized:0" to configuredRegion)
                candidates.add("$sanitized:0" to "us-east-1")
                candidates.add("us.$sanitized:0" to "us-east-1")
            }

            return candidates.distinct()
        }
    }
}

/**
 * Chiama modelli Amazon (Nova, Titan ecc.) tramite AWS Bedrock Runtime diretto con SigV4.
 * Endpoint: POST https://bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
 * Usa il formato nativo Amazon Bedrock ("messages" + "inferenceConfig"), distinto dal formato
 * Anthropic usato da Claude — questo è il motivo del provider separato BEDROCK_IAM_AMAZON.
 *
 * Il parametro apiKey arriva nel formato "accessKey|secretKey|region".
 */
@Singleton
class BedrockIamAmazonClient @Inject constructor(
    @Named("no_auth") private val okHttpClientNoAuth: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (accessKey, secretKey, configuredRegion) = BedrockIamClaudeClient.parseCredentials(apiKey)
        val bodyBytes = buildRequestBody(modelId, userText, images, effort).toString().toByteArray()
        val candidates = BedrockIamClaudeClient.generateCandidateInvocations(modelId, configuredRegion)

        var lastException: Exception? = null
        for ((candId, candRegion) in candidates) {
            val url = "https://bedrock-runtime.$candRegion.amazonaws.com/model/${java.net.URLEncoder.encode(candId, "UTF-8")}/converse"
            val baseRequest = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                .build()
            val signedRequest = AwsSigV4Signer.sign(baseRequest, accessKey, secretKey, candRegion, bodyBytes)

            try {
                okHttpClientNoAuth.newCall(signedRequest).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        android.util.Log.d("BedrockIAM", "Amazon/universal inference succeeded: model=$candId in region=$candRegion")
                        return@withContext extractText(bodyString)
                    }

                    if (response.code == 400 && (bodyString.contains("model identifier", ignoreCase = true) || bodyString.contains("ValidationException", ignoreCase = true) || bodyString.contains("throughput", ignoreCase = true))) {
                        android.util.Log.w("BedrockIAM", "Amazon candidate $candId in $candRegion failed (400), trying next...")
                        lastException = buildHttpErrorException("Bedrock Runtime IAM (Amazon)", response.code, bodyString, json)
                        return@use
                    }

                    throw buildHttpErrorException("Bedrock Runtime IAM (Amazon)", response.code, bodyString, json)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServiceUnavailableException) {
                throw e
            } catch (e: RateLimitException) {
                throw e
            } catch (e: Exception) {
                if (e is AiCallException && (e.message?.contains("HTTP 401") == true || e.message?.contains("HTTP 403") == true)) {
                    throw e
                }
                lastException = e
            }
        }
        throw lastException ?: AiCallException("Chiamata Bedrock Runtime IAM (Amazon) fallita per il modello $modelId")
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            putJsonObject("image") {
                                put("format", if (image.mimeType == "image/png") "png" else "jpeg")
                                putJsonObject("source") {
                                    put("bytes", image.base64)
                                }
                            }
                        }
                    }
                    addJsonObject {
                        put("text", userText)
                    }
                }
            }
        }
        putJsonObject("inferenceConfig") {
            put("maxTokens", MAX_OUTPUT_TOKENS)
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractBedrockConverseText(json, rawResponseBody)

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String =
        completeJson(apiKey, modelId, userText)

    companion object {
        private const val MAX_OUTPUT_TOKENS = 8192
    }
}

/**
 * Chiama modelli OpenAI su AWS Bedrock Runtime diretto con SigV4 (Access Key + Secret Key).
 * Endpoint: POST https://bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
 * Usa il body OpenAI-compatible (messages/content) supportato da Bedrock per i modelli GPT.
 *
 * Il parametro apiKey arriva nel formato "accessKey|secretKey|region".
 */
@Singleton
class BedrockIamGptClient @Inject constructor(
    @Named("no_auth") private val okHttpClientNoAuth: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (accessKey, secretKey, configuredRegion) = BedrockIamClaudeClient.parseCredentials(apiKey)
        val bodyBytes = buildRequestBody(modelId, userText, images).toString().toByteArray()
        val candidates = BedrockIamClaudeClient.generateCandidateInvocations(modelId, configuredRegion)

        var lastException: Exception? = null
        for ((candId, candRegion) in candidates) {
            val url = "https://bedrock-runtime.$candRegion.amazonaws.com/model/${java.net.URLEncoder.encode(candId, "UTF-8")}/invoke"
            val baseRequest = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                .build()
            val signedRequest = AwsSigV4Signer.sign(baseRequest, accessKey, secretKey, candRegion, bodyBytes)

            try {
                okHttpClientNoAuth.newCall(signedRequest).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        android.util.Log.d("BedrockIAM", "GPT inference succeeded: model=$candId in region=$candRegion")
                        return@withContext extractText(bodyString)
                    }

                    if (response.code == 400 && (bodyString.contains("model identifier", ignoreCase = true) || bodyString.contains("ValidationException", ignoreCase = true) || bodyString.contains("throughput", ignoreCase = true))) {
                        android.util.Log.w("BedrockIAM", "GPT candidate $candId in $candRegion failed (400), trying next...")
                        lastException = buildHttpErrorException("Bedrock Runtime IAM (GPT)", response.code, bodyString, json)
                        return@use
                    }

                    throw buildHttpErrorException("Bedrock Runtime IAM (GPT)", response.code, bodyString, json)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ServiceUnavailableException) {
                throw e
            } catch (e: RateLimitException) {
                throw e
            } catch (e: Exception) {
                if (e is AiCallException && (e.message?.contains("HTTP 401") == true || e.message?.contains("HTTP 403") == true)) {
                    throw e
                }
                lastException = e
            }
        }
        throw lastException ?: AiCallException("Chiamata Bedrock Runtime IAM (GPT) fallita per il modello $modelId")
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        put("model", modelId)
        put("max_tokens", MAX_OUTPUT_TOKENS)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.base64}") }
                        }
                    }
                    addJsonObject { put("type", "text"); put("text", userText) }
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractOpenAiText(json, rawResponseBody, "Bedrock Runtime IAM (GPT)")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String {
        throw AiCallException("Ricerca online non disponibile per GPT su Bedrock Runtime IAM.")
    }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 16384
    }
}

/**
 * Chiama Gemini tramite l'endpoint pubblico Google AI Studio (generativelanguage.googleapis.com),
 * autenticato con una semplice API key (fascia gratuita con limiti di frequenza, nessun account
 * Google Cloud/fatturazione richiesto — a differenza di Vertex AI).
 */
@Singleton
class GeminiCompletionClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        // La API key va nell'header (non nella query string): l'interceptor OkHttp logga la riga
        // della richiesta (URL incluso) anche a livello BASIC, esporrebbe la chiave in Logcat.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(buildRequestBody(modelId, userText, images, effort).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Gemini (AI Studio)", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    images.forEach { image ->
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", image.mimeType)
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject { put("text", userText) }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            val lower = modelId.lowercase()
            if ("2.5" in lower || "3." in lower || "thinking" in lower) {
                val budget = when (effort) {
                    AiEffort.LOW -> if ("pro" in lower) 128 else 0
                    AiEffort.MEDIUM -> 2048
                    AiEffort.HIGH -> 8192
                }
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", budget)
                }
            }
        }
        // Disabilita i filtri di sicurezza per evitare falsi positivi sulle foto di cibo 
        // (es. coltelli o forme ambigue) che bloccano l'intera risposta (§5 M1).
        putJsonArray("safetySettings") {
            val categories = listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
                "HARM_CATEGORY_CIVIC_INTEGRITY"
            )
            categories.forEach { category ->
                addJsonObject {
                    put("category", category)
                    put("threshold", "BLOCK_NONE")
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractGeminiText(json, rawResponseBody, "Gemini")

    /**
     * Unico provider con ricerca online realmente funzionante ora (§Pezzo D del piano): usa
     * Grounding with Google Search dell'API pubblica AI Studio, con la stessa API key già usata
     * per Visione/Normalizzazione/Matching — nessun account/fatturazione aggiuntivi richiesti.
     */
    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(buildSearchRequestBody(userText).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Gemini (ricerca online)", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    /**
     * A differenza di [buildRequestBody] (Passaggi 1/2/3), qui NON si può impostare
     * `generationConfig.responseMimeType = "application/json"`: l'API Gemini non permette di
     * combinare Grounding with Google Search con l'output JSON forzato. Ci si affida invece
     * all'istruzione "SOLO JSON" nel prompt (vedi PromptTemplates.onlineFoodResearchPrompt) e alla
     * pulizia già esistente lato client (AiOrchestrator.stripMarkdownFences + decodeDto).
     */
    private fun buildSearchRequestBody(userText: String): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", userText) }
                }
            }
        }
        putJsonArray("tools") {
            addJsonObject {
                putJsonObject("google_search") {}
            }
        }
    }
}

/**
 * Chiama Gemini tramite Google Cloud Vertex AI. Richiede un access token OAuth2 (ottenuto via
 * [com.ugocalc.app.data.remote.ai.auth.VertexAiTokenProvider]) e l'endpoint specifico del progetto.
 *
 * Il parametro apiKey arriva nel formato "accessToken|projectId|region".
 */
@Singleton
class VertexAiCompletionClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
        effort: AiEffort,
    ): String = withContext(Dispatchers.IO) {
        val (token, projectId, region) = parseCredentials(apiKey)
        val url = "https://$region-aiplatform.googleapis.com/v1/projects/$projectId/locations/$region/publishers/google/models/$modelId:generateContent"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(buildRequestBody(modelId, userText, images, effort).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildHttpErrorException("Vertex AI", response.code, bodyString, json)
            }
            extractText(bodyString)
        }
    }

    private fun parseCredentials(apiKey: String): Triple<String, String, String> {
        val parts = apiKey.split('|')
        require(parts.size == 3) { "Credenziali Vertex AI non valide: formato atteso 'token|projectId|region'" }
        return Triple(parts[0], parts[1], parts[2])
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>, effort: AiEffort = AiEffort.MEDIUM): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    images.forEach { image ->
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", image.mimeType)
                                put("data", image.base64)
                            }
                        }
                    }
                    addJsonObject { put("text", userText) }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            val lower = modelId.lowercase()
            if ("2.5" in lower || "3." in lower || "thinking" in lower) {
                val budget = when (effort) {
                    AiEffort.LOW -> if ("pro" in lower) 128 else 0
                    AiEffort.MEDIUM -> 2048
                    AiEffort.HIGH -> 8192
                }
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", budget)
                }
            }
        }
        putJsonArray("safetySettings") {
            val categories = listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
                "HARM_CATEGORY_CIVIC_INTEGRITY"
            )
            categories.forEach { category ->
                addJsonObject {
                    put("category", category)
                    put("threshold", "BLOCK_NONE")
                }
            }
        }
    }

    private fun extractText(rawResponseBody: String): String =
        extractGeminiText(json, rawResponseBody, "Vertex AI")

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String =
        throw AiCallException("Ricerca online non supportata su Vertex AI (usare Gemini AI Studio per il grounding).")
}
