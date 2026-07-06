package com.ugocalc.app.ui.camera

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

private const val TAG = "CameraAngleOverlay"

private const val EXTRA_PITCH_DEG = "pitchDeg"
private const val EXTRA_TURN_DEG = "turnDeg"
private const val EXTRA_LABEL = "label"

private val ALIGNED_COLOR = Color.rgb(46, 125, 50) // stesso verde di ALIGNED_COLOR nella vecchia livella
private val MISALIGNED_COLOR = Color.rgb(211, 47, 47)

/**
 * Livella reale mostrata come overlay flottante SOPRA la fotocamera di sistema (sostituisce
 * `PhotoAngleGuideScreen`, che mostrava la stessa guida ma PRIMA di aprire la fotocamera, su una
 * schermata separata): l'utente vede l'inclinazione/rotazione mentre inquadra davvero, invece di
 * doverla memorizzare da uno schermo precedente.
 *
 * Stessa logica sensori (accelerometro per il pitch, giroscopio integrato sull'asse Z per la
 * rotazione) e stesse tolleranze di [PhotoAngleTarget]/[PHOTO_ANGLE_PITCH_TOLERANCE_DEG]/
 * [PHOTO_ANGLE_TURN_TOLERANCE_DEG] della vecchia guida — solo il rendering cambia (View di sistema
 * via WindowManager invece di Compose, perché deve stare sopra un'altra app).
 *
 * FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE: la view non deve MAI intercettare i tap destinati alla
 * fotocamera sotto, altrimenti l'utente non potrebbe più scattare.
 */
class CameraAngleOverlayService : Service(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var sensorManager: SensorManager? = null
    private var lastGyroTimestampNs: Long? = null

    private var pitchDeg = 0f
    private var turnedDeg = 0f
    private var target: PhotoAngleTarget = PHOTO_ANGLE_TARGETS.first()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            target = PhotoAngleTarget(
                pitchDeg = intent.getFloatExtra(EXTRA_PITCH_DEG, target.pitchDeg),
                turnDeg = intent.getFloatExtra(EXTRA_TURN_DEG, target.turnDeg),
                label = intent.getStringExtra(EXTRA_LABEL) ?: target.label,
            )
            Log.d(TAG, "onStartCommand: target=$target")
        }
        addOverlayIfNeeded()
        startSensors()
        return START_NOT_STICKY
    }

    private fun addOverlayIfNeeded() {
        if (overlayView != null) {
            Log.d(TAG, "addOverlayIfNeeded: già presente, no-op")
            return
        }
        val manager = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (manager == null) {
            Log.d(TAG, "addOverlayIfNeeded: WindowManager non disponibile")
            return
        }
        windowManager = manager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // In alto a destra, non al centro: non deve coprire lo shutter button (in basso) né il
            // mirino centrale.
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        val view = TextView(this).apply {
            setPadding(24, 16, 24, 16)
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.argb(160, 0, 0, 0))
        }

        try {
            manager.addView(view, params)
            overlayView = view
            Log.d(TAG, "addOverlayIfNeeded: view aggiunta al WindowManager (type=$overlayType)")
            updateOverlayText()
        } catch (e: Exception) {
            Log.d(TAG, "addOverlayIfNeeded: eccezione nell'aggiungere la view — probabile blocco del produttore", e)
        }
    }

    private fun startSensors() {
        if (sensorManager != null) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (manager == null) {
            Log.d(TAG, "startSensors: SensorManager non disponibile")
            return
        }
        sensorManager = manager
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        Log.d(TAG, "startSensors: listener registrati")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val (gx, gy, gz) = event.values
                val gNorm = sqrt(gx * gx + gy * gy + gz * gz)
                if (gNorm > 0f) {
                    val flatDeviationRad = acos((abs(gz) / gNorm).coerceIn(-1f, 1f))
                    pitchDeg = 90f - Math.toDegrees(flatDeviationRad.toDouble()).toFloat()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val timestampNs = event.timestamp
                val lastNs = lastGyroTimestampNs
                lastGyroTimestampNs = timestampNs
                if (lastNs != null) {
                    val dtSeconds = (timestampNs - lastNs) / 1_000_000_000f
                    val yawRateRadPerSec = event.values[2]
                    turnedDeg += Math.toDegrees(yawRateRadPerSec.toDouble()).toFloat() * dtSeconds
                }
            }
            else -> return
        }
        updateOverlayText()
    }

    private fun updateOverlayText() {
        val view = overlayView ?: return
        val pitchDelta = target.pitchDeg - pitchDeg
        val pitchAligned = abs(pitchDelta) <= PHOTO_ANGLE_PITCH_TOLERANCE_DEG
        val showTurnGuidance = target.turnDeg > 0f
        val turnDelta = target.turnDeg - turnedDeg
        val turnAligned = abs(turnDelta) <= PHOTO_ANGLE_TURN_TOLERANCE_DEG
        val aligned = pitchAligned && (!showTurnGuidance || turnAligned)

        val pitchLine = "Inclinazione: ${"%.0f".format(pitchDeg)}° (target ${"%.0f".format(target.pitchDeg)}°)"
        val turnLine = if (showTurnGuidance) {
            "\nRotazione: ${"%.0f".format(turnedDeg)}° (target ${"%.0f".format(target.turnDeg)}°)"
        } else {
            ""
        }
        view.text = "${target.label}\n$pitchLine$turnLine"
        view.setTextColor(if (aligned) ALIGNED_COLOR else MISALIGNED_COLOR)
    }

    private fun stopSensors() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        lastGyroTimestampNs = null
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
            Log.d(TAG, "removeOverlay: view rimossa dal WindowManager")
        } catch (e: Exception) {
            Log.d(TAG, "removeOverlay: eccezione nel rimuovere la view", e)
        } finally {
            overlayView = null
            windowManager = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: rimozione overlay e sensori di sicurezza (l'utente potrebbe essere uscito con back)")
        stopSensors()
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        fun buildStartIntent(context: Context, target: PhotoAngleTarget): Intent =
            Intent(context, CameraAngleOverlayService::class.java).apply {
                putExtra(EXTRA_PITCH_DEG, target.pitchDeg)
                putExtra(EXTRA_TURN_DEG, target.turnDeg)
                putExtra(EXTRA_LABEL, target.label)
            }
    }
}
