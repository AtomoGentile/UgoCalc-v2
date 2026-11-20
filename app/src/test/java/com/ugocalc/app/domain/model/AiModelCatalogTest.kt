package com.ugocalc.app.domain.model

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
}
