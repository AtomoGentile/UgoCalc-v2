package com.ugocalc.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ugocalc.app.data.remote.aws.RemoteConfigClient
import com.ugocalc.app.data.remote.aws.RemoteConfigResult
import com.ugocalc.app.data.remote.aws.getBoolean
import com.ugocalc.app.data.remote.aws.getInt
import com.ugocalc.app.data.remote.aws.getString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remoteConfigDataStore by preferencesDataStore(name = "ugocalc_remote_config")

data class RemoteConfig(
    val minBenchmarkMeals: Int = 30,
    val v2ShadowEnabled: Boolean = false,
    val maintenanceMessage: String = "",
    val forceUpdateMinVersion: String = "",
)

private val KEY_CACHED_JSON = stringPreferencesKey("cached_json")
private val KEY_FETCHED_AT = longPreferencesKey("fetched_at_ms")
private const val TTL_MS = 4 * 60 * 60 * 1000L // 4 hours

@Singleton
class RemoteConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: RemoteConfigClient,
    private val json: Json,
) {
    private val dataStore = context.remoteConfigDataStore

    suspend fun getConfig(): RemoteConfig {
        val prefs = dataStore.data.first()
        val cachedJson = prefs[KEY_CACHED_JSON]
        val fetchedAt = prefs[KEY_FETCHED_AT] ?: 0L
        val now = System.currentTimeMillis()

        val parsed: JsonObject? = if (cachedJson != null && (now - fetchedAt) < TTL_MS) {
            runCatching { json.parseToJsonElement(cachedJson) as? JsonObject }.getOrNull()
        } else {
            null
        }

        if (parsed != null) return parsed.toConfig()

        return when (val result = client.fetch()) {
            is RemoteConfigResult.Success -> {
                dataStore.edit { prefs ->
                    prefs[KEY_CACHED_JSON] = result.raw.toString()
                    prefs[KEY_FETCHED_AT] = now
                }
                result.raw.toConfig()
            }
            is RemoteConfigResult.NetworkError, is RemoteConfigResult.ParseError -> {
                cachedJson
                    ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()?.toConfig() }
                    ?: RemoteConfig()
            }
        }
    }

    suspend fun forceRefresh(): RemoteConfig {
        dataStore.edit { it.remove(KEY_FETCHED_AT) }
        return getConfig()
    }

    private fun JsonObject.toConfig() = RemoteConfig(
        minBenchmarkMeals = getInt("min_benchmark_meals", 30),
        v2ShadowEnabled = getBoolean("v2_shadow_enabled", false),
        maintenanceMessage = getString("maintenance_message", ""),
        forceUpdateMinVersion = getString("force_update_min_version", ""),
    )
}
