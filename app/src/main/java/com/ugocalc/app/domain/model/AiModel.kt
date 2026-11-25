package com.ugocalc.app.domain.model

import androidx.compose.runtime.Immutable
import com.ugocalc.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AiProviderType {
    OPENAI_API,
    ANTHROPIC,
    GEMINI,
    // AWS Bedrock via Mantle (Bearer token + workspace ID).
    BEDROCK_CLAUDE,
    BEDROCK_GPT,
    // AWS Bedrock Runtime diretto: credenziali IAM (Access Key + Secret + Region), SigV4 signing.
    // Claude → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke  (formato Anthropic)
    // GPT   → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke  (formato OpenAI-compat)
    // Amazon (Nova ecc.) → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke  (Converse/Native format)
    BEDROCK_IAM_CLAUDE,
    BEDROCK_IAM_GPT,
    BEDROCK_IAM_AMAZON,
    VERTEX_AI,
}

@Immutable
data class AiModel(
    val id: String,
    val provider: AiProviderType,
    val displayName: String,
    val subtitle: String = "",
    val logoRes: Int? = null,
    /** Flag reale `thinking` ricavato da Google AI Studio (`/v1beta/models`), `null` se non dichiarato dal provider. */
    val thinkingSupported: Boolean? = null,
) {
    /** Chiave univoca che distingue lo stesso modello su provider diversi (es. Bedrock Mantle vs IAM). */
    val uid: String get() = "${provider.name}::$id"
}

enum class AiEffort(val label: String, val apiValue: String, val description: String) {
    OFF("Off", "none", "Nessun ragionamento · immediato"),
    LOW("Low", "low", "Ragionamento minimo · veloce"),
    MEDIUM("Medium", "medium", "Bilanciato · standard"),
    HIGH("High", "high", "Ragionamento profondo"),
    MAX("Max", "max", "Massimo (Opus 4.6 / GPT-5 / Gemini 4)"),
}

enum class AiModelTier(val badge: String, val hint: String) {
    RECOMMENDED("Consigliato", "Miglior equilibrio tra precisione e velocità"),
    FAST("Veloce", "Economico, ideale per piatti semplici"),
    ACCURATE("Alta precisione", "Ottimo per piatti complessi e multi-ingrediente"),
    STANDARD("Standard", "Modello generico"),
}

fun AiModel.recommendedTier(): AiModelTier {
    val lower = id.lowercase()
    return when {
        "lite" in lower || "mini" in lower || "nano" in lower || "micro" in lower || "haiku" in lower ->
            AiModelTier.FAST
        "pro" in lower || "opus" in lower || "o1" in lower || "o3" in lower || "o4" in lower || "gpt-5" in lower ->
            AiModelTier.ACCURATE
        "flash" in lower || "sonnet" in lower || "gpt-4o" in lower || "gpt-4.1" in lower ->
            AiModelTier.RECOMMENDED
        else -> AiModelTier.STANDARD
    }
}

/**
 * Restituisce i livelli di effort reali supportati dall'API del modello, combinando i metadati
 * live ricevuti da Google AI Studio (`thinkingSupported`) con le specifiche ufficiali per famiglia.
 * Lista vuota = il modello non supporta ragionamento/effort configurabile (es. GPT-4o, GPT-4.1, Gemini 2.0).
 */
fun AiModel.supportedEfforts(): List<AiEffort> {
    val lower = id.lowercase()
    return when (provider) {
        AiProviderType.GEMINI, AiProviderType.VERTEX_AI -> when {
            thinkingSupported == false -> emptyList()
            "gemini-4" in lower -> listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH, AiEffort.MAX)
            "gemini-3" in lower -> when {
                "3.1-pro" in lower || "3-1-pro" in lower || "3.5-pro" in lower ->
                    listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
                "pro" in lower ->
                    // gemini-3-pro-preview supporta esclusivamente LOW e HIGH (MEDIUM genera HTTP 400).
                    listOf(AiEffort.LOW, AiEffort.HIGH)
                "flash" in lower || "lite" in lower ->
                    // gemini-3-flash / 3.6-flash / 3.8-flash: MINIMAL, LOW, MEDIUM, HIGH.
                    listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
                else -> listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            }
            "2.5" in lower -> if ("pro" in lower) {
                listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            } else {
                listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            }
            thinkingSupported == true -> listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            else -> emptyList()
        }
        AiProviderType.OPENAI_API, AiProviderType.BEDROCK_GPT, AiProviderType.BEDROCK_IAM_GPT -> when {
            "gpt-5.2" in lower || "gpt-5.3" in lower || "gpt-5.4" in lower || "gpt-5-pro" in lower || "o3-pro" in lower ->
                listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH, AiEffort.MAX)
            "gpt-5" in lower -> listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            "o1" in lower || "o3" in lower || "o4" in lower -> listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            else -> emptyList()
        }
        AiProviderType.ANTHROPIC, AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_IAM_CLAUDE -> when {
            "5-5" in lower || "5.5" in lower || "5-2" in lower || "5.2" in lower || "4-6" in lower || "4.6" in lower ->
                listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH, AiEffort.MAX)
            "4-5" in lower || "4.5" in lower || "4-1" in lower || "4.1" in lower ||
                "4-0" in lower || "sonnet-4" in lower || "opus-4" in lower || "haiku-4" in lower ||
                "3-7" in lower || "3.7" in lower ->
                listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH)
            else -> emptyList()
        }
        AiProviderType.BEDROCK_IAM_AMAZON -> emptyList()
    }
}

fun AiModel.supportsEffort(): Boolean = supportedEfforts().isNotEmpty()

/** Etichetta reale del livello di effort così come definita dall'API del modello selezionato. */
fun AiModel.effortLabel(effort: AiEffort): String {
    val lower = id.lowercase()
    return when (provider) {
        AiProviderType.GEMINI, AiProviderType.VERTEX_AI -> when {
            "gemini-4" in lower -> when (effort) {
                AiEffort.OFF, AiEffort.LOW -> "Low"
                AiEffort.MEDIUM -> "Medium"
                AiEffort.HIGH -> "High"
                AiEffort.MAX -> "XHigh"
            }
            "gemini-3" in lower -> when (effort) {
                AiEffort.OFF -> if ("pro" in lower) "Low" else "Minimal"
                AiEffort.LOW -> "Low"
                AiEffort.MEDIUM -> if ("pro" in lower && "3.1" !in lower && "3-1" !in lower && "3.5" !in lower) "High" else "Medium"
                AiEffort.HIGH, AiEffort.MAX -> "High"
            }
            else -> effort.label
        }
        AiProviderType.OPENAI_API, AiProviderType.BEDROCK_GPT, AiProviderType.BEDROCK_IAM_GPT -> when {
            "gpt-5.2" in lower || "gpt-5.3" in lower || "gpt-5.4" in lower || "gpt-5-pro" in lower || "o3-pro" in lower -> when (effort) {
                AiEffort.OFF -> "None"
                AiEffort.MAX -> "XHigh"
                else -> effort.label
            }
            "gpt-5" in lower -> when (effort) {
                AiEffort.OFF -> "Minimal"
                else -> effort.label
            }
            else -> effort.label
        }
        else -> effort.label
    }
}

/** Descrizione tecnica fedele al parametro realmente inviato all'API per questo modello. */
fun AiModel.effortDescription(effort: AiEffort): String {
    val lower = id.lowercase()
    val realLabel = effortLabel(effort)
    return when (provider) {
        AiProviderType.GEMINI, AiProviderType.VERTEX_AI -> when {
            "gemini-4" in lower || "gemini-3" in lower ->
                "thinkingLevel: ${realLabel.lowercase()} · ${effort.description}"
            "2.5" in lower -> {
                val budget = when (effort) {
                    AiEffort.OFF -> if ("pro" in lower) 1024 else 0
                    AiEffort.LOW -> 1024
                    AiEffort.MEDIUM -> 8192
                    AiEffort.HIGH, AiEffort.MAX -> 24576
                }
                if (budget == 0) "thinkingBudget: 0 (disattivato · immediato)"
                else "thinkingBudget: $budget token · ${effort.description}"
            }
            else -> effort.description
        }
        AiProviderType.OPENAI_API, AiProviderType.BEDROCK_GPT, AiProviderType.BEDROCK_IAM_GPT ->
            "reasoning_effort: ${realLabel.lowercase()} · ${effort.description}"
        AiProviderType.ANTHROPIC, AiProviderType.BEDROCK_CLAUDE, AiProviderType.BEDROCK_IAM_CLAUDE ->
            if ("5-5" in lower || "5.5" in lower || "5-2" in lower || "5.2" in lower || "4-6" in lower || "4.6" in lower) {
                "adaptive effort: ${realLabel.lowercase()} · ${effort.description}"
            } else {
                val budget = when (effort) {
                    AiEffort.OFF -> 0
                    AiEffort.LOW -> 1024
                    AiEffort.MEDIUM -> 4096
                    AiEffort.HIGH, AiEffort.MAX -> 16384
                }
                if (budget == 0) "thinking disattivato · risposta immediata"
                else "thinking budget: $budget token · ${effort.description}"
            }
        else -> effort.description
    }
}

/** Adatta l'effort scelto dall'utente al livello più vicino realmente supportato dal modello. */
fun AiModel.effectiveEffort(requested: AiEffort): AiEffort? {
    val supported = supportedEfforts()
    if (supported.isEmpty()) return null
    if (requested in supported) return requested
    return when (requested) {
        AiEffort.OFF -> supported.first()
        AiEffort.MAX -> supported.last()
        AiEffort.LOW -> supported.first()
        AiEffort.HIGH -> supported.last()
        AiEffort.MEDIUM -> supported[supported.size / 2]
    }
}

object AiModelCatalog {
    private val _catalogVersion = MutableStateFlow(0)
    val catalogVersion: StateFlow<Int> = _catalogVersion.asStateFlow()

    val DEFAULT_ANTHROPIC_MODELS: List<AiModel> = listOf(
        AiModel(
            id = "claude-sonnet-4-5-20250929",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Sonnet 4.5",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-haiku-4-5-20251001",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Haiku 4.5",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-opus-4-1-20250805",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Opus 4.1",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
    )

    val DEFAULT_OPENAI_MODELS: List<AiModel> = listOf(
        AiModel(
            id = "gpt-4o",
            provider = AiProviderType.OPENAI_API,
            displayName = "GPT-4o",
            subtitle = "OpenAI",
            logoRes = R.drawable.gpt,
        ),
        AiModel(
            id = "gpt-4o-mini",
            provider = AiProviderType.OPENAI_API,
            displayName = "GPT-4o mini",
            subtitle = "OpenAI",
            logoRes = R.drawable.gpt,
        ),
        AiModel(
            id = "gpt-4.1",
            provider = AiProviderType.OPENAI_API,
            displayName = "GPT-4.1",
            subtitle = "OpenAI",
            logoRes = R.drawable.gpt,
        ),
        AiModel(
            id = "gpt-4.1-mini",
            provider = AiProviderType.OPENAI_API,
            displayName = "GPT-4.1 mini",
            subtitle = "OpenAI",
            logoRes = R.drawable.gpt,
        ),
    )

    val DEFAULT_GEMINI_MODELS: List<AiModel> = listOf(
        AiModel(
            id = "gemini-2.5-flash",
            provider = AiProviderType.GEMINI,
            displayName = "Gemini 2.5 Flash",
            subtitle = "Google",
            logoRes = R.drawable.logogemini,
        ),
        AiModel(
            id = "gemini-2.5-pro",
            provider = AiProviderType.GEMINI,
            displayName = "Gemini 2.5 Pro",
            subtitle = "Google",
            logoRes = R.drawable.logogemini,
        ),
        AiModel(
            id = "gemini-2.5-flash-lite",
            provider = AiProviderType.GEMINI,
            displayName = "Gemini 2.5 Flash Lite",
            subtitle = "Google",
            logoRes = R.drawable.logogemini,
        ),
    )

    val DEFAULT_VERTEX_MODELS: List<AiModel> = listOf(
        AiModel(
            id = "gemini-2.5-flash",
            provider = AiProviderType.VERTEX_AI,
            displayName = "Gemini 2.5 Flash",
            subtitle = "Vertex AI",
            logoRes = R.drawable.logogemini,
        ),
        AiModel(
            id = "gemini-2.5-pro",
            provider = AiProviderType.VERTEX_AI,
            displayName = "Gemini 2.5 Pro",
            subtitle = "Vertex AI",
            logoRes = R.drawable.logogemini,
        ),
    )

    val STATIC: List<AiModel> = DEFAULT_ANTHROPIC_MODELS + DEFAULT_OPENAI_MODELS + DEFAULT_VERTEX_MODELS

    @Volatile private var anthropicModels: List<AiModel> = emptyList()
    @Volatile private var openAiModels: List<AiModel> = emptyList()
    @Volatile private var geminiModels: List<AiModel> = emptyList()
    @Volatile private var bedrockModels: List<AiModel> = emptyList()
    @Volatile private var iamModels: List<AiModel> = emptyList()

    val ALL: List<AiModel>
        get() = (anthropicModels.ifEmpty { DEFAULT_ANTHROPIC_MODELS }) +
            (openAiModels.ifEmpty { DEFAULT_OPENAI_MODELS }) +
            (geminiModels.ifEmpty { DEFAULT_GEMINI_MODELS }) +
            DEFAULT_VERTEX_MODELS +
            bedrockModels +
            iamModels

    val BEDROCK_MANTLE_OPTIONS: List<AiModel> get() = bedrockModels
    val BEDROCK_IAM_OPTIONS: List<AiModel> get() = iamModels

    fun setAnthropicModels(modelIds: List<String>) {
        anthropicModels = modelIds.distinct().map { id ->
            AiModel(
                id = id,
                provider = AiProviderType.ANTHROPIC,
                displayName = anthropicDisplayName(id),
                subtitle = "Anthropic",
                logoRes = R.drawable.logoclaude,
            )
        }
        _catalogVersion.update { it + 1 }
    }

    fun clearAnthropicModels() {
        anthropicModels = emptyList()
        _catalogVersion.update { it + 1 }
    }

    fun setOpenAiModels(modelIds: List<String>) {
        openAiModels = modelIds.distinct().map { id ->
            AiModel(
                id = id,
                provider = AiProviderType.OPENAI_API,
                displayName = openAiDisplayName(id),
                subtitle = "OpenAI",
                logoRes = R.drawable.gpt,
            )
        }
        _catalogVersion.update { it + 1 }
    }

    fun clearOpenAiModels() {
        openAiModels = emptyList()
        _catalogVersion.update { it + 1 }
    }

    fun setGeminiModels(
        modelIds: List<String>,
        thinkingCapabilities: Map<String, Boolean> = emptyMap(),
    ) {
        geminiModels = modelIds.distinct().map { id ->
            AiModel(
                id = id,
                provider = AiProviderType.GEMINI,
                displayName = geminiDisplayName(id),
                subtitle = "Google",
                logoRes = R.drawable.logogemini,
                thinkingSupported = thinkingCapabilities[id],
            )
        }
        _catalogVersion.update { it + 1 }
    }

    fun clearGeminiModels() {
        geminiModels = emptyList()
        _catalogVersion.update { it + 1 }
    }

    private fun anthropicDisplayName(id: String): String {
        val stripped = id.replace(Regex("-\\d{8}$"), "")
        val parts = stripped.split("-").filter { it.isNotEmpty() }
        val merged = mutableListOf<String>()
        for (part in parts) {
            if (merged.isNotEmpty() && merged.last().all { it.isDigit() || it == '.' } && part.all { it.isDigit() }) {
                merged[merged.lastIndex] = merged.last() + "." + part
            } else {
                merged.add(part)
            }
        }
        return merged.joinToString(" ") { part ->
            if (part.first().isDigit()) part else part.replaceFirstChar { it.uppercase() }
        }.ifBlank { id }
    }

    private fun openAiDisplayName(id: String): String {
        val lower = id.lowercase()
        return when {
            lower == "gpt-4o" -> "GPT-4o"
            lower == "gpt-4o-mini" -> "GPT-4o mini"
            lower.startsWith("gpt-") -> {
                val rest = id.removePrefix("gpt-").removePrefix("GPT-")
                val parts = rest.split("-").filter { it.isNotEmpty() }
                if (parts.isEmpty()) "GPT"
                else "GPT-${parts.first()}" + if (parts.size > 1) " " + parts.drop(1).joinToString(" ") else ""
            }
            else -> id.split("-").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                if (part.first().isDigit()) part else part.replaceFirstChar { it.uppercase() }
            }.ifBlank { id }
        }
    }

    private fun geminiDisplayName(id: String): String {
        // e.g. "gemini-2.5-pro" → "Gemini 2.5 Pro", "gemini-2.5-flash-preview-05-20" → "Gemini 2.5 Flash Preview 05-20"
        return id
            .removePrefix("models/")
            .split("-")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                // Keep numeric parts as-is (e.g. "2.5", "05"), capitalize words
                if (part.first().isDigit()) part else part.replaceFirstChar { it.uppercase() }
            }
            .ifBlank { id }
    }

    fun setBedrockModels(modelIds: List<String>) {
        bedrockModels = modelIds.map { bedrockModelFrom(it) }
        _catalogVersion.update { it + 1 }
    }

    fun clearBedrockModels() {
        bedrockModels = emptyList()
        _catalogVersion.update { it + 1 }
    }

    fun setBedrockIamModels(modelIds: List<String>) {
        iamModels = modelIds.map { bedrockModelFrom(it, iam = true) }
        _catalogVersion.update { it + 1 }
    }

    fun clearBedrockIamModels() {
        iamModels = emptyList()
        _catalogVersion.update { it + 1 }
    }

    fun bedrockModelFrom(modelId: String, iam: Boolean = false): AiModel {
        // For IAM clients, preserve the full inference profile ARN since Bedrock Runtime requires it.
        // For Mantle clients, extract the model ID for display and API calls.
        val actualModelId = if (!iam && modelId.startsWith("arn:aws:bedrock:")) {
            modelId.substringAfterLast("/")
        } else {
            modelId
        }

        val isGpt = actualModelId.startsWith("openai.") || actualModelId.contains("gpt", ignoreCase = true)
        val isClaude = actualModelId.startsWith("anthropic.") || actualModelId.contains("claude", ignoreCase = true)
        val isMeta = actualModelId.startsWith("meta.") || actualModelId.contains("llama", ignoreCase = true)
        val isAmazon = actualModelId.startsWith("amazon.") || actualModelId.contains("nova", ignoreCase = true)
        val isMistral = actualModelId.startsWith("mistral.") || actualModelId.contains("mistral", ignoreCase = true)
        val isCohere = actualModelId.startsWith("cohere.") || actualModelId.contains("pegasus", ignoreCase = true) || actualModelId.contains("command", ignoreCase = true) || actualModelId.contains("rerank", ignoreCase = true)
        val isAi21 = actualModelId.startsWith("ai21.") || actualModelId.contains("jamba", ignoreCase = true)
        val isDeepSeek = actualModelId.startsWith("deepseek.") || actualModelId.contains("deepseek", ignoreCase = true)
        val isQwen = actualModelId.startsWith("qwen.") || actualModelId.contains("qwen", ignoreCase = true)
        val isKimi = actualModelId.startsWith("moonshot.") || actualModelId.startsWith("kimi.") || actualModelId.contains("kimi", ignoreCase = true)
        val isZhipu = actualModelId.startsWith("zhipu.") || actualModelId.contains("glm", ignoreCase = true)
        val isMinimax = actualModelId.startsWith("minimax.") || actualModelId.contains("minimax", ignoreCase = true)
        val isNvidia = actualModelId.startsWith("nvidia.") || actualModelId.contains("nemotron", ignoreCase = true)
        val isWriter = actualModelId.startsWith("writer.") || actualModelId.contains("palmyra", ignoreCase = true)
        val isTwelveLabs = actualModelId.startsWith("twelvelabs.")
        val isGemma = (actualModelId.startsWith("google.") && actualModelId.contains("gemma")) || actualModelId.contains("gemma", ignoreCase = true)
        val isXai = actualModelId.contains("grok", ignoreCase = true) || actualModelId.contains("voxtral", ignoreCase = true)

        val provider = when {
            iam && isClaude -> AiProviderType.BEDROCK_IAM_CLAUDE
            iam && isGpt -> AiProviderType.BEDROCK_IAM_GPT
            iam -> AiProviderType.BEDROCK_IAM_AMAZON  // Converse API: Amazon, Llama, Mistral, ecc.
            isGpt -> AiProviderType.BEDROCK_GPT
            else -> AiProviderType.BEDROCK_CLAUDE
        }

        val logoRes = when {
            isClaude -> R.drawable.logoclaude
            isGpt -> R.drawable.gpt
            isMeta -> R.drawable.facebook_llama_logo_3dcc
            isMistral -> R.drawable.mistral
            isCohere -> R.drawable.cohere_logo_png_seeklogo_513871
            isAmazon -> R.drawable.nova
            isAi21 -> R.drawable.ai21_labs_logo
            isDeepSeek -> R.drawable.deepseek_logo
            isQwen -> R.drawable.qwen_logo_png_seeklogo_611723
            isKimi -> R.drawable.kimi_ai_logo_hd
            isZhipu -> R.drawable.zai
            isMinimax -> R.drawable.minimax
            isNvidia -> R.drawable.nvidialogo
            isWriter -> R.drawable.writer
            isTwelveLabs -> R.drawable.twelvelabs
            isGemma -> R.drawable.gemma
            isXai -> R.drawable.gpt
            else -> null
        }

        val displayName = actualModelId
            .removePrefix("global.")
            .removePrefix("anthropic.")
            .removePrefix("openai.")
            .removePrefix("meta.")
            .removePrefix("amazon.")
            .removePrefix("mistral.")
            .removePrefix("cohere.")
            .removePrefix("ai21.")
            .removePrefix("deepseek.")
            .removePrefix("qwen.")
            .removePrefix("moonshot.")
            .removePrefix("kimi.")
            .removePrefix("zhipu.")
            .removePrefix("minimax.")
            .removePrefix("nvidia.")
            .removePrefix("writer.")
            .removePrefix("twelvelabs.")
            .removePrefix("google.")
            // Strip date+version suffix: "-20251001-v1:0" → ""
            .replace(Regex("-\\d{8}-v\\d+:\\d+$"), "")
            // Normalize: "claude-sonnet-5" → "Sonnet 5", "gpt-5.6" → "GPT-5.6"
            .let { raw ->
                when {
                    raw.startsWith("claude-") -> {
                        val parts = raw.removePrefix("claude-").split("-")
                        // Merge consecutive digit-only tokens with "." so "4-8" → "4.8"
                        val merged = mutableListOf<String>()
                        for (part in parts) {
                            if (merged.isNotEmpty() && merged.last().all { it.isDigit() || it == '.' } && part.all { it.isDigit() }) {
                                merged[merged.lastIndex] = merged.last() + "." + part
                            } else {
                                merged.add(part)
                            }
                        }
                        merged.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    }
                    raw.startsWith("gpt-") -> raw.uppercase()
                    else -> raw.split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }
            }

        val subtitle = if (iam) "Bedrock IAM" else "Bedrock Mantle"

        return AiModel(
            id = actualModelId,
            provider = provider,
            displayName = displayName,
            subtitle = subtitle,
            logoRes = logoRes,
        )
    }
}
