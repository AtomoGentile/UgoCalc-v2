package com.ugocalc.app.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF
import com.ugocalc.app.domain.inference.m0.CameraMetadata

/**
 * Istantanea di telemetria live raccolta da Camera2 (`CaptureResult` + `Camera2CameraInfo`) e
 * sensori inerziali al momento esatto dello scatto in CameraX Pro Studio.
 */
data class Camera2LiveMetadata(
    val activeCameraId: String? = null,
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    val focusDistanceDiopters: Float? = null,
    val afState: Int? = null,
    val oisEnabled: Boolean? = null,
    val isoValue: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val focalLengthMm: Double? = null,
    val aperture: Double? = null,
    val zoomRatio: Float? = null,
    val pitchDeg: Float? = null,
    val rollDeg: Float? = null,
    val imageWidthPx: Int? = null,
    val imageHeightPx: Int? = null,
    val captureSource: String? = null,
)

/**
 * Helper per estrarre le caratteristiche fisiche della fotocamera (§16, §13 punto 7) e fonderle con
 * EXIF e telemetria live Camera2: evita il mismatch tra lente principale, ultrawide e teleobiettivo
 * usando `activeCameraId` quando disponibile.
 */
object CameraMetadataHelper {

    /**
     * Tenta di recuperare le dimensioni fisiche del sensore della fotocamera posteriore principale.
     * Restituisce null se non è possibile accedere al CameraManager o se i metadati mancano.
     */
    fun getPrimaryCameraPhysicalSize(context: Context): SizeF? =
        getCameraPhysicalSize(context, activeCameraId = null)

    /**
     * Recupera `SENSOR_INFO_PHYSICAL_SIZE` per lo specifico [activeCameraId] (evitando mismatch tra
     * sensore principale, grandangolo e teleobiettivo); se [activeCameraId] è assente o non valido,
     * ripiega sulla prima fotocamera posteriore.
     */
    fun getCameraPhysicalSize(context: Context, activeCameraId: String? = null): SizeF? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (!activeCameraId.isNullOrBlank()) {
                val activeSize = runCatching {
                    manager.getCameraCharacteristics(activeCameraId)
                        .get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                }.getOrNull()
                if (activeSize != null && activeSize.width > 0f && activeSize.height > 0f) {
                    return activeSize
                }
            }
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: return null
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Arricchisce i metadati estratti dall'EXIF con le caratteristiche fisiche del sensore note al
     * sistema (§13 punto 7), usando [activeCameraId] se fornito.
     */
    fun enrichWithPhysicalSize(
        metadata: CameraMetadata,
        context: Context,
        activeCameraId: String? = null,
    ): CameraMetadata {
        val size = getCameraPhysicalSize(context, activeCameraId) ?: return metadata
        return metadata.copy(
            sensorWidthMm = size.width.toDouble(),
            sensorHeightMm = size.height.toDouble(),
        )
    }

    /**
     * Fonde in modo pulito i tag EXIF del JPEG con la telemetria live Camera2/sensori catturata
     * allo scatto e calcola distanza stimata e FoV tramite [OpticalGeometryCalculator.enrichMetadata].
     *
     * Precedenza:
     * - EXIF è autorevole per esposizione/focale/dimensioni dello scatto finale, con fallback live;
     * - Camera2 live è autorevole per `focusDistanceDiopters`, `afState`, `oisEnabled`, `zoomRatio`,
     *   `pitchDeg`, `rollDeg` e dimensione fisica del sensore dello specifico `activeCameraId`;
     * - per le foto importate da galleria (`GALLERY`), non viene iniettato il sensore fisico di
     *   questo dispositivo per evitare distorsioni ottiche su scatti di altri telefoni.
     */
    fun mergeWithLiveTelemetry(
        exifMetadata: CameraMetadata,
        context: Context,
        live: Camera2LiveMetadata? = null,
        captureSource: String? = null,
    ): CameraMetadata {
        val resolvedSource = live?.captureSource ?: captureSource ?: exifMetadata.captureSource
        val isGallery = resolvedSource == OpticalGeometryCalculator.CAPTURE_SOURCE_GALLERY

        val sensorSizeFromSystem = if (isGallery) {
            null
        } else if (live?.sensorWidthMm != null && live.sensorHeightMm != null) {
            null
        } else {
            getCameraPhysicalSize(context, activeCameraId = live?.activeCameraId)
        }

        val merged = exifMetadata.copy(
            isoValue = exifMetadata.isoValue ?: live?.isoValue,
            exposureTimeSeconds = exifMetadata.exposureTimeSeconds ?: live?.exposureTimeSeconds,
            focalLengthMm = exifMetadata.focalLengthMm ?: live?.focalLengthMm,
            aperture = exifMetadata.aperture ?: live?.aperture,
            sensorWidthMm = live?.sensorWidthMm
                ?: exifMetadata.sensorWidthMm
                ?: sensorSizeFromSystem?.width?.toDouble(),
            sensorHeightMm = live?.sensorHeightMm
                ?: exifMetadata.sensorHeightMm
                ?: sensorSizeFromSystem?.height?.toDouble(),
            focusDistanceDiopters = live?.focusDistanceDiopters ?: exifMetadata.focusDistanceDiopters,
            zoomRatio = live?.zoomRatio ?: exifMetadata.zoomRatio,
            afState = live?.afState ?: exifMetadata.afState,
            oisEnabled = live?.oisEnabled ?: exifMetadata.oisEnabled,
            rollDeg = live?.rollDeg ?: exifMetadata.rollDeg,
            pitchDeg = live?.pitchDeg ?: exifMetadata.pitchDeg,
            imageWidthPx = exifMetadata.imageWidthPx ?: live?.imageWidthPx,
            imageHeightPx = exifMetadata.imageHeightPx ?: live?.imageHeightPx,
            captureSource = resolvedSource,
        )

        return OpticalGeometryCalculator.enrichMetadata(merged)
    }
}
