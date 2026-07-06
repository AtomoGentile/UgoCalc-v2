package com.ugocalc.app.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Un target di angolazione/rotazione per uno scatto della sessione (piano V2 §13 punto 7: suggeritore d'angolazione in tempo reale). */
data class PhotoAngleTarget(
    /** 0° = telefono verticale (scatto orizzontale), 90° = telefono piatto (scatto dall'alto). */
    val pitchDeg: Float,
    /** Quanto girare intorno al piatto rispetto alla posizione di partenza di QUESTO scatto (non cumulativo rispetto alla foto 1): 0 = nessun giro richiesto. */
    val turnDeg: Float,
    val label: String,
)

/**
 * 5 scatti pensati per dare a M3 più viste geometriche dello stesso piatto: uno dall'alto (area e
 * forma), poi quattro a 45° girando intorno al piatto di 90° alla volta, per stimare altezza/volume
 * da lati diversi e ridurre le occlusioni rispetto a un singolo punto di vista.
 */
val PHOTO_ANGLE_TARGETS = listOf(
    PhotoAngleTarget(pitchDeg = 90f, turnDeg = 0f, label = "Foto 1 — dall'alto"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 0f, label = "Foto 2 — inclinato a 45°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 90f, label = "Foto 3 — gira intorno al piatto, poi 45°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 90f, label = "Foto 4 — gira ancora, poi 45°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 90f, label = "Foto 5 — gira ancora, poi 45°"),
)

private const val PITCH_TOLERANCE_DEG = 3f
private const val TURN_TOLERANCE_DEG = 8f
private val ALIGNED_COLOR = Color(0xFF2E7D32)

/**
 * Livella in tempo reale mostrata prima di ogni scatto (§13 punto 7): guida l'utente
 * sull'inclinazione (accelerometro, angolo tra l'asse della fotocamera e la verticale) e sulla
 * rotazione intorno al piatto rispetto a dove si trova ORA (integrazione del giroscopio sull'asse
 * Z per la durata di questa schermata) — niente bussola magnetica: sarebbe soggetta a interferenze
 * e non serve un riferimento assoluto, solo "gira di altri 90° da qui".
 *
 * Non c'è preview live della fotocamera qui (vedi `CameraCaptureScreen`: lo scatto resta delegato
 * alla fotocamera di sistema): questa livella è un passaggio *prima* di aprirla, non un overlay sul
 * mirino — vedi discussione su perché un vero mirino richiederebbe CameraX.
 */
@Composable
fun PhotoAngleGuideScreen(
    target: PhotoAngleTarget,
    photoNumber: Int,
    totalPhotos: Int,
    onReady: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val sensorState = remember { OrientationSensorState() }

    DisposableEffect(Unit) {
        sensorState.start(context)
        onDispose { sensorState.stop() }
    }

    val pitchDelta = target.pitchDeg - sensorState.pitchDeg
    val turnDelta = target.turnDeg - sensorState.turnedDeg
    val pitchAligned = abs(pitchDelta) <= PITCH_TOLERANCE_DEG
    val turnAligned = abs(turnDelta) <= TURN_TOLERANCE_DEG
    val showTurnGuidance = target.turnDeg > 0f
    val aligned = pitchAligned && (!showTurnGuidance || turnAligned)

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Foto $photoNumber di $totalPhotos", style = MaterialTheme.typography.titleMedium)
        Text(
            target.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        Text(
            text = when {
                pitchAligned -> "Inclinazione: perfetta ✓"
                pitchDelta > 0 -> "Abbassa di più il telefono verso il piatto (più orizzontale)"
                else -> "Solleva il telefono, meno inclinato verso il basso"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (pitchAligned) ALIGNED_COLOR else MaterialTheme.colorScheme.error,
        )

        if (showTurnGuidance) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = when {
                    turnAligned -> "Rotazione: perfetta ✓"
                    turnDelta > 0 -> "Gira ancora in senso orario intorno al piatto"
                    else -> "Hai girato troppo: torna un po' indietro (senso antiorario)"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (turnAligned) ALIGNED_COLOR else MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = onReady) {
            Text(if (aligned) "Scatta ✓" else "Scatta comunque")
        }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Annulla")
        }
    }
}

/**
 * Stato sensori "usa e getta" per la durata della schermata guida: si registra/deregistra col
 * ciclo di vita del Composable, non un singleton always-on come
 * [com.ugocalc.app.util.GyroscopeMotionTracker] — qui serve solo mentre l'utente sta inquadrando,
 * pochi secondi, quindi l'integrazione del giroscopio (che deriva nel tempo) resta trascurabile.
 */
private class OrientationSensorState : SensorEventListener {
    var pitchDeg by mutableStateOf(0f)
        private set
    var turnedDeg by mutableStateOf(0f)
        private set

    private var sensorManager: SensorManager? = null
    private var lastGyroTimestampNs: Long? = null

    fun start(context: Context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = manager
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val (gx, gy, gz) = event.values
                val gNorm = sqrt(gx * gx + gy * gy + gz * gz)
                if (gNorm > 0f) {
                    // Angolo tra l'asse Z del telefono (normale allo schermo, direzione della
                    // fotocamera posteriore) e la verticale di gravità: 0° quando il telefono è
                    // verticale (gz≈0), 90° quando è piatto (|gz|≈gNorm).
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
                    // Asse Z del giroscopio: velocità di rotazione intorno all'asse che punta fuori
                    // dallo schermo, cioè la rotazione dell'utente intorno al piatto mentre lo
                    // inquadra. Integrato solo da quando questa schermata è aperta (§ vedi sopra).
                    val yawRateRadPerSec = event.values[2]
                    turnedDeg += Math.toDegrees(yawRateRadPerSec.toDouble()).toFloat() * dtSeconds
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
