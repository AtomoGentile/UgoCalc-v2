package com.ugocalc.app.util

/**
 * Buffer circolare di campioni di velocità angolare (piano V2 §16): puro Kotlin, senza dipendenze
 * Android, così la logica di finestra temporale (la parte delicata) resta testabile in JVM. Il
 * wrapper Android ([GyroscopeMotionTracker]) si limita a nutrirlo con eventi reali del sensore.
 *
 * Thread-safe: [GyroscopeMotionTracker] scrive da un HandlerThread dedicato; [peakNear] può essere
 * chiamato dal thread chiamante (Main o IO). I metodi sono synchronized per evitare race condition.
 */
class MotionSampleBuffer(private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS) {

    private data class Sample(val epochMillis: Long, val angularSpeedRadPerSec: Double)

    private val samples = ArrayDeque<Sample>()

    /** Registra un campione e scarta quelli più vecchi della finestra di ritenzione. */
    @Synchronized
    fun record(epochMillis: Long, angularSpeedRadPerSec: Double) {
        samples.addLast(Sample(epochMillis, angularSpeedRadPerSec))
        val cutoff = epochMillis - retentionMillis
        while (samples.isNotEmpty() && samples.first().epochMillis < cutoff) {
            samples.removeFirst()
        }
    }

    /**
     * Picco di velocità angolare tra i campioni entro [windowMillis] da [epochMillis] (prima o
     * dopo). `null` se non c'è alcun campione in quella finestra — nessun hardware, buffer vuoto,
     * o finestra troppo stretta rispetto alla frequenza di campionamento.
     */
    @Synchronized
    fun peakNear(epochMillis: Long, windowMillis: Long = DEFAULT_WINDOW_MILLIS): Double? =
        samples
            .filter { kotlin.math.abs(it.epochMillis - epochMillis) <= windowMillis }
            .maxOfOrNull { it.angularSpeedRadPerSec }

    companion object {
        const val DEFAULT_RETENTION_MILLIS = 10_000L
        const val DEFAULT_WINDOW_MILLIS = 500L
    }
}
