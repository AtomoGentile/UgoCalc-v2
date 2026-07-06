package com.ugocalc.app.util

/**
 * Buffer circolare di campioni di velocità angolare (piano V2 §16): puro Kotlin, senza dipendenze
 * Android, così la logica di finestra temporale (la parte delicata) resta testabile in JVM. Il
 * wrapper Android ([GyroscopeMotionTracker]) si limita a nutrirlo con eventi reali del sensore.
 *
 * Non è thread-safe di proposito: [GyroscopeMotionTracker] lo usa solo dal listener del sensore
 * (sempre lo stesso thread di consegna) e da letture occasionali, non da scritture concorrenti.
 */
class MotionSampleBuffer(private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS) {

    private data class Sample(val epochMillis: Long, val angularSpeedRadPerSec: Double)

    private val samples = ArrayDeque<Sample>()

    /** Registra un campione e scarta quelli più vecchi della finestra di ritenzione. */
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
    fun peakNear(epochMillis: Long, windowMillis: Long = DEFAULT_WINDOW_MILLIS): Double? =
        samples
            .filter { kotlin.math.abs(it.epochMillis - epochMillis) <= windowMillis }
            .maxOfOrNull { it.angularSpeedRadPerSec }

    companion object {
        const val DEFAULT_RETENTION_MILLIS = 10_000L
        const val DEFAULT_WINDOW_MILLIS = 500L
    }
}
