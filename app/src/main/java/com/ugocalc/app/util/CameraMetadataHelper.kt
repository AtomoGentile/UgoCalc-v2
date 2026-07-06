package com.ugocalc.app.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF
import com.ugocalc.app.domain.inference.m0.CameraMetadata

/**
 * Helper per estrarre le caratteristiche fisiche della fotocamera (§16, §13 punto 7): serve a M3
 * per convertire le misure pixel in centimetri reali quando non c'è un'ancora di scala, e a M0 per
 * calibrare le soglie di rumore/mosso in base al sensore.
 */
object CameraMetadataHelper {

    /**
     * Tenta di recuperare le dimensioni fisiche del sensore della fotocamera posteriore principale.
     * Restituisce null se non è possibile accedere al CameraManager o se i metadati mancano.
     */
    fun getPrimaryCameraPhysicalSize(context: Context): SizeF? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: return null
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Arricchisce i metadati estratti dall'EXIF con le caratteristiche fisiche del sensore note al
     * sistema (§13 punto 7).
     */
    fun enrichWithPhysicalSize(metadata: CameraMetadata, context: Context): CameraMetadata {
        val size = getPrimaryCameraPhysicalSize(context) ?: return metadata
        return metadata.copy(
            sensorWidthMm = size.width.toDouble(),
            sensorHeightMm = size.height.toDouble()
        )
    }
}
