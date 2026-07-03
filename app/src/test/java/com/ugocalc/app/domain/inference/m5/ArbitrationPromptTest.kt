package com.ugocalc.app.domain.inference.m5

import com.ugocalc.app.domain.inference.model.FlagType
import com.ugocalc.app.domain.inference.model.FlaggedItem
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.model.Confidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArbitrationPromptTest {

    private val scena = SceneGraph(
        schemaVersion = SceneGraph.SCHEMA_VERSION,
        ancore = emptyList(),
        alimenti = listOf(
            PerceivedFood(
                nome = "riso in bianco",
                alternativeIdentificazione = listOf("riso basmati"),
                occlusione = OcclusionDegree.NESSUNA,
                misureGrezze = RawMeasurements(frazionePiatto = 0.45),
                confidenza = Confidence.MEDIO,
            ),
            PerceivedFood(
                nome = "mela",
                occlusione = OcclusionDegree.NESSUNA,
                misureGrezze = RawMeasurements(conteggio = 1),
                confidenza = Confidence.ALTO,
            ),
        ),
    )

    @Test
    fun `prompt mentions only the flagged food, not the untouched ones`() {
        val flaggati = listOf(FlaggedItem("riso in bianco", "identificazione ambigua", FlagType.AMBIGUITA_IDENTITA))
        val prompt = ArbitrationPrompt.build(scena, flaggati)

        assertTrue(prompt.contains("riso in bianco"))
        assertTrue(prompt.contains("identificazione ambigua"))
        assertFalse("un alimento non flaggato non deve comparire nel prompt di arbitrato", prompt.contains("mela"))
    }

    @Test
    fun `prompt never asks for grams`() {
        val flaggati = listOf(FlaggedItem("riso in bianco", "peso fuori intervallo", FlagType.VINCOLO_FISICO))
        val prompt = ArbitrationPrompt.build(scena, flaggati)
        assertFalse(prompt.contains("peso_stimato_g"))
        assertTrue(prompt.contains("NON stimare mai un peso in grammi"))
    }

    @Test
    fun `prompt declares the current schema version and required JSON fields`() {
        val flaggati = listOf(FlaggedItem("riso in bianco", "dubbio", FlagType.VINCOLO_FISICO))
        val prompt = ArbitrationPrompt.build(scena, flaggati)
        assertTrue(prompt.contains("\"schema_version\": ${SceneGraph.SCHEMA_VERSION}"))
        listOf("\"revisioni\"", "\"nome\"", "\"nome_confermato\"", "\"misure_grezze\"", "\"confidenza\"", "\"risolto\"")
            .forEach { assertTrue("manca il campo $it", prompt.contains(it)) }
    }
}
