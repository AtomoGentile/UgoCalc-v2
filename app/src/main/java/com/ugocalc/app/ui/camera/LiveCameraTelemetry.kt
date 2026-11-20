package com.ugocalc.app.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.util.Camera2LiveMetadata
import com.ugocalc.app.util.CameraMetadataHelper
import com.ugocalc.app.util.OpticalGeometryCalculator
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

enum class CoachingSeverity {
    OK,
    INFO,
    WARNING,
}

/**
 * Stato telemetrico in tempo reale di CameraX Pro Studio: unisce sensori inerziali a 60fps
 * (pitch, roll, rotazione, giroscopio), metadati ottici live Camera2 (diottrie, AF, OIS, ISO,
 * otturatore, focale, apertura, lente fisica attiva) e analisi Y-plane a ~4fps.
 */
data class LiveCameraTelemetryState(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val turnDeg: Float = 0f,
    val hasAttitudeReading: Boolean = false,
    val motionRadPerSec: Double? = null,
    val activeCameraId: String? = null,
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    val focusDistanceDiopters: Float? = null,
    val estimatedDistanceCm: Double? = null,
    val fovWidthCm: Double? = null,
    val fovHeightCm: Double? = null,
    val afState: Int? = null,
    val isAfLocked: Boolean = false,
    val oisEnabled: Boolean? = null,
    val isoValue: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val focalLengthMm: Double? = null,
    val focalLength35mmEq: Double? = null,
    val aperture: Double? = null,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val liveBrightness: Double? = null,
    val liveSharpness: Double? = null,
    val coachingHint: String? = null,
    val coachingSeverity: CoachingSeverity = CoachingSeverity.INFO,
) {
    /** Converte l'istantanea live nel DTO agnostico dalla UI usato per il merge con EXIF. */
    fun toCamera2LiveMetadata(
        imageWidthPx: Int? = null,
        imageHeightPx: Int? = null,
    ): Camera2LiveMetadata = Camera2LiveMetadata(
        activeCameraId = activeCameraId,
        sensorWidthMm = sensorWidthMm,
        sensorHeightMm = sensorHeightMm,
        focusDistanceDiopters = focusDistanceDiopters,
        afState = afState,
        oisEnabled = oisEnabled,
        isoValue = isoValue,
        exposureTimeSeconds = exposureTimeSeconds,
        focalLengthMm = focalLengthMm,
        aperture = aperture,
        zoomRatio = zoomRatio,
        pitchDeg = if (hasAttitudeReading) pitchDeg else null,
        rollDeg = if (hasAttitudeReading) rollDeg else null,
        imageWidthPx = imageWidthPx,
        imageHeightPx = imageHeightPx,
        captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
    )
}

/** Lettura istantanea dei sensori inerziali (60fps). */
data class AttitudeReading(
    val pitchDeg: Float,
    val rollDeg: Float,
    val turnDeg: Float,
    val motionRadPerSec: Double?,
)

/** Pacchetto grezzo letto da `CameraCaptureSession.CaptureCallback.onCaptureCompleted`. */
data class CaptureTelemetryPacket(
    val activePhysicalCameraId: String? = null,
    val focusDistanceDiopters: Float? = null,
    val afState: Int? = null,
    val oisEnabled: Boolean? = null,
    val isoValue: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val focalLengthMm: Double? = null,
    val aperture: Double? = null,
)

/**
 * Tracker inerziale a ~60fps (`SENSOR_DELAY_GAME`) basato su `TYPE_ROTATION_VECTOR` con fallback
 * automatico ad accelerometro + magnetometro (o solo accelerometro se il magnetometro manca).
 *
 * Calcola:
 * - `pitchDeg` (`0°` verticale, `90°` piatto parallelo al piatto — stessa convenzione di [PhotoAngleTarget]);
 * - `rollDeg` (`-90°..+90°`, `0°` orizzontale);
 * - `turnDeg` (`0°..360°`, rotazione intorno al piatto rispetto all'orientamento di riferimento dello scatto #1);
 * - `motionRadPerSec` dal giroscopio hardware se presente.
 */
class LiveAttitudeTracker(
    context: Context,
    private val onAttitudeChanged: (AttitudeReading) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val rotationMatrix = FloatArray(9)
    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var smoothedPitch = 0f
    private var smoothedRoll = 0f
    private var smoothedHeadingSin = 0f
    private var smoothedHeadingCos = 1f
    private var initialized = false
    private var referenceHeadingDeg: Float? = null
    private var latestHeadingDeg: Float? = null
    private var latestMotionRadPerSec: Double? = null

    private var isStarted = false

    fun start() {
        if (isStarted || sensorManager == null) return
        isStarted = true
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accelerometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager?.unregisterListener(this)
    }

    /** Fissa l'orientamento corrente come zero (0°) per calcolare la rotazione degli scatti successivi. */
    fun captureReferenceHeading() {
        latestHeadingDeg?.let { referenceHeadingDeg = it }
    }

    /** Azzera il riferimento di rotazione (es. se tutte le foto sono state rimosse). */
    fun clearReferenceHeading() {
        referenceHeadingDeg = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                processRotationMatrix(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccel = true
                if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                    processRotationMatrix(rotationMatrix)
                } else {
                    processAccelerometerOnly(accelValues)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magValues, 0, 3)
                hasMag = true
                if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                    processRotationMatrix(rotationMatrix)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val rx = event.values[0]
                val ry = event.values[1]
                val rz = event.values[2]
                latestMotionRadPerSec = sqrt((rx * rx + ry * ry + rz * rz).toDouble())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processRotationMatrix(r: FloatArray) {
        // Mondo Up (0,0,1) nel sistema del dispositivo = terza riga di R: (r[6], r[7], r[8])
        val gx = r[6]
        val gy = r[7]
        val gz = r[8]

        val flatDeviationRad = acos(abs(gz).coerceIn(0f, 1f))
        val rawPitch = (90.0 - Math.toDegrees(flatDeviationRad.toDouble())).toFloat().coerceIn(0f, 90f)
        val rawRoll = Math.toDegrees(atan2(gx.toDouble(), hypot(gy.toDouble(), gz.toDouble()))).toFloat()

        // Direzione di puntamento della camera (-Z dispositivo) proiettata sul piano orizzontale (Est, Nord)
        val camEast = -r[2]
        val camNorth = -r[5]
        val rawHeading = if (hypot(camEast, camNorth) > 0.25f) {
            normalize360(Math.toDegrees(atan2(camEast.toDouble(), camNorth.toDouble())).toFloat())
        } else {
            // Telefono quasi piatto (90°): usa l'asse +Y (lato superiore del telefono) sul piano orizzontale
            normalize360(Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble())).toFloat())
        }

        emitSmoothed(rawPitch, rawRoll, rawHeading)
    }

    private fun processAccelerometerOnly(g: FloatArray) {
        val gNorm = sqrt((g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).toDouble()).toFloat()
        if (gNorm <= 0.001f) return
        val gx = g[0] / gNorm
        val gy = g[1] / gNorm
        val gz = g[2] / gNorm
        val flatDeviationRad = acos(abs(gz).coerceIn(0f, 1f))
        val rawPitch = (90.0 - Math.toDegrees(flatDeviationRad.toDouble())).toFloat().coerceIn(0f, 90f)
        val rawRoll = Math.toDegrees(atan2(gx.toDouble(), hypot(gy.toDouble(), gz.toDouble()))).toFloat()
        emitSmoothed(rawPitch, rawRoll, latestHeadingDeg ?: 0f)
    }

    private fun emitSmoothed(rawPitch: Float, rawRoll: Float, rawHeading: Float) {
        val headingRad = Math.toRadians(rawHeading.toDouble())
        val hSin = sin(headingRad).toFloat()
        val hCos = cos(headingRad).toFloat()

        if (!initialized) {
            initialized = true
            smoothedPitch = rawPitch
            smoothedRoll = rawRoll
            smoothedHeadingSin = hSin
            smoothedHeadingCos = hCos
        } else {
            val alpha = 0.22f
            smoothedPitch += alpha * (rawPitch - smoothedPitch)
            smoothedRoll += alpha * (rawRoll - smoothedRoll)
            smoothedHeadingSin += alpha * (hSin - smoothedHeadingSin)
            smoothedHeadingCos += alpha * (hCos - smoothedHeadingCos)
        }

        val smoothedHeading = normalize360(
            Math.toDegrees(atan2(smoothedHeadingSin.toDouble(), smoothedHeadingCos.toDouble())).toFloat()
        )
        latestHeadingDeg = smoothedHeading
        val ref = referenceHeadingDeg
        val turnDeg = if (ref != null) normalize360(smoothedHeading - ref) else 0f

        onAttitudeChanged(
            AttitudeReading(
                pitchDeg = smoothedPitch,
                rollDeg = smoothedRoll,
                turnDeg = turnDeg,
                motionRadPerSec = latestMotionRadPerSec,
            )
        )
    }

    private fun normalize360(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}

/**
 * Callback Camera2 (`Camera2Interop.Extender`) agganciata alla preview CameraX per leggere a bassa
 * latenza diottrie di messa a fuoco, AF, OIS, ISO, tempo di esposizione, focale e apertura.
 */
class Camera2TelemetryCaptureCallback(
    private val throttleMs: Long = 100L,
    private val onPacket: (CaptureTelemetryPacket) -> Unit,
) : CameraCaptureSession.CaptureCallback() {

    private val latestRef = AtomicReference<CaptureTelemetryPacket?>(null)
    @Volatile
    private var lastEmitElapsedMs = 0L

    val latestPacket: CaptureTelemetryPacket? get() = latestRef.get()

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult,
    ) {
        val physicalId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) }.getOrNull()
        } else {
            null
        }
        val focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        val oisMode = result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
        val oisEnabled = oisMode?.let { it != CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF }
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val exposureSeconds = exposureNs?.takeIf { it > 0L }?.let { it / 1_000_000_000.0 }
        val focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH)?.takeIf { it > 0f }?.toDouble()
        val aperture = result.get(CaptureResult.LENS_APERTURE)?.takeIf { it > 0f }?.toDouble()

        val packet = CaptureTelemetryPacket(
            activePhysicalCameraId = physicalId,
            focusDistanceDiopters = focusDiopters,
            afState = afState,
            oisEnabled = oisEnabled,
            isoValue = iso,
            exposureTimeSeconds = exposureSeconds,
            focalLengthMm = focalMm,
            aperture = aperture,
        )
        latestRef.set(packet)

        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitElapsedMs >= throttleMs) {
            lastEmitElapsedMs = now
            onPacket(packet)
        }
    }
}

/**
 * Estrae ID camera e dimensione fisica del sensore usando `Camera2CameraInfo`, preferendo la lente
 * fisica attiva quando la camera logica multi-sensore passa tra ultrawide, principale e tele.
 */
@OptIn(ExperimentalCamera2Interop::class)
fun extractCamera2SensorInfo(
    cameraInfo: CameraInfo,
    context: Context,
    activePhysicalCameraId: String? = null,
): Pair<String?, SizeF?> {
    return runCatching {
        val c2Info = Camera2CameraInfo.from(cameraInfo)
        val logicalId = c2Info.cameraId
        val targetId = activePhysicalCameraId?.takeIf { it.isNotBlank() } ?: logicalId
        val physicalSize = CameraMetadataHelper.getCameraPhysicalSize(context, targetId)
            ?: c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        targetId to physicalSize
    }.getOrDefault(null to null)
}

/**
 * Analyzer CameraX a ~4fps sul piano Y (luminanza): zero allocazioni Bitmap, chiude SEMPRE
 * `imageProxy` in `finally` per evitare blocchi della pipeline immagini.
 */
class LiveQualityImageAnalyzer(
    private val minIntervalMs: Long = 250L,
    private val onMetrics: (brightness: Double, sharpness: Double) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var lastAnalyzedElapsedMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedElapsedMs < minIntervalMs) return
            lastAnalyzedElapsedMs = now

            val yPlane = imageProxy.planes.firstOrNull() ?: return
            val width = imageProxy.width
            val height = imageProxy.height
            if (width < 16 || height < 16) return

            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val limit = buffer.limit()

            val targetGrid = 48
            val stepX = (width / targetGrid).coerceAtLeast(1)
            val stepY = (height / targetGrid).coerceAtLeast(1)
            val gridW = (width / stepX).coerceAtLeast(3)
            val gridH = (height / stepY).coerceAtLeast(3)

            val lum = IntArray(gridW * gridH)
            var sum = 0L
            for (gy in 0 until gridH) {
                val py = gy * stepY
                val rowOffset = py * rowStride
                for (gx in 0 until gridW) {
                    val px = gx * stepX
                    val idx = rowOffset + px * pixelStride
                    val yVal = if (idx in 0 until limit) buffer.get(idx).toInt() and 0xFF else 0
                    lum[gy * gridW + gx] = yVal
                    sum += yVal
                }
            }
            val brightness = sum.toDouble() / lum.size

            // Varianza del Laplaciano 3×3 sulla griglia sottocampionata
            val innerCount = (gridW - 2) * (gridH - 2)
            if (innerCount <= 0) {
                onMetrics(brightness, 0.0)
                return
            }
            var lapSum = 0.0
            var lapSqSum = 0.0
            for (y in 1 until gridH - 1) {
                val row = y * gridW
                for (x in 1 until gridW - 1) {
                    val center = lum[row + x]
                    val lap = (-4 * center + lum[row + x - 1] + lum[row + x + 1] + lum[row - gridW + x] + lum[row + gridW + x]).toDouble()
                    lapSum += lap
                    lapSqSum += lap * lap
                }
            }
            val meanLap = lapSum / innerCount
            val variance = (lapSqSum / innerCount - meanLap * meanLap).coerceAtLeast(0.0)
            onMetrics(brightness, variance)
        } catch (_: Exception) {
            // Ignora frame isolati malformati senza interrompere la preview
        } finally {
            imageProxy.close()
        }
    }
}

/** Calcola la distanza angolare circolare minima in gradi tra due angoli (0°..360°). */
fun angularDeltaDeg(currentDeg: Float, targetDeg: Float): Float =
    ((currentDeg - targetDeg + 540f) % 360f) - 180f

/** Verifica se l'assetto corrente soddisfa il [PhotoAngleTarget] entro le tolleranze standard. */
fun isAngleAligned(
    state: LiveCameraTelemetryState,
    target: PhotoAngleTarget,
): Boolean {
    if (!state.hasAttitudeReading) return false
    val pitchOk = abs(state.pitchDeg - target.pitchDeg) <= PHOTO_ANGLE_PITCH_TOLERANCE_DEG
    val rollOk = if (target.pitchDeg >= 80f) {
        abs(state.rollDeg) <= PHOTO_ANGLE_PITCH_TOLERANCE_DEG * 2.5f
    } else {
        abs(state.rollDeg) <= 10f
    }
    val turnOk = if (target.turnDeg > 0f) {
        abs(angularDeltaDeg(state.turnDeg, target.turnDeg)) <= PHOTO_ANGLE_TURN_TOLERANCE_DEG
    } else {
        true
    }
    val motionOk = (state.motionRadPerSec ?: 0.0) <= QualityGate.DEFAULT_MOTION_MAX_RAD_PER_SEC
    return pitchOk && rollOk && turnOk && motionOk
}

/**
 * Calcola un suggerimento operativo conciso in italiano basato su sensori, luce, fuoco,
 * distanza e allineamento rispetto al target di scatto corrente.
 */
fun computeLiveCoachingHint(
    state: LiveCameraTelemetryState,
    target: PhotoAngleTarget,
    aligned: Boolean,
): Pair<String, CoachingSeverity> {
    val motion = state.motionRadPerSec
    if (motion != null && motion > QualityGate.DEFAULT_MOTION_MAX_RAD_PER_SEC) {
        return "Tieni fermo il telefono" to CoachingSeverity.WARNING
    }

    val brightness = state.liveBrightness
    if (brightness != null && brightness < QualityGate.DEFAULT_BRIGHTNESS_MIN + 5.0) {
        return "Poca luce — attiva la torcia" to CoachingSeverity.WARNING
    }
    if (brightness != null && brightness > QualityGate.DEFAULT_BRIGHTNESS_MAX + 5.0) {
        return "Troppa luce o riflessi sul piatto" to CoachingSeverity.WARNING
    }

    if (state.afState == OpticalGeometryCalculator.AF_STATE_NOT_FOCUSED_LOCKED ||
        state.afState == OpticalGeometryCalculator.AF_STATE_PASSIVE_UNFOCUSED
    ) {
        return "Tocca il piatto per mettere a fuoco" to CoachingSeverity.WARNING
    }

    val sharpness = state.liveSharpness
    if (sharpness != null && sharpness < 55.0) {
        return "Immagine morbida — tocca per fuoco" to CoachingSeverity.WARNING
    }

    val dist = state.estimatedDistanceCm
    if (dist != null && dist < 12.0) {
        return "Allontanati leggermente dal piatto" to CoachingSeverity.INFO
    }
    if (dist != null && dist > 95.0) {
        return "Avvicinati un po' al piatto" to CoachingSeverity.INFO
    }

    if (aligned) {
        return "Perfetto — scatta ora" to CoachingSeverity.OK
    }

    if (state.hasAttitudeReading) {
        val pitchDelta = target.pitchDeg - state.pitchDeg
        if (abs(pitchDelta) > PHOTO_ANGLE_PITCH_TOLERANCE_DEG) {
            return if (pitchDelta > 0f) {
                "Inclina più verso il basso (${target.pitchDeg.toInt()}°)" to CoachingSeverity.INFO
            } else {
                "Solleva leggermente il telefono (${target.pitchDeg.toInt()}°)" to CoachingSeverity.INFO
            }
        }
        if (target.turnDeg > 0f) {
            val turnDelta = angularDeltaDeg(state.turnDeg, target.turnDeg)
            if (abs(turnDelta) > PHOTO_ANGLE_TURN_TOLERANCE_DEG) {
                return "Ruota intorno al piatto (${target.turnDeg.toInt()}°)" to CoachingSeverity.INFO
            }
        }
        if (abs(state.rollDeg) > 10f) {
            return "Raddrizza l'orizzonte" to CoachingSeverity.INFO
        }
    }

    return target.label to CoachingSeverity.INFO
}

/**
 * Ricomputa i campi derivati (`estimatedDistanceCm`, `fovWidthCm`, `fovHeightCm`, `focalLength35mmEq`)
 * sullo stato live usando [OpticalGeometryCalculator] per il display HUD in tempo reale.
 */
fun LiveCameraTelemetryState.withComputedOptics(
    previewWidthPx: Int = 4000,
    previewHeightPx: Int = 3000,
): LiveCameraTelemetryState {
    val enriched = OpticalGeometryCalculator.enrichMetadata(
        CameraMetadata(
            isoValue = isoValue,
            exposureTimeSeconds = exposureTimeSeconds,
            focalLengthMm = focalLengthMm,
            focalLength35mmEq = focalLength35mmEq,
            aperture = aperture,
            sensorWidthMm = sensorWidthMm,
            sensorHeightMm = sensorHeightMm,
            focusDistanceDiopters = focusDistanceDiopters,
            zoomRatio = zoomRatio,
            afState = afState,
            oisEnabled = oisEnabled,
            rollDeg = if (hasAttitudeReading) rollDeg else null,
            pitchDeg = if (hasAttitudeReading) pitchDeg else null,
            imageWidthPx = previewWidthPx,
            imageHeightPx = previewHeightPx,
            captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
        )
    )
    return copy(
        estimatedDistanceCm = enriched.estimatedDistanceCm,
        fovWidthCm = enriched.fovWidthCm,
        fovHeightCm = enriched.fovHeightCm,
        focalLength35mmEq = enriched.focalLength35mmEq,
        isAfLocked = OpticalGeometryCalculator.isAfLocked(afState),
    )
}
