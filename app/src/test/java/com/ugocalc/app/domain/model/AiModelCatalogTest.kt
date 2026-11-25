package com.ugocalc.app.domain.model

import com.ugocalc.app.data.remote.ai.client.putClaudeEffortAndMaxTokens
import com.ugocalc.app.data.remote.ai.client.putGeminiThinkingConfig
import com.ugocalc.app.data.remote.ai.client.putOpenAiEffort
import com.ugocalc.app.domain.inference.m1.PerceptionPrompt
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.prompts.PromptTemplates
import com.ugocalc.app.ui.components.formatCompactGrams
import com.ugocalc.app.ui.components.formatMealDisplayTitle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun `GitHub Models provider and models are completely removed`() {
        assertFalse(AiProviderType.entries.any { it.name == "OPENAI" || it.name == "GITHUB_MODELS" })
        assertFalse(AiModelCatalog.STATIC.any { it.id == "openai/gpt-4o" || it.subtitle.contains("GitHub", ignoreCase = true) })
        assertFalse(AiModelCatalog.ALL.any { it.id == "openai/gpt-4o" || it.subtitle.contains("GitHub", ignoreCase = true) })
        assertFalse("openai/gpt-4o" in ModelPricingCatalog.allKnownModelIds)
        assertTrue(AiModelCatalog.STATIC.isNotEmpty())
    }

    @Test
    fun `real per-model efforts match official Gemini, OpenAI and Anthropic capabilities`() {
        val gemini3Pro = AiModel("gemini-3-pro-preview", AiProviderType.GEMINI, "Gemini 3 Pro")
        assertEquals(listOf(AiEffort.LOW, AiEffort.HIGH), gemini3Pro.supportedEfforts())

        val gemini31Pro = AiModel("gemini-3.1-pro-preview", AiProviderType.GEMINI, "Gemini 3.1 Pro")
        assertEquals(listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH), gemini31Pro.supportedEfforts())

        val gemini3Flash = AiModel("gemini-3-flash-preview", AiProviderType.GEMINI, "Gemini 3 Flash")
        assertEquals(listOf(AiEffort.OFF, AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH), gemini3Flash.supportedEfforts())
        assertEquals("Minimal", gemini3Flash.effortLabel(AiEffort.OFF))

        val gemini4 = AiModel("gemini-4-neon", AiProviderType.GEMINI, "Gemini 4 Neon")
        assertEquals(listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH, AiEffort.MAX), gemini4.supportedEfforts())
        assertEquals("XHigh", gemini4.effortLabel(AiEffort.MAX))

        val nonThinkingFromAiStudio = AiModel("gemini-3-flash-preview", AiProviderType.GEMINI, "No Think", thinkingSupported = false)
        assertTrue(nonThinkingFromAiStudio.supportedEfforts().isEmpty())

        val futureThinkingFromAiStudio = AiModel("gemini-exp-future", AiProviderType.GEMINI, "Future", thinkingSupported = true)
        assertEquals(listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH), futureThinkingFromAiStudio.supportedEfforts())

        val gpt54 = AiModel("gpt-5.4", AiProviderType.OPENAI_API, "GPT-5.4")
        assertEquals("None", gpt54.effortLabel(AiEffort.OFF))
        assertEquals("XHigh", gpt54.effortLabel(AiEffort.MAX))

        val claude55 = AiModel("claude-opus-5-5", AiProviderType.ANTHROPIC, "Claude Opus 5.5")
        assertEquals(listOf(AiEffort.LOW, AiEffort.MEDIUM, AiEffort.HIGH, AiEffort.MAX), claude55.supportedEfforts())
        assertEquals("Max", claude55.effortLabel(AiEffort.MAX))
    }

    @Test
    fun `user chat prompt note is injected into both V1 and V2 vision prompts`() {
        val note = "Pasta integrale 90g, poco olio d'oliva"
        val v1Prompt = PromptTemplates.visionPrompt(photoTelemetryBlock = null, userPromptNote = note)
        assertTrue(v1Prompt.contains("[NOTE UTENTE SUL PIATTO]"))
        assertTrue(v1Prompt.contains(note))

        val v2Prompt = PerceptionPrompt.build(sessionMode = SessionMode.CASA, userPromptNote = note)
        assertTrue(v2Prompt.contains("[NOTE UTENTE SUL PIATTO]"))
        assertTrue(v2Prompt.contains(note))

        assertEquals(PromptTemplates.VISION_PROMPT, PromptTemplates.visionPrompt(null, "   "))
    }

    @Test
    fun `effective HTTP effort payloads serialize official provider fields`() {
        val g3Flash = buildJsonObject { putGeminiThinkingConfig("gemini-3-flash-preview", AiEffort.OFF) }
        assertEquals("MINIMAL", g3Flash["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        val g3ProMed = buildJsonObject { putGeminiThinkingConfig("gemini-3-pro-preview", AiEffort.MEDIUM) }
        assertEquals("HIGH", g3ProMed["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        val g31ProMed = buildJsonObject { putGeminiThinkingConfig("gemini-3.1-pro-preview", AiEffort.MEDIUM) }
        assertEquals("MEDIUM", g31ProMed["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        val g4Max = buildJsonObject { putGeminiThinkingConfig("gemini-4-neon", AiEffort.MAX) }
        assertEquals("XHIGH", g4Max["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        val g25ProHigh = buildJsonObject { putGeminiThinkingConfig("gemini-2.5-pro", AiEffort.HIGH) }
        assertEquals(24576, g25ProHigh["thinkingConfig"]?.jsonObject?.get("thinkingBudget")?.jsonPrimitive?.int)

        val g25FlashOff = buildJsonObject { putGeminiThinkingConfig("gemini-2.5-flash", AiEffort.OFF) }
        assertEquals(0, g25FlashOff["thinkingConfig"]?.jsonObject?.get("thinkingBudget")?.jsonPrimitive?.int)

        val gNoThink = buildJsonObject { putGeminiThinkingConfig("gemini-3-flash-preview", AiEffort.HIGH, aiStudioThinkingOverride = false) }
        assertNull(gNoThink["thinkingConfig"])

        val openAiMax = buildJsonObject { putOpenAiEffort("gpt-5.4", AiEffort.MAX) }
        assertEquals("xhigh", openAiMax["reasoning_effort"]?.jsonPrimitive?.content)

        val openAiGpt4o = buildJsonObject { putOpenAiEffort("gpt-4o", AiEffort.HIGH) }
        assertNull(openAiGpt4o["reasoning_effort"])

        val claudeOpus46Max = buildJsonObject { putClaudeEffortAndMaxTokens("claude-opus-4-6", 16384, AiEffort.MAX) }
        assertEquals("adaptive", claudeOpus46Max["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("max", claudeOpus46Max["output_config"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)

        val claudeSonnet46Max = buildJsonObject { putClaudeEffortAndMaxTokens("claude-sonnet-4-6", 16384, AiEffort.MAX) }
        assertEquals("high", claudeSonnet46Max["output_config"]?.jsonObject?.get("effort")?.jsonPrimitive?.content)

        val nonClaudeBedrock = buildJsonObject { putClaudeEffortAndMaxTokens("qwen.qwen-4.5-72b", 16384, AiEffort.HIGH) }
        assertNull(nonClaudeBedrock["thinking"])
        assertNull(nonClaudeBedrock["output_config"])
    }

    @Test
    fun `display formatting helpers capitalize meal titles and trim trailing zero decimals`() {
        assertEquals("Fettine di maiale alla griglia", formatMealDisplayTitle("fettine di maiale alla griglia"))
        assertEquals("Pasto", formatMealDisplayTitle("   "))
        assertEquals("0g", formatCompactGrams(0.0))
        assertEquals("21g", formatCompactGrams(21.0))
        assertEquals("19,9g", formatCompactGrams(19.9))
        assertEquals("124g", formatCompactGrams(124.4))
    }
}
