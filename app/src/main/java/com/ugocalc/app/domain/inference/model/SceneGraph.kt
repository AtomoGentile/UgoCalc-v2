package com.ugocalc.app.domain.inference.model

import com.ugocalc.app.domain.model.Confidence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contratto dati M1 → M2 (piano V2 §7). È l'output della Percezione unificata (M1, unica chiamata
 * multimodale): la "scena riconosciuta" da cui il resto della pipeline deterministica lavora.
 *
 * Regola generale dei contratti (§7): l'IA percepisce e propone dimensioni/conteggi/riempimenti,
 * **mai grammi diretti** (§2). La conversione in peso è compito esclusivo di M3.
 */
@Serializable
data class SceneGraph(
    /**
     * Versione dello schema dichiarata dall'output di M1 (§7 "Versionamento dello schema").
     * Prompt e data class evolvono insieme: ogni modifica allo schema incrementa
     * [SCHEMA_VERSION] nello stesso commit che tocca entrambi. Il campo non ha default JSON di
     * proposito: un output che non dichiara la versione fallisce la deserializzazione rigida
     * invece di passare in silenzio. M2 rifiuta (errore gestito) le versioni non riconosciute.
     */
    @SerialName("schema_version") val schemaVersion: Int,
    /** Spiegazione testuale del ragionamento fatto dall'IA per analizzare la scena nel suo complesso (§ richiesta utente). */
    val ragionamento: String = "",
    /** Ancore di scala individuate nella scena, ciascuna con fonte e confidenza (§5 M1, §8). */
    val ancore: List<ScaleAnchor>,
    /** Inventario canonico degli alimenti, con misure grezze e occlusioni. */
    val alimenti: List<PerceivedFood>,
    /** Relazioni di sovrapposizione tra alimenti, utili a M4 per la coerenza di scena (§7). */
    @SerialName("sovrapposizioni") val sovrapposizioni: List<OverlapRelation> = emptyList(),
) {
    companion object {
        /** Versione corrente dello schema SceneGraph attesa da prompt M1 e pipeline. */
        const val SCHEMA_VERSION = 4
    }
}

/**
 * Un riferimento di scala riconosciuto nella scena. La priorità con cui vengono tentate dipende
 * dalla modalità di sessione (§8): la principale sono i riferimenti corporei/universali, non le
 * stoviglie di casa, che intervengono solo in modalità Casa quando calibrate (§14).
 */
@Serializable
data class ScaleAnchor(
    val tipo: ScaleAnchorType,
    val descrizione: String,
    /** Dimensione reale nota in centimetri, se l'ancora la fornisce (es. Ø piatto calibrato). */
    @SerialName("dimensione_reale_cm") val dimensioneRealeCm: Double? = null,
    val confidenza: Confidence,
)

/** Tipi di ancora di scala in ordine di affidabilità di principio (§8). */
@Serializable
enum class ScaleAnchorType {
    /** Stoviglia di casa dal diametro/capacità calibrati: ancora certa, solo in modalità Casa. */
    @SerialName("stoviglia_calibrata") STOVIGLIA_CALIBRATA,

    /** Riferimento corporeo personale (dito, apertura mano): condiviso tra le modalità, prima scelta. */
    @SerialName("riferimento_corporeo") RIFERIMENTO_CORPOREO,

    /** Oggetto di dimensione universale nota (moneta, carta di credito, posata standard). */
    @SerialName("oggetto_universale") OGGETTO_UNIVERSALE,

    /** Stima generica in assenza di ancore migliori: comporta confidenza bassa dichiarata (§9). */
    @SerialName("stima_generica") STIMA_GENERICA,
}

/**
 * Range di peso plausibile per un alimento basato sull'intuizione statistica dell'IA (piano V2 §4,
 * §5 M4). È il "buon senso" della V1 iniettato nella V2: serve a M4 come guardrail per rigettare
 * calcoli geometrici di M3 palesemente assurdi (es. peperoncino da 123g).
 */
@Serializable
data class PlausibilityLimits(
    @SerialName("peso_min_plausibile_g") val pesoMinG: Double,
    @SerialName("peso_max_plausibile_g") val pesoMaxG: Double,
)

/**
 * Un alimento percepito da M1. Porta il nome canonico, eventuali alternative di identificazione
 * (che M4 confronta per l'ambiguità sui carboidrati), il grado di occlusione e le misure grezze da
 * cui M3 calcolerà il peso.
 */
@Serializable
data class PerceivedFood(
    val nome: String,
    /** Spiegazione testuale del perché è stato identificato così o delle misure scelte (§ richiesta utente). */
    val ragionamento: String = "",
    /** Identificazioni alternative plausibili; se cambiano molto i CHO, M4 le segnala come ambigue. */
    @SerialName("alternative_identificazione") val alternativeIdentificazione: List<String> = emptyList(),
    @SerialName("occlusione") val occlusione: OcclusionDegree = OcclusionDegree.NESSUNA,
    @SerialName("misure_grezze") val misureGrezze: RawMeasurements,
    /** Range di peso plausibile basato sull'intuizione dell'IA (stile V1), usato da M4 come guardrail per M3. */
    @SerialName("limiti_buon_senso") val limitiBuonSenso: PlausibilityLimits? = null,
    val confidenza: Confidence,
)

/** Grado di occlusione di un alimento, che aumenta l'incertezza sulla stima geometrica. */
@Serializable
enum class OcclusionDegree {
    @SerialName("nessuna") NESSUNA,
    @SerialName("parziale") PARZIALE,
    @SerialName("elevata") ELEVATA,
}

/**
 * Misure grezze osservabili di un alimento (§5 M1): frazioni di superficie, altezze, conteggi,
 * riempimenti, dimensioni geometriche. **Mai grammi.** Tutti i campi sono opzionali: M1 riempie
 * solo quelli pertinenti alla forma osservata, e M3 usa quelli richiesti dalla strategia scelta.
 */
@Serializable
data class RawMeasurements(
    /** Contabile/Blocco: numero di pezzi contati. */
    @SerialName("conteggio") val conteggio: Int? = null,

    /** Distesa: frazione della superficie del piatto coperta [0..1]. */
    @SerialName("frazione_piatto") val frazionePiatto: Double? = null,
    /** Distesa/Blocco: altezza o spessore del cumulo in centimetri. */
    @SerialName("altezza_cm") val altezzaCm: Double? = null,
    /** Distesa: quanto è "pieno/compatto" il cumulo [0..1], distinto dalla superficie coperta. */
    @SerialName("riempimento") val riempimento: Double? = null,

    /** A contenitore: tipo di contenitore riconosciuto (bicchiere, tazza, ciotola, scodella…). */
    @SerialName("contenitore") val contenitore: String? = null,
    /** A contenitore: frazione di riempimento del contenitore [0..1]. */
    @SerialName("frazione_riempimento") val frazioneRiempimento: Double? = null,

    /** Blocco misurabile: forma geometrica dichiarata (parallelepipedo, cilindro, cuneo…). */
    @SerialName("forma") val forma: String? = null,
    @SerialName("lunghezza_cm") val lunghezzaCm: Double? = null,
    @SerialName("larghezza_cm") val larghezzaCm: Double? = null,
    @SerialName("diametro_cm") val diametroCm: Double? = null,

    /** Nota libera di percezione, non usata nel calcolo ma utile alla spiegabilità e al debug. */
    @SerialName("nota") val nota: String = "",
)

/** Relazione "un alimento sta sopra un altro", per stimare le porzioni occluse (§5 M4). */
@Serializable
data class OverlapRelation(
    val sopra: String,
    val sotto: String,
)
