package com.ugocalc.app.data.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.util.Locale

class StarterFoodDatabaseTest {

    @Test
    fun `starter database exposes expected display name and around 180+ curated entries`() {
        assertEquals("Tabella Base Italiana (CREA)", StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME)
        assertTrue(
            "Expected at least 170 entries, got ${StarterFoodDatabase.ENTRIES.size}",
            StarterFoodDatabase.ENTRIES.size >= 170,
        )
    }

    @Test
    fun `all entries have unique non-blank names and realistic non-negative macronutrients`() {
        val normalizedNames = mutableSetOf<String>()
        for (entry in StarterFoodDatabase.ENTRIES) {
            assertTrue("Food name must not be blank", entry.nome.isNotBlank())
            val key = Normalizer.normalize(entry.nome.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            assertTrue("Duplicate entry found: '${entry.nome}'", normalizedNames.add(key))

            assertTrue("CHO must be finite for '${entry.nome}'", entry.choPer100g.isFinite())
            assertTrue("Grassi must be finite for '${entry.nome}'", entry.grassiPer100g.isFinite())
            assertTrue("Proteine must be finite for '${entry.nome}'", entry.proteinePer100g.isFinite())
            assertTrue("CHO must be in 0..100 for '${entry.nome}'", entry.choPer100g in 0.0..100.0)
            assertTrue("Grassi must be in 0..100 for '${entry.nome}'", entry.grassiPer100g in 0.0..100.0)
            assertTrue("Proteine must be in 0..100 for '${entry.nome}'", entry.proteinePer100g in 0.0..100.0)
            assertTrue(
                "CHO + Grassi + Proteine cannot exceed 100.5g per 100g for '${entry.nome}' (${entry.choPer100g} + ${entry.grassiPer100g} + ${entry.proteinePer100g})",
                entry.choPer100g + entry.grassiPer100g + entry.proteinePer100g <= 100.5,
            )
            assertTrue(
                "Indice Glicemico must be populated in 0..100 for '${entry.nome}', got ${entry.indiceGlicemico}",
                entry.indiceGlicemico != null && entry.indiceGlicemico in 0..100,
            )
        }
    }

    @Test
    fun `starter database covers staples from all 12 Italian food categories`() {
        val namesLower = StarterFoodDatabase.ENTRIES.map { it.nome.lowercase(Locale.ROOT) }
        val expectedKeywords = listOf(
            "pasta di semola (cotta)",       // Pasta e Cereali
            "pane integrale",                // Pane e Prodotti da Forno
            "pizza margherita",              // Pane e Prodotti da Forno
            "patate bollite",                // Patate e Tuberi
            "ceci bolliti",                  // Legumi
            "petto di pollo",                // Carni e Salumi
            "prosciutto crudo",              // Carni e Salumi
            "salmone fresco",                // Pesce e Frutti di Mare
            "mozzarella",                    // Uova e Latticini
            "mela",                          // Frutta e Frutta Secca
            "mandorle",                      // Frutta e Frutta Secca
            "zucchine",                      // Verdure
            "olio extravergine",             // Condimenti e Salse
            "cioccolato fondente",           // Dolci e Colazione
            "cappuccino",                    // Bevande
        )
        for (keyword in expectedKeywords) {
            assertTrue(
                "Expected starter database to include an item matching '$keyword'",
                namesLower.any { it.contains(keyword) },
            )
        }
    }
}
