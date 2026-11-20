package com.ugocalc.app.data.remote.barcode

import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Errore di rete o trasporto durante una richiesta verso OpenFoodFacts. */
class OpenFoodFactsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Database pubblico di prodotti confezionati (§ richiesta utente, scanner codici a barre):
 * gratuito, senza API key, coperto in gran parte dagli stessi produttori/utenti che caricano le
 * etichette. Nessuna garanzia di copertura completa — da qui il fallback AI in [BarcodeRepository]
 * per i codici assenti dal database.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor(
    @Named("no_auth") private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /** Lookup diretto per codice a barre scansionato. Null se il prodotto non è nel database o manca CHO/grassi. */
    suspend fun lookupBarcode(barcode: String): NormalizedFoodEntry? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments")
            .header("User-Agent", "UgoCalc-Android - app personale (github.com/AtomoGentile/UgoCalc)")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseProductResponse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw OpenFoodFactsException(
                "Impossibile contattare OpenFoodFacts: verifica la connessione Internet.",
                e,
            )
        }
    }

    /**
     * Ricerca per nome (§ richiesta utente: "cibi preconfezionati con etichetta non disponibile
     * all'utente"), quando non si ha il codice a barre sotto mano.
     */
    suspend fun searchByName(query: String, limit: Int = 10): List<NormalizedFoodEntry> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(
                "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1" +
                    "&action=process&json=1&page_size=$limit&fields=product_name,nutriments"
            )
            .header("User-Agent", "UgoCalc-Android - app personale (github.com/AtomoGentile/UgoCalc)")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parseSearchResponse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw OpenFoodFactsException(
                "Impossibile contattare OpenFoodFacts: verifica la connessione Internet.",
                e,
            )
        }
    }

    internal fun parseProductResponse(body: String): NormalizedFoodEntry? {
        val root = parseOrNull(body) ?: return null
        if ((root["status"] as? JsonPrimitive)?.intOrNull != 1) return null
        return (root["product"] as? JsonObject)?.let(::toEntry)
    }

    internal fun parseSearchResponse(body: String): List<NormalizedFoodEntry> {
        val root = parseOrNull(body) ?: return emptyList()
        return (root["products"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::toEntry) }
    }

    private fun parseOrNull(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private fun toEntry(product: JsonObject): NormalizedFoodEntry? {
        val name = (product["product_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val nutriments = product["nutriments"] as? JsonObject ?: return null
        val cho = (nutriments["carbohydrates_100g"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return null
        val fat = (nutriments["fat_100g"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return null
        return NormalizedFoodEntry(name, cho, fat)
    }
}
