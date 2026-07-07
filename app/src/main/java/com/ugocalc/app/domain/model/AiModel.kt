package com.ugocalc.app.domain.model

enum class AiProviderType {
    OPENAI,
    OPENAI_API,

    OPENAI, ANTHROPIC, GEMINI
}

/** Un modello selezionabile nella schermata "scelta modello" (§2.4 punto 1) e nelle Impostazioni > API. */
data class AiModel(
    val id: String,
    val provider: AiProviderType,
    val displayName: String,
    val logoRes: Int? = null,
)

object AiModelCatalog {
    val ALL = listOf(
        // TEMPORANEO: id nel formato "{publisher}/{model}" richiesto da GitHub Models
        // (models.github.ai), usato per test gratuiti in attesa di una API key OpenAI vera.
        // Vedi OpenAiCompletionClient. La API key da inserire in Impostazioni > API è un
        // GitHub Personal Access Token con permesso "models: read", non una chiave OpenAI.
        AiModel(id = "openai/gpt-4o", provider = AiProviderType.OPENAI, displayName = "ChatGPT"),
        AiModel(id = "claude-opus-4.8", provider = AiProviderType.ANTHROPIC, displayName = "Claude"),
        AiModel(id = "gemini-3.5-flash", provider = AiProviderType.GEMINI, displayName = "Gemini"),
    )
}
