package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.model.ScaleAnchor
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleResolverTest {

    @Test
    fun `no anchors falls back to the default plate diameter with low quality`() {
        val scale = ScaleResolver.resolve(emptyList())
        assertTrue(scale.plateDiameterIsDefault)
        assertEquals(Confidence.BASSO, scale.quality)
        assertEquals(PhysicalData.DEFAULT_PLATE_DIAMETER_CM, scale.plateDiameterCm, 0.0)
    }

    @Test
    fun `an anchor without a known cm dimension is ignored, same as no anchors`() {
        val ancore = listOf(anchor(ScaleAnchorType.OGGETTO_UNIVERSALE, cm = null, confidenza = Confidence.ALTO))
        val scale = ScaleResolver.resolve(ancore)
        assertTrue(scale.plateDiameterIsDefault)
    }

    @Test
    fun `a calibrated dishware anchor wins over a body reference anchor`() {
        val ancore = listOf(
            anchor(ScaleAnchorType.RIFERIMENTO_CORPOREO, cm = 24.0, confidenza = Confidence.ALTO),
            anchor(ScaleAnchorType.STOVIGLIA_CALIBRATA, cm = 26.0, confidenza = Confidence.MEDIO),
        )
        val scale = ScaleResolver.resolve(ancore)
        assertEquals(26.0, scale.plateDiameterCm, 0.0)
        assertEquals(Confidence.MEDIO, scale.quality)
        assertTrue(!scale.plateDiameterIsDefault)
    }

    @Test
    fun `at equal anchor type the higher confidence wins`() {
        val ancore = listOf(
            anchor(ScaleAnchorType.OGGETTO_UNIVERSALE, cm = 20.0, confidenza = Confidence.BASSO),
            anchor(ScaleAnchorType.OGGETTO_UNIVERSALE, cm = 25.0, confidenza = Confidence.ALTO),
        )
        val scale = ScaleResolver.resolve(ancore)
        assertEquals(25.0, scale.plateDiameterCm, 0.0)
        assertEquals(Confidence.ALTO, scale.quality)
    }

    @Test
    fun `priority order is stoviglia over corporeo over universale over generica`() {
        val ancore = listOf(
            anchor(ScaleAnchorType.STIMA_GENERICA, cm = 22.0, confidenza = Confidence.ALTO),
            anchor(ScaleAnchorType.OGGETTO_UNIVERSALE, cm = 23.0, confidenza = Confidence.ALTO),
            anchor(ScaleAnchorType.RIFERIMENTO_CORPOREO, cm = 24.0, confidenza = Confidence.ALTO),
            anchor(ScaleAnchorType.STOVIGLIA_CALIBRATA, cm = 26.0, confidenza = Confidence.BASSO),
        )
        assertEquals(26.0, ScaleResolver.resolve(ancore).plateDiameterCm, 0.0)
        assertEquals(24.0, ScaleResolver.resolve(ancore.filterNot { it.tipo == ScaleAnchorType.STOVIGLIA_CALIBRATA }).plateDiameterCm, 0.0)
        assertEquals(23.0, ScaleResolver.resolve(ancore.filter { it.tipo == ScaleAnchorType.OGGETTO_UNIVERSALE || it.tipo == ScaleAnchorType.STIMA_GENERICA }).plateDiameterCm, 0.0)
    }

    @Test
    fun `matches calibrated conical plate by description name and populates shape and bottom diameter`() {
        val cal = ScaleCalibrationEntity(
            type = ScaleAnchorType.STOVIGLIA_CALIBRATA.name,
            name = "Piatto fondo bianco",
            valueCm = 22.0,
            heightCm = 4.5,
            plateShapeType = "conico",
            plateBottomDiameterCm = 11.0,
        )
        val ancore = listOf(
            ScaleAnchor(
                tipo = ScaleAnchorType.STOVIGLIA_CALIBRATA,
                descrizione = "Piatto fondo bianco",
                dimensioneRealeCm = 22.0,
                confidenza = Confidence.ALTO,
            )
        )
        val scale = ScaleResolver.resolve(ancore, listOf(cal))
        assertEquals("conico", scale.plateShapeType)
        assertEquals(11.0, scale.plateBottomDiameterCm!!, 1e-9)
        assertEquals(4.5, scale.plateHeightCm!!, 1e-9)
        assertNull(scale.calibratedCapacityMl)
    }

    @Test
    fun `matches calibrated dishware by STOVIGLIA_CALIBRATA type fallback and populates capacity map`() {
        val plateCal = ScaleCalibrationEntity(
            type = ScaleAnchorType.STOVIGLIA_CALIBRATA.name,
            name = "Fondina nonna",
            valueCm = 20.5,
            plateShapeType = "conico",
            plateBottomDiameterCm = 10.0,
        )
        val mugCal = ScaleCalibrationEntity(
            type = ScaleAnchorType.STOVIGLIA_CALIBRATA.name,
            name = "Tazza blu",
            valueCm = 8.5,
            capacityMl = 320.0,
        )
        val ancore = listOf(
            ScaleAnchor(
                tipo = ScaleAnchorType.STOVIGLIA_CALIBRATA,
                descrizione = "piatto fondo di casa",
                dimensioneRealeCm = 20.5,
                confidenza = Confidence.ALTO,
            )
        )
        val scale = ScaleResolver.resolve(ancore, listOf(plateCal, mugCal))
        assertEquals("conico", scale.plateShapeType)
        assertEquals(10.0, scale.plateBottomDiameterCm!!, 1e-9)
        assertEquals(320.0, scale.calibratedCapacitiesByName["tazza blu"]!!, 1e-9)
    }

    @Test
    fun `matches calibrated container anchor and exposes calibratedCapacityMl`() {
        val mugCal = ScaleCalibrationEntity(
            type = ScaleAnchorType.STOVIGLIA_CALIBRATA.name,
            name = "Tazza colazione",
            valueCm = 9.0,
            capacityMl = 280.0,
        )
        val ancore = listOf(
            ScaleAnchor(
                tipo = ScaleAnchorType.STOVIGLIA_CALIBRATA,
                descrizione = "Tazza colazione",
                dimensioneRealeCm = 9.0,
                confidenza = Confidence.ALTO,
            )
        )
        val scale = ScaleResolver.resolve(ancore, listOf(mugCal))
        assertEquals(280.0, scale.calibratedCapacityMl!!, 1e-9)
        assertEquals("cilindro", scale.plateShapeType)
    }

    private fun anchor(tipo: ScaleAnchorType, cm: Double?, confidenza: Confidence) = ScaleAnchor(
        tipo = tipo,
        descrizione = "anchor di test",
        dimensioneRealeCm = cm,
        confidenza = confidenza,
    )
}
