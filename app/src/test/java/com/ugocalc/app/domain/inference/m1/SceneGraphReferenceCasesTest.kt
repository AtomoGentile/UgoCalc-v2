package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.model.Confidence
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casi di riferimento fissi per l'output JSON atteso da M1 (§11 "Moduli IA"): non si valuta con
 * unit test classici sul comportamento del modello, ma si verifica che risposte plausibili (o
 * deliberatamente malformate) si comportino come previsto nella decodifica rigida verso
 * [SceneGraph], con la STESSA configurazione [Json] usata in produzione (vedi NetworkModule).
 */
class SceneGraphReferenceCasesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `a well-formed M1 response decodes into the expected SceneGraph`() {
        val raw = """
            {
              "schema_version": 1,
              "ancore": [
                { "tipo": "riferimento_corporeo", "descrizione": "apertura mano", "dimensione_reale_cm": 20.0, "confidenza": "medio" }
              ],
              "alimenti": [
                {
                  "nome": "riso in bianco",
                  "alternative_identificazione": ["riso basmati"],
                  "occlusione": "nessuna",
                  "misure_grezze": { "frazione_piatto": 0.45, "altezza_cm": 2.1, "riempimento": 0.8, "nota": "cumulo uniforme" },
                  "confidenza": "alto"
                }
              ],
              "sovrapposizioni": []
            }
        """.trimIndent()

        val scene = json.decodeFromString<SceneGraph>(raw)

        assertEquals(SceneGraph.SCHEMA_VERSION, scene.schemaVersion)
        assertEquals(1, scene.ancore.size)
        assertEquals(ScaleAnchorType.RIFERIMENTO_CORPOREO, scene.ancore[0].tipo)
        assertEquals(1, scene.alimenti.size)
        val riso = scene.alimenti[0]
        assertEquals("riso in bianco", riso.nome)
        assertEquals(listOf("riso basmati"), riso.alternativeIdentificazione)
        assertEquals(OcclusionDegree.NESSUNA, riso.occlusione)
        assertEquals(Confidence.ALTO, riso.confidenza)
        assertEquals(0.45, riso.misureGrezze.frazionePiatto)
    }

    @Test
    fun `a response missing schema_version fails deserialization instead of defaulting silently`() {
        val raw = """
            { "ancore": [], "alimenti": [], "sovrapposizioni": [] }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<SceneGraph>(raw)
        }
    }

    @Test
    fun `optional raw measurement fields left out by the model stay null, never fabricated`() {
        val raw = """
            {
              "schema_version": 1,
              "ancore": [],
              "alimenti": [
                { "nome": "mela", "occlusione": "nessuna", "misure_grezze": { "conteggio": 2 }, "confidenza": "alto" }
              ],
              "sovrapposizioni": []
            }
        """.trimIndent()

        val scene = json.decodeFromString<SceneGraph>(raw)
        val mela = scene.alimenti.single().misureGrezze
        assertEquals(2, mela.conteggio)
        assertNull(mela.frazionePiatto)
        assertNull(mela.altezzaCm)
        assertNull(mela.contenitore)
        assertNull(mela.diametroCm)
    }

    @Test
    fun `a response declaring a future schema version still decodes, so M2 can reject it explicitly`() {
        val raw = """
            { "schema_version": 99, "ancore": [], "alimenti": [], "sovrapposizioni": [] }
        """.trimIndent()

        val scene = json.decodeFromString<SceneGraph>(raw)

        assertEquals(99, scene.schemaVersion)
        assertTrue(scene.schemaVersion != SceneGraph.SCHEMA_VERSION)
    }
}
