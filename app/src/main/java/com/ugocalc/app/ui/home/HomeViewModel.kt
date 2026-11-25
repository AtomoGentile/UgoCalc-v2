package com.ugocalc.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.glycemic.DailyGlycemicTimeline
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicIndexResolver
import com.ugocalc.app.domain.glycemic.MealTimelineInput
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiModelTier
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.recommendedTier
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject

enum class AnalyzeButtonState { ENABLED_ONLINE, ENABLED_OFFLINE, DISABLED_NO_TABLE }

/**
 * Riga preferiti pronta per la UI: risolve sia i preferiti legati a una voce di cronologia sia le
 * "combinazioni standard" alimento+peso (§ richiesta utente), che non hanno una cronologia da aprire.
 */
data class FavoriteDisplay(
    val id: Long,
    val label: String,
    val choG: Double,
    val grassiG: Double,
    val historyEntryId: Long?,
    /** true se [choG]/[grassiG] sono valori per 100g (peso non specificato dall'utente), non per una quantità assoluta. */
    val isPerHundredGrams: Boolean = false,
    /** Peso assoluto del combo, se noto (§ richiesta utente "i preferiti, se toccati, aggiungono direttamente al totale"). */
    val comboGrams: Double? = null,
    val proteineG: Double = 0.0,
    val indiceGlicemico: Int? = null,
    val glycemicLoad: Double? = null,
)

data class HomeUiState(
    val greeting: String = greetingForHour(),
    val analyzeButtonState: AnalyzeButtonState = AnalyzeButtonState.DISABLED_NO_TABLE,
    val todayMealsCount: Int = 0,
    val weekMealsCount: Int = 0,
    val todayChoG: Double = 0.0,
    val todayProteineG: Double = 0.0,
    val todayGrassiG: Double = 0.0,
    val todayGlycemicLoad: Double = 0.0,
    val todayGlycemicTimeline: DailyGlycemicTimeline? = null,
    val recentMeals: List<AnalysisHistoryEntity> = emptyList(),
    val searchableMeals: List<AnalysisHistoryEntity> = emptyList(),
    val hasAnyAiKey: Boolean = false,
    val totalFoodCount: Int = 0,
    val tableCount: Int = 0,
    val isOnline: Boolean = true,
    val favoritesEnabled: Boolean = true,
    val favorites: List<FavoriteDisplay> = emptyList(),
    /** Modelli preferiti (§2.2) pronti all'uso (chiave configurata): se non vuoto, "Analizza" salta la scelta modello. */
    val preferredModels: List<AiModel> = emptyList(),
    /** Tutti i modelli AI attualmente disponibili con chiave configurata, ordinati per tier. */
    val availableModels: List<AiModel> = emptyList(),
    /** Effort AI attivo, regolabile dal bottom sheet rapido in Home. */
    val aiEffort: AiEffort = AiEffort.MEDIUM,
    /** Scelta esplicita Casa/Ristorante per la prossima analisi (piano V2 §8): mai dedotta, sempre scelta qui prima di "Analizza". */
    val sessionMode: SessionMode = SessionMode.CASA,
)

internal fun greetingForHour(
    hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
): String = when (hourOfDay) {
    in 5..12 -> "Buongiorno"
    in 13..17 -> "Buon pomeriggio"
    else -> "Buonasera"
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionStore: AnalysisSessionStore,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val refreshTrigger = MutableStateFlow(0)

    init {
        combine(
            tableRepository.observeTables(),
            connectivityObserver.observe(),
            refreshTrigger,
        ) { tables, isOnline, _ ->
            val tableCount = tables.size
            val totalFoods = tables.sumOf { table ->
                tableRepository.getEntriesForTable(table.id).size
            }
            val buttonState = when {
                tableCount == 0 -> AnalyzeButtonState.DISABLED_NO_TABLE
                isOnline -> AnalyzeButtonState.ENABLED_ONLINE
                else -> AnalyzeButtonState.ENABLED_OFFLINE
            }
            Triple(tableCount to totalFoods, isOnline, buttonState)
        }
            .flowOn(Dispatchers.IO)
            .onEach { (counts, isOnline, buttonState) ->
                val (tableCount, totalFoods) = counts
                _uiState.update {
                    it.copy(
                        tableCount = tableCount,
                        totalFoodCount = totalFoods,
                        isOnline = isOnline,
                        analyzeButtonState = buttonState,
                    )
                }
            }
            .launchIn(viewModelScope)

        combine(
            historyRepository.observeHistory(),
            settingsRepository.settings,
        ) { history, settings ->
            val todayStart = startOfTodayMillis()
            val weekStart = System.currentTimeMillis() - SEVEN_DAYS_MILLIS
            val todayMeals = history.filter { it.analyzedAtEpochMillis >= todayStart }
            val weekCount = history.count { it.analyzedAtEpochMillis >= weekStart }
            val profile = settings.toGlycemicProfile()
            val todayInputs = todayMeals.map { meal -> buildMealTimelineInput(meal, json) }
            val timeline = GlycemicCurveCalculator.simulateDayTimeline(todayInputs, profile)
            val totalGl = timeline?.totalDailyGl ?: todayInputs.sumOf { it.glycemicLoad }
            TodayHistorySnapshot(
                todayMealsCount = todayMeals.size,
                weekMealsCount = weekCount,
                todayChoG = todayMeals.sumOf { it.totalChoG },
                todayProteineG = todayMeals.sumOf { it.totalProteineG },
                todayGrassiG = todayMeals.sumOf { it.totalGrassiG },
                todayGlycemicLoad = totalGl,
                todayGlycemicTimeline = timeline,
                recentMeals = history.take(4),
                searchableMeals = history.take(60),
            )
        }
            .flowOn(Dispatchers.Default)
            .onEach { snap ->
                _uiState.update {
                    it.copy(
                        todayMealsCount = snap.todayMealsCount,
                        weekMealsCount = snap.weekMealsCount,
                        todayChoG = snap.todayChoG,
                        todayProteineG = snap.todayProteineG,
                        todayGrassiG = snap.todayGrassiG,
                        todayGlycemicLoad = snap.todayGlycemicLoad,
                        todayGlycemicTimeline = snap.todayGlycemicTimeline,
                        recentMeals = snap.recentMeals,
                        searchableMeals = snap.searchableMeals,
                    )
                }
            }
            .launchIn(viewModelScope)

        favoritesRepository.observeFavorites()
            .onEach { favorites ->
                val resolved = favorites.mapNotNull { favorite ->
                    val historyEntryId = favorite.historyEntryId
                    val comboGrams = favorite.comboGrams
                    val comboCho = favorite.comboChoPer100g
                    val comboGrassi = favorite.comboGrassiPer100g
                    val comboProt = favorite.comboProteinePer100g ?: 0.0
                    val comboGi = favorite.comboIndiceGlicemico
                    when {
                        historyEntryId != null -> {
                            val history = historyRepository.getById(historyEntryId) ?: return@mapNotNull null
                            FavoriteDisplay(
                                id = favorite.id,
                                label = favorite.label,
                                choG = history.totalChoG,
                                grassiG = history.totalGrassiG,
                                historyEntryId = history.id,
                                proteineG = history.totalProteineG,
                                glycemicLoad = history.glycemicLoad.takeIf { it > 0.0 },
                            )
                        }
                        comboCho != null && comboGrassi != null && comboGrams != null -> {
                            val absCho = comboCho * comboGrams / 100.0
                            val resolvedGi = comboGi ?: GlycemicIndexResolver.resolveByName(favorite.label, comboCho).gi
                            val gl = (resolvedGi * absCho / 100.0).takeIf { absCho > 0.0 }
                            FavoriteDisplay(
                                id = favorite.id,
                                label = favorite.label,
                                choG = absCho,
                                grassiG = comboGrassi * comboGrams / 100.0,
                                historyEntryId = null,
                                comboGrams = comboGrams,
                                proteineG = comboProt * comboGrams / 100.0,
                                indiceGlicemico = comboGi,
                                glycemicLoad = gl,
                            )
                        }
                        comboCho != null && comboGrassi != null -> FavoriteDisplay(
                            id = favorite.id,
                            label = favorite.label,
                            choG = comboCho,
                            grassiG = comboGrassi,
                            historyEntryId = null,
                            isPerHundredGrams = true,
                            proteineG = comboProt,
                            indiceGlicemico = comboGi,
                        )
                        else -> null
                    }
                }
                _uiState.update { it.copy(favorites = resolved) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        // Osserva anche AiModelCatalog.catalogVersion e refreshTrigger così i modelli preferiti
        // e lo stato chiave AI si aggiornano immediatamente dopo modifiche in Impostazioni > API.
        combine(
            settingsRepository.settings,
            connectivityObserver.observe(),
            AiModelCatalog.catalogVersion,
            refreshTrigger,
        ) { settings, isOnline, _, _ ->
            val hasKey = settingsRepository.hasAnyApiKeyConfigured()
            val enabledIds = settings.enabledAnalysisModelIds
            val available = if (!isOnline) {
                emptyList()
            } else {
                AiModelCatalog.ALL
                    .filter { model ->
                        val isEnabled = enabledIds.isEmpty() || model.uid in enabledIds || model.id in enabledIds
                        isEnabled && isProviderConfigured(model.provider)
                    }
                    .distinctBy { it.uid }
                    .sortedBy {
                        when (it.recommendedTier()) {
                            AiModelTier.RECOMMENDED -> 0
                            AiModelTier.FAST -> 1
                            AiModelTier.ACCURATE -> 2
                            AiModelTier.STANDARD -> 3
                        }
                    }
            }
            val preferred = if (!isOnline) {
                emptyList()
            } else {
                AiModelCatalog.ALL
                    .filter { model ->
                        (model.id in settings.preferredModelIds || model.uid in settings.preferredModelIds) &&
                            isProviderConfigured(model.provider)
                    }
                    .distinctBy { it.uid }
            }
            HomeAiConfigSnapshot(
                settings = settings,
                hasKey = hasKey,
                preferred = preferred,
                available = available,
            )
        }
            .flowOn(Dispatchers.IO)
            .onEach { snap ->
                _uiState.update {
                    it.copy(
                        hasAnyAiKey = snap.hasKey,
                        preferredModels = snap.preferred,
                        availableModels = snap.available,
                        aiEffort = snap.settings.aiEffort,
                        favoritesEnabled = snap.settings.favoritesEnabled,
                        sessionMode = snap.settings.activeSessionMode,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Forza il riallineamento di chiavi API, modelli preferiti e conteggi al ritorno su Home. */
    fun refresh() {
        _uiState.update { it.copy(greeting = greetingForHour()) }
        refreshTrigger.update { it + 1 }
    }

    private fun isProviderConfigured(provider: AiProviderType): Boolean = when (provider) {
        AiProviderType.BEDROCK_CLAUDE,
        AiProviderType.BEDROCK_GPT -> settingsRepository.hasBedrockCredentials()
        AiProviderType.BEDROCK_IAM_CLAUDE,
        AiProviderType.BEDROCK_IAM_GPT,
        AiProviderType.BEDROCK_IAM_AMAZON -> settingsRepository.hasBedrockIamCredentials()
        AiProviderType.VERTEX_AI -> settingsRepository.hasVertexAiCredentials()
        else -> settingsRepository.getApiKey(provider) != null
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Selettore Casa/Ristorante: persistito subito perché pilota anche i colori dell'intera app.
     */
    fun setSessionMode(mode: SessionMode) {
        _uiState.update { it.copy(sessionMode = mode) }
        viewModelScope.launch { settingsRepository.setActiveSessionMode(mode) }
    }

    /** Selezione rapida del modello attivo dal bottom sheet stile Claude in Home. */
    fun selectQuickModel(model: AiModel) {
        _uiState.update { it.copy(preferredModels = listOf(model)) }
        viewModelScope.launch { settingsRepository.setPreferredModelIds(setOf(model.uid)) }
    }

    /** Aggiorna l'effort AI dallo slider tattile nel bottom sheet in Home. */
    fun setAiEffort(effort: AiEffort) {
        _uiState.update { it.copy(aiEffort = effort) }
        viewModelScope.launch { settingsRepository.setAiEffort(effort) }
    }

    /** Azzera lo stato di una eventuale analisi precedente prima di avviarne una nuova (§2.4 punto 1). */
    fun startNewAnalysis() {
        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
    }

    /** "Analizza" con i modelli preferiti (§ modello preferito esteso): salta del tutto la schermata di scelta modello. */
    fun startAnalysisWithPreferredModels() {
        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
        sessionStore.setSelectedModels(_uiState.value.preferredModels)
    }

    /** "+" in Homepage: aggiunge un preferito da database senza passare da Impostazioni. */
    fun addComboFavorite(
        foodName: String,
        grams: Double?,
        choPer100g: Double,
        grassiPer100g: Double,
        proteinePer100g: Double = 0.0,
        indiceGlicemico: Int? = null,
    ) {
        viewModelScope.launch {
            val nextOrder = _uiState.value.favorites.size
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(
                label = label,
                grams = grams,
                choPer100g = choPer100g,
                grassiPer100g = grassiPer100g,
                sortOrder = nextOrder,
                proteinePer100g = proteinePer100g,
                indiceGlicemico = indiceGlicemico,
            )
        }
    }

    /**
     * Preferito "combo" con peso già noto: calcola subito il totale e lo pubblica in sessione,
     * senza passare da nessuna schermata intermedia — il chiamante naviga dritto al risultato finale.
     */
    fun useComboFavoriteDirectly(favorite: FavoriteDisplay) {
        val grams = favorite.comboGrams ?: return
        val choPer100g = if (grams > 0) favorite.choG / grams * 100.0 else 0.0
        val grassiPer100g = if (grams > 0) favorite.grassiG / grams * 100.0 else 0.0
        val protPer100g = if (grams > 0) favorite.proteineG / grams * 100.0 else 0.0
        val entry = NormalizedFoodEntry(
            nome = favorite.label,
            choPer100g = choPer100g,
            grassiPer100g = grassiPer100g,
            proteinePer100g = protPer100g,
            indiceGlicemico = favorite.indiceGlicemico,
        )
        val line = CalculatedFoodLine(
            recognizedFood = RecognizedFood(nome = favorite.label, pesoStimatoG = grams, commento = "Da preferito", confidenza = Confidence.ALTO),
            match = FoodMatch(nomeRiconosciuto = favorite.label, voceTabellaAbbinata = favorite.label, nessunaCorrispondenza = false, confidenzaAbbinamento = Confidence.ALTO),
            tableEntry = entry,
        )
        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
        sessionStore.setTotals(AnalysisTotals(listOf(line)))
    }

    /**
     * Preferito legato a una voce di cronologia, o combo senza peso fissato: pubblica le righe
     * già note in sessione e il chiamante naviga alla composizione manuale nel passo Quantità.
     */
    suspend fun openFavoriteForQuantityEntry(favorite: FavoriteDisplay) {
        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
        val historyEntryId = favorite.historyEntryId
        if (historyEntryId != null) {
            val history = historyRepository.getById(historyEntryId) ?: return
            val lines: List<CalculatedFoodLine> = json.decodeFromString(history.detailsJson)
            val rows = lines.filterNot { it.isExcluded }.map { line ->
                val grams = line.recognizedFood.pesoStimatoG
                val tableEntry = line.tableEntry ?: NormalizedFoodEntry(
                    nome = line.recognizedFood.nome,
                    choPer100g = if (grams > 0) line.choContributionG / grams * 100.0 else 0.0,
                    grassiPer100g = if (grams > 0) line.grassiContributionG / grams * 100.0 else 0.0,
                    proteinePer100g = if (grams > 0) line.proteineContributionG / grams * 100.0 else 0.0,
                )
                AnalysisSessionStore.PendingManualRow(tableEntry, grams)
            }
            sessionStore.setPendingManualRows(rows)
        } else {
            val entry = NormalizedFoodEntry(
                nome = favorite.label,
                choPer100g = favorite.choG,
                grassiPer100g = favorite.grassiG,
                proteinePer100g = favorite.proteineG,
                indiceGlicemico = favorite.indiceGlicemico,
            )
            sessionStore.setPendingManualRows(listOf(AnalysisSessionStore.PendingManualRow(entry, 100.0)))
        }
    }

    /**
     * Precarica gli alimenti di un pasto salvato in cronologia come bozza modificabile nel passo Quantità,
     * senza alterare lo schema Room.
     */
    suspend fun repeatHistoryMeal(historyId: Long): Boolean {
        val history = historyRepository.getById(historyId) ?: return false
        val lines: List<CalculatedFoodLine> = runCatching {
            json.decodeFromString<List<CalculatedFoodLine>>(history.detailsJson)
        }.getOrDefault(emptyList())

        val activeLines = lines.filter { !it.isExcluded && it.recognizedFood.pesoStimatoG > 0.0 }
            .ifEmpty { lines.filter { it.recognizedFood.pesoStimatoG > 0.0 } }
        val rows = activeLines.map { line ->
            val grams = line.recognizedFood.pesoStimatoG
            val tableEntry = line.tableEntry ?: NormalizedFoodEntry(
                nome = line.recognizedFood.nome,
                choPer100g = if (!line.isExcluded && grams > 0) line.choContributionG / grams * 100.0 else 0.0,
                grassiPer100g = if (!line.isExcluded && grams > 0) line.grassiContributionG / grams * 100.0 else 0.0,
                proteinePer100g = if (!line.isExcluded && grams > 0) line.proteineContributionG / grams * 100.0 else 0.0,
            )
            AnalysisSessionStore.PendingManualRow(tableEntry, grams)
        }
        if (rows.isEmpty()) return false

        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
        sessionStore.setPendingManualRows(rows)
        return true
    }

    companion object {
        private const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}

private data class HomeAiConfigSnapshot(
    val settings: com.ugocalc.app.data.local.prefs.UserSettings,
    val hasKey: Boolean,
    val preferred: List<AiModel>,
    val available: List<AiModel>,
)

private data class TodayHistorySnapshot(
    val todayMealsCount: Int,
    val weekMealsCount: Int,
    val todayChoG: Double,
    val todayProteineG: Double,
    val todayGrassiG: Double,
    val todayGlycemicLoad: Double,
    val todayGlycemicTimeline: DailyGlycemicTimeline?,
    val recentMeals: List<AnalysisHistoryEntity>,
    val searchableMeals: List<AnalysisHistoryEntity>,
)

internal fun buildMealTimelineInput(
    meal: AnalysisHistoryEntity,
    json: Json,
): MealTimelineInput {
    val lines = runCatching {
        json.decodeFromString<List<CalculatedFoodLine>>(meal.detailsJson)
    }.getOrDefault(emptyList())
    val resolvedGl = when {
        meal.glycemicLoad > 0.0 -> meal.glycemicLoad
        lines.any { !it.isExcluded } -> GlycemicCurveCalculator.computeTotalGlycemicLoad(lines)
        meal.totalChoG > 0.0 -> GlycemicCurveCalculator.simulateFromTotals(
            choG = meal.totalChoG,
            grassiG = meal.totalGrassiG,
            proteineG = meal.totalProteineG,
        ).totalGlycemicLoad
        else -> 0.0
    }
    return MealTimelineInput(
        title = meal.foodTitle,
        analyzedAtEpochMillis = meal.analyzedAtEpochMillis,
        choG = meal.totalChoG,
        grassiG = meal.totalGrassiG,
        proteineG = meal.totalProteineG,
        glycemicLoad = resolvedGl,
        lines = lines,
    )
}
