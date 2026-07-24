package com.ugocalc.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

    init {
        // §2.4 precondizione: il pulsante "Analizza" è disabilitato finché non c'è almeno una tabella.
        combine(tableRepository.observeTableCount(), connectivityObserver.observe()) { tableCount, isOnline ->
            when {
                tableCount == 0 -> AnalyzeButtonState.DISABLED_NO_TABLE
                isOnline -> AnalyzeButtonState.ENABLED_ONLINE
                else -> AnalyzeButtonState.ENABLED_OFFLINE
            }
        }
        .flowOn(Dispatchers.Default)
        .onEach { state -> _uiState.value = _uiState.value.copy(analyzeButtonState = state) }
        .launchIn(viewModelScope)

        favoritesRepository.observeFavorites()
            .flowOn(Dispatchers.Default)
            .onEach { favorites ->
                val resolved = favorites.mapNotNull { favorite ->
                    val historyEntryId = favorite.historyEntryId
                    val comboGrams = favorite.comboGrams
                    val comboCho = favorite.comboChoPer100g
                    val comboGrassi = favorite.comboGrassiPer100g
                    when {
                        historyEntryId != null -> {
                            val history = historyRepository.getById(historyEntryId) ?: return@mapNotNull null
                            FavoriteDisplay(favorite.id, favorite.label, history.totalChoG, history.totalGrassiG, history.id)
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
                _uiState.value = _uiState.value.copy(favorites = resolved)
            }.launchIn(viewModelScope)

        // Offline, o senza API key configurata, un modello preferito non è comunque utilizzabile:
        // in quel caso non si salta la scelta modello, si lascia il flusso normale (che mostra
        // l'avviso "offline"/"nessun modello disponibile").
        combine(settingsRepository.settings, connectivityObserver.observe()) { settings, isOnline ->
            settings to if (!isOnline) emptyList()
            else AiModelCatalog.ALL.filter { model ->
                model.id in settings.preferredModelIds && when (model.provider) {
                    com.ugocalc.app.domain.model.AiProviderType.BEDROCK_CLAUDE,
                    com.ugocalc.app.domain.model.AiProviderType.BEDROCK_GPT -> settingsRepository.hasBedrockCredentials()
                    com.ugocalc.app.domain.model.AiProviderType.BEDROCK_IAM_CLAUDE,
                    com.ugocalc.app.domain.model.AiProviderType.BEDROCK_IAM_GPT -> settingsRepository.hasBedrockIamCredentials()
                    else -> settingsRepository.getApiKey(model.provider) != null
                }
            }
        }.onEach { (settings, models) ->
            _uiState.value = _uiState.value.copy(
                preferredModels = models,
                favoritesEnabled = settings.favoritesEnabled,
                sessionMode = settings.activeSessionMode,
            )
        }.launchIn(viewModelScope)
    }

    /**
     * Icona Casa/Ristorante in topAppBar (§ richiesta utente): persistita subito, non solo al
     * momento di "Analizza", perché pilota anche i colori dell'intera app (vedi MainActivity).
     */
    fun setSessionMode(mode: SessionMode) {
        _uiState.value = _uiState.value.copy(sessionMode = mode)
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

    /** "+" in Homepage (§ richiesta utente): aggiunge un preferito da database senza passare da Impostazioni. */
    fun addComboFavorite(foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) {
        viewModelScope.launch {
            val nextOrder = _uiState.value.favorites.size
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(label, grams, choPer100g, grassiPer100g, nextOrder)
        }
    }

    /**
     * Preferito "combo" con peso già noto (§ richiesta utente "i preferiti, se toccati, aggiungono
     * direttamente al totale"): calcola subito il totale e lo pubblica in sessione, senza passare
     * da nessuna schermata intermedia — il chiamante naviga poi dritto al risultato finale.
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
        sessionStore.setTotals(AnalysisTotals(listOf(line)))
    }

    /**
     * Preferito legato a una voce di cronologia, o combo senza peso fissato (§ richiesta utente "i
     * preferiti portano alla sezione per inserire la quantità"): pubblica le righe già note in
     * sessione, il chiamante naviga poi alla composizione manuale che le carica direttamente nel
     * passo Quantità, pronte da modificare.
     *
     * `suspend` invece di un `viewModelScope.launch` interno: il chiamante deve aspettare che la
     * sessione sia popolata prima di navigare, altrimenti ManualFoodEntryViewModel potrebbe
     * inizializzarsi prima che [AnalysisSessionStore.setPendingManualRows] sia stato chiamato.
     */
    suspend fun openFavoriteForQuantityEntry(favorite: FavoriteDisplay) {
        sessionStore.reset()
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
