package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Calibrazione persistente di un'ancora di scala (§8, §13 punto 4): l'utente ha dichiarato che un
 * certo oggetto (stoviglia di casa, riferimento corporeo) ha una dimensione esatta. Il motore V2
 * usa questi dati per fornire a M1 ancore pre-calibrate, riducendo l'errore di scala.
 */
@Serializable
@Entity(tableName = "scale_calibrations")
data class ScaleCalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Modalità in cui è stata salvata (CASA o RISTORANTE). */
    val sessionMode: String = "CASA",
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
    /** Forma del piatto: "cilindro" (default) o "conico". Usato solo per piatti nella strategia DISTESA_SUL_PIATTO. */
    val plateShapeType: String? = null,
    /** Diametro del fondo del piatto conico in centimetri (opzionale, richiesto solo se plateShapeType="conico"). */
    val plateBottomDiameterCm: Double? = null,
    /** Percorso locale dell'immagine di riferimento scattata dall'utente (§13 punto 4): permette a M1 di fare matching visivo. */
    val imagePath: String? = null,
    /** UUID stabile generato client-side: usato come chiave idempotente per la sync cross-device (calibration push/pull). */
    val clientId: String? = null,
)
