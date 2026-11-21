package com.ugocalc.app.data.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.util.Locale

class StarterFoodDatabaseTest {

    @Test
    fun `starter database exposes expected display name, catalog version, and 600+ curated entries`() {
        assertEquals("Tabella Base Italiana (CREA)", StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME)
        assertTrue("Expected CATALOG_VERSION >= 2", StarterFoodDatabase.CATALOG_VERSION >= 2)
        assertTrue(
            "Expected at least 600 entries, got ${StarterFoodDatabase.ENTRIES.size}",
            StarterFoodDatabase.ENTRIES.size >= 600,
        )
        assertEquals(
            StarterFoodDatabase.CATEGORY_ENTRIES.values.sumOf { it.size },
            StarterFoodDatabase.ENTRIES.size,
        )
    }

    @Test
    fun `all 14 ItalianFoodCategory entries are populated and resolve via categoryForFoodName`() {
        assertEquals(14, StarterFoodDatabase.ItalianFoodCategory.entries.size)
        for (category in StarterFoodDatabase.ItalianFoodCategory.entries) {
            val items = StarterFoodDatabase.CATEGORY_ENTRIES[category]
            assertNotNull("Missing category $category in CATEGORY_ENTRIES", items)
            assertTrue(
                "Expected category ${category.label} to have at least 15 entries, got ${items!!.size}",
                items.size >= 15,
            )
            for (item in items) {
                assertEquals(
                    "categoryForFoodName mismatch for '${item.nome}'",
                    category,
                    StarterFoodDatabase.categoryForFoodName(item.nome),
                )
            }
        }

        // Fallback keyword classification for custom/imported foods + blank safety
        assertEquals(
            StarterFoodDatabase.ItalianFoodCategory.PRIMI_PIATTI,
            StarterFoodDatabase.categoryForFoodName("Spaghetti alla Carbonara della casa"),
        )
        assertEquals(
            StarterFoodDatabase.ItalianFoodCategory.PANE_PIZZA,
            StarterFoodDatabase.categoryForFoodName("Pizza bufala e basilico"),
        )
        assertEquals(
            StarterFoodDatabase.ItalianFoodCategory.UOVA_LATTICINI,
            StarterFoodDatabase.categoryForFoodName("Skyr ai mirtilli artigianale"),
        )
        assertNull(StarterFoodDatabase.categoryForFoodName("   "))
    }

    @Test
    fun `all entries have unique non-blank NFD names and realistic non-negative macronutrients`() {
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
    fun `starter database covers staples and iconic Italian dishes across all 14 categories`() {
        val namesLower = StarterFoodDatabase.ENTRIES.map { it.nome.lowercase(Locale.ROOT) }
        val expectedKeywords = listOf(
            "pasta di semola (cotta)",       // Pasta e Cereali
            "riso basmati (cotto)",          // Pasta e Cereali
            "pasta alla carbonara",          // Primi e Piatti Pronti
            "pasta all'amatriciana",         // Primi e Piatti Pronti
            "risotto alla milanese",         // Primi e Piatti Pronti
            "pane integrale",                // Pane, Pizza e Forno
            "pane carasau",                  // Pane, Pizza e Forno
            "pizza margherita",              // Pane, Pizza e Forno
            "pizza diavola",                 // Pane, Pizza e Forno
            "patate bollite",                // Patate e Rustici
            "arancino",                      // Patate e Rustici
            "farinata di ceci",              // Patate e Rustici
            "ceci bolliti",                  // Legumi e Veg
            "lenticchie rosse",              // Legumi e Veg
            "petto di pollo",                // Carni
            "ossobuco",                      // Carni
            "prosciutto crudo",              // Salumi
            "guanciale",                     // Salumi
            "salmone fresco",                // Pesce e Mare
            "baccalà",                       // Pesce e Mare
            "mozzarella",                    // Uova e Latticini
            "burrata",                       // Uova e Latticini
            "skyr",                          // Uova e Latticini
            "mela",                          // Frutta e Semi
            "mandorle",                      // Frutta e Semi
            "semi di chia",                  // Frutta e Semi
            "zucchine",                      // Verdure
            "cime di rapa",                  // Verdure
            "olio extravergine",             // Condimenti e Salse
            "cioccolato fondente",           // Dolci e Colazione
            "cannolo siciliano",             // Dolci e Colazione
            "panettone",                     // Dolci e Colazione
            "cappuccino",                    // Bevande
            "proteine whey",                 // Bevande
        )
        for (keyword in expectedKeywords) {
            assertTrue(
                "Expected starter database to include an item matching '$keyword'",
                namesLower.any { it.contains(keyword) },
            )
        }
    }
}
