package com.ugocalc.app.domain.inference

import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.m1.PerceptionOutcome
import com.ugocalc.app.domain.inference.m5.ArbitrationOutcome
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.OcclusionDegree
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.RevisedFood
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
        val pipeline = V2AnalysisPipeline(perceive = { _, _, _, _, _, _, _ -> perceiveCalls++; error("non deve essere chiamato") })

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
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.FallbackAV1("servizio non disponibile") },
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
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaVersioneFutura) },
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
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scena) },
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

    /** Scena con un alimento ambiguo (alternative + confidenza non alta): fa scattare M4â†’M5. */
    private val scenaAmbigua = SceneGraph(
        schemaVersion = SceneGraph.SCHEMA_VERSION,
        ancore = emptyList(),
        alimenti = listOf(
            PerceivedFood(
                nome = "riso in bianco",
                alternativeIdentificazione = listOf("riso basmati"),
                occlusione = OcclusionDegree.NESSUNA,
                misureGrezze = RawMeasurements(frazionePiatto = 0.45, altezzaCm = 2.1, riempimento = 0.8),
                confidenza = Confidence.MEDIO,
            ),
        ),
    )

    @Test
    fun `M4 flag triggers M5 exactly once and applies its revision`() = runBlocking {
        var arbitrationCalls = 0
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaAmbigua) },
            arbitrate = { _, _, _, _, flaggati ->
                arbitrationCalls++
                assertEquals(1, flaggati.size)
                assertEquals("riso in bianco", flaggati.first().nomeAlimento)
                ArbitrationOutcome.Riuscita(
                    listOf(
                        RevisedFood(
                            nome = "riso in bianco",
                            nomeConfermato = "riso basmati",
                            confidenza = Confidence.ALTO,
                            risolto = true,
                        ),
                    ),
                )
            },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertEquals(1, arbitrationCalls)
        assertTrue(outcome is PipelineOutcome.Verificato)
        val verificato = outcome as PipelineOutcome.Verificato
        assertEquals("riso basmati", verificato.sceneGraph.alimenti.first().nome)
        assertEquals("riso basmati", verificato.stime.first().nome)
        assertTrue(verificato.sceneGraph.alimenti.first().alternativeIdentificazione.isEmpty())
        assertTrue(
            "l'ambiguitÃ  Ã¨ stata risolta, M4 non deve piÃ¹ flaggare l'identitÃ ",
            verificato.verifica.flaggati.none { it.motivo.contains("identificazione") },
        )
    }

    @Test
    fun `M5 unavailable leaves pre-arbitration estimate and declares the reason explicitly`() = runBlocking {
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaAmbigua) },
            arbitrate = { _, _, _, _, _ -> ArbitrationOutcome.NonDisponibile("nessuna API key configurata") },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertTrue(outcome is PipelineOutcome.Verificato)
        val verificato = outcome as PipelineOutcome.Verificato
        assertEquals("riso in bianco", verificato.sceneGraph.alimenti.first().nome)
        assertEquals("nessuna API key configurata", verificato.arbitratoMotivoMancato)
    }

    @Test
    fun `no flags means M5 is never invoked`() = runBlocking {
        var arbitrationCalls = 0
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaAmbigua.copy(alimenti = scenaAmbigua.alimenti.map { it.copy(confidenza = Confidence.ALTO, alternativeIdentificazione = emptyList()) })) },
            arbitrate = { _, _, _, _, _ -> arbitrationCalls++; ArbitrationOutcome.NonDisponibile("non deve essere chiamato") },
        )

        pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertEquals(0, arbitrationCalls)
    }

    @Test
    fun `residual ambiguity after M5 picks the alternative with the highest CHO`() = runBlocking {
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaAmbigua) },
            arbitrate = { _, _, _, _, _ -> ArbitrationOutcome.NonDisponibile("nessuna API key configurata") },
            resolveAmbiguity = { candidati ->
                assertEquals(listOf("riso in bianco", "riso basmati"), candidati)
                mapOf("riso in bianco" to 28.0, "riso basmati" to 31.0)
            },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertTrue(outcome is PipelineOutcome.Verificato)
        val verificato = outcome as PipelineOutcome.Verificato
        assertEquals("riso basmati", verificato.sceneGraph.alimenti.first().nome)
        assertEquals("riso basmati", verificato.stime.first().nome)
        assertEquals(1, verificato.verifica.ambiguitaRisolta.size)
        val risoluzione = verificato.verifica.ambiguitaRisolta.first()
        assertEquals("riso in bianco", risoluzione.nomeOriginale)
        assertEquals("riso basmati", risoluzione.nomeScelto)
        assertEquals(31.0, risoluzione.choPer100g, 0.0001)
    }

    @Test
    fun `unresolved candidates leave the ambiguous food unchanged`() = runBlocking {
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scenaAmbigua) },
            arbitrate = { _, _, _, _, _ -> ArbitrationOutcome.NonDisponibile("nessuna API key configurata") },
            resolveAmbiguity = { emptyMap() },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.CASA,
            model = model,
        )

        assertTrue(outcome is PipelineOutcome.Verificato)
        val verificato = outcome as PipelineOutcome.Verificato
        assertEquals("riso in bianco", verificato.sceneGraph.alimenti.first().nome)
        assertTrue(verificato.verifica.ambiguitaRisolta.isEmpty())
    }

    @Test
    fun `M8 records the finished analysis exactly once and its id reaches the outcome`() = runBlocking {
        val scena = SceneGraph(
            schemaVersion = SceneGraph.SCHEMA_VERSION,
            ancore = emptyList(),
            alimenti = listOf(
                PerceivedFood(
                    nome = "mela",
                    occlusione = OcclusionDegree.NESSUNA,
                    misureGrezze = RawMeasurements(conteggio = 1),
                    confidenza = Confidence.ALTO,
                ),
            ),
        )
        var recordCalls = 0
        val pipeline = V2AnalysisPipeline(
            perceive = { _, _, _, _, _, _, _ -> PerceptionOutcome.Riuscita(scena) },
            recordTelemetry = { outcome, recordedModel, recordedMode ->
                recordCalls++
                assertEquals(model, recordedModel)
                assertEquals(SessionMode.RISTORANTE, recordedMode)
                assertTrue(outcome.stime.isNotEmpty())
                42L
            },
        )

        val outcome = pipeline.run(
            photos = listOf(SessionPhoto(goodPhoto, ByteArray(10))),
            imageMimeType = "image/jpeg",
            sessionMode = SessionMode.RISTORANTE,
            model = model,
        )

        assertEquals(1, recordCalls)
        assertTrue(outcome is PipelineOutcome.Verificato)
        assertEquals(42L, (outcome as PipelineOutcome.Verificato).telemetryId)
    }
}
