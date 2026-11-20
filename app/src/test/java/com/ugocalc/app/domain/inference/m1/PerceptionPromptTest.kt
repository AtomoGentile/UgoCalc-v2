package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptionPromptTest {

    @Test
    fun `prompt never asks for grams`() {
        val casa = PerceptionPrompt.build(SessionMode.CASA)
        val ristorante = PerceptionPrompt.build(SessionMode.RISTORANTE)
        // Le uniche occorrenze di "grammi" devono essere i divieti espliciti, mai una richiesta.
        assertTrue(casa.contains("MAI stimare") || casa.contains("NON stimare") || casa.contains("mai un peso in grammi"))
        assertFalse(casa.contains("peso_stimato_g"))
        assertFalse(ristorante.contains("peso_stimato_g"))
    }

    @Test
    fun `prompt declares the current schema version`() {
        val prompt = PerceptionPrompt.build(SessionMode.CASA)
        assertTrue(prompt.contains("\"schema_version\": ${SceneGraph.SCHEMA_VERSION}"))
    }

    @Test
    fun `prompt requires all SceneGraph JSON fields by exact name`() {
        val prompt = PerceptionPrompt.build(SessionMode.CASA)
        listOf(
            "\"ancore\"", "\"alimenti\"", "\"sovrapposizioni\"",
            "\"tipo\"", "\"descrizione\"", "\"dimensione_reale_cm\"", "\"confidenza\"",
            "\"nome\"", "\"alternative_identificazione\"", "\"occlusione\"", "\"misure_grezze\"",
            "\"conteggio\"", "\"frazione_piatto\"", "\"altezza_cm\"", "\"riempimento\"", "\"profilo_cumulo\"",
            "\"contenitore\"", "\"frazione_riempimento\"", "\"capacita_contenitore_ml\"", "\"forma\"",
            "\"lunghezza_cm\"", "\"larghezza_cm\"", "\"diametro_cm\"", "\"nota\"",
            "\"sopra\"", "\"sotto\"",
        ).forEach { field ->
            assertTrue("il prompt deve menzionare il campo $field", prompt.contains(field))
        }
    }

    @Test
    fun `prompt explains inner plate bottom, mound profile options, and visual proportion`() {
        val prompt = PerceptionPrompt.build(SessionMode.CASA)
        assertTrue(prompt.contains("fondo utile interno"))
        assertTrue(prompt.contains("proporzione visiva"))
        assertTrue(prompt.contains("2 scatti base ottimali"))
        listOf("\"piatto\"", "\"cupola\"", "\"cono\"").forEach { profile ->
            assertTrue("il prompt deve menzionare il profilo cumulo $profile", prompt.contains(profile))
        }
    }

    @Test
    fun `home mode tries calibrated dishware first, restaurant mode never mentions it as usable`() {
        val casa = PerceptionPrompt.build(SessionMode.CASA)
        val ristorante = PerceptionPrompt.build(SessionMode.RISTORANTE)

        val casaOrderIndex = casa.indexOf("Stoviglia calibrata")
        val casaBodyIndex = casa.indexOf("Riferimento corporeo: dita")
        assertTrue("in Casa la stoviglia calibrata va menzionata come prima opzione", casaOrderIndex in 0 until casaBodyIndex)

        assertTrue(
            "fuori casa deve esplicitare che la stoviglia calibrata non va usata",
            ristorante.contains("NON usare mai \"stoviglia_calibrata\""),
        )
    }

    @Test
    fun `every ScaleAnchorType and OcclusionDegree serial name appears in the prompt`() {
        val prompt = PerceptionPrompt.build(SessionMode.RISTORANTE)
        listOf("stoviglia_calibrata", "riferimento_corporeo", "oggetto_universale", "stima_generica")
            .forEach { assertTrue("manca il tipo di ancora $it", prompt.contains(it)) }
        listOf("nessuna", "parziale", "elevata")
            .forEach { assertTrue("manca il grado di occlusione $it", prompt.contains(it)) }
    }
}
