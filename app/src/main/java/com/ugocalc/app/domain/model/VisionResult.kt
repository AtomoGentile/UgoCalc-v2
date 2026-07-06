package com.ugocalc.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Output grezzo del Passaggio 1 (Visione) per UN alimento, così come torna dal modello AI (§3). */
@Serializable
data class RecognizedFood(
    val nome: String,
    @SerialName("peso_stimato_g") val pesoStimatoG: Double,
    val commento: String,
    val confidenza: Confidence,
)

/** Output completo del Passaggio 1 per un singolo modello AI (§3, schema JSON). */
@Serializable
data class VisionAnalysisResult(
    val alimenti: List<RecognizedFood>,
    val confidenzaGlobale: Confidence,
    val fattoriIncertezza: List<String> = emptyList(),
    val avvertenze: String = "",
    /**
     * Nomi degli alimenti su cui è stata applicata la regola di sicurezza "CHO più alto" (§13 punto
     * 2): l'identificazione era ambigua, la pipeline ha scelto l'alternativa con più carboidrati.
     * Mostrato in UI come avviso di stima cautelativa da confermare.
     */
    val stimeCautelative: List<String> = emptyList(),
)

/**
 * Esito del Passaggio 1 per un modello selezionato, incluso il caso di fallimento (§2.4 punto 4,
 * §3 "Gestione del fallimento"): l'app deve poter mostrare "Analisi non riuscita" senza bloccare
 * gli altri modelli, quindi il risultato è modellato come sealed class per modello.
 */
sealed class ModelVisionOutcome {
    abstract val model: AiModel

    data class Success(
        override val model: AiModel,
        val result: VisionAnalysisResult,
    ) : ModelVisionOutcome()

    data class Failure(
        override val model: AiModel,
        val reason: String,
    ) : ModelVisionOutcome()
}
