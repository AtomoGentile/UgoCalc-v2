package com.ugocalc.app.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ugocalc.app.domain.model.Confidence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ugocalc_settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

data class UserSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val textScale: Float = 1.0f,
    /** "Modelli preferiti": se non vuoto, salta del tutto la schermata di scelta modello (§2.2) e avvia l'analisi con questi. */
    val preferredModelIds: Set<String> = emptySet(),
    /** "Livello di confidenza" attivo/non attivo e relativa soglia (§2.2, default 70%). */
    val confidenceIndicatorEnabled: Boolean = false,
    val confidenceThresholdPercent: Int = Confidence.DEFAULT_THRESHOLD_PERCENT,
    val notificationsEnabled: Boolean = true,
    /** Mostra la sezione "Preferiti" in Homepage (§2.2, voce di attivazione richiesta dall'utente). */
    val favoritesEnabled: Boolean = true,
    /** Colori personalizzati (§ richiesta utente): null = usa il colore predefinito del tema. Valori ARGB. */
    val primaryColorArgb: Int? = null,
    val secondaryColorArgb: Int? = null,
    val tertiaryColorArgb: Int? = null,
    val backgroundColorArgb: Int? = null,
    /** "Colore di errore" (§ richiesta utente, es. pulsante Analizza rosso intenso se offline). */
    val errorColorArgb: Int? = null,
    /** Grafico a colonne CHO/grassi per pasto nella Cronologia (§ richiesta utente). */
    val historyChartEnabled: Boolean = true,
    /** Famiglia di carattere personalizzata (§ richiesta utente "colore, carattere se riesci"): null = predefinito di sistema. */
    val fontFamilyName: String? = null,
    val fontColorArgb: Int? = null,
    /** Disclaimer sull'utilizzo dei dati (§ richiesta utente): mostrato una sola volta al primo avvio. */
    val hasSeenDataDisclaimer: Boolean = false,
    /** Popup "Novità" (§ richiesta utente): 0 = mai avviata, altrimenti l'ultimo versionCode per cui l'utente ha già visto le novità. */
    val lastSeenVersionCode: Int = 0,
    /**
     * Consenso esplicito al trattamento delle correzioni manuali per il calcolo dei fattori di
     * correzione del bias (piano V2 §8, richiesta utente): resta su tutto il dispositivo, non lascia
     * mai il telefono, ma è comunque un trattamento aggiuntivo dei dati inseriti dall'utente
     * (i pesi corretti a mano), quindi richiede un consenso separato dal disclaimer generale.
     * Falso di default: finché non è attivo, [com.ugocalc.app.data.repository.BiasCorrectionRepository]
     * non calcola né salva alcun fattore.
     */
    val biasDataConsentGiven: Boolean = false,
    /**
     * Consenso dedicato alla modalità ombra V2 (piano V2 §13 punto 1, §10 Fase 2): separato dal
     * consenso bias perché qui, oltre al trattamento dati, c'è un costo reale — attivarlo fa partire
     * 2 chiamate AI aggiuntive per analisi (M1 + M6 della pipeline V2), a pagamento con la chiave API
     * dell'utente, solo per raccogliere dati di validazione. V1 resta comunque l'unica pipeline
     * mostrata. Falso di default.
     */
    val v2ShadowModeEnabled: Boolean = false,
)

@Singleton
class SettingsDataStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val PREFERRED_MODEL_IDS = stringSetPreferencesKey("preferred_model_ids")
        val CONFIDENCE_ENABLED = booleanPreferencesKey("confidence_enabled")
        val CONFIDENCE_THRESHOLD = intPreferencesKey("confidence_threshold")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val FAVORITES_ENABLED = booleanPreferencesKey("favorites_enabled")
        val PRIMARY_COLOR = intPreferencesKey("primary_color_argb")
        val SECONDARY_COLOR = intPreferencesKey("secondary_color_argb")
        val TERTIARY_COLOR = intPreferencesKey("tertiary_color_argb")
        val BACKGROUND_COLOR = intPreferencesKey("background_color_argb")
        val ERROR_COLOR = intPreferencesKey("error_color_argb")
        val HISTORY_CHART_ENABLED = booleanPreferencesKey("history_chart_enabled")
        val FONT_FAMILY = stringPreferencesKey("font_family_name")
        val FONT_COLOR = intPreferencesKey("font_color_argb")
        val HAS_SEEN_DATA_DISCLAIMER = booleanPreferencesKey("has_seen_data_disclaimer")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        val BIAS_DATA_CONSENT_GIVEN = booleanPreferencesKey("bias_data_consent_given")
        val V2_SHADOW_MODE_ENABLED = booleanPreferencesKey("v2_shadow_mode_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            textScale = prefs[Keys.TEXT_SCALE]?.toFloatOrNull() ?: 1.0f,
            preferredModelIds = prefs[Keys.PREFERRED_MODEL_IDS] ?: emptySet(),
            confidenceIndicatorEnabled = prefs[Keys.CONFIDENCE_ENABLED] ?: false,
            confidenceThresholdPercent = prefs[Keys.CONFIDENCE_THRESHOLD] ?: Confidence.DEFAULT_THRESHOLD_PERCENT,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            favoritesEnabled = prefs[Keys.FAVORITES_ENABLED] ?: true,
            primaryColorArgb = prefs[Keys.PRIMARY_COLOR],
            secondaryColorArgb = prefs[Keys.SECONDARY_COLOR],
            tertiaryColorArgb = prefs[Keys.TERTIARY_COLOR],
            backgroundColorArgb = prefs[Keys.BACKGROUND_COLOR],
            errorColorArgb = prefs[Keys.ERROR_COLOR],
            historyChartEnabled = prefs[Keys.HISTORY_CHART_ENABLED] ?: true,
            fontFamilyName = prefs[Keys.FONT_FAMILY],
            fontColorArgb = prefs[Keys.FONT_COLOR],
            hasSeenDataDisclaimer = prefs[Keys.HAS_SEEN_DATA_DISCLAIMER] ?: false,
            lastSeenVersionCode = prefs[Keys.LAST_SEEN_VERSION_CODE] ?: 0,
            biasDataConsentGiven = prefs[Keys.BIAS_DATA_CONSENT_GIVEN] ?: false,
            v2ShadowModeEnabled = prefs[Keys.V2_SHADOW_MODE_ENABLED] ?: false,
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale.toString() }
    suspend fun setPreferredModelIds(ids: Set<String>) = context.dataStore.edit { it[Keys.PREFERRED_MODEL_IDS] = ids }
    suspend fun setConfidenceIndicatorEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CONFIDENCE_ENABLED] = enabled }
    suspend fun setConfidenceThresholdPercent(percent: Int) =
        context.dataStore.edit { it[Keys.CONFIDENCE_THRESHOLD] = percent.coerceIn(0, 100) }
    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    suspend fun setFavoritesEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FAVORITES_ENABLED] = enabled }

    /** Colore personalizzato per lo slot indicato, oppure `null` per tornare al predefinito del tema. */
    suspend fun setPrimaryColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.PRIMARY_COLOR) else it[Keys.PRIMARY_COLOR] = argb
    }
    suspend fun setSecondaryColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.SECONDARY_COLOR) else it[Keys.SECONDARY_COLOR] = argb
    }
    suspend fun setTertiaryColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.TERTIARY_COLOR) else it[Keys.TERTIARY_COLOR] = argb
    }
    suspend fun setBackgroundColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.BACKGROUND_COLOR) else it[Keys.BACKGROUND_COLOR] = argb
    }
    suspend fun setErrorColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.ERROR_COLOR) else it[Keys.ERROR_COLOR] = argb
    }
    suspend fun setHistoryChartEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HISTORY_CHART_ENABLED] = enabled }

    suspend fun setFontFamily(name: String?) = context.dataStore.edit {
        if (name == null) it.remove(Keys.FONT_FAMILY) else it[Keys.FONT_FAMILY] = name
    }
    suspend fun setFontColor(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.FONT_COLOR) else it[Keys.FONT_COLOR] = argb
    }
    suspend fun setHasSeenDataDisclaimer(seen: Boolean) =
        context.dataStore.edit { it[Keys.HAS_SEEN_DATA_DISCLAIMER] = seen }
    suspend fun setLastSeenVersionCode(versionCode: Int) =
        context.dataStore.edit { it[Keys.LAST_SEEN_VERSION_CODE] = versionCode }
    suspend fun setBiasDataConsentGiven(given: Boolean) =
        context.dataStore.edit { it[Keys.BIAS_DATA_CONSENT_GIVEN] = given }
    suspend fun setV2ShadowModeEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.V2_SHADOW_MODE_ENABLED] = enabled }
}
