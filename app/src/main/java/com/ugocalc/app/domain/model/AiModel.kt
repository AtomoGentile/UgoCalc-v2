package com.ugocalc.app.domain.model

import com.ugocalc.app.R

enum class AiProviderType {
    OPENAI,
    OPENAI_API,
    ANTHROPIC,
    GEMINI,
    // AWS Bedrock Runtime: credenziali IAM (Access Key + Secret + Region), SigV4 signing.
    // Claude → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
    // GPT   → bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
    BEDROCK_CLAUDE,
    BEDROCK_GPT,
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
            logoRes = R.drawable.logogpt,
        ),
        AiModel(
            id = "openai/gpt-5.6",
            provider = AiProviderType.OPENAI,
            displayName = "GPT-5.6",
            subtitle = "OpenAI",
            logoRes = R.drawable.logogpt,
        ),
        AiModel(
            id = "claude-sonnet-5",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Sonnet 5",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-opus-4-8",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Opus 4.8",
            subtitle = "Anthropic",
            logoRes = R.drawable.logoclaude,
        ),
        AiModel(
            id = "claude-fable-5",
            provider = AiProviderType.ANTHROPIC,
            displayName = "Claude Fable 5",
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

    val ALL get() = STATIC + bedrockModels

    private var bedrockModels: List<AiModel> = emptyList()

    // Modelli Bedrock che vogliamo mostrare nella UI (solo top tier, niente modelli legacy).
    private val bedrockAllowlist = setOf(
        "anthropic.claude-sonnet-5",
        "anthropic.claude-opus-4-8",
        "anthropic.claude-fable-5",
        "openai.gpt-5.6",
    )

    fun setBedrockModels(modelIds: List<String>) {
        bedrockModels = modelIds
            .filter { id -> bedrockAllowlist.any { allowed -> id.startsWith(allowed) } }
            .map { id -> bedrockModelFrom(id) }
    }

    fun bedrockModelFrom(modelId: String): AiModel {
        val isGpt = modelId.startsWith("openai.")
        val provider = if (isGpt) AiProviderType.BEDROCK_GPT else AiProviderType.BEDROCK_CLAUDE
        val logoRes = if (isGpt) R.drawable.logogpt else R.drawable.logoclaude
        val displayName = modelId
            .removePrefix("anthropic.")
            .removePrefix("openai.")
            .removePrefix("meta.")
            .removePrefix("amazon.")
            .replaceFirstChar { it.uppercase() }
        return AiModel(
            id = modelId,
            provider = provider,
            displayName = displayName,
            subtitle = "Bedrock",
            logoRes = logoRes,
        )
    }
}
