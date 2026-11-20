package com.ugocalc.app.ui.camera

/** Un target di angolazione/rotazione per uno scatto della sessione (piano V2 §13 punto 7: suggeritore d'angolazione in tempo reale). */
data class PhotoAngleTarget(
    /** 0° = telefono verticale (scatto orizzontale), 90° = telefono piatto (scatto dall'alto). */
    val pitchDeg: Float,
    /** Quanto girare intorno al piatto rispetto alla posizione di partenza di QUESTO scatto (non cumulativo rispetto alla foto 1): 0 = nessun giro richiesto. */
    val turnDeg: Float,
    val label: String,
)

/**
 * Protocollo rapido fotocamera (piano V2 §13 punto 7):
 * - I primi [RECOMMENDED_MIN_PHOTOS] scatti (Foto 1 dall'alto a 90° per area/diametro del piatto +
 *   Foto 2 laterale a 45° per altezza e profilo del cumulo) costituiscono il protocollo base
 *   ottimale e bastano nella quasi totalità dei casi.
 * - Gli scatti 3–5 (45° ruotati di 90°, 180°, 270°) restano disponibili in coda come viste
 *   opzionali solo per piatti con forti occlusioni.
 */
const val RECOMMENDED_MIN_PHOTOS = 2

val PHOTO_ANGLE_TARGETS = listOf(
    PhotoAngleTarget(pitchDeg = 90f, turnDeg = 0f, label = "Foto 1 — dall'alto 90° (area piatto)"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 0f, label = "Foto 2 — laterale 45° (altezza/profilo)"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 90f, label = "Foto 3 (opzionale) — 45° ruotata 90°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 180f, label = "Foto 4 (opzionale) — 45° ruotata 180°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 270f, label = "Foto 5 (opzionale) — 45° ruotata 270°"),
)

const val PHOTO_ANGLE_PITCH_TOLERANCE_DEG = 3f
const val PHOTO_ANGLE_TURN_TOLERANCE_DEG = 8f
