package com.ugocalc.app.domain.usecase

import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.dto.MatchingInputTableEntryDto
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFoodsAcrossTablesUseCaseTest {

    private val model: AiModel = AiModelCatalog.ALL.first()
    private val json = Json { ignoreUnknownKeys = true }

    private fun food(name: String, weightG: Double = 100.0) = RecognizedFood(
        nome = name,
        pesoStimatoG = weightG,
        commento = "",
        confidenza = Confidence.ALTO,
    )

    // ── Normalization & Lexical Similarity ─────────────────────────────────────

    @Test
    fun `normalizeRaw strips accents, case and punctuation`() {
        assertEquals("pure di patate", MatchFoodsAcrossTablesUseCase.normalizeRaw("Purè di Patate!"))
        assertEquals("ragu alla bolognese", MatchFoodsAcrossTablesUseCase.normalizeRaw("  Ragù  alla   Bolognese "))
        assertEquals("tiramisu", MatchFoodsAcrossTablesUseCase.normalizeRaw("Tiramisù"))
    }

    @Test
    fun `Italian plural normalization matches singular and plural while keeping pesca and pesce distinct`() {
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Mele", "Mela"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Patate fritte", "Patata fritta"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Pomodori", "Pomodoro"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Uova", "Uovo"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Finocchi", "Finocchio"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Arance", "Arancia"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Pesche", "Pesca"), 1e-6)
        assertEquals(1.0, MatchFoodsAcrossTablesUseCase.similarity("Pesci", "Pesce"), 1e-6)

        // Critical non-collisions between distinct Italian foods
        assertTrue(MatchFoodsAcrossTablesUseCase.similarity("Pesca", "Pesce") < 0.65)
        assertTrue(MatchFoodsAcrossTablesUseCase.similarity("Pollo", "Polpo") < 0.65)
        assertTrue(MatchFoodsAcrossTablesUseCase.similarity("Patata", "Pasta") < 0.65)
        assertTrue(MatchFoodsAcrossTablesUseCase.similarity("Pane", "Panna") < 0.65)
    }

    // ── Stage 1: Instant Local Match ───────────────────────────────────────────

    @Test
    fun `Stage 1 resolves exact and high-overlap matches locally with zero AI calls respecting table priority`() = runBlocking {
        var aiCalls = 0
        val priorityTable = listOf(
            NormalizedFoodEntry("Pizza margherita", 35.0, 7.0),
            NormalizedFoodEntry("Purè di patate", 16.0, 4.0),
        )
        val secondaryTable = listOf(
            NormalizedFoodEntry("Pizza margherita", 30.0, 5.0),
            NormalizedFoodEntry("Pasta di semola (cotta)", 30.0, 0.5),
            NormalizedFoodEntry("Bresaola della Valtellina", 0.0, 2.6),
        )
        val useCase = MatchFoodsAcrossTablesUseCase(
            loadDoneTablesWithEntries = { listOf(priorityTable, secondaryTable) },
            runAiMatching = { _, _, _, _ ->
                aiCalls++
                error("AI must not be called when Stage 1 resolves all foods")
            },
            json = json,
        )

        val result = useCase(
            recognizedFoods = listOf(
                food("pizza margherita", 200.0),
                food("Pure di patate", 150.0),
                food("Pasta di semola cotta", 100.0),
                food("Bresaola Valtellina", 80.0),
            ),
            referenceModel = model,
        )

        assertEquals(0, aiCalls)
        assertEquals(4, result.size)
        // Priority table value (35.0) wins over secondary table value (30.0)
        assertEquals(35.0, result[0].tableEntry!!.choPer100g, 1e-6)
        assertEquals(Confidence.ALTO, result[0].match!!.confidenzaAbbinamento)
        assertEquals("Purè di patate", result[1].tableEntry!!.nome)
        assertEquals("Pasta di semola (cotta)", result[2].tableEntry!!.nome)
        assertEquals("Bresaola della Valtellina", result[3].tableEntry!!.nome)
    }

    // ── Stage 2: Candidate Filtering for Large Tables ──────────────────────────

    @Test
    fun `Stage 2 caps AI payload to top 60 candidates for tables over 80 entries and keeps full table when at most 80`() = runBlocking {
        val largeTable = (1..120).map { idx ->
            NormalizedFoodEntry("Alimento generico numero $idx", 10.0, 2.0)
        } + NormalizedFoodEntry("Risotto ai funghi porcini", 26.0, 5.0)

        val smallTable = (1..80).map { idx ->
            NormalizedFoodEntry("Voce piccola $idx", 12.0, 1.0)
        }

        val sentTableSizes = mutableListOf<Int>()
        val sentTableNames = mutableListOf<List<String>>()
        val useCase = MatchFoodsAcrossTablesUseCase(
            loadDoneTablesWithEntries = { listOf(largeTable, smallTable) },
            runAiMatching = { _, _, tabellaJson, _ ->
                val entries = json.decodeFromString<List<MatchingInputTableEntryDto>>(tabellaJson)
                sentTableSizes += entries.size
                sentTableNames += entries.map { it.nome }
                MatchingResult(
                    abbinamenti = listOf(
                        FoodMatch(
                            nomeRiconosciuto = "Risotto funghi",
                            voceTabellaAbbinata = "",
                            nessunaCorrispondenza = true,
                            confidenzaAbbinamento = Confidence.BASSO,
                        )
                    )
                )
            },
            json = json,
        )

        useCase(listOf(food("Risotto funghi")), model)

        assertEquals(listOf(60, 80), sentTableSizes)
        assertTrue(
            "Relevant entry must be ranked inside top 60 candidates sent to AI",
            "Risotto ai funghi porcini" in sentTableNames[0],
        )
    }

    // ── Stage 3: Tolerant Reconciliation ───────────────────────────────────────

    @Test
    fun `Stage 3 reconciles diacritic and slight spelling variations returned by AI`() = runBlocking {
        val table = listOf(
            NormalizedFoodEntry("Ragù alla bolognese", 3.0, 10.5),
            NormalizedFoodEntry("Tiramisù classico della casa", 26.0, 18.0),
        )
        val useCase = MatchFoodsAcrossTablesUseCase(
            loadDoneTablesWithEntries = { listOf(table) },
            runAiMatching = { _, _, _, _ ->
                MatchingResult(
                    abbinamenti = listOf(
                        FoodMatch(
                            nomeRiconosciuto = "Sugo di carne",
                            voceTabellaAbbinata = "Ragu alla bolognese", // missing accent
                            nessunaCorrispondenza = false,
                            confidenzaAbbinamento = Confidence.ALTO,
                        ),
                        FoodMatch(
                            nomeRiconosciuto = "Dolce al caffe", // missing accent vs recognized "Dolce al caffè"
                            voceTabellaAbbinata = "Tiramisu classico della casa", // missing accent
                            nessunaCorrispondenza = false,
                            confidenzaAbbinamento = Confidence.MEDIO,
                        ),
                    )
                )
            },
            json = json,
        )

        val result = useCase(
            recognizedFoods = listOf(food("Sugo di carne"), food("Dolce al caffè")),
            referenceModel = model,
        )

        assertFalse(result[0].isExcluded)
        assertEquals("Ragù alla bolognese", result[0].tableEntry!!.nome)
        assertFalse(result[1].isExcluded)
        assertEquals("Tiramisù classico della casa", result[1].tableEntry!!.nome)
        assertEquals(Confidence.MEDIO, result[1].match!!.confidenzaAbbinamento)
    }

    // ── Stage 4: Graceful Local Fallback on AI Failure ─────────────────────────

    @Test
    fun `Stage 4 falls back to best local fuzzy match with MEDIO and BASSO confidence when AI fails`() = runBlocking {
        val table = listOf(
            NormalizedFoodEntry("Pane integrale", 48.5, 1.3),
            NormalizedFoodEntry("Mela", 13.7, 0.1),
            NormalizedFoodEntry("Olio extravergine d'oliva", 0.0, 99.9),
        )
        val useCase = MatchFoodsAcrossTablesUseCase(
            loadDoneTablesWithEntries = { listOf(table) },
            runAiMatching = { _, _, _, _ -> throw AiCallException("Servizio offline") },
            json = json,
        )

        val result = useCase(
            recognizedFoods = listOf(
                food("Pane integrale fresco"),    // score ~0.80 -> MEDIO (>= 0.78)
                food("Mela rossa"),               // score ~0.67 -> BASSO (0.65..0.78)
                food("Sushi nigiri al salmone"),  // score < 0.65 -> excluded
            ),
            referenceModel = model,
        )

        assertFalse(result[0].isExcluded)
        assertEquals("Pane integrale", result[0].tableEntry!!.nome)
        assertEquals(Confidence.MEDIO, result[0].match!!.confidenzaAbbinamento)

        assertFalse(result[1].isExcluded)
        assertEquals("Mela", result[1].tableEntry!!.nome)
        assertEquals(Confidence.BASSO, result[1].match!!.confidenzaAbbinamento)

        assertTrue(result[2].isExcluded)
        assertNull(result[2].tableEntry)
        assertNotNull(result[2].match)
        assertTrue(result[2].match!!.nessunaCorrispondenza)
    }

    @Test
    fun `when AI succeeds and explicitly reports no match, food stays excluded without forced fallback`() = runBlocking {
        val table = listOf(NormalizedFoodEntry("Mela", 13.7, 0.1))
        val useCase = MatchFoodsAcrossTablesUseCase(
            loadDoneTablesWithEntries = { listOf(table) },
            runAiMatching = { _, _, _, _ ->
                MatchingResult(
                    abbinamenti = listOf(
                        FoodMatch(
                            nomeRiconosciuto = "Mela rossa",
                            voceTabellaAbbinata = "",
                            nessunaCorrispondenza = true,
                            confidenzaAbbinamento = Confidence.BASSO,
                        )
                    )
                )
            },
            json = json,
        )

        val result = useCase(listOf(food("Mela rossa")), model)
        assertTrue(result[0].isExcluded)
    }
}
