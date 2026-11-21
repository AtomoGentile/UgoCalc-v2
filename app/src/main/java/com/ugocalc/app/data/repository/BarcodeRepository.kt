package com.ugocalc.app.data.repository

import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsClient
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsException
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class BarcodeLookupResult {
    data class Found(val entry: NormalizedFoodEntry, val source: String) : BarcodeLookupResult()
    data object NotFound : BarcodeLookupResult()
    data class Error(val message: String) : BarcodeLookupResult()
}

sealed class BarcodeSearchListResult {
    data class FoundList(val entries: List<NormalizedFoodEntry>, val source: String) : BarcodeSearchListResult()
    data object NotFound : BarcodeSearchListResult()
    data class Error(val message: String) : BarcodeSearchListResult()
}

/**
 * Scanner codici a barre (§ richiesta utente): prima cerca su OpenFoodFacts (database pubblico
 * gratuito di prodotti confezionati), poi — se il codice non c'è — prova con la ricerca online AI
 * usando Gemini o Anthropic (in base alle API key configurate).
 */
@Singleton
class BarcodeRepository @Inject constructor(
    private val openFoodFactsClient: OpenFoodFactsClient,
    private val aiOrchestrator: AiOrchestrator,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun lookupBarcode(barcode: String): BarcodeLookupResult {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return BarcodeLookupResult.NotFound
        val fromDatabase = try {
            openFoodFactsClient.lookupBarcode(trimmed)
        } catch (e: OpenFoodFactsException) {
            val fallback = aiFallback("prodotto confezionato con codice a barre EAN $trimmed")
            return if (fallback is BarcodeLookupResult.Found) fallback
            else BarcodeLookupResult.Error(e.message ?: "Errore di rete su OpenFoodFacts")
        } catch (e: IOException) {
            return BarcodeLookupResult.Error("Errore di rete durante la ricerca su OpenFoodFacts.")
        }
        if (fromDatabase != null) return BarcodeLookupResult.Found(fromDatabase, "OpenFoodFacts")
        return aiFallback("prodotto confezionato con codice a barre EAN $trimmed")
    }

    /** Ricerca per nome (§ richiesta utente: "etichetta non disponibile all'utente"), senza passare dallo scanner. */
    suspend fun searchByName(query: String): BarcodeLookupResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return BarcodeLookupResult.NotFound
        val fromDatabase = try {
            openFoodFactsClient.searchByName(trimmed).firstOrNull()
        } catch (e: OpenFoodFactsException) {
            val fallback = aiFallback(trimmed)
            return if (fallback is BarcodeLookupResult.Found) fallback
            else BarcodeLookupResult.Error(e.message ?: "Errore di rete su OpenFoodFacts")
        } catch (e: IOException) {
            return BarcodeLookupResult.Error("Errore di rete durante la ricerca su OpenFoodFacts.")
        }
        if (fromDatabase != null) return BarcodeLookupResult.Found(fromDatabase, "OpenFoodFacts")
        return aiFallback(trimmed)
    }

    /**
     * Ricerca per nome che restituisce i migliori prodotti corrispondenti da OpenFoodFacts
     * (con fallback AI su Gemini o Anthropic se nessun prodotto viene trovato).
     */
    suspend fun searchByNameList(query: String, limit: Int = 10): BarcodeSearchListResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return BarcodeSearchListResult.NotFound
        val fromDatabase = try {
            openFoodFactsClient.searchByName(trimmed, limit)
        } catch (e: OpenFoodFactsException) {
            return when (val fallback = aiFallback(trimmed)) {
                is BarcodeLookupResult.Found -> BarcodeSearchListResult.FoundList(listOf(fallback.entry), fallback.source)
                else -> BarcodeSearchListResult.Error(e.message ?: "Errore di rete su OpenFoodFacts")
            }
        } catch (e: IOException) {
            return BarcodeSearchListResult.Error("Errore di rete durante la ricerca su OpenFoodFacts.")
        }
        if (fromDatabase.isNotEmpty()) {
            return BarcodeSearchListResult.FoundList(fromDatabase, "OpenFoodFacts")
        }
        return when (val fallback = aiFallback(trimmed)) {
            is BarcodeLookupResult.Found -> BarcodeSearchListResult.FoundList(listOf(fallback.entry), fallback.source)
            is BarcodeLookupResult.NotFound -> BarcodeSearchListResult.NotFound
            is BarcodeLookupResult.Error -> BarcodeSearchListResult.Error(fallback.message)
        }
    }

    private suspend fun aiFallback(query: String): BarcodeLookupResult {
        val model = AiModelCatalog.ALL.firstOrNull {
            it.provider == AiProviderType.GEMINI && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
        } ?: AiModelCatalog.ALL.firstOrNull {
            it.provider == AiProviderType.ANTHROPIC && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
        } ?: return BarcodeLookupResult.NotFound

        return try {
            val result = aiOrchestrator.runOnlineFoodResearch(model, query)
            val cho = result.choPer100g
            val grassi = result.grassiPer100g
            if (result.haDatiUtilizzabili && cho != null && grassi != null) {
                BarcodeLookupResult.Found(
                    NormalizedFoodEntry(
                        nome = result.alimento,
                        choPer100g = cho,
                        grassiPer100g = grassi,
                        proteinePer100g = result.proteinePer100g ?: 0.0,
                        indiceGlicemico = result.indiceGlicemico?.coerceIn(0, 100),
                    ),
                    "Ricerca AI",
                )
            } else {
                BarcodeLookupResult.NotFound
            }
        } catch (e: AiCallException) {
            BarcodeLookupResult.Error(e.message ?: "Ricerca non riuscita")
        }
    }
}
