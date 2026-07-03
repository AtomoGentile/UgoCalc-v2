package com.ugocalc.app.data.remote.barcode

import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database pubblico di prodotti confezionati (§ richiesta utente, scanner codici a barre):
 * gratuito, senza API key, coperto in gran parte dagli stessi produttori/utenti che caricano le
 * etichette. Nessuna garanzia di copertura completa — da qui il fallback AI in [BarcodeRepository]
 * per i codici assenti dal database.
 */
@Singleton
class OpenFoodFactsClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /** Lookup diretto per codice a barre scansionato. Null se il prodotto non è nel database o manca CHO/grassi. */
    suspend fun lookupBarcode(barcode: String): NormalizedFoodEntry? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments")
            .header("User-Agent", "UgoCalc-Android - app personale (github.com/AtomoGentile/UgoCalc)")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = parseOrNull(response.body?.string().orEmpty()) ?: return@use null
            if (root["status"]?.jsonPrimitive?.intOrNull != 1) return@use null
            root["product"]?.jsonObject?.let(::toEntry)
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
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val root = parseOrNull(response.body?.string().orEmpty()) ?: return@use emptyList()
            root["products"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.let(::toEntry) }
        }
    }

    private fun parseOrNull(body: String): JsonObject? = try {
        json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
        null
    }

    private fun toEntry(product: JsonObject): NormalizedFoodEntry? {
        val name = product["product_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val nutriments = product["nutriments"]?.jsonObject ?: return null
        val cho = nutriments["carbohydrates_100g"]?.jsonPrimitive?.doubleOrNull ?: return null
        val fat = nutriments["fat_100g"]?.jsonPrimitive?.doubleOrNull ?: return null
        return NormalizedFoodEntry(name, cho, fat)
    }
}
