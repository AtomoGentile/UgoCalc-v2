package com.ugocalc.app.domain.inference

import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.model.VisionAnalysisResult

/**
 * Adattatore tra [PipelineOutcome.Verificato] (uscita della pipeline V2) e i contratti che il
 * resto dell'app — schermata risultati, schermata finale, storico — già consuma per la V1
 * (piano V2 §13 punto 1, guida migrazione §3): `ModelVisionOutcome`/`RecognizedFood` per la lista
 * pesi e `AnalysisTotals`/`CalculatedFoodLine` per i totali CHO/grassi già calcolati da M6/M7
 * dentro la pipeline. Nessuna nuova chiamata AI: qui si rimappano solo dati già pronti.
 */
fun PipelineOutcome.Verificato.toModelVisionSuccess(model: AiModel): ModelVisionOutcome.Success {
    val confidenzaGlobalePeso = computeWeightedAverageConfidence(stime)
    val alimenti = stime.map { stima ->
        RecognizedFood(
            nome = stima.nome,
            // L'utente pesa a crudo: mostriamo pesoCrudoG come peso di riferimento.
            pesoStimatoG = stima.pesoCrudoG,
            commento = stima.catenaCalcolo.sintesi,
            ragionamento = stima.ragionamento,
            confidenza = Confidence.fromScore(stima.confidenza),
            confidenzaPeso = stima.confidenza,
            usoDefaultFisico = stima.usoDefaultFisico,
        )
    }
    val result = VisionAnalysisResult(
        alimenti = alimenti,
        ragionamento = sceneGraph.ragionamento,
        confidenzaGlobale = Confidence.ALTO,
        confidenzaGlobalePeso = confidenzaGlobalePeso,
        fattoriIncertezza = verifica.flaggati.map { it.motivo },
        stimeCautelative = verifica.ambiguitaRisolta.map { it.nomeScelto },
    )
    return ModelVisionOutcome.Success(model, result)
}

fun PipelineOutcome.Verificato.toAnalysisTotals(): AnalysisTotals {
    val lines = stime.map { stima ->
        val recognizedFood = RecognizedFood(
            nome = stima.nome,
            pesoStimatoG = stima.pesoCrudoG,
            commento = stima.catenaCalcolo.sintesi,
            confidenza = Confidence.fromScore(stima.confidenza),
            confidenzaPeso = stima.confidenza,
            isTrascurabile = stima.strategia == com.ugocalc.app.domain.inference.model.EstimationStrategy.TRASCURABILE,
            usoDefaultFisico = stima.usoDefaultFisico,
        )
        val entry = abbinamenti[stima.nome]
        val match = if (entry != null) {
            FoodMatch(
                nomeRiconosciuto = stima.nome,
                voceTabellaAbbinata = entry.nome,
                nessunaCorrispondenza = false,
                confidenzaAbbinamento = Confidence.fromScore(stima.confidenza),
            )
        } else {
            null
        }
        CalculatedFoodLine(recognizedFood = recognizedFood, match = match, tableEntry = entry)
    }
    return AnalysisTotals(lines)
}

private fun computeWeightedAverageConfidence(estimates: List<com.ugocalc.app.domain.inference.model.WeightEstimate>): ConfidenceScore {
    if (estimates.isEmpty()) return ConfidenceScore(50)

    val totalWeight = estimates.sumOf { it.pesoCrudoG }
    if (totalWeight <= 0.0) return ConfidenceScore(50)

    val weightedSum = estimates.sumOf { it.confidenza.percent * it.pesoCrudoG }
    val average = (weightedSum / totalWeight).toInt().coerceIn(0, 100)
    return ConfidenceScore(average)
}
