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
 * 5 scatti pensati per dare a M3 più viste geometriche dello stesso piatto: uno dall'alto (area e
 * forma), poi quattro a 45° girando intorno al piatto di 90° alla volta (0°, 90°, 180°, 270°), per
 * stimare altezza/volume da lati diversi e ridurre le occlusioni rispetto a un singolo punto di
 * vista (§ richiesta utente).
 */
val PHOTO_ANGLE_TARGETS = listOf(
    PhotoAngleTarget(pitchDeg = 90f, turnDeg = 0f, label = "Foto 1 — dall'alto (visione aerea)"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 0f, label = "Foto 2 — inclinato 0°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 90f, label = "Foto 3 — inclinato 90°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 180f, label = "Foto 4 — inclinato 180°"),
    PhotoAngleTarget(pitchDeg = 45f, turnDeg = 270f, label = "Foto 5 — inclinato 270°"),
)

const val PHOTO_ANGLE_PITCH_TOLERANCE_DEG = 3f
const val PHOTO_ANGLE_TURN_TOLERANCE_DEG = 8f
