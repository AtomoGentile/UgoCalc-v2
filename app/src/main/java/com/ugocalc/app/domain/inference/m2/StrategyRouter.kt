package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.PerceivedFood
import com.ugocalc.app.domain.inference.model.RawMeasurements
import com.ugocalc.app.domain.inference.model.SceneGraph
import java.text.Normalizer

/**
 * M2 – Router di strategia (piano V2 §5). Modulo deterministico che assegna a ogni alimento
 * riconosciuto una delle sei [EstimationStrategy], tramite una tabella locale nome→strategia
 * ([StrategyRules]).
 *
 * È il punto di estensione principale del sistema (§5, §14): aggiungere o correggere una regola non
 * richiede di toccare i prompt AI, solo il file dati delle regole. Per questo la tabella è iniettata
 * e sostituibile, e i test verificano il comportamento contro un insieme di regole fisso.
 */
class StrategyRouter(
    private val rules: List<StrategyRule> = StrategyRules.DEFAULT,
    private val fallback: EstimationStrategy = StrategyRules.FALLBACK,
) {

    /** Assegna la strategia a un singolo nome di alimento. La prima regola che combacia vince. */
    fun route(nome: String): StrategyAssignment {
        val normalizzato = normalize(nome)
        val regola = rules.firstOrNull { rule -> rule.keywords.any { matchesWholeWord(normalizzato, it) } }
        return if (regola != null) {
            StrategyAssignment(nome = nome, strategia = regola.strategia, abbinamentoEsplicito = true)
        } else {
            StrategyAssignment(nome = nome, strategia = fallback, abbinamentoEsplicito = false)
        }
    }

    /**
     * Assegna la strategia a un alimento percepito. Se il nome non combacia con nessuna regola,
     * il fallback non è fisso ma è la strategia più prudente **compatibile con le misure
     * disponibili** (§5 M2): un alimento sconosciuto ma contato va da Contabile, uno in un
     * contenitore va da A contenitore, ecc. L'assegnazione resta marcata come non esplicita,
     * così la confidenza a valle viene abbassata.
     */
    fun route(food: PerceivedFood): StrategyAssignment {
        val perNome = route(food.nome)
        if (perNome.abbinamentoEsplicito) return perNome
        return perNome.copy(strategia = prudentFallback(food.misureGrezze))
    }

    /**
     * Assegna la strategia a tutti gli alimenti di uno [SceneGraph], preservandone l'ordine.
     * Rifiuta esplicitamente (errore gestito, non crash) uno SceneGraph con `schema_version`
     * non riconosciuta (§7 "Versionamento dello schema"): prompt e data class devono evolvere
     * insieme, e un disallineamento deve emergere subito, non come deserializzazione parziale.
     */
    fun route(scene: SceneGraph): SceneRoutingResult {
        if (scene.schemaVersion != SceneGraph.SCHEMA_VERSION) {
            return SceneRoutingResult.SchemaNonSupportato(
                trovata = scene.schemaVersion,
                attesa = SceneGraph.SCHEMA_VERSION,
            )
        }
        return SceneRoutingResult.Ok(scene.alimenti.map { route(it) })
    }

    /** Strategia più prudente compatibile con le misure grezze effettivamente presenti. */
    private fun prudentFallback(m: RawMeasurements): EstimationStrategy = when {
        m.conteggio != null -> EstimationStrategy.CONTABILE
        m.contenitore != null || m.frazioneRiempimento != null -> EstimationStrategy.A_CONTENITORE
        m.frazionePiatto != null -> EstimationStrategy.DISTESA_SUL_PIATTO
        m.diametroCm != null || (m.lunghezzaCm != null && m.larghezzaCm != null) ->
            EstimationStrategy.BLOCCO_MISURABILE
        else -> fallback
    }

    private fun matchesWholeWord(nomeNormalizzato: String, keyword: String): Boolean {
        val kw = normalize(keyword)
        if (kw.isEmpty()) return false
        return Regex("\\b" + Regex.escape(kw) + "\\b").containsMatchIn(nomeNormalizzato)
    }

    companion object {
        /**
         * Normalizza un nome per il confronto: minuscolo, senza accenti/diacritici, spazi ridotti.
         * Rende il match indipendente da "Purè" vs "pure", "Caffè" vs "caffe", maiuscole, ecc.
         */
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\s+"), " ")
    }
}

/**
 * Esito dell'assegnazione di strategia per un alimento. [abbinamentoEsplicito] è falso quando
 * nessuna regola ha combaciato ed è stato usato il fallback: a valle questo giustifica una
 * confidenza più bassa, perché la strategia è una supposizione, non una corrispondenza nota.
 */
data class StrategyAssignment(
    val nome: String,
    val strategia: EstimationStrategy,
    val abbinamentoEsplicito: Boolean,
)

/** Una regola della tabella: se il nome contiene una delle [keywords] (a parola intera), usa [strategia]. */
data class StrategyRule(
    val keywords: List<String>,
    val strategia: EstimationStrategy,
)

/**
 * Esito del routing di un intero [SceneGraph] (§7 "Versionamento dello schema"). [Ok] porta
 * l'assegnazione per ciascun alimento; [SchemaNonSupportato] segnala che lo schema dichiarato da M1
 * non è quello atteso dalla pipeline corrente — errore gestito esplicitamente, non un crash o una
 * deserializzazione parziale.
 */
sealed interface SceneRoutingResult {
    data class Ok(val assegnazioni: List<StrategyAssignment>) : SceneRoutingResult
    data class SchemaNonSupportato(val trovata: Int, val attesa: Int) : SceneRoutingResult
}
