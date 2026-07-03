package com.ugocalc.app.data.repository

import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsClient
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import javax.inject.Inject
import javax.inject.Singleton

sealed class BarcodeLookupResult {
    data class Found(val entry: NormalizedFoodEntry, val source: String) : BarcodeLookupResult()
    data object NotFound : BarcodeLookupResult()
    data class Error(val message: String) : BarcodeLookupResult()
}

/**
 * Scanner codici a barre (§ richiesta utente): prima cerca su OpenFoodFacts (database pubblico
 * gratuito di prodotti confezionati), poi — se il codice non c'è — prova con la ricerca online AI
 * già usata per gli alimenti mancanti dalle tabelle (oggi funzionante solo con Gemini, unico
 * provider con accesso web reale, vedi GeminiCompletionClient).
 */
@Singleton
class BarcodeRepository @Inject constructor(
    private val openFoodFactsClient: OpenFoodFactsClient,
    private val aiOrchestrator: AiOrchestrator,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun lookupBarcode(barcode: String): BarcodeLookupResult {
        val fromDatabase = openFoodFactsClient.lookupBarcode(barcode)
        if (fromDatabase != null) return BarcodeLookupResult.Found(fromDatabase, "OpenFoodFacts")
        return aiFallback("prodotto confezionato con codice a barre EAN $barcode")
    }

    /** Ricerca per nome (§ richiesta utente: "etichetta non disponibile all'utente"), senza passare dallo scanner. */
    suspend fun searchByName(query: String): BarcodeLookupResult {
        val fromDatabase = openFoodFactsClient.searchByName(query).firstOrNull()
        if (fromDatabase != null) return BarcodeLookupResult.Found(fromDatabase, "OpenFoodFacts")
        return aiFallback(query)
    }

    private suspend fun aiFallback(query: String): BarcodeLookupResult {
        val geminiModel = AiModelCatalog.ALL.firstOrNull {
            it.provider == AiProviderType.GEMINI && settingsRepository.getApiKey(it.provider) != null
        } ?: return BarcodeLookupResult.NotFound

        return try {
            val result = aiOrchestrator.runOnlineFoodResearch(geminiModel, query)
            val cho = result.choPer100g
            val grassi = result.grassiPer100g
            if (result.haDatiUtilizzabili && cho != null && grassi != null) {
                BarcodeLookupResult.Found(NormalizedFoodEntry(result.alimento, cho, grassi), "Ricerca AI")
            } else {
                BarcodeLookupResult.NotFound
            }
        } catch (e: AiCallException) {
            BarcodeLookupResult.Error(e.message ?: "Ricerca non riuscita")
        }
    }
}
