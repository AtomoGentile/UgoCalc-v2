package com.ugocalc.app.domain.usecase

import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.dto.MatchingInputFoodDto
import com.ugocalc.app.data.remote.ai.dto.MatchingInputTableEntryDto
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.mapEntryToDomain
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Passaggio 3 (§5): per ciascun alimento riconosciuto cerca un abbinamento, provando le tabelle
 * ufficiali in ordine di priorità (§2.2). Se per una tabella il risultato è
 * `nessuna_corrispondenza: true`, l'alimento resta "in sospeso" e viene ritentato sulla tabella
 * successiva, finché non trova un abbinamento o le tabelle si esauriscono (§5) — a quel punto
 * l'alimento è escluso dal calcolo (§5 "Gestione delle non corrispondenze").
 */
class MatchFoodsAcrossTablesUseCase @Inject constructor(
    private val tableRepository: TableRepository,
    private val aiOrchestrator: AiOrchestrator,
    private val json: Json,
) {
    suspend operator fun invoke(recognizedFoods: List<RecognizedFood>, referenceModel: AiModel): List<CalculatedFoodLine> {
        if (recognizedFoods.isEmpty()) return emptyList()

        val tables = tableRepository.getAllTablesByPriority()
            .filter { it.normalizationStatus == NormalizationStatus.DONE }

        val pending = recognizedFoods.toMutableList()
        val resolved = mutableMapOf<RecognizedFood, Pair<FoodMatch, NormalizedFoodEntry>>()

        for (table in tables) {
            if (pending.isEmpty()) break
            val tableEntries = tableRepository.getEntriesForTable(table.id).map { mapEntryToDomain(it) }
            if (tableEntries.isEmpty()) continue

            val listaAlimentiJson = json.encodeToString(pending.map { MatchingInputFoodDto(it.nome, it.pesoStimatoG) })
            val tabellaJson = json.encodeToString(tableEntries.map { MatchingInputTableEntryDto(it.nome, it.choPer100g, it.grassiPer100g) })

            val matchingResult = try {
                aiOrchestrator.runMatching(referenceModel, listaAlimentiJson, tabellaJson)
            } catch (e: AiCallException) {
                continue // questa tabella non è utilizzabile per il matching, si prova la successiva
            }

            val stillPending = mutableListOf<RecognizedFood>()
            for (food in pending) {
                val match = matchingResult.abbinamenti.firstOrNull { normalize(it.nomeRiconosciuto) == normalize(food.nome) }
                if (match == null || match.nessunaCorrispondenza) {
                    stillPending += food
                    continue
                }
                val entry = tableEntries.firstOrNull { normalize(it.nome) == normalize(match.voceTabellaAbbinata) }
                if (entry == null) {
                    stillPending += food
                } else {
                    resolved[food] = match to entry
                }
            }
            pending.clear()
            pending += stillPending
        }

        return recognizedFoods.map { food ->
            val resolution = resolved[food]
            CalculatedFoodLine(
                recognizedFood = food,
                match = resolution?.first ?: if (food in pending) noMatchPlaceholder(food) else null,
                tableEntry = resolution?.second,
            )
        }
    }

    private fun noMatchPlaceholder(food: RecognizedFood) = FoodMatch(
        nomeRiconosciuto = food.nome,
        voceTabellaAbbinata = "",
        nessunaCorrispondenza = true,
        confidenzaAbbinamento = Confidence.BASSO,
    )

    private fun normalize(value: String) = value.trim().lowercase()
}
