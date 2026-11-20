package com.ugocalc.app.util

import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalGeometryCalculatorTest {

    @Test
    fun `computeDistanceCm converts valid diopters and rejects out-of-bounds values`() {
        assertEquals(32.0, OpticalGeometryCalculator.computeDistanceCm(3.125f)!!, 0.01)
        assertEquals(50.0, OpticalGeometryCalculator.computeDistanceCm(2.0f)!!, 0.01)
        assertEquals(5.0, OpticalGeometryCalculator.computeDistanceCm(20.0f)!!, 0.01)
        assertEquals(300.0, OpticalGeometryCalculator.computeDistanceCm(1f / 3f)!!, 0.05)

        assertNull(OpticalGeometryCalculator.computeDistanceCm(null))
        assertNull(OpticalGeometryCalculator.computeDistanceCm(0f))
        assertNull(OpticalGeometryCalculator.computeDistanceCm(-2.5f))
        assertNull(OpticalGeometryCalculator.computeDistanceCm(Float.NaN))
        assertNull(OpticalGeometryCalculator.computeDistanceCm(Float.POSITIVE_INFINITY))
        // 0.1 diopters = 1000 cm (> 300 cm upper bound)
        assertNull(OpticalGeometryCalculator.computeDistanceCm(0.1f))
        // 30 diopters = 3.33 cm (< 5 cm lower bound)
        assertNull(OpticalGeometryCalculator.computeDistanceCm(30f))
    }

    @Test
    fun `computeFovCm uses physical sensor dimensions, zoom ratio, and portrait orientation`() {
        // 32 cm distance, 6.4x4.8mm (4:3) sensor, 4.7mm focal, 1x zoom, landscape 4000x3000 (4:3)
        val landscapeFov = OpticalGeometryCalculator.computeFovCm(
            distanceCm = 32.0,
            focalLengthMm = 4.7,
            focalLength35mmEq = null,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            zoomRatio = 1.0f,
            imageWidthPx = 4000,
            imageHeightPx = 3000,
        )
        assertNotNull(landscapeFov)
        assertEquals(32.0 * 6.4 / 4.7, landscapeFov!!.widthCm, 0.05)
        assertEquals(32.0 * 4.8 / 4.7, landscapeFov.heightCm, 0.05)

        // Portrait 3000x4000 swaps width and height
        val portraitFov = OpticalGeometryCalculator.computeFovCm(
            distanceCm = 32.0,
            focalLengthMm = 4.7,
            focalLength35mmEq = null,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            zoomRatio = 1.0f,
            imageWidthPx = 3000,
            imageHeightPx = 4000,
        )
        assertNotNull(portraitFov)
        assertEquals(landscapeFov.heightCm, portraitFov!!.widthCm, 0.01)
        assertEquals(landscapeFov.widthCm, portraitFov.heightCm, 0.01)

        // 2x zoom halves both FoV axes
        val zoomedFov = OpticalGeometryCalculator.computeFovCm(
            distanceCm = 32.0,
            focalLengthMm = 4.7,
            focalLength35mmEq = null,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            zoomRatio = 2.0f,
            imageWidthPx = 4000,
            imageHeightPx = 3000,
        )
        assertNotNull(zoomedFov)
        assertEquals(landscapeFov.widthCm / 2.0, zoomedFov!!.widthCm, 0.01)
        assertEquals(landscapeFov.heightCm / 2.0, zoomedFov.heightCm, 0.01)
    }

    @Test
    fun `computeFovCm falls back to 36x24mm full-frame when physical sensor is missing`() {
        // 36 cm distance, 36mm eq focal -> 36 cm wide, 24 cm tall on 3:2 landscape
        val fov = OpticalGeometryCalculator.computeFovCm(
            distanceCm = 36.0,
            focalLengthMm = null,
            focalLength35mmEq = 36.0,
            sensorWidthMm = null,
            sensorHeightMm = null,
            zoomRatio = 1.0f,
            imageWidthPx = 3000,
            imageHeightPx = 2000,
        )
        assertNotNull(fov)
        assertEquals(36.0, fov!!.widthCm, 0.05)
        assertEquals(24.0, fov.heightCm, 0.05)
    }

    @Test
    fun `computeFovCm adjusts for 16-to-9 aspect crop on a 4-to-3 sensor`() {
        val fov = OpticalGeometryCalculator.computeFovCm(
            distanceCm = 40.0,
            focalLengthMm = 5.0,
            focalLength35mmEq = null,
            sensorWidthMm = 8.0,
            sensorHeightMm = 6.0,
            zoomRatio = 1.0f,
            imageWidthPx = 1920,
            imageHeightPx = 1080,
        )
        assertNotNull(fov)
        // Long edge uses full 8.0mm -> 40 * 8 / 5 = 64cm; short edge cropped to 8 / (16/9) = 4.5mm -> 40 * 4.5 / 5 = 36cm
        assertEquals(64.0, fov!!.widthCm, 0.05)
        assertEquals(36.0, fov.heightCm, 0.05)
    }

    @Test
    fun `enrichMetadata computes derived distance, FoV, and 35mm equivalent focal length`() {
        val raw = CameraMetadata(
            isoValue = 120,
            exposureTimeSeconds = 0.008,
            focalLengthMm = 4.7,
            aperture = 1.8,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            focusDistanceDiopters = 3.125f, // 32 cm
            zoomRatio = 1.0f,
            imageWidthPx = 4000,
            imageHeightPx = 3000,
            captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
        )
        val enriched = OpticalGeometryCalculator.enrichMetadata(raw)

        assertEquals(32.0, enriched.estimatedDistanceCm!!, 0.01)
        assertNotNull(enriched.fovWidthCm)
        assertNotNull(enriched.fovHeightCm)
        assertNotNull(enriched.focalLength35mmEq)
        // 4.7 * (43.2666 / 8.0) ≈ 25.4mm
        assertEquals(25.4, enriched.focalLength35mmEq!!, 0.2)
    }

    @Test
    fun `formatTelemetryPromptLine emits full structured Italian optical telemetry`() {
        val meta = CameraMetadata(
            isoValue = 120,
            exposureTimeSeconds = 1.0 / 125.0,
            focalLengthMm = 4.7,
            focalLength35mmEq = 26.0,
            aperture = 1.8,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            focusDistanceDiopters = 3.125f,
            zoomRatio = 1.5f,
            afState = OpticalGeometryCalculator.AF_STATE_FOCUSED_LOCKED,
            oisEnabled = true,
            rollDeg = -2f,
            pitchDeg = 88f,
            imageWidthPx = 4032,
            imageHeightPx = 3024,
            captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
        )
        val line = OpticalGeometryCalculator.formatTelemetryPromptLine(
            index = 0,
            angleDescription = "inclinazione misurata 88° (target: Foto 1 — dall'alto 90°)",
            metadata = meta,
            motionRadPerSec = 0.12,
            sharpness = 412.0,
            brightness = 135.0,
        )
        assertNotNull(line)
        assertTrue(line!!.startsWith("Foto 1:"))
        assertTrue(line.contains("distanza ~32 cm"))
        assertTrue(line.contains("FoV"))
        assertTrue(line.contains("zoom 1.5×"))
        assertTrue(line.contains("26mm eq."))
        assertTrue(line.contains("sensore 6.4×4.8mm"))
        assertTrue(line.contains("AF bloccato"))
        assertTrue(line.contains("OIS attivo"))
        assertTrue(line.contains("ISO 120"))
        assertTrue(line.contains("otturatore 1/125s"))
        assertTrue(line.contains("roll -2°"))
        assertTrue(line.contains("giroscopio 0.12 rad/s (stabile)"))
        assertTrue(line.contains("nitidezza 412"))
        assertTrue(line.contains("luminosità 135/255"))
    }

    @Test
    fun `formatCompactSessionSummary produces concise Italian telemetry pill`() {
        val meta = OpticalGeometryCalculator.enrichMetadata(
            CameraMetadata(
                isoValue = 120,
                focalLengthMm = 4.7,
                focalLength35mmEq = 26.0,
                sensorWidthMm = 6.4,
                sensorHeightMm = 4.8,
                focusDistanceDiopters = 3.125f, // 32 cm
                zoomRatio = 1.5f,
                imageWidthPx = 4000,
                imageHeightPx = 3000,
                captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
            ),
        )
        val summary = OpticalGeometryCalculator.formatCompactSessionSummary(
            listOf(
                PhotoQuality(
                    id = "foto_0",
                    sharpness = 350.0,
                    brightness = 130.0,
                    hash = 1L,
                    motionRadPerSec = 0.10,
                    cameraMetadata = meta,
                ),
            ),
        )
        assertNotNull(summary)
        assertTrue(summary!!.contains("CameraX"))
        assertTrue(summary.contains("dist ~32 cm"))
        assertTrue(summary.contains("FoV "))
        assertTrue(summary.contains("26mm"))
        assertTrue(summary.contains("1.5×"))
        assertTrue(summary.contains("ISO 120"))
        assertTrue(summary.contains("stabile"))
    }

    @Test
    fun `isAfLocked recognizes locked and passive focused states only`() {
        assertTrue(OpticalGeometryCalculator.isAfLocked(OpticalGeometryCalculator.AF_STATE_FOCUSED_LOCKED))
        assertTrue(OpticalGeometryCalculator.isAfLocked(OpticalGeometryCalculator.AF_STATE_PASSIVE_FOCUSED))
        assertFalse(OpticalGeometryCalculator.isAfLocked(OpticalGeometryCalculator.AF_STATE_ACTIVE_SCAN))
        assertFalse(OpticalGeometryCalculator.isAfLocked(OpticalGeometryCalculator.AF_STATE_NOT_FOCUSED_LOCKED))
        assertFalse(OpticalGeometryCalculator.isAfLocked(null))
    }

    @Test
    fun `formatCompactSessionSummary returns null for empty input`() {
        assertNull(OpticalGeometryCalculator.formatCompactSessionSummary(emptyList()))
    }
}
