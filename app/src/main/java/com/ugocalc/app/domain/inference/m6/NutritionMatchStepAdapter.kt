package com.ugocalc.app.domain.inference.m6

import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import javax.inject.Inject

/**
 * M6 – Nutrition Engine (piano V2 §5, §14): resta quello della V1 ([MatchFoodsAcrossTablesUseCase]),
 * nessuna riscrittura. Questo è l'unico punto di innesto tra la pipeline V2 e il motore
 * nutrizionale esistente (guida migrazione §3): converte le [WeightEstimate] di M3/M4/M5 — peso
 * deterministico + catena di calcolo — nel contratto [RecognizedFood] che matching, calcolo
 * CHO/grassi e storico già consumano senza modifiche, qualunque sia la provenienza del peso.
 *
 * Un alimento senza abbinamento resta fuori dalla mappa restituita: [com.ugocalc.app.domain.inference.m7.NutritionCalculator]
 * lo tratta già come escluso dal totale, mai stimato a zero in silenzio (stessa regola della V1).
 */
class NutritionMatchStepAdapter @Inject constructor(
    private val matchFoodsAcrossTables: MatchFoodsAcrossTablesUseCase,
) {
    suspend fun match(model: AiModel, stime: List<WeightEstimate>): Map<String, NormalizedFoodEntry> {
        val recognizedFoods = stime.map {
            RecognizedFood(
                nome = it.nome,
                pesoStimatoG = it.pesoG,
                commento = it.catenaCalcolo.sintesi,
                confidenza = it.confidenza,
            )
        }
        val lines = matchFoodsAcrossTables(recognizedFoods, model)
        return lines.mapNotNull { line ->
            val entry = line.tableEntry
            if (line.isExcluded || entry == null) null else line.recognizedFood.nome to entry
        }.toMap()
    }
}
