package com.ugocalc.app.data.repository

import com.ugocalc.app.data.local.data.StarterFoodDatabase
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSearchNormalizationTest {

    private fun candidate(
        id: Long,
        nome: String,
        tablePriority: Int = 0,
        tableName: String = StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME,
    ): RankedFoodCandidate = RankedFoodCandidate(
        result = FoodSearchResult(
            entryId = id,
            entry = NormalizedFoodEntry(nome = nome, choPer100g = 20.0, grassiPer100g = 5.0, proteinePer100g = 5.0, indiceGlicemico = 50),
            tableDisplayName = tableName,
        ),
        tablePriority = tablePriority,
    )

    @Test
    fun `normalizeSearchText strips diacritics, punctuation, apostrophes, and collapses spaces`() {
        assertEquals("ragu alla bolognese", normalizeSearchText("Ragù alla bolognese"))
        assertEquals("pasta all amatriciana", normalizeSearchText("Pasta all'amatriciana"))
        assertEquals("petto di pollo cotto", normalizeSearchText("  Petto di pollo (cotto) "))
        assertEquals("tiramisu", normalizeSearchText("Tiramisù"))
        assertEquals("pure di patate", normalizeSearchText("Purè di patate"))
        assertEquals("nduja calabrese", normalizeSearchText("'Nduja calabrese"))
        assertEquals("caffe espresso amaro", normalizeSearchText("Caffè espresso amaro"))
        assertEquals("", normalizeSearchText("   !!!   "))
    }

    @Test
    fun `accent-insensitive search matches accented Italian names`() {
        val starterCandidates = StarterFoodDatabase.ENTRIES.mapIndexed { idx, entry ->
            RankedFoodCandidate(
                result = FoodSearchResult(
                    entryId = idx.toLong() + 1,
                    entry = entry,
                    tableDisplayName = StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME,
                ),
                tablePriority = 0,
            )
        }

        val raguResults = rankFoodSearchCandidates("ragu", starterCandidates)
        assertTrue(raguResults.isNotEmpty())
        assertEquals("Ragù alla bolognese", raguResults.first().entry.nome)
        assertTrue(raguResults.any { it.entry.nome == "Pasta al ragù alla bolognese" })

        val caffeResults = rankFoodSearchCandidates("caffe", starterCandidates)
        assertTrue(caffeResults.any { it.entry.nome == "Caffè espresso amaro" })

        val tiramisuResults = rankFoodSearchCandidates("tiramisu", starterCandidates)
        assertEquals("Tiramisù", tiramisuResults.first().entry.nome)

        val baccalaResults = rankFoodSearchCandidates("baccala", starterCandidates)
        assertTrue(baccalaResults.any { it.entry.nome.startsWith("Baccalà") })
    }

    @Test
    fun `multi-token order-independent matching finds Italian dishes and cuts`() {
        val starterCandidates = StarterFoodDatabase.ENTRIES.mapIndexed { idx, entry ->
            RankedFoodCandidate(
                result = FoodSearchResult(
                    entryId = idx.toLong() + 1,
                    entry = entry,
                    tableDisplayName = StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME,
                ),
                tablePriority = 0,
            )
        }

        val carbonara = rankFoodSearchCandidates("pasta carbonara", starterCandidates)
        assertTrue(carbonara.isNotEmpty())
        assertEquals("Pasta alla carbonara", carbonara.first().entry.nome)

        val polloPetto = rankFoodSearchCandidates("pollo petto", starterCandidates)
        assertTrue(polloPetto.any { it.entry.nome == "Petto di pollo (cotto)" })
        assertTrue(polloPetto.any { it.entry.nome == "Petto di pollo (crudo)" })

        val grecoZero = rankFoodSearchCandidates("0 yogurt greco", starterCandidates)
        assertEquals("Yogurt greco 0% grassi", grecoZero.first().entry.nome)
    }

    @Test
    fun `ranking prioritizes exact over prefix over phrase substring over token match and respects table priority`() {
        val candidates = listOf(
            candidate(1, "Fondente al cioccolato", tablePriority = 0),        // token match (rank 4)
            candidate(2, "Torta al cioccolato fondente", tablePriority = 0),  // phrase substring (rank 3)
            candidate(3, "Cioccolato fondente (70%)", tablePriority = 0),     // word-aligned prefix (rank 1)
            candidate(4, "Cioccolato fondente", tablePriority = 1, tableName = "Secondaria"), // exact, lower priority
            candidate(5, "Cioccolato fondente", tablePriority = 0, tableName = "Primaria"),   // exact, higher priority
        )

        val ranked = rankFoodSearchCandidates("cioccolato fondente", candidates)
        assertEquals(
            listOf(5L, 4L, 3L, 2L, 1L),
            ranked.map { it.entryId },
        )
    }

    @Test
    fun `ranking tie-breaks equal ranks by shorter name and enforces result limit`() {
        val candidates = (1..80).map { i ->
            candidate(i.toLong(), "Pasta variante numero $i")
        } + candidate(999L, "Pasta")

        val ranked = rankFoodSearchCandidates("pasta", candidates, limit = 60)
        assertEquals(60, ranked.size)
        assertEquals(999L, ranked.first().entryId)
    }
}
