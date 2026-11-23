package com.ugocalc.app.data.local.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ugocalc.app.domain.glycemic.GlycemicProfile
import com.ugocalc.app.domain.glycemic.GlycemicProfilePreset
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.ConfidenceScore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ugocalc_settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

@Immutable
data class UserSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val textScale: Float = 1.0f,
    /** "Modelli preferiti": se non vuoto, salta del tutto la schermata di scelta modello (§2.2) e avvia l'analisi con questi. */
    val preferredModelIds: Set<String> = emptySet(),
    /** "Livello di confidenza" attivo/non attivo e relativa soglia di allerta (default 60%). */
    val confidenceIndicatorEnabled: Boolean = false,
    val confidenceAlertThresholdPercent: Int = ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
    val notificationsEnabled: Boolean = true,
    /** Mostra la sezione "Preferiti" in Homepage (§2.2, voce di attivazione richiesta dall'utente). */
    val favoritesEnabled: Boolean = true,
    /** Colori personalizzati per modalità Casa (§ richiesta utente): null = usa il colore predefinito del tema. Valori ARGB. */
    val primaryColorArgb: Int? = null,
    val secondaryColorArgb: Int? = null,
    val tertiaryColorArgb: Int? = null,
    val backgroundColorArgb: Int? = null,
    /** "Colore di errore" (§ richiesta utente, es. pulsante Analizza rosso intenso se offline). */
    val errorColorArgb: Int? = null,
    /** Colori personalizzati per modalità Ristorante: null = usa il colore predefinito del tema. Valori ARGB. */
    val primaryColorRistoranteArgb: Int? = null,
    val secondaryColorRistoranteArgb: Int? = null,
    val tertiaryColorRistoranteArgb: Int? = null,
    val backgroundColorRistoranteArgb: Int? = null,
    val errorColorRistoranteArgb: Int? = null,
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
    /** Modelli specifici da testare in shadow mode (quando [v2ShadowModeEnabled] è true). Se vuoto, usa il modello di riferimento come V1. */
    val shadowModelIds: Set<String> = emptySet(),
    /** Modelli abilitati nella schermata di selezione per "Analizza". Se vuoto, tutti i modelli disponibili sono selezionabili. */
    val enabledAnalysisModelIds: Set<String> = emptySet(),
    /** Modelli abilitati nella schermata di selezione per "Normalizzazione". Se vuoto, tutti i modelli disponibili sono selezionabili. */
    val enabledNormalizationModelIds: Set<String> = emptySet(),
    /**
     * Modalità Casa/Ristorante attiva a livello di app (§ richiesta utente, icona in topAppBar
     * Homepage): pilota sia l'icona mostrata sia quale set di colori personalizzati viene applicato
     * ([primaryColorArgb] ecc. per Casa, [primaryColorRistoranteArgb] ecc. per Ristorante). Separata
     * da [com.ugocalc.app.ui.AnalysisSessionStore.Session.sessionMode], che resta la scelta per la
     * singola analisi in corso (piano V2 §8).
     */
    val activeSessionMode: SessionMode = SessionMode.CASA,
    /**
     * Modalità visibile V2 promossa automaticamente dal BenchmarkReportWorker quando le metriche
     * V2 superano quelle di V1 (piano V2 §13 punto 1). Una volta abilitata, V2 diventa il motore
     * principale visibile all'utente.
     */
    val v2VisibleModeEnabled: Boolean = false,
    val globalBiasEnabled: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val hasSeededStarterTable: Boolean = false,
    val starterCatalogVersion: Int = 0,
    val angleOverlayEnabled: Boolean = false,
    val glycemicProfilePreset: String = "STANDARD",
    val baselineGlucoseMgDl: Int = 90,
    val carbSensitivityMgDlPer10g: Int = 18,
    val targetPostPrandialMaxMgDl: Int = 140,
    val aiEffort: com.ugocalc.app.domain.model.AiEffort = com.ugocalc.app.domain.model.AiEffort.MEDIUM,
    val showGlycemicCurve: Boolean = false,
) {
    fun toGlycemicProfile(): GlycemicProfile {
        val preset = runCatching { GlycemicProfilePreset.valueOf(glycemicProfilePreset) }
            .getOrDefault(GlycemicProfilePreset.STANDARD)
        return GlycemicProfile(
            preset = preset,
            baselineGlucoseMgDl = baselineGlucoseMgDl.toDouble(),
            carbSensitivityMgDlPer10g = carbSensitivityMgDlPer10g.toDouble(),
            targetPostPrandialMaxMgDl = targetPostPrandialMaxMgDl.toDouble(),
        )
    }
}

@Singleton
class SettingsDataStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val PREFERRED_MODEL_IDS = stringSetPreferencesKey("preferred_model_ids")
        val CONFIDENCE_ENABLED = booleanPreferencesKey("confidence_enabled")
        val CONFIDENCE_ALERT_THRESHOLD = intPreferencesKey("confidence_alert_threshold")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val FAVORITES_ENABLED = booleanPreferencesKey("favorites_enabled")
        val PRIMARY_COLOR = intPreferencesKey("primary_color_argb")
        val SECONDARY_COLOR = intPreferencesKey("secondary_color_argb")
        val TERTIARY_COLOR = intPreferencesKey("tertiary_color_argb")
        val BACKGROUND_COLOR = intPreferencesKey("background_color_argb")
        val ERROR_COLOR = intPreferencesKey("error_color_argb")
        val PRIMARY_COLOR_RISTORANTE = intPreferencesKey("primary_color_ristorante_argb")
        val SECONDARY_COLOR_RISTORANTE = intPreferencesKey("secondary_color_ristorante_argb")
        val TERTIARY_COLOR_RISTORANTE = intPreferencesKey("tertiary_color_ristorante_argb")
        val BACKGROUND_COLOR_RISTORANTE = intPreferencesKey("background_color_ristorante_argb")
        val ERROR_COLOR_RISTORANTE = intPreferencesKey("error_color_ristorante_argb")
        val HISTORY_CHART_ENABLED = booleanPreferencesKey("history_chart_enabled")
        val FONT_FAMILY = stringPreferencesKey("font_family_name")
        val FONT_COLOR = intPreferencesKey("font_color_argb")
        val HAS_SEEN_DATA_DISCLAIMER = booleanPreferencesKey("has_seen_data_disclaimer")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        val BIAS_DATA_CONSENT_GIVEN = booleanPreferencesKey("bias_data_consent_given")
        val V2_SHADOW_MODE_ENABLED = booleanPreferencesKey("v2_shadow_mode_enabled")
        val SHADOW_MODEL_IDS = stringSetPreferencesKey("shadow_model_ids")
        val ENABLED_ANALYSIS_MODEL_IDS = stringSetPreferencesKey("enabled_analysis_model_ids")
        val ENABLED_NORMALIZATION_MODEL_IDS = stringSetPreferencesKey("enabled_normalization_model_ids")
        val ACTIVE_SESSION_MODE = stringPreferencesKey("active_session_mode")
        val V2_VISIBLE_MODE_ENABLED = booleanPreferencesKey("v2_visible_mode_enabled")
        val GLOBAL_BIAS_ENABLED = booleanPreferencesKey("global_bias_enabled")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val HAS_SEEDED_STARTER_TABLE = booleanPreferencesKey("has_seeded_starter_table")
        val STARTER_CATALOG_VERSION = intPreferencesKey("starter_catalog_version")
        val ANGLE_OVERLAY_ENABLED = booleanPreferencesKey("angle_overlay_enabled")
        val GLYCEMIC_PROFILE_PRESET = stringPreferencesKey("glycemic_profile_preset")
        val BASELINE_GLUCOSE_MG_DL = intPreferencesKey("baseline_glucose_mg_dl")
        val CARB_SENSITIVITY_MG_DL_PER_10G = intPreferencesKey("carb_sensitivity_mg_dl_per_10g")
        val TARGET_POST_PRANDIAL_MAX_MG_DL = intPreferencesKey("target_post_prandial_max_mg_dl")
        val AI_EFFORT = stringPreferencesKey("ai_effort")
        val SHOW_GLYCEMIC_CURVE = booleanPreferencesKey("show_glycemic_curve")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            textScale = prefs[Keys.TEXT_SCALE]?.toFloatOrNull() ?: 1.0f,
            preferredModelIds = prefs[Keys.PREFERRED_MODEL_IDS] ?: emptySet(),
            confidenceIndicatorEnabled = prefs[Keys.CONFIDENCE_ENABLED] ?: false,
            confidenceAlertThresholdPercent = prefs[Keys.CONFIDENCE_ALERT_THRESHOLD] ?: ConfidenceScore.DEFAULT_ALERT_THRESHOLD_PERCENT,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            favoritesEnabled = prefs[Keys.FAVORITES_ENABLED] ?: true,
            primaryColorArgb = prefs[Keys.PRIMARY_COLOR],
            secondaryColorArgb = prefs[Keys.SECONDARY_COLOR],
            tertiaryColorArgb = prefs[Keys.TERTIARY_COLOR],
            backgroundColorArgb = prefs[Keys.BACKGROUND_COLOR],
            errorColorArgb = prefs[Keys.ERROR_COLOR],
            primaryColorRistoranteArgb = prefs[Keys.PRIMARY_COLOR_RISTORANTE],
            secondaryColorRistoranteArgb = prefs[Keys.SECONDARY_COLOR_RISTORANTE],
            tertiaryColorRistoranteArgb = prefs[Keys.TERTIARY_COLOR_RISTORANTE],
            backgroundColorRistoranteArgb = prefs[Keys.BACKGROUND_COLOR_RISTORANTE],
            errorColorRistoranteArgb = prefs[Keys.ERROR_COLOR_RISTORANTE],
            historyChartEnabled = prefs[Keys.HISTORY_CHART_ENABLED] ?: true,
            fontFamilyName = prefs[Keys.FONT_FAMILY],
            fontColorArgb = prefs[Keys.FONT_COLOR],
            hasSeenDataDisclaimer = prefs[Keys.HAS_SEEN_DATA_DISCLAIMER] ?: false,
            lastSeenVersionCode = prefs[Keys.LAST_SEEN_VERSION_CODE] ?: 0,
            biasDataConsentGiven = prefs[Keys.BIAS_DATA_CONSENT_GIVEN] ?: false,
            v2ShadowModeEnabled = prefs[Keys.V2_SHADOW_MODE_ENABLED] ?: false,
            shadowModelIds = prefs[Keys.SHADOW_MODEL_IDS] ?: emptySet(),
            enabledAnalysisModelIds = prefs[Keys.ENABLED_ANALYSIS_MODEL_IDS] ?: emptySet(),
            enabledNormalizationModelIds = prefs[Keys.ENABLED_NORMALIZATION_MODEL_IDS] ?: emptySet(),
            activeSessionMode = prefs[Keys.ACTIVE_SESSION_MODE]?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() } ?: SessionMode.CASA,
            v2VisibleModeEnabled = prefs[Keys.V2_VISIBLE_MODE_ENABLED] ?: false,
            globalBiasEnabled = prefs[Keys.GLOBAL_BIAS_ENABLED] ?: false,
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            hasSeededStarterTable = prefs[Keys.HAS_SEEDED_STARTER_TABLE] ?: false,
            starterCatalogVersion = prefs[Keys.STARTER_CATALOG_VERSION] ?: 0,
            angleOverlayEnabled = prefs[Keys.ANGLE_OVERLAY_ENABLED] ?: false,
            glycemicProfilePreset = prefs[Keys.GLYCEMIC_PROFILE_PRESET] ?: GlycemicProfilePreset.STANDARD.name,
            baselineGlucoseMgDl = prefs[Keys.BASELINE_GLUCOSE_MG_DL] ?: 90,
            carbSensitivityMgDlPer10g = prefs[Keys.CARB_SENSITIVITY_MG_DL_PER_10G] ?: 18,
            targetPostPrandialMaxMgDl = prefs[Keys.TARGET_POST_PRANDIAL_MAX_MG_DL] ?: 140,
            aiEffort = prefs[Keys.AI_EFFORT]?.let {
                runCatching { com.ugocalc.app.domain.model.AiEffort.valueOf(it) }.getOrNull()
            } ?: com.ugocalc.app.domain.model.AiEffort.MEDIUM,
            showGlycemicCurve = prefs[Keys.SHOW_GLYCEMIC_CURVE] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale.toString() }
    suspend fun setPreferredModelIds(ids: Set<String>) = context.dataStore.edit { it[Keys.PREFERRED_MODEL_IDS] = ids }
    suspend fun setConfidenceIndicatorEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CONFIDENCE_ENABLED] = enabled }
    suspend fun setConfidenceAlertThresholdPercent(percent: Int) =
        context.dataStore.edit { it[Keys.CONFIDENCE_ALERT_THRESHOLD] = percent.coerceIn(0, 100) }
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

    suspend fun setPrimaryColorRistorante(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.PRIMARY_COLOR_RISTORANTE) else it[Keys.PRIMARY_COLOR_RISTORANTE] = argb
    }
    suspend fun setSecondaryColorRistorante(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.SECONDARY_COLOR_RISTORANTE) else it[Keys.SECONDARY_COLOR_RISTORANTE] = argb
    }
    suspend fun setTertiaryColorRistorante(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.TERTIARY_COLOR_RISTORANTE) else it[Keys.TERTIARY_COLOR_RISTORANTE] = argb
    }
    suspend fun setBackgroundColorRistorante(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.BACKGROUND_COLOR_RISTORANTE) else it[Keys.BACKGROUND_COLOR_RISTORANTE] = argb
    }
    suspend fun setErrorColorRistorante(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(Keys.ERROR_COLOR_RISTORANTE) else it[Keys.ERROR_COLOR_RISTORANTE] = argb
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
    suspend fun setShadowModelIds(ids: Set<String>) =
        context.dataStore.edit { it[Keys.SHADOW_MODEL_IDS] = ids }
    suspend fun setEnabledAnalysisModelIds(ids: Set<String>) =
        context.dataStore.edit { it[Keys.ENABLED_ANALYSIS_MODEL_IDS] = ids }
    suspend fun setEnabledNormalizationModelIds(ids: Set<String>) =
        context.dataStore.edit { it[Keys.ENABLED_NORMALIZATION_MODEL_IDS] = ids }
    suspend fun setActiveSessionMode(mode: SessionMode) =
        context.dataStore.edit { it[Keys.ACTIVE_SESSION_MODE] = mode.name }
    suspend fun setV2VisibleModeEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.V2_VISIBLE_MODE_ENABLED] = enabled }
    suspend fun setGlobalBiasEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.GLOBAL_BIAS_ENABLED] = enabled }
    suspend fun setHasCompletedOnboarding(completed: Boolean) =
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    suspend fun setHasSeededStarterTable(seeded: Boolean) =
        context.dataStore.edit { it[Keys.HAS_SEEDED_STARTER_TABLE] = seeded }
    suspend fun setStarterCatalogVersion(version: Int) =
        context.dataStore.edit { it[Keys.STARTER_CATALOG_VERSION] = version }
    suspend fun setAngleOverlayEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.ANGLE_OVERLAY_ENABLED] = enabled }
    suspend fun setGlycemicProfilePreset(preset: String) =
        context.dataStore.edit { it[Keys.GLYCEMIC_PROFILE_PRESET] = preset }
    suspend fun setBaselineGlucoseMgDl(mgDl: Int) =
        context.dataStore.edit { it[Keys.BASELINE_GLUCOSE_MG_DL] = mgDl.coerceIn(60, 200) }
    suspend fun setCarbSensitivityMgDlPer10g(mgDlPer10g: Int) =
        context.dataStore.edit { it[Keys.CARB_SENSITIVITY_MG_DL_PER_10G] = mgDlPer10g.coerceIn(5, 80) }
    suspend fun setTargetPostPrandialMaxMgDl(mgDl: Int) =
        context.dataStore.edit { it[Keys.TARGET_POST_PRANDIAL_MAX_MG_DL] = mgDl.coerceIn(110, 260) }
    suspend fun applyGlycemicProfilePreset(preset: GlycemicProfilePreset) = context.dataStore.edit {
        it[Keys.GLYCEMIC_PROFILE_PRESET] = preset.name
        it[Keys.BASELINE_GLUCOSE_MG_DL] = preset.defaultBaselineMgDl
        it[Keys.CARB_SENSITIVITY_MG_DL_PER_10G] = preset.defaultCarbSensitivity
        it[Keys.TARGET_POST_PRANDIAL_MAX_MG_DL] = preset.defaultTargetMaxMgDl
    }
    suspend fun setAiEffort(effort: com.ugocalc.app.domain.model.AiEffort) =
        context.dataStore.edit { it[Keys.AI_EFFORT] = effort.name }
    suspend fun setShowGlycemicCurve(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_GLYCEMIC_CURVE] = enabled }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
