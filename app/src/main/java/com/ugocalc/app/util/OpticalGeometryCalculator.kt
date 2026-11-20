package com.ugocalc.app.util

import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.QualityGate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Dimensioni fisiche del campo inquadrato (Field of View) sul piano di fuoco, in centimetri. */
data class PhysicalFovCm(
    val widthCm: Double,
    val heightCm: Double,
)

/**
 * Motore deterministico di geometria ottica e formattazione telemetrica per CameraX Pro Studio
 * (puro Kotlin/JVM, nessuna dipendenza dal framework Android così resta completamente testabile).
 *
 * Calcola la distanza fisica fotocamera→piatto a partire dalle diottrie Camera2
 * (`LENS_FOCUS_DISTANCE`) e proietta il campo inquadrato reale (FoV W×H in cm) usando le dimensioni
 * fisiche del sensore (o il fallback equivalente 35mm 36×24mm), la focale, lo zoom e il rapporto
 * d'aspetto dell'immagine finale.
 */
object OpticalGeometryCalculator {

    const val CAPTURE_SOURCE_CAMERAX_PRO = "CAMERAX_PRO"
    const val CAPTURE_SOURCE_SYSTEM_CAMERA = "SYSTEM_CAMERA"
    const val CAPTURE_SOURCE_GALLERY = "GALLERY"

    /** Intervallo fisico credibile per foto di piatti (5 cm – 300 cm). */
    const val MIN_VALID_DISTANCE_CM = 5.0
    const val MAX_VALID_DISTANCE_CM = 300.0

    /** Dimensioni di riferimento del fotogramma 35mm full-frame (usate come fallback se manca il sensore fisico). */
    const val FULL_FRAME_WIDTH_MM = 36.0
    const val FULL_FRAME_HEIGHT_MM = 24.0
    private val FULL_FRAME_DIAGONAL_MM = sqrt(FULL_FRAME_WIDTH_MM * FULL_FRAME_WIDTH_MM + FULL_FRAME_HEIGHT_MM * FULL_FRAME_HEIGHT_MM)

    /** Costanti equivalenti a `android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_*`. */
    const val AF_STATE_INACTIVE = 0
    const val AF_STATE_PASSIVE_SCAN = 1
    const val AF_STATE_PASSIVE_FOCUSED = 2
    const val AF_STATE_ACTIVE_SCAN = 3
    const val AF_STATE_FOCUSED_LOCKED = 4
    const val AF_STATE_NOT_FOCUSED_LOCKED = 5
    const val AF_STATE_PASSIVE_UNFOCUSED = 6

    private const val MIN_VALID_FOV_CM = 1.0
    private const val MAX_VALID_FOV_CM = 1000.0
    private const val STABLE_MOTION_THRESHOLD_RAD_S = 0.35

    /** Restituisce vero quando lo stato AF Camera2 indica fuoco agganciato con certezza. */
    fun isAfLocked(afState: Int?): Boolean =
        afState == AF_STATE_FOCUSED_LOCKED || afState == AF_STATE_PASSIVE_FOCUSED

    /** Etichetta leggibile dello stato autofocus Camera2. */
    fun afStateLabel(afState: Int?): String? = when (afState) {
        AF_STATE_FOCUSED_LOCKED -> "AF bloccato"
        AF_STATE_PASSIVE_FOCUSED -> "AF a fuoco"
        AF_STATE_ACTIVE_SCAN, AF_STATE_PASSIVE_SCAN -> "AF in ricerca"
        AF_STATE_NOT_FOCUSED_LOCKED, AF_STATE_PASSIVE_UNFOCUSED -> "AF non a fuoco"
        AF_STATE_INACTIVE -> "AF inattivo"
        else -> null
    }

    /**
     * Converte la distanza focale espressa in diottrie (1/m) in centimetri (`100.0 / diopters`),
     * scartando valori non finiti, `<= 0` o fuori dal range fisico `[MIN_VALID_DISTANCE_CM, MAX_VALID_DISTANCE_CM]`.
     */
    fun computeDistanceCm(focusDistanceDiopters: Float?): Double? {
        if (focusDistanceDiopters == null || !focusDistanceDiopters.isFinite() || focusDistanceDiopters <= 0f) {
            return null
        }
        val distanceCm = 100.0 / focusDistanceDiopters.toDouble()
        if (!distanceCm.isFinite() || distanceCm < MIN_VALID_DISTANCE_CM || distanceCm > MAX_VALID_DISTANCE_CM) {
            return null
        }
        return distanceCm
    }

    /** Stima la focale equivalente 35mm da focale reale e dimensioni fisiche del sensore. */
    fun computeFocalLength35mmEq(
        focalLengthMm: Double?,
        sensorWidthMm: Double?,
        sensorHeightMm: Double?,
    ): Double? {
        if (focalLengthMm == null || !focalLengthMm.isFinite() || focalLengthMm <= 0.0) return null
        if (sensorWidthMm == null || !sensorWidthMm.isFinite() || sensorWidthMm <= 0.0) return null
        if (sensorHeightMm == null || !sensorHeightMm.isFinite() || sensorHeightMm <= 0.0) return null
        val sensorDiagonal = sqrt(sensorWidthMm * sensorWidthMm + sensorHeightMm * sensorHeightMm)
        if (sensorDiagonal <= 0.0) return null
        return focalLengthMm * (FULL_FRAME_DIAGONAL_MM / sensorDiagonal)
    }

    /**
     * Calcola il campo fisico inquadrato (larghezza × altezza in cm) sul piano a distanza [distanceCm].
     * Usa [sensorWidthMm]/[sensorHeightMm] + [focalLengthMm] se disponibili, altrimenti ripiega sul
     * riferimento full-frame 36×24mm con [focalLength35mmEq]. Tiene conto dello [zoomRatio] e
     * dell'orientamento/crop dell'immagine ([imageWidthPx] × [imageHeightPx]).
     */
    fun computeFovCm(
        distanceCm: Double?,
        focalLengthMm: Double?,
        focalLength35mmEq: Double?,
        sensorWidthMm: Double?,
        sensorHeightMm: Double?,
        zoomRatio: Float? = null,
        imageWidthPx: Int? = null,
        imageHeightPx: Int? = null,
    ): PhysicalFovCm? {
        if (distanceCm == null || !distanceCm.isFinite() || distanceCm < MIN_VALID_DISTANCE_CM || distanceCm > MAX_VALID_DISTANCE_CM) {
            return null
        }
        val effectiveZoom = zoomRatio?.takeIf { it.isFinite() && it > 0.1f }?.toDouble() ?: 1.0

        val usePhysicalSensor =
            focalLengthMm != null && focalLengthMm.isFinite() && focalLengthMm > 0.0 &&
                sensorWidthMm != null && sensorWidthMm.isFinite() && sensorWidthMm > 0.0 &&
                sensorHeightMm != null && sensorHeightMm.isFinite() && sensorHeightMm > 0.0

        val sensorLongMm: Double
        val sensorShortMm: Double
        val effectiveFocalMm: Double
        val rawSensorW: Double
        val rawSensorH: Double

        if (usePhysicalSensor) {
            rawSensorW = sensorWidthMm!!
            rawSensorH = sensorHeightMm!!
            sensorLongMm = maxOf(rawSensorW, rawSensorH)
            sensorShortMm = minOf(rawSensorW, rawSensorH)
            effectiveFocalMm = focalLengthMm!!
        } else if (focalLength35mmEq != null && focalLength35mmEq.isFinite() && focalLength35mmEq > 0.0) {
            rawSensorW = FULL_FRAME_WIDTH_MM
            rawSensorH = FULL_FRAME_HEIGHT_MM
            sensorLongMm = FULL_FRAME_WIDTH_MM
            sensorShortMm = FULL_FRAME_HEIGHT_MM
            effectiveFocalMm = focalLength35mmEq
        } else {
            return null
        }

        val activeSensorW: Double
        val activeSensorH: Double
        if (imageWidthPx != null && imageHeightPx != null && imageWidthPx > 0 && imageHeightPx > 0) {
            val isPortrait = imageHeightPx > imageWidthPx
            val imgLong = maxOf(imageWidthPx, imageHeightPx).toDouble()
            val imgShort = minOf(imageWidthPx, imageHeightPx).toDouble()
            val imgAspect = imgLong / imgShort
            val sensorAspect = sensorLongMm / sensorShortMm

            val croppedLong: Double
            val croppedShort: Double
            if (imgAspect > sensorAspect) {
                croppedLong = sensorLongMm
                croppedShort = sensorLongMm / imgAspect
            } else {
                croppedShort = sensorShortMm
                croppedLong = sensorShortMm * imgAspect
            }

            if (isPortrait) {
                activeSensorW = croppedShort
                activeSensorH = croppedLong
            } else {
                activeSensorW = croppedLong
                activeSensorH = croppedShort
            }
        } else {
            activeSensorW = rawSensorW
            activeSensorH = rawSensorH
        }

        val fovWidthCm = (distanceCm * activeSensorW) / (effectiveFocalMm * effectiveZoom)
        val fovHeightCm = (distanceCm * activeSensorH) / (effectiveFocalMm * effectiveZoom)

        if (!fovWidthCm.isFinite() || !fovHeightCm.isFinite()) return null
        if (fovWidthCm !in MIN_VALID_FOV_CM..MAX_VALID_FOV_CM || fovHeightCm !in MIN_VALID_FOV_CM..MAX_VALID_FOV_CM) {
            return null
        }
        return PhysicalFovCm(widthCm = fovWidthCm, heightCm = fovHeightCm)
    }

    /**
     * Completa [metadata] calcolando in modo deterministico i campi derivati mancanti:
     * `focalLength35mmEq`, `estimatedDistanceCm`, `fovWidthCm` e `fovHeightCm`.
     */
    fun enrichMetadata(metadata: CameraMetadata): CameraMetadata {
        val focal35 = metadata.focalLength35mmEq
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: computeFocalLength35mmEq(metadata.focalLengthMm, metadata.sensorWidthMm, metadata.sensorHeightMm)

        val distanceCm = metadata.estimatedDistanceCm
            ?.takeIf { it.isFinite() && it in MIN_VALID_DISTANCE_CM..MAX_VALID_DISTANCE_CM }
            ?: computeDistanceCm(metadata.focusDistanceDiopters)

        val existingFovValid =
            metadata.fovWidthCm != null && metadata.fovWidthCm.isFinite() && metadata.fovWidthCm > 0.0 &&
                metadata.fovHeightCm != null && metadata.fovHeightCm.isFinite() && metadata.fovHeightCm > 0.0

        val fov = if (existingFovValid) {
            PhysicalFovCm(metadata.fovWidthCm!!, metadata.fovHeightCm!!)
        } else {
            computeFovCm(
                distanceCm = distanceCm,
                focalLengthMm = metadata.focalLengthMm,
                focalLength35mmEq = focal35,
                sensorWidthMm = metadata.sensorWidthMm,
                sensorHeightMm = metadata.sensorHeightMm,
                zoomRatio = metadata.zoomRatio,
                imageWidthPx = metadata.imageWidthPx,
                imageHeightPx = metadata.imageHeightPx,
            )
        }

        return metadata.copy(
            focalLength35mmEq = focal35,
            estimatedDistanceCm = distanceCm,
            fovWidthCm = fov?.widthCm,
            fovHeightCm = fov?.heightCm,
        )
    }

    /** Formatta il tempo di esposizione in frazione fotografica leggibile (es. `1/125s`, `0.50s`). */
    fun formatShutterSpeed(exposureTimeSeconds: Double?): String? {
        if (exposureTimeSeconds == null || !exposureTimeSeconds.isFinite() || exposureTimeSeconds <= 0.0) return null
        return if (exposureTimeSeconds < 0.5) {
            val denom = (1.0 / exposureTimeSeconds).roundToInt().coerceAtLeast(1)
            "1/${denom}s"
        } else {
            String.format(Locale.US, "%.2fs", exposureTimeSeconds)
        }
    }

    /**
     * Formatta una riga di telemetria ottica e sensori per la foto `index + 1`, pronta per essere
     * iniettata nei prompt V1 e V2. Restituisce `null` se non è disponibile alcun dato utile.
     */
    fun formatTelemetryPromptLine(
        index: Int,
        angleDescription: String? = null,
        metadata: CameraMetadata? = null,
        motionRadPerSec: Double? = null,
        sharpness: Double? = null,
        brightness: Double? = null,
    ): String? {
        val enriched = metadata?.let(::enrichMetadata)
        val segments = mutableListOf<String>()
        val cleanAngle = angleDescription?.trim()?.takeIf { it.isNotEmpty() }

        // 1. Assetto / angolazione (pitch + roll)
        if (cleanAngle != null) {
            val rollPart = enriched?.rollDeg
                ?.takeIf { it.isFinite() && !cleanAngle.contains("roll", ignoreCase = true) }
                ?.let { String.format(Locale.US, ", roll %+.0f°", it) }
                .orEmpty()
            segments += "$cleanAngle$rollPart"
        } else if (enriched?.pitchDeg != null || enriched?.rollDeg != null) {
            val parts = buildList {
                enriched.pitchDeg?.takeIf { it.isFinite() }?.let { add(String.format(Locale.US, "inclinazione %.0f°", it)) }
                enriched.rollDeg?.takeIf { it.isFinite() }?.let { add(String.format(Locale.US, "roll %+.0f°", it)) }
            }
            if (parts.isNotEmpty()) segments += parts.joinToString(", ")
        }

        // 2. Geometria ottica: distanza & FoV
        if (enriched != null) {
            val optics = buildList {
                enriched.estimatedDistanceCm?.let { dist ->
                    add(String.format(Locale.US, "distanza ~%.0f cm", dist))
                }
                if (enriched.fovWidthCm != null && enriched.fovHeightCm != null) {
                    add(String.format(Locale.US, "FoV %.1f×%.1f cm", enriched.fovWidthCm, enriched.fovHeightCm))
                }
                enriched.zoomRatio?.takeIf { it.isFinite() && it > 0f }?.let { zoom ->
                    add(String.format(Locale.US, "zoom %.1f×", zoom))
                }
                val focalStr = when {
                    enriched.focalLength35mmEq != null && enriched.focalLengthMm != null ->
                        String.format(Locale.US, "%dmm eq. (%.1fmm)", enriched.focalLength35mmEq.roundToInt(), enriched.focalLengthMm)
                    enriched.focalLength35mmEq != null ->
                        "${enriched.focalLength35mmEq.roundToInt()}mm eq."
                    enriched.focalLengthMm != null ->
                        String.format(Locale.US, "%.1fmm", enriched.focalLengthMm)
                    else -> null
                }
                focalStr?.let { add(it) }
                if (enriched.sensorWidthMm != null && enriched.sensorHeightMm != null) {
                    add(String.format(Locale.US, "sensore %.1f×%.1fmm", enriched.sensorWidthMm, enriched.sensorHeightMm))
                }
                if (enriched.imageWidthPx != null && enriched.imageHeightPx != null && enriched.imageWidthPx > 0 && enriched.imageHeightPx > 0) {
                    add("${enriched.imageWidthPx}×${enriched.imageHeightPx}px")
                }
            }
            if (optics.isNotEmpty()) segments += "Ottica: ${optics.joinToString(", ")}"

            // 3. Esposizione, AF e OIS
            val capture = buildList {
                afStateLabel(enriched.afState)?.let { add(it) }
                enriched.oisEnabled?.let { add(if (it) "OIS attivo" else "OIS off") }
                enriched.isoValue?.let { add("ISO $it") }
                formatShutterSpeed(enriched.exposureTimeSeconds)?.let { add("otturatore $it") }
                enriched.aperture?.takeIf { it.isFinite() && it > 0.0 }?.let { add(String.format(Locale.US, "f/%.1f", it)) }
                captureSourceLabel(enriched.captureSource)?.let { add(it) }
            }
            if (capture.isNotEmpty()) segments += "Scatto: ${capture.joinToString(", ")}"
        }

        // 4. Giroscopio & qualità immagine (evita duplicazioni se già presenti in angleDescription)
        val alreadyHasGyro = cleanAngle?.contains("giroscopio", ignoreCase = true) == true ||
            cleanAngle?.contains("rad/s", ignoreCase = true) == true
        val alreadyHasQuality = cleanAngle?.contains("nitidezza", ignoreCase = true) == true

        val sensorQuality = buildList {
            if (!alreadyHasGyro && motionRadPerSec != null && motionRadPerSec.isFinite()) {
                val stabilityTag = when {
                    motionRadPerSec <= STABLE_MOTION_THRESHOLD_RAD_S -> "stabile"
                    motionRadPerSec <= QualityGate.DEFAULT_MOTION_MAX_RAD_PER_SEC -> "leggero movimento"
                    else -> "mosso"
                }
                add(String.format(Locale.US, "giroscopio %.2f rad/s (%s)", motionRadPerSec, stabilityTag))
            }
            if (!alreadyHasQuality) {
                if (sharpness != null && sharpness.isFinite()) {
                    add(String.format(Locale.US, "nitidezza %.0f", sharpness))
                }
                if (brightness != null && brightness.isFinite()) {
                    add(String.format(Locale.US, "luminosità %.0f/255", brightness))
                }
            }
        }
        if (sensorQuality.isNotEmpty()) segments += "Sensori: ${sensorQuality.joinToString(", ")}"

        if (segments.isEmpty()) return null
        return "Foto ${index + 1}: ${segments.joinToString(" | ")}"
    }

    /**
     * Genera il blocco multi-riga di telemetria per l'intera lista di [PhotoQuality], usato per
     * arricchire il prompt V1 (`PromptTemplates.visionPrompt`). Restituisce `null` se vuoto.
     */
    fun formatTelemetryPromptBlock(qualities: List<PhotoQuality>): String? {
        if (qualities.isEmpty()) return null
        val lines = qualities.mapIndexedNotNull { index, photo ->
            formatTelemetryPromptLine(
                index = index,
                angleDescription = photo.angleDescription,
                metadata = photo.cameraMetadata,
                motionRadPerSec = photo.motionRadPerSec,
                sharpness = photo.sharpness,
                brightness = photo.brightness,
            )
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * Genera una pillola telemetrica compatta in italiano per la UI (es. `VisionResultsScreen`),
     * ad es.: `CameraX · dist ~32 cm · FoV 28×21 cm · 26mm · ISO 120 · stabile`.
     */
    fun formatCompactSessionSummary(qualities: List<PhotoQuality>): String? {
        if (qualities.isEmpty()) return null
        val enrichedList = qualities.mapNotNull { it.cameraMetadata?.let(::enrichMetadata) }
        val parts = mutableListOf<String>()

        val sourceLabel = when {
            enrichedList.any { it.captureSource == CAPTURE_SOURCE_CAMERAX_PRO } -> "CameraX"
            enrichedList.any { it.captureSource == CAPTURE_SOURCE_SYSTEM_CAMERA } -> "Camera"
            enrichedList.any { it.captureSource == CAPTURE_SOURCE_GALLERY } -> "Galleria"
            else -> null
        }
        sourceLabel?.let { parts += it }

        enrichedList.firstNotNullOfOrNull { it.estimatedDistanceCm }?.let { dist ->
            parts += "dist ~${dist.roundToInt()} cm"
        }

        enrichedList.firstOrNull { it.fovWidthCm != null && it.fovHeightCm != null }?.let { meta ->
            parts += "FoV ${meta.fovWidthCm!!.roundToInt()}×${meta.fovHeightCm!!.roundToInt()} cm"
        }

        val primaryFocal = enrichedList.firstNotNullOfOrNull { it.focalLength35mmEq }
        if (primaryFocal != null) {
            parts += "${primaryFocal.roundToInt()}mm"
        }

        val zoom = enrichedList.firstNotNullOfOrNull { meta ->
            meta.zoomRatio?.takeIf { it.isFinite() && abs(it - 1.0f) >= 0.1f }
        }
        if (zoom != null) {
            parts += String.format(Locale.US, "%.1f×", zoom)
        }

        enrichedList.firstNotNullOfOrNull { it.isoValue }?.let { iso ->
            parts += "ISO $iso"
        }

        val motions = qualities.mapNotNull { it.motionRadPerSec?.takeIf { m -> m.isFinite() } }
        when {
            motions.isNotEmpty() -> {
                val peak = motions.max()
                parts += when {
                    peak <= STABLE_MOTION_THRESHOLD_RAD_S -> "stabile"
                    peak <= QualityGate.DEFAULT_MOTION_MAX_RAD_PER_SEC -> "lieve moto"
                    else -> "mosso"
                }
            }
            enrichedList.any { it.oisEnabled == true } -> parts += "OIS"
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun captureSourceLabel(captureSource: String?): String? = when (captureSource) {
        CAPTURE_SOURCE_CAMERAX_PRO -> "CameraX Pro"
        CAPTURE_SOURCE_SYSTEM_CAMERA -> "Fotocamera di sistema"
        CAPTURE_SOURCE_GALLERY -> "Galleria"
        else -> null
    }
}
