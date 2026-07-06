package com.ugocalc.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import java.io.ByteArrayInputStream

/** Lato massimo della Bitmap decodificata per calcolare le metriche: bastano poche decine di migliaia di pixel, non serve la risoluzione dello scatto originale. */
private const val METRICS_MAX_DIMENSION_PX = 128

/** Sentinella usata da [ExifInterface.getAttributeDouble]/[ExifInterface.getAttributeInt] per "tag assente". */
private const val EXIF_ABSENT = -1.0

/**
 * Estrae le metriche di qualità che [com.ugocalc.app.domain.inference.m0.QualityGate] si aspetta
 * già pronte (piano V2 §5 M0: "l'estrazione delle metriche dal bitmap è responsabilità del livello
 * Android"). Vive in `util` (non in `domain/inference`, che resta puro Kotlin testabile in JVM)
 * proprio perché dipende da `android.graphics.Bitmap`.
 *
 * [motionRadPerSec] e [cameraMetadata] arrivano da fonti indipendenti dal Bitmap (giroscopio, EXIF,
 * §16): opzionali con default `null` per non rompere chiamate esistenti che non li passano.
 */
fun computePhotoQuality(
    id: String,
    imageBytes: ByteArray,
    motionRadPerSec: Double? = null,
    cameraMetadata: CameraMetadata? = null,
    angleDescription: String? = null,
): PhotoQuality {
    val luminance = decodeGrayscaleLuminance(imageBytes, METRICS_MAX_DIMENSION_PX)
    return PhotoQuality(
        id = id,
        sharpness = laplacianVariance(luminance),
        brightness = luminance.pixels.average(),
        hash = perceptualHash64(luminance),
        motionRadPerSec = motionRadPerSec,
        cameraMetadata = cameraMetadata,
        angleDescription = angleDescription,
    )
}

/**
 * Metadati camera diagnostici (§16), solo trasportati — [com.ugocalc.app.domain.inference.m0.QualityGate]
 * non li usa per scartare nulla oggi (soglie ISO/esposizione non calibrate su dati reali, rischio di
 * falsi scarti). Tutti i campi `null` se il tag EXIF è assente, come spesso accade su foto elaborate.
 */
fun extractCameraMetadata(imageBytes: ByteArray): CameraMetadata {
    val exif = ExifInterface(ByteArrayInputStream(imageBytes))
    return CameraMetadata(
        isoValue = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 },
        exposureTimeSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, EXIF_ABSENT).takeIf { it > 0 },
        focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, EXIF_ABSENT).takeIf { it > 0 },
        aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, EXIF_ABSENT).takeIf { it > 0 },
    )
}

private class GrayscaleImage(val pixels: IntArray, val width: Int, val height: Int)

/** Decodifica [imageBytes] sottocampionata (lato max [maxDimensionPx]) in luminanza (0..255) per pixel. */
private fun decodeGrayscaleLuminance(imageBytes: ByteArray, maxDimensionPx: Int): GrayscaleImage {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

    var sampleSize = 1
    while (boundsOptions.outWidth / sampleSize > maxDimensionPx || boundsOptions.outHeight / sampleSize > maxDimensionPx) {
        sampleSize *= 2
    }

    val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    val width = bitmap.width
    val height = bitmap.height
    val argb = IntArray(width * height)
    bitmap.getPixels(argb, 0, width, 0, 0, width, height)
    bitmap.recycle()

    val luminance = IntArray(width * height) { i ->
        val pixel = argb[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Coefficienti standard di luminanza percepita (Rec. 601).
        (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
    return GrayscaleImage(luminance, width, height)
}

/** Varianza del Laplaciano (kernel 3×3 standard) sulla luminanza: più alta = immagine più nitida. */
private fun laplacianVariance(image: GrayscaleImage): Double {
    if (image.width < 3 || image.height < 3) return 0.0
    val responses = mutableListOf<Double>()
    for (y in 1 until image.height - 1) {
        for (x in 1 until image.width - 1) {
            fun at(dx: Int, dy: Int) = image.pixels[(y + dy) * image.width + (x + dx)]
            val laplacian = -4 * at(0, 0) + at(-1, 0) + at(1, 0) + at(0, -1) + at(0, 1)
            responses += laplacian.toDouble()
        }
    }
    val mean = responses.average()
    return responses.sumOf { (it - mean) * (it - mean) } / responses.size
}

/** aHash classico su griglia 8×8 (64 bit): bit = 1 se il blocco è più chiaro della media dell'immagine sottocampionata. */
private fun perceptualHash64(image: GrayscaleImage): Long {
    val gridSize = 8
    val blockWidth = (image.width / gridSize).coerceAtLeast(1)
    val blockHeight = (image.height / gridSize).coerceAtLeast(1)

    val blockAverages = DoubleArray(gridSize * gridSize)
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val startX = (col * blockWidth).coerceAtMost(image.width - 1)
            val startY = (row * blockHeight).coerceAtMost(image.height - 1)
            val endX = (startX + blockWidth).coerceAtMost(image.width)
            val endY = (startY + blockHeight).coerceAtMost(image.height)
            var sum = 0.0
            var count = 0
            for (y in startY until endY) {
                for (x in startX until endX) {
                    sum += image.pixels[y * image.width + x]
                    count++
                }
            }
            blockAverages[row * gridSize + col] = if (count > 0) sum / count else 0.0
        }
    }
    val overallMean = blockAverages.average()

    var hash = 0L
    for (i in blockAverages.indices) {
        if (blockAverages[i] > overallMean) hash = hash or (1L shl i)
    }
    return hash
}
