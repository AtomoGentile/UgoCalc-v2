package com.ugocalc.app.data.remote.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.data.remote.ai.auth.VertexAiTokenProvider
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.client.AiTextCompletionClient
import com.ugocalc.app.data.remote.ai.client.AnthropicCompletionClient
import com.ugocalc.app.data.remote.ai.client.BedrockIamAmazonClient
import com.ugocalc.app.data.remote.ai.client.BedrockIamClaudeClient
import com.ugocalc.app.data.remote.ai.client.BedrockIamGptClient
import com.ugocalc.app.data.remote.ai.client.BedrockMantleClaudeClient
import com.ugocalc.app.data.remote.ai.client.BedrockMantleGptClient
import com.ugocalc.app.data.remote.ai.client.GeminiCompletionClient
import com.ugocalc.app.data.remote.ai.client.ImagePart
import com.ugocalc.app.data.remote.ai.client.MalformedJsonException
import com.ugocalc.app.data.remote.ai.client.OpenAiCompletionClient
import com.ugocalc.app.data.remote.ai.client.RateLimitException
import com.ugocalc.app.data.remote.ai.client.ServiceUnavailableException
import com.ugocalc.app.data.remote.ai.client.VertexAiCompletionClient
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    private val bedrockClaudeClient: BedrockMantleClaudeClient,
    private val bedrockGptClient: BedrockMantleGptClient,
    private val bedrockIamClaudeClient: BedrockIamClaudeClient,
    private val bedrockIamGptClient: BedrockIamGptClient,
    private val bedrockIamAmazonClient: BedrockIamAmazonClient,
    private val vertexAiClient: VertexAiCompletionClient,
    private val vertexAiTokenProvider: VertexAiTokenProvider,
    private val apiKeyStore: SecureApiKeyStore,
    internal val json: Json,
) {

    internal fun clientFor(provider: AiProviderType): AiTextCompletionClient = when (provider) {
        AiProviderType.OPENAI_API -> openAiClient
        AiProviderType.ANTHROPIC -> anthropicClient
        AiProviderType.GEMINI -> geminiClient
        AiProviderType.BEDROCK_CLAUDE -> bedrockClaudeClient
        AiProviderType.BEDROCK_GPT -> bedrockGptClient
        AiProviderType.BEDROCK_IAM_CLAUDE -> bedrockIamClaudeClient
        AiProviderType.BEDROCK_IAM_GPT -> bedrockIamGptClient
        AiProviderType.BEDROCK_IAM_AMAZON -> bedrockIamAmazonClient
        AiProviderType.VERTEX_AI -> vertexAiClient
    }

    internal suspend fun apiKeyFor(model: AiModel): String {
        return when (model.provider) {
            AiProviderType.VERTEX_AI -> {
                val token = vertexAiTokenProvider.getAccessToken()
                val projectId = apiKeyStore.getVertexAiProjectId()
                    ?: throw AiCallException("Project ID Vertex AI non configurato per ${model.displayName}")
                val region = apiKeyStore.getVertexAiRegion()
                "$token|$projectId|$region"
            }
            AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_GPT -> {
                val mantleKey = apiKeyStore.getBedrockMantleApiKey()
                if (mantleKey.isNullOrBlank()) {
                    throw AiCallException("API key Bedrock Mantle non configurata per ${model.displayName}")
                }
                val projectId = apiKeyStore.getBedrockMantleProjectId()
                if (projectId.isBlank()) {
                    throw AiCallException("Project ID Bedrock Mantle non configurato per ${model.displayName}")
                }
                val region = apiKeyStore.getBedrockRegion()
                if (region.isBlank()) {
                    throw AiCallException("Region AWS Bedrock Mantle non configurata per ${model.displayName}")
                }
                "$mantleKey|$projectId|$region"
            }
            AiProviderType.BEDROCK_IAM_CLAUDE, AiProviderType.BEDROCK_IAM_GPT, AiProviderType.BEDROCK_IAM_AMAZON -> {
                val accessKey = apiKeyStore.getBedrockIamAccessKey()
                val secretKey = apiKeyStore.getBedrockIamSecretKey()
                if (accessKey.isNullOrBlank() || secretKey.isNullOrBlank()) {
                    throw AiCallException("Credenziali IAM Bedrock non configurate per ${model.displayName}")
                }
                val region = apiKeyStore.getBedrockIamRegion()
                if (region.isBlank()) {
                    throw AiCallException("Region AWS Bedrock IAM non configurata per ${model.displayName}")
                }
                "$accessKey|$secretKey|$region"
            }
            else -> apiKeyStore.getApiKey(model.provider)
                ?: throw AiCallException("Nessuna API key configurata per ${model.displayName}")
        }
    }

    /**
     * Codifica immagini grezze in [ImagePart] pronte per [completeJson]:
     * - applica eventuale rotazione/flip EXIF prima del ridimensionamento;
     * - ridimensiona a max [MAX_AI_IMAGE_DIMENSION_PX] px per lato;
     * - comprime in JPEG scalando la qualità (85 -> 75 -> 65) per restare sotto ~2,8 MB;
     * - assegna il MIME type effettivo (`image/jpeg` se ricompressa, oppure rilevato dai magic
     *   bytes PNG/WEBP/JPEG se inviata senza ricompressione).
     */
    fun encodeImages(images: List<ByteArray>, mimeType: String): List<ImagePart> =
        images.map { bytes ->
            val (preparedBytes, actualMimeType) = prepareImageForAi(bytes, mimeType)
            ImagePart(Base64.encodeToString(preparedBytes, Base64.NO_WRAP), actualMimeType)
        }

    private fun prepareImageForAi(bytes: ByteArray, fallbackMimeType: String): Pair<ByteArray, String> {
        val orientation = readExifOrientation(bytes)
        val needsOrientationFix = orientation != ExifInterface.ORIENTATION_NORMAL &&
            orientation != ExifInterface.ORIENTATION_UNDEFINED

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) {
            return bytes to detectImageMimeType(bytes, fallbackMimeType)
        }

        val maxDim = srcW.coerceAtLeast(srcH)
        val needsResize = maxDim > MAX_AI_IMAGE_DIMENSION_PX
        val needsSizeCap = bytes.size > MAX_AI_IMAGE_BYTES

        if (!needsOrientationFix && !needsResize && !needsSizeCap) {
            return bytes to detectImageMimeType(bytes, fallbackMimeType)
        }

        var sampleSize = 1
        while (srcW / (sampleSize * 2) >= MAX_AI_IMAGE_DIMENSION_PX ||
            srcH / (sampleSize * 2) >= MAX_AI_IMAGE_DIMENSION_PX
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return bytes to detectImageMimeType(bytes, fallbackMimeType)

        var working: Bitmap = decoded
        try {
            val matrix = Matrix()
            applyExifOrientation(matrix, orientation)
            val decodedMax = working.width.coerceAtLeast(working.height)
            if (decodedMax > MAX_AI_IMAGE_DIMENSION_PX) {
                val scale = MAX_AI_IMAGE_DIMENSION_PX.toFloat() / decodedMax.toFloat()
                matrix.postScale(scale, scale)
            }
            if (!matrix.isIdentity) {
                val transformed = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
                if (transformed !== working) {
                    working.recycle()
                    working = transformed
                }
            }

            var compressed = compressJpegWithinCap(working)
            if (compressed.size > MAX_AI_IMAGE_BYTES && working.width > 512 && working.height > 512) {
                val shrinkMatrix = Matrix().apply { postScale(0.75f, 0.75f) }
                val shrunk = Bitmap.createBitmap(working, 0, 0, working.width, working.height, shrinkMatrix, true)
                if (shrunk !== working) {
                    working.recycle()
                    working = shrunk
                }
                compressed = compressJpegWithinCap(working)
            }
            return compressed to "image/jpeg"
        } finally {
            if (!working.isRecycled) {
                working.recycle()
            }
        }
    }

    private fun readExifOrientation(bytes: ByteArray): Int =
        runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
    }

    private fun compressJpegWithinCap(bitmap: Bitmap): ByteArray {
        val qualities = intArrayOf(85, 75, 65)
        var result = ByteArray(0)
        for (quality in qualities) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            result = out.toByteArray()
            if (result.size <= MAX_AI_IMAGE_BYTES) return result
        }
        return result
    }

    companion object {
        private const val MAX_AI_IMAGE_DIMENSION_PX = 1568
        private const val MAX_AI_IMAGE_BYTES = 2_800_000
        private val CODE_FENCE_REGEX = Regex("```([a-zA-Z0-9_-]*)\\s*\\r?\\n?([\\s\\S]*?)```")

        /**
         * Rileva il MIME type effettivo dai magic bytes dell'immagine (PNG, WEBP, JPEG, GIF),
         * ripiegando su [fallback] se il formato non è riconosciuto.
         */
        internal fun detectImageMimeType(bytes: ByteArray, fallback: String): String {
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return "image/png"
            }
            if (bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
            ) {
                return "image/jpeg"
            }
            if (bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
            ) {
                return "image/webp"
            }
            if (bytes.size >= 4 &&
                bytes[0] == 'G'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == '8'.code.toByte()
            ) {
                return "image/gif"
            }
            return fallback.ifBlank { "image/jpeg" }
        }

        /**
         * Estrae il payload JSON da una risposta grezza del modello:
         * 1. se inizia già con `{` o `[`, estrae il primo blocco bilanciato rispettando stringhe e `\"`;
         * 2. altrimenti cerca un blocco delimitato da fence markdown (```json ... ``` o ``` ... ```);
         * 3. altrimenti scansiona il testo alla ricerca del primo `{...}` o `[...]` bilanciato.
         */
        internal fun extractJsonPayload(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""

            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                extractFirstBalancedJson(trimmed)?.let { return it }
            }

            val fencedMatches = CODE_FENCE_REGEX.findAll(trimmed).toList()
            if (fencedMatches.isNotEmpty()) {
                val preferred = fencedMatches.firstOrNull { match ->
                    match.groupValues[1].equals("json", ignoreCase = true)
                } ?: fencedMatches.firstOrNull { match ->
                    val inner = match.groupValues[2].trim()
                    inner.startsWith("{") || inner.startsWith("[") || extractFirstBalancedJson(inner) != null
                } ?: fencedMatches.first()
                val inner = preferred.groupValues[2].trim()
                return extractFirstBalancedJson(inner) ?: inner
            }

            return extractFirstBalancedJson(trimmed) ?: trimmed
        }

        private fun extractFirstBalancedJson(text: String): String? {
            val start = text.indexOfFirst { it == '{' || it == '[' }
            if (start < 0) return null

            val expectedClosing = ArrayDeque<Char>()
            var inString = false
            var escaped = false

            for (i in start until text.length) {
                val ch = text[i]
                if (inString) {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        inString = false
                    }
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '{' -> expectedClosing.addLast('}')
                    '[' -> expectedClosing.addLast(']')
                    '}', ']' -> {
                        if (expectedClosing.isEmpty() || expectedClosing.last() != ch) {
                            return null
                        }
                        expectedClosing.removeLast()
                        if (expectedClosing.isEmpty()) {
                            return text.substring(start, i + 1)
                        }
                    }
                }
            }
            return null
        }

        internal fun defaultRetryDelayMs(attempt: Int, error: Throwable): Long = when (error) {
            is MalformedJsonException -> 1500.milliseconds.inWholeMilliseconds
            is ServiceUnavailableException -> 3.seconds.inWholeMilliseconds
            is RateLimitException -> (5 * (attempt + 1)).seconds.inWholeMilliseconds
            is IOException -> (1500L * (attempt + 1))
            else -> 1500L
        }

        internal suspend inline fun <T> executeWithRetry(
            maxAttempts: Int = 3,
            crossinline delayProvider: (attempt: Int, error: Throwable) -> Long = { attempt, error ->
                defaultRetryDelayMs(attempt, error)
            },
            crossinline block: suspend () -> T,
        ): T {
            var lastError: Throwable? = null
            repeat(maxAttempts) { attempt ->
                try {
                    return block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: MalformedJsonException) {
                    lastError = e
                    if (attempt < (maxAttempts - 1)) delay(delayProvider(attempt, e))
                } catch (e: ServiceUnavailableException) {
                    lastError = e
                    if (attempt < (maxAttempts - 1)) delay(delayProvider(attempt, e))
                } catch (e: RateLimitException) {
                    lastError = e
                    if (attempt < (maxAttempts - 1)) delay(delayProvider(attempt, e))
                } catch (e: IOException) {
                    lastError = e
                    if (attempt < (maxAttempts - 1)) delay(delayProvider(attempt, e))
                }
            }
            val error = lastError!!
            if (error is IOException) {
                val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                throw AiCallException(
                    "Errore di rete o timeout durante la chiamata AI ($detail). Verifica la connessione e riprova.",
                    error,
                )
            }
            throw error
        }
    }

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
     * Ritenta solo su errori transitori o recuperabili:
     * - [MalformedJsonException] (JSON malformato / troncato);
     * - [ServiceUnavailableException] (HTTP 500/502/503/504);
     * - [RateLimitException] (HTTP 429);
     * - [IOException] / `SocketTimeoutException` (rete instabile o timeout temporaneo).
     *
     * NON cattura mai [CancellationException] e NON ritenta errori definitivi come HTTP 400/401/403/404.
     */
    internal suspend inline fun <T> withRetry(
        maxAttempts: Int = 3,
        crossinline delayProvider: (attempt: Int, error: Throwable) -> Long = { attempt, error ->
            defaultRetryDelayMs(attempt, error)
        },
        crossinline block: suspend () -> T,
    ): T = executeWithRetry(maxAttempts = maxAttempts, delayProvider = delayProvider, block = block)

    internal inline fun <reified T> decodeDto(raw: String): T {
        val cleaned = extractJsonPayload(raw)
        return try {
            json.decodeFromString(cleaned)
        } catch (e: SerializationException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        } catch (e: IllegalArgumentException) {
            throw MalformedJsonException(malformedJsonMessage(cleaned, e), e)
        }
    }

    internal fun extractJsonPayload(raw: String): String = Companion.extractJsonPayload(raw)

    /** Include un estratto della risposta grezza: senza, un fallimento di parsing è indiagnosticabile a distanza. */
    internal fun malformedJsonMessage(cleaned: String, cause: Exception): String {
        val preview = cleaned.take(300).ifBlank { "(risposta vuota)" }
        return "JSON malformato nella risposta del modello (${cause.message}). Risposta ricevuta: $preview"
    }
}
