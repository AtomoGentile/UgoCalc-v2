package com.ugocalc.app.domain.inference.m0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGateTest {

    private val gate = QualityGate()

    private fun sharp(id: String, hash: Long = id.hashCode().toLong()) =
        PhotoQuality(id = id, sharpness = 200.0, brightness = 128.0, hash = hash)

    @Test
    fun `a sharp well-exposed unique photo is accepted`() {
        val result = gate.assess(listOf(sharp("a")))
        assertEquals(listOf("a"), result.fotoValide)
        assertTrue(result.scartate.isEmpty())
        assertFalse(result.tutteScadenti)
    }

    @Test
    fun `a blurry photo is rejected as SFOCATA`() {
        val result = gate.assess(listOf(PhotoQuality("a", sharpness = 50.0, brightness = 128.0, hash = 1L)))
        assertEquals(PhotoRejectionReason.SFOCATA, result.scartate.single().motivo)
        assertTrue(result.fotoValide.isEmpty())
    }

    @Test
    fun `extreme exposure is rejected as too dark or too bright`() {
        val scura = gate.assess(listOf(PhotoQuality("a", sharpness = 200.0, brightness = 20.0, hash = 1L)))
        assertEquals(PhotoRejectionReason.TROPPO_SCURA, scura.scartate.single().motivo)

        val chiara = gate.assess(listOf(PhotoQuality("b", sharpness = 200.0, brightness = 240.0, hash = 2L)))
        assertEquals(PhotoRejectionReason.TROPPO_CHIARA, chiara.scartate.single().motivo)
    }

    @Test
    fun `a near-identical photo is dropped as a duplicate, the first is kept`() {
        val h = 0x0F0F0F0F0F0F0F0FL
        val quasiUguale = h xor 0b1L // un solo bit di differenza → distanza di Hamming 1
        val result = gate.assess(
            listOf(
                PhotoQuality("prima", sharpness = 200.0, brightness = 128.0, hash = h),
                PhotoQuality("seconda", sharpness = 200.0, brightness = 128.0, hash = quasiUguale),
            ),
        )
        assertEquals(listOf("prima"), result.fotoValide)
        assertEquals(PhotoRejectionReason.DUPLICATA, result.scartate.single { it.id == "seconda" }.motivo)
    }

    @Test
    fun `two visually different photos are both kept`() {
        val result = gate.assess(
            listOf(
                PhotoQuality("a", sharpness = 200.0, brightness = 128.0, hash = 0x0L),
                PhotoQuality("b", sharpness = 200.0, brightness = 128.0, hash = -1L), // tutti i 64 bit diversi
            ),
        )
        assertEquals(listOf("a", "b"), result.fotoValide)
    }

    @Test
    fun `high angular velocity from the gyroscope is rejected as MOSSA_DAL_SENSORE`() {
        val result = gate.assess(
            listOf(PhotoQuality("a", sharpness = 200.0, brightness = 128.0, hash = 1L, motionRadPerSec = 5.0)),
        )
        assertEquals(PhotoRejectionReason.MOSSA_DAL_SENSORE, result.scartate.single().motivo)
    }

    @Test
    fun `angular velocity below the threshold does not affect an otherwise good photo`() {
        val result = gate.assess(
            listOf(PhotoQuality("a", sharpness = 200.0, brightness = 128.0, hash = 1L, motionRadPerSec = 0.1)),
        )
        assertEquals(listOf("a"), result.fotoValide)
    }

    @Test
    fun `a missing motion reading behaves exactly as before this field existed`() {
        val result = gate.assess(listOf(sharp("a")))
        assertEquals(listOf("a"), result.fotoValide)
    }

    @Test
    fun `if every photo is bad the result reports all-bad`() {
        val result = gate.assess(
            listOf(
                PhotoQuality("a", sharpness = 10.0, brightness = 128.0, hash = 1L),
                PhotoQuality("b", sharpness = 200.0, brightness = 5.0, hash = 2L),
            ),
        )
        assertTrue(result.tutteScadenti)
        assertEquals(2, result.scartate.size)
    }
}
