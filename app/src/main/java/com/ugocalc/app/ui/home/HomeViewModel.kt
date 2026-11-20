package com.ugocalc.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
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
)

data class HomeUiState(
    val analyzeButtonState: AnalyzeButtonState = AnalyzeButtonState.DISABLED_NO_TABLE,
    val todayMealsCount: Int = 0,
    val todayChoG: Double = 0.0,
    val todayGrassiG: Double = 0.0,
    val recentMeals: List<AnalysisHistoryEntity> = emptyList(),
    val hasAnyAiKey: Boolean = false,
    val totalFoodCount: Int = 0,
    val tableCount: Int = 0,
    val isOnline: Boolean = true,
    val favoritesEnabled: Boolean = true,
    val favorites: List<FavoriteDisplay> = emptyList(),
    /** Modelli preferiti (§2.2) pronti all'uso (chiave configurata): se non vuoto, "Analizza" salta la scelta modello. */
    val preferredModels: List<AiModel> = emptyList(),
    /** Scelta esplicita Casa/Ristorante per la prossima analisi (piano V2 §8): mai dedotta, sempre scelta qui prima di "Analizza". */
    val sessionMode: SessionMode = SessionMode.CASA,
)

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

        historyRepository.observeHistory()
            .onEach { history ->
                val todayStart = startOfTodayMillis()
                val todayMeals = history.filter { it.analyzedAtEpochMillis >= todayStart }
                _uiState.update {
                    it.copy(
                        todayMealsCount = todayMeals.size,
                        todayChoG = todayMeals.sumOf { meal -> meal.totalChoG },
                        todayGrassiG = todayMeals.sumOf { meal -> meal.totalGrassiG },
                        recentMeals = history.take(3),
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)

        favoritesRepository.observeFavorites()
            .onEach { favorites ->
                val resolved = favorites.mapNotNull { favorite ->
                    val historyEntryId = favorite.historyEntryId
                    val comboGrams = favorite.comboGrams
                    val comboCho = favorite.comboChoPer100g
                    val comboGrassi = favorite.comboGrassiPer100g
                    when {
                        historyEntryId != null -> {
                            val history = historyRepository.getById(historyEntryId) ?: return@mapNotNull null
                            FavoriteDisplay(
                                id = favorite.id,
                                label = favorite.label,
                                choG = history.totalChoG,
                                grassiG = history.totalGrassiG,
                                historyEntryId = history.id,
                            )
                        }
                        comboCho != null && comboGrassi != null && comboGrams != null -> FavoriteDisplay(
                            id = favorite.id,
                            label = favorite.label,
                            choG = comboCho * comboGrams / 100.0,
                            grassiG = comboGrassi * comboGrams / 100.0,
                            historyEntryId = null,
                            comboGrams = comboGrams,
                        )
                        comboCho != null && comboGrassi != null -> FavoriteDisplay(
                            id = favorite.id,
                            label = favorite.label,
                            choG = comboCho,
                            grassiG = comboGrassi,
                            historyEntryId = null,
                            isPerHundredGrams = true,
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
            Triple(settings, hasKey, preferred)
        }
            .flowOn(Dispatchers.IO)
            .onEach { (settings, hasKey, models) ->
                _uiState.update {
                    it.copy(
                        hasAnyAiKey = hasKey,
                        preferredModels = models,
                        favoritesEnabled = settings.favoritesEnabled,
                        sessionMode = settings.activeSessionMode,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Forza il riallineamento di chiavi API, modelli preferiti e conteggi al ritorno su Home. */
    fun refresh() {
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
    fun addComboFavorite(foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) {
        viewModelScope.launch {
            val nextOrder = _uiState.value.favorites.size
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(label, grams, choPer100g, grassiPer100g, nextOrder)
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
        val entry = NormalizedFoodEntry(nome = favorite.label, choPer100g = choPer100g, grassiPer100g = grassiPer100g)
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
                )
                AnalysisSessionStore.PendingManualRow(tableEntry, grams)
            }
            sessionStore.setPendingManualRows(rows)
        } else {
            val entry = NormalizedFoodEntry(nome = favorite.label, choPer100g = favorite.choG, grassiPer100g = favorite.grassiG)
            sessionStore.setPendingManualRows(listOf(AnalysisSessionStore.PendingManualRow(entry, 100.0)))
        }
    }
}
