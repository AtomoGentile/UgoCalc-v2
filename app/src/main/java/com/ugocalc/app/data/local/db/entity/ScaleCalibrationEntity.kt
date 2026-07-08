package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Calibrazione persistente di un'ancora di scala (§8, §13 punto 4): l'utente ha dichiarato che un
 * certo oggetto (stoviglia di casa, riferimento corporeo) ha una dimensione esatta. Il motore V2
 * usa questi dati per fornire a M1 ancore pre-calibrate, riducendo l'errore di scala.
 */
@Entity(tableName = "scale_calibrations")
data class ScaleCalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nome dell'enum [com.ugocalc.app.domain.inference.model.ScaleAnchorType]. */
    val type: String,
    /** Nome descrittivo dato dall'utente (es. "Piatto piano bianco", "Mio dito indice"). */
    val name: String,
    /** Dimensione reale in centimetri (diametro per piatti, lunghezza per dita/oggetti). */
    val valueCm: Double,
    /** Larghezza reale in centimetri (opzionale, per riferimenti 2D/3D). */
    val widthCm: Double? = null,
    /** Altezza reale in centimetri (opzionale, per riferimenti 3D). */
    val heightCm: Double? = null,
    /** Capacità in millilitri, usata solo per contenitori (bicchieri, tazze) nella strategia A CONTENITORE. */
    val capacityMl: Double? = null,
)
