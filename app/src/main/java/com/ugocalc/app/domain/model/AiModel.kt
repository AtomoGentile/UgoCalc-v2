package com.ugocalc.app.domain.model

import com.ugocalc.app.R

enum class AiProviderType {
    OPENAI,
    OPENAI_API,
    ANTHROPIC,
    GEMINI,
    // AWS Bedrock via Mantle (Bearer token + workspace ID).
    BEDROCK_CLAUDE,
    BEDROCK_GPT,
    // AWS Bedrock Runtime diretto: credenziali IAM (Access Key + Secret + Region), SigV4 signing.
    // Claude → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
    // GPT   → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
    BEDROCK_IAM_CLAUDE,
    BEDROCK_IAM_GPT,
}

/** Un modello selezionabile nella schermata "scelta modello" (§2.4 punto 1) e nelle Impostazioni > API. */
data class AiModel(
    val id: String,
    val provider: AiProviderType,
    val displayName: String,
    val subtitle: String = "",
    val logoRes: Int? = null,
)

object AiModelCatalog {
    val STATIC = listOf(
        AiModel(
            id = "openai/gpt-4o",
            provider = AiProviderType.OPENAI,
            displayName = "GPT-4o",
            subtitle = "GitHub Models",
            logoRes = R.drawable.gpt,
        ),
        AiModel(
            id = "claude-sonnet-5",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Sonnet 5",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-opus-4-8",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Opus 4.8",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-fable-5",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Fable 5",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "gemini-3.1-pro",
            provider = AiProviderType.GEMINI,
            displayName = "Gemini 3.1 Pro",
            subtitle = "Google",
            logoRes = R.drawable.logogemini,
        ),
    )

    val ALL get() = STATIC + BEDROCK_MANTLE_OPTIONS + BEDROCK_IAM_OPTIONS

    private var bedrockModels: List<AiModel> = emptyList()
    private var iamModels: List<AiModel> = emptyList()

    val BEDROCK_MANTLE_OPTIONS: List<AiModel> get() = bedrockModels
    val BEDROCK_IAM_OPTIONS: List<AiModel> get() = iamModels

    fun setBedrockModels(modelIds: List<String>) {
        bedrockModels = modelIds.map { bedrockModelFrom(it) }
    }

    fun setBedrockIamModels(modelIds: List<String>) {
        iamModels = modelIds.map { bedrockModelFrom(it, iam = true) }
    }

    fun bedrockModelFrom(modelId: String, iam: Boolean = false): AiModel {
        val isGpt = modelId.startsWith("openai.")
        val isClaude = modelId.startsWith("anthropic.")
        val isMeta = modelId.startsWith("meta.")
        val isAmazon = modelId.startsWith("amazon.")
        val isMistral = modelId.startsWith("mistral.")
        val isCohere = modelId.startsWith("cohere.")
        val isAi21 = modelId.startsWith("ai21.")
        val isDeepSeek = modelId.startsWith("deepseek.")
        val isQwen = modelId.startsWith("qwen.")
        val isKimi = modelId.startsWith("moonshot.") || modelId.startsWith("kimi.") || modelId.contains("kimi", ignoreCase = true)
        val isZhipu = modelId.startsWith("zhipu.") || modelId.contains("glm", ignoreCase = true)
        val isMinimax = modelId.startsWith("minimax.")
        val isNvidia = modelId.startsWith("nvidia.")
        val isWriter = modelId.startsWith("writer.")
        val isTwelveLabs = modelId.startsWith("twelvelabs.")
        val isGemma = modelId.startsWith("google.") && modelId.contains("gemma")

        val provider = when {
            iam && isGpt -> AiProviderType.BEDROCK_IAM_GPT
            iam -> AiProviderType.BEDROCK_IAM_CLAUDE
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
            else -> null
        }

        val displayName = modelId
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
            id = modelId,
            provider = provider,
            displayName = displayName,
            subtitle = subtitle,
            logoRes = logoRes,
        )
    }
}
