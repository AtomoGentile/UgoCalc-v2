package com.ugocalc.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Giroscopio per M0 (piano V2 §16): la foto è scattata dalla fotocamera di sistema (§ vedi
 * `CameraCaptureScreen`), quindi non esiste alcun callback nell'istante esatto dello scatto. Questo
 * singleton tiene un buffer circolare di velocità angolare sempre attivo — non legato a
 * `onResume`/`onPause` di nessuna Activity, come [AppForegroundObserver] con `ProcessLifecycleOwner`
 * — così gli eventi continuano ad arrivare anche mentre la fotocamera di sistema è in primo piano
 * (il processo resta vivo). [peakMotionNear] interroga poi il buffer usando il timestamp del file
 * scritto dalla fotocamera come istante approssimato dello scatto: un'approssimazione dichiarata,
 * non il timestamp esatto del sensore immagine.
 */
@Singleton
class GyroscopeMotionTracker @Inject constructor(@ApplicationContext context: Context) : SensorEventListener {

    private val motionBuffer = MotionSampleBuffer()

    init {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Se il device non ha un giroscopio, nessuna registrazione: peakMotionNear resterà sempre
        // null, degradazione esplicita (nessun campo popolato), mai un valore inventato.
        if (sensorManager != null && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val angularSpeedRadPerSec = sqrt(
            event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2],
        ).toDouble()
        motionBuffer.record(System.currentTimeMillis(), angularSpeedRadPerSec)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Picco di velocità angolare vicino a [epochMillis] (§16), o `null` se non c'è alcun campione utile. */
    fun peakMotionNear(epochMillis: Long): Double? = motionBuffer.peakNear(epochMillis)
}
