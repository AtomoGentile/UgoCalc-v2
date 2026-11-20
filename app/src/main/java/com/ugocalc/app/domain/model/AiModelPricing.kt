package com.ugocalc.app.domain.model

/**
 * Pricing per modello AI (USD per 1M di token).
 * Aggiornato febbraio 2025. Per modelli Bedrock, i prezzi sono filtrati dal catalogo
 * AWS; per Anthropic e Gemini diretti, dalle pagine di pricing ufficiali.
 */
data class ModelPricing(
    val inputPricePerMillionTokens: Double,  // USD/1M input tokens
    val outputPricePerMillionTokens: Double, // USD/1M output tokens
)

object ModelPricingCatalog {
    private val PRICING_MAP = mapOf(
        // Anthropic (direct API)
        "claude-sonnet-4-5-20250929" to ModelPricing(3.0, 15.0),
        "claude-haiku-4-5-20251001" to ModelPricing(1.0, 5.0),
        "claude-opus-4-1-20250805" to ModelPricing(15.0, 75.0),
        "claude-sonnet-4" to ModelPricing(3.0, 15.0),
        "claude-haiku-4" to ModelPricing(1.0, 5.0),
        "claude-opus-4" to ModelPricing(15.0, 75.0),
        "claude-3-5-sonnet" to ModelPricing(3.0, 15.0),
        "claude-3-5-haiku" to ModelPricing(0.8, 4.0),

        // OpenAI (direct API)
        "gpt-4o-mini" to ModelPricing(0.15, 0.6),
        "gpt-4o" to ModelPricing(2.5, 10.0),
        "gpt-4.1-nano" to ModelPricing(0.1, 0.4),
        "gpt-4.1-mini" to ModelPricing(0.4, 1.6),
        "gpt-4.1" to ModelPricing(2.0, 8.0),

        // Google Gemini (AI Studio & Vertex AI)
        "gemini-2.5-pro" to ModelPricing(1.25, 10.0),
        "gemini-2.5-flash-lite" to ModelPricing(0.10, 0.40),
        "gemini-2.5-flash" to ModelPricing(0.15, 0.60),

        // AWS Bedrock Claude
        "anthropic.claude-3-5-sonnet-20241022-v2:0" to ModelPricing(3.0, 15.0),
        "anthropic.claude-3-opus-20250219-v1:0" to ModelPricing(15.0, 75.0),
        "anthropic.claude-3-haiku-20250307-v1:0" to ModelPricing(0.8, 4.0),

        // AWS Bedrock GPT (via Mantle, prezzi approssimati)
        "openai.gpt-4-turbo-preview" to ModelPricing(10.0, 30.0),
        "openai.gpt-4o-2024-08-06" to ModelPricing(2.5, 10.0),

        // AWS Bedrock Amazon (Nova, etc.)
        "amazon.nova-pro" to ModelPricing(0.8, 3.2),
        "amazon.nova-lite" to ModelPricing(0.075, 0.3),
        "amazon.nova-xlarge" to ModelPricing(8.0, 32.0),

        // AWS Bedrock Meta Llama
        "meta.llama3-2-11b-instruct-v1:0" to ModelPricing(0.075, 0.3),
        "meta.llama3-2-1b-instruct-v1:0" to ModelPricing(0.0375, 0.15),
        "meta.llama3-1-405b-instruct-v1:0" to ModelPricing(5.35, 16.05),

        // Default per modelli sconosciuti (stima conservativa)
        "default" to ModelPricing(1.0, 3.0),
    )

    val allKnownModelIds: Set<String> get() = PRICING_MAP.keys - "default"

    fun getPricingFor(model: AiModel): ModelPricing {
        return PRICING_MAP[model.id]
            ?: PRICING_MAP.entries
                .filter { (key, _) -> key != "default" && model.id.contains(key, ignoreCase = true) }
                .maxByOrNull { (key, _) -> key.length }
                ?.value
            ?: PRICING_MAP["default"]!!
    }

    /**
     * Stima il costo di una chiamata AI basandosi sulla lunghezza del prompt e della risposta.
     * Approssimazione: 1 token ≈ 4 caratteri (standard OpenAI).
     */
    fun estimateCost(
        model: AiModel,
        promptText: String,
        responseText: String,
        imageCount: Int = 0,
    ): Double {
        val pricing = getPricingFor(model)

        // Token stimati per il testo
        val inputTokens = (promptText.length / 4.0).toLong() + (imageCount * 1000) // ~1K token per immagine
        val outputTokens = (responseText.length / 4.0).toLong()

        // Costo in USD
        val inputCost = (inputTokens / 1_000_000.0) * pricing.inputPricePerMillionTokens
        val outputCost = (outputTokens / 1_000_000.0) * pricing.outputPricePerMillionTokens

        return inputCost + outputCost
    }
}
