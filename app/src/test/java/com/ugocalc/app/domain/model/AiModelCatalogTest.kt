package com.ugocalc.app.domain.model

import com.ugocalc.app.domain.inference.m1.PerceptionPrompt
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.prompts.PromptTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
