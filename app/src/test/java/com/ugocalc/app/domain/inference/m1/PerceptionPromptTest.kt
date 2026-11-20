package com.ugocalc.app.domain.inference.m1

import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.util.OpticalGeometryCalculator
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

    @Test
    fun `prompt includes rich optical telemetry, AF, OIS, pitch-roll, exposure, gyro, and quality metrics`() {
        val meta = CameraMetadata(
            isoValue = 160,
            exposureTimeSeconds = 1.0 / 120.0,
            focalLengthMm = 4.7,
            focalLength35mmEq = 26.0,
            aperture = 1.8,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            focusDistanceDiopters = 3.125f, // ~32 cm
            zoomRatio = 2.0f,
            afState = OpticalGeometryCalculator.AF_STATE_FOCUSED_LOCKED,
            oisEnabled = true,
            rollDeg = -1f,
            pitchDeg = 89f,
            imageWidthPx = 4000,
            imageHeightPx = 3000,
            captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
        )
        val quality = PhotoQuality(
            id = "foto_0",
            sharpness = 390.0,
            brightness = 142.0,
            hash = 1L,
            motionRadPerSec = 0.09,
            cameraMetadata = meta,
            angleDescription = "inclinazione misurata 89° (target: Foto 1 — dall'alto 90°)",
        )
        val prompt = PerceptionPrompt.build(
            sessionMode = SessionMode.CASA,
            angleDescriptions = listOf(quality.angleDescription),
            cameraMetadatas = listOf(meta),
            photoQualities = listOf(quality),
            pastoImageCount = 1,
        )

        assertTrue(prompt.contains("Foto 1:"))
        assertTrue(prompt.contains("distanza ~32 cm"))
        assertTrue(prompt.contains("FoV"))
        assertTrue(prompt.contains("zoom 2.0×"))
        assertTrue(prompt.contains("AF bloccato"))
        assertTrue(prompt.contains("OIS attivo"))
        assertTrue(prompt.contains("roll -1°"))
        assertTrue(prompt.contains("26mm eq."))
        assertTrue(prompt.contains("sensore 6.4×4.8mm"))
        assertTrue(prompt.contains("ISO 160"))
        assertTrue(prompt.contains("otturatore 1/120s"))
        assertTrue(prompt.contains("giroscopio 0.09 rad/s (stabile)"))
        assertTrue(prompt.contains("nitidezza 390"))
        assertTrue(prompt.contains("luminosità 142/255"))
    }

    @Test
    fun `prompt emits telemetry even when angleDescriptions is empty and only cameraMetadatas is provided`() {
        val meta = CameraMetadata(
            isoValue = 100,
            focalLength35mmEq = 24.0,
            focusDistanceDiopters = 2.5f, // 40 cm
            pitchDeg = 46f,
            rollDeg = 2f,
        )
        val prompt = PerceptionPrompt.build(
            sessionMode = SessionMode.RISTORANTE,
            angleDescriptions = emptyList(),
            cameraMetadatas = listOf(meta),
            pastoImageCount = 1,
        )
        assertTrue(prompt.contains("Foto 1:"))
        assertTrue(prompt.contains("distanza ~40 cm"))
        assertTrue(prompt.contains("24mm eq."))
        assertTrue(prompt.contains("inclinazione 46°"))
        assertTrue(prompt.contains("roll +2°"))
    }
}
