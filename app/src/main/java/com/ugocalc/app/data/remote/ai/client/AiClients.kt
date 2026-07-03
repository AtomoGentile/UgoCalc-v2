package com.ugocalc.app.data.remote.ai.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

open class AiCallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Sottotipo specifico per JSON sintatticamente non valido nella risposta del modello (vedi AiOrchestrator.withRetry: solo questo va ritentato, un HTTP/rate-limit non va ripetuto subito). */
class MalformedJsonException(message: String, cause: Throwable? = null) : AiCallException(message, cause)

/**
 * Sottotipo specifico per HTTP 503 ("servizio temporaneamente non disponibile"): a differenza di
 * un 429 (quota esaurita, ripetere subito peggiora la situazione) è quasi sempre un sovraccarico
 * transitorio lato provider — vedi AiOrchestrator.withRetry, che ritenta anche su questo.
 */
class ServiceUnavailableException(message: String, cause: Throwable? = null) : AiCallException(message, cause)

/** Una singola immagine allegata a una chiamata AI (già codificata in base64). */
data class ImagePart(val base64: String, val mimeType: String)

interface AiTextCompletionClient {
    suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart> = emptyList(),
    ): String

    /**
     * Ricerca online (con accesso al web) per un alimento assente dalle tabelle dell'utente: a
     * differenza di [completeJson], che interpreta solo dati già forniti nel prompt, qui il
     * modello deve poter cercare sul web. Nessuna implementazione di default: ogni provider deve
     * dichiarare esplicitamente se e come la supporta. Gemini e Anthropic la implementano
     * davvero (quest'ultima mai verificata con una chiave reale); OpenAI no, perché qui è ancora
     * il proxy gratuito GitHub Models al posto di una vera API OpenAI.
     */
    suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String
}

/**
 * TEMPORANEO: chiama GitHub Models (models.github.ai), non l'endpoint ufficiale OpenAI — vedi
 * commento su [com.ugocalc.app.domain.model.AiModelCatalog]. `apiKey` è un GitHub Personal Access
 * Token con permesso "models: read", non una chiave OpenAI. API compatibile OpenAI Chat Completions.
 */
@Singleton
class OpenAiCompletionClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
    ): String = withContext(Dispatchers.IO) {
        // Le API GitHub bloccano come "scraping" le richieste senza uno User-Agent riconoscibile
        // (OkHttp ne manda uno generico di default) — vedi anche Accept/X-GitHub-Api-Version,
        // raccomandati dalla documentazione GitHub Models.
        val request = Request.Builder()
            .url("https://models.github.ai/inference/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "UgoCalc-Android")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .post(buildRequestBody(modelId, userText, images).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = "Chiamata GitHub Models fallita: HTTP ${response.code} $bodyString"
                throw if (response.code == 503) ServiceUnavailableException(message) else AiCallException(message)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>): JsonObject = buildJsonObject {
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
    }

    private fun extractText(rawResponseBody: String): String {
        val root = try {
            json.parseToJsonElement(rawResponseBody).jsonObject
        } catch (e: Exception) {
            throw AiCallException("Risposta GitHub Models non valida (JSON malformato)", e)
        }
        return root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw AiCallException("Risposta GitHub Models priva di contenuto testuale")
    }

    override suspend fun searchOnline(apiKey: String, modelId: String, userText: String): String {
        throw AiCallException("Ricerca online non disponibile: GitHub Models (usato qui al posto di una vera API OpenAI) non supporta la ricerca web.")
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
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_API_VERSION)
            .post(buildRequestBody(modelId, userText, images).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = "Chiamata Anthropic fallita: HTTP ${response.code} $bodyString"
                throw if (response.code == 503) ServiceUnavailableException(message) else AiCallException(message)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(modelId: String, userText: String, images: List<ImagePart>): JsonObject = buildJsonObject {
        put("model", modelId)
        // A differenza di OpenAI/Gemini, dove è opzionale, Claude richiede max_tokens obbligatorio.
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

    private fun extractText(rawResponseBody: String): String {
        val root = try {
            json.parseToJsonElement(rawResponseBody).jsonObject
        } catch (e: Exception) {
            throw AiCallException("Risposta Anthropic non valida (JSON malformato)", e)
        }
        return root["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: throw AiCallException("Risposta Anthropic priva di contenuto testuale")
    }

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
                val message = "Chiamata Anthropic (ricerca online) fallita: HTTP ${response.code} $bodyString"
                throw if (response.code == 503) ServiceUnavailableException(message) else AiCallException(message)
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
 * Chiama Gemini tramite l'endpoint pubblico Google AI Studio (generativelanguage.googleapis.com),
 * autenticato con una semplice API key (fascia gratuita con limiti di frequenza, nessun account
 * Google Cloud/fatturazione richiesto — a differenza di Vertex AI).
 */
@Singleton
class GeminiCompletionClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiTextCompletionClient {

    override suspend fun completeJson(
        apiKey: String,
        modelId: String,
        userText: String,
        images: List<ImagePart>,
    ): String = withContext(Dispatchers.IO) {
        // La API key va nell'header (non nella query string): l'interceptor OkHttp logga la riga
        // della richiesta (URL incluso) anche a livello BASIC, esporrebbe la chiave in Logcat.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(buildRequestBody(userText, images).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = "Chiamata Gemini (AI Studio) fallita: HTTP ${response.code} $bodyString"
                throw if (response.code == 503) ServiceUnavailableException(message) else AiCallException(message)
            }
            extractText(bodyString)
        }
    }

    private fun buildRequestBody(userText: String, images: List<ImagePart>): JsonObject = buildJsonObject {
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
        }
    }

    private fun extractText(rawResponseBody: String): String {
        val root = try {
            json.parseToJsonElement(rawResponseBody).jsonObject
        } catch (e: Exception) {
            throw AiCallException("Risposta Gemini non valida (JSON malformato)", e)
        }
        return root["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: throw AiCallException("Risposta Gemini priva di contenuto testuale")
    }

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
                val message = "Chiamata Gemini (ricerca online) fallita: HTTP ${response.code} $bodyString"
                throw if (response.code == 503) ServiceUnavailableException(message) else AiCallException(message)
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
