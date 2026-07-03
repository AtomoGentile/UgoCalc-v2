package com.ugocalc.app.domain.inference

import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.m1.PerceptionOutcome
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.SceneGraph
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.Confidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2AnalysisPipelineTest {

    private val model: AiModel = AiModelCatalog.ALL.first()
    private val goodPhoto = PhotoQuality(id = "1", sharpness = 500.0, brightness = 120.0, hash = 0L)
    private val blurryPhoto = PhotoQuality(id = "2", sharpness = 1.0, brightness = 120.0, hash = 1L)

    @Test
    fun `all photos rejected by M0 short-circuits before calling M1`() = runBlocking {
        var perceiveCalls = 0
        val pipeline = V2AnalysisPipeline(perceive = { _, _, _, _ -> perceiveCalls++; error("non deve essere chiamato") })

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(blurryPhoto, ByteArray(0))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertTrue(outcome is PipelineOutcome.NessunaFotoValida)
        assertEquals(0, perceiveCalls)
    }

    @Test
    fun `M1 fallback to V1 propagates as a distinct outcome`() = runBlocking {
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _ -> PerceptionOutcome.FallbackAV1("servizio non disponibile") },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertEquals(PipelineOutcome.FallbackAV1("servizio non disponibile"), outcome)
    }

    @Test
    fun `an unrecognized schema_version is rejected before reaching M3`() = runBlocking {
        val scenaVersioneFutura = SceneGraph(schemaVersion = SceneGraph.SCHEMA_VERSION + 1, ancore = emptyList(), alimenti = emptyList())
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _ -> PerceptionOutcome.Riuscita(scenaVersioneFutura) },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertEquals(PipelineOutcome.SchemaNonSupportato(SceneGraph.SCHEMA_VERSION + 1, SceneGraph.SCHEMA_VERSION), outcome)
    }

    @Test
    fun `a full successful run wires M2, M3 and M4 together end to end`() = runBlocking {
        val scena = SceneGraph(
            schemaVersion = SceneGraph.SCHEMA_VERSION,
            ancore = emptyList(),
            alimenti = listOf(
                PerceivedFood(
                    nome = "riso in bianco",
                    occlusione = OcclusionDegree.NESSUNA,
                    misureGrezze = RawMeasurements(frazionePiatto = 0.45, altezzaCm = 2.1, riempimento = 0.8),
                    confidenza = Confidence.ALTO,
                ),
                PerceivedFood(
                    nome = "mela",
                    occlusione = OcclusionDegree.NESSUNA,
                    misureGrezze = RawMeasurements(conteggio = 1),
                    confidenza = Confidence.ALTO,
                ),
            ),
        )
        val pipeline = V2AnalysisPipeline(
            qualityGate = QualityGate(),
            perceive = { _, _, _, _ -> PerceptionOutcome.Riuscita(scena) },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertTrue(outcome is PipelineOutcome.Verificato)
        val verificato = outcome as PipelineOutcome.Verificato
        assertEquals(2, verificato.stime.size)
        assertEquals("riso in bianco", verificato.stime[0].nome)
        assertEquals(EstimationStrategy.DISTESA_SUL_PIATTO, verificato.stime[0].strategia)
        assertEquals("mela", verificato.stime[1].nome)
        assertEquals(EstimationStrategy.CONTABILE, verificato.stime[1].strategia)
        assertTrue(verificato.stime.all { it.pesoG > 0.0 })
        assertTrue(verificato.verifica.controlli.isNotEmpty())
    }
}
