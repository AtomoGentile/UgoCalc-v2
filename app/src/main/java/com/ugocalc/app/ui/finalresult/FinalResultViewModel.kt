package com.ugocalc.app.ui.finalresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stato della ricerca online (§Pezzo E) per una singola riga del dettaglio, tenuto per indice di riga. */
sealed class OnlineResearchUiState {
    data object Loading : OnlineResearchUiState()
    data class Result(val result: OnlineFoodResearchResult) : OnlineResearchUiState()
    data class Error(val message: String) : OnlineResearchUiState()
}

data class FinalResultUiState(
    val totals: AnalysisTotals? = null,
    val foodTitle: String = "",
    val modelNames: List<String> = emptyList(),
    val saved: Boolean = false,
    val onlineResearch: Map<Int, OnlineResearchUiState> = emptyMap(),
    /** Stellina accanto a "Salva" (§ richiesta utente): se attiva, il salvataggio crea subito anche il preferito. */
    val favoriteRequested: Boolean = false,
    /** Scorciatoia "Registra peso vero" a fine analisi (§13 punto 3): mostrata solo se questa sessione ha davvero prodotto un record da correggere. */
    val showGroundTruthCta: Boolean = false,
    /** Stessa scorciatoia per il benchmark V1/V2 (§13 punto 5), indipendente dalla precedente. */
    val showBenchmarkGroundTruthCta: Boolean = false,
)

@HiltViewModel
class FinalResultViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
    private val historyRepository: HistoryRepository,
    private val tableRepository: TableRepository,
    private val aiOrchestrator: AiOrchestrator,
    private val favoritesRepository: FavoritesRepository,
    private val v2TelemetryRepository: V2TelemetryRepository,
    private val benchmarkRepository: BenchmarkRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinalResultUiState())
    val uiState: StateFlow<FinalResultUiState> = _uiState

    init {
        sessionStore.session
            .map { Triple(it.totals, it.selectedModels, it.totals?.lines) }
            .distinctUntilChanged()
            .onEach { (totals, selectedModels, _) ->
                val defaultTitle = totals?.lines?.maxByOrNull { it.recognizedFood.pesoStimatoG }?.recognizedFood?.nome ?: "Piatto"
                _uiState.value = _uiState.value.copy(
                    totals = totals,
                    foodTitle = _uiState.value.foodTitle.ifBlank { defaultTitle },
                    modelNames = selectedModels.map { it.displayName },
                )
            }.launchIn(viewModelScope)

        // Sessione stabile fino al reset (dopo Salva): il controllo va fatto una sola volta, non ad ogni aggiornamento dei totali.
        val sessionId = sessionStore.session.value.sessionId
        viewModelScope.launch {
            val showGroundTruth = v2TelemetryRepository.hasPendingGroundTruth(sessionId)
            val showBenchmarkGroundTruth = benchmarkRepository.hasPendingGroundTruth(sessionId)
            _uiState.update { it.copy(showGroundTruthCta = showGroundTruth, showBenchmarkGroundTruthCta = showBenchmarkGroundTruth) }
        }
    }

    fun setFoodTitle(title: String) {
        _uiState.value = _uiState.value.copy(foodTitle = title)
    }

    /**
     * Correzione manuale di un abbinamento (§ ricerca tra i cibi del database): l'utente ha
     * cercato e scelto direttamente la voce di tabella corretta, quindi il nuovo abbinamento è
     * per definizione certo — sostituisce quello (sbagliato o assente) trovato dall'AI. I totali
     * si ricalcolano automaticamente perché il ViewModel resta in ascolto di `sessionStore.session`.
     */
    fun applyManualMatch(lineIndex: Int, entry: NormalizedFoodEntry) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val original = totals.lines[lineIndex]
        val updatedLine = original.copy(
            match = FoodMatch(
                nomeRiconosciuto = original.recognizedFood.nome,
                voceTabellaAbbinata = entry.nome,
                nessunaCorrispondenza = false,
                confidenzaAbbinamento = Confidence.ALTO,
            ),
            tableEntry = entry,
        )
        val newLines = totals.lines.toMutableList().apply { this[lineIndex] = updatedLine }
        sessionStore.setTotals(AnalysisTotals(newLines))
    }

    /**
     * L'utente corregge il nome e/o il peso che l'AI ha riconosciuto (es. l'AI dice "pane" ma è
     * "pasta", o "165g" era in realtà "220g"). Un'unica funzione invece di due chiamate separate:
     * entrambe le modifiche vanno applicate all'oggetto originale e salvate con un solo
     * `sessionStore.setTotals`, altrimenti una seconda chiamata che rilegge `_uiState.value.totals`
     * subito dopo la prima rischierebbe di leggere lo stato precedente (lo StateFlow di
     * [AnalysisSessionStore] si ripropaga a `_uiState` in modo asincrono) e sovrascrivere la prima
     * modifica. Se il nome cambia, l'abbinamento precedente (se c'era) riguardava il nome sbagliato
     * e non ha più senso: la riga torna "senza corrispondenza" finché l'utente non la ricerca di
     * nuovo (manualmente o online) col nome corretto. Se cambia solo il peso, l'abbinamento resta
     * quello già trovato — CHO e grassi si ricalcolano da soli perché dipendono da
     * [com.ugocalc.app.domain.model.RecognizedFood.pesoStimatoG].
     */
    fun correctRecognizedFood(lineIndex: Int, newName: String, newGrams: Double?) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val original = totals.lines[lineIndex]

        val trimmedName = newName.trim()
        val nameChanged = (trimmedName.isNotBlank()) && (trimmedName != original.recognizedFood.nome)
        val weightChanged = newGrams != null && newGrams > 0.0 && newGrams != original.recognizedFood.pesoStimatoG
        if (!nameChanged && !weightChanged) return

        val updatedRecognizedFood = original.recognizedFood.copy(
            nome = if (nameChanged) trimmedName else original.recognizedFood.nome,
            pesoStimatoG = if (weightChanged) newGrams else original.recognizedFood.pesoStimatoG,
        )
        val updatedLine = if (nameChanged) {
            // Il nome cambiato invalida l'abbinamento precedente, riguardava un alimento diverso.
            original.copy(recognizedFood = updatedRecognizedFood, match = null, tableEntry = null)
        } else {
            original.copy(recognizedFood = updatedRecognizedFood)
        }
        val newLines = totals.lines.toMutableList().apply { this[lineIndex] = updatedLine }
        sessionStore.setTotals(AnalysisTotals(newLines))
    }

    /**
     * Aggiunge un alimento scelto direttamente dall'utente tra quelli in tabella, saltando del
     * tutto il riconoscimento AI — per un alimento che l'AI non ha visto nella foto, o quando
     * l'utente preferisce indicarlo a mano. Stesso costrutto usato dall'inserimento manuale puro
     * ([com.ugocalc.app.ui.manualentry.ManualFoodEntryViewModel]): l'abbinamento è per definizione
     * certo, non c'è nulla da matchare.
     */
    fun addManualFood(entry: NormalizedFoodEntry, grams: Double) {
        if (grams <= 0.0) return
        val newLine = CalculatedFoodLine(
            recognizedFood = RecognizedFood(nome = entry.nome, pesoStimatoG = grams, commento = "Inserito manualmente", confidenza = Confidence.ALTO),
            match = FoodMatch(nomeRiconosciuto = entry.nome, voceTabellaAbbinata = entry.nome, nessunaCorrispondenza = false, confidenzaAbbinamento = Confidence.ALTO),
            tableEntry = entry,
        )
        val newLines = (_uiState.value.totals?.lines ?: emptyList()) + newLine
        sessionStore.setTotals(AnalysisTotals(newLines))
    }

    /** Rimuove una riga già presente (aggiunta manualmente o riconosciuta dall'AI): correzione dell'errore opposto, un alimento visto per sbaglio. */
    fun removeLine(lineIndex: Int) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val newLines = totals.lines.toMutableList().apply { removeAt(lineIndex) }
        sessionStore.setTotals(AnalysisTotals(newLines))
    }

    /**
     * "Ricerca online la pietanza mancante" (§Pezzo E): oggi funziona solo se tra i modelli
     * selezionati per questa analisi c'è Gemini (unico provider con ricerca web reale, vedi
     * GeminiCompletionClient) — altrimenti mostra un errore chiaro invece di tentare una chiamata
     * che fallirebbe comunque.
     */
    fun searchOnline(lineIndex: Int) {
        val totals = _uiState.value.totals ?: return
        val line = totals.lines.getOrNull(lineIndex) ?: return
        val geminiModel = sessionStore.session.value.selectedModels.firstOrNull { it.provider == AiProviderType.GEMINI }
        if (geminiModel == null) {
            _uiState.update { it.copy(onlineResearch = it.onlineResearch + (lineIndex to OnlineResearchUiState.Error("La ricerca online richiede Gemini tra i modelli selezionati per questa analisi."))) }
            return
        }
        _uiState.update { it.copy(onlineResearch = it.onlineResearch + (lineIndex to OnlineResearchUiState.Loading)) }
        viewModelScope.launch {
            val newState = try {
                OnlineResearchUiState.Result(aiOrchestrator.runOnlineFoodResearch(geminiModel, line.recognizedFood.nome))
            } catch (e: AiCallException) {
                OnlineResearchUiState.Error(e.message ?: "Ricerca online non riuscita")
            }
            _uiState.update { it.copy(onlineResearch = it.onlineResearch + (lineIndex to newState)) }
        }
    }

    fun dismissOnlineResult(lineIndex: Int) {
        _uiState.update { it.copy(onlineResearch = it.onlineResearch - lineIndex) }
    }

    /** "Aggiungi al database": persiste il risultato trovato online e risolve subito la riga (stesso meccanismo di [applyManualMatch]). */
    fun addOnlineResultToDatabase(lineIndex: Int) {
        val state = _uiState.value.onlineResearch[lineIndex] as? OnlineResearchUiState.Result ?: return
        val result = state.result
        val cho = result.choPer100g ?: return
        val grassi = result.grassiPer100g ?: return
        viewModelScope.launch {
            val entry = tableRepository.addOnlineResearchEntry(result.alimento, cho, grassi)
            applyManualMatch(lineIndex, entry)
            dismissOnlineResult(lineIndex)
        }
    }

    /** Stellina accanto a "Salva" (§ richiesta utente): preferito creato al momento del salvataggio. */
    fun toggleFavoriteRequested() {
        _uiState.value = _uiState.value.copy(favoriteRequested = !_uiState.value.favoriteRequested)
    }

    /** Pulsante "Salva" (§2.4 punto 7). */
    fun save() {
        val totals = _uiState.value.totals ?: return
        val session = sessionStore.session.value
        viewModelScope.launch {
            val historyId = historyRepository.saveAnalysis(
                foodTitle = _uiState.value.foodTitle,
                totals = totals,
                modelDisplayNames = _uiState.value.modelNames,
                sessionId = session.sessionId,
                sessionMode = session.sessionMode,
            )
            if (_uiState.value.favoriteRequested) {
                val nextOrder = favoritesRepository.observeFavorites().first().size
                favoritesRepository.addFavorite(historyId, _uiState.value.foodTitle, nextOrder)
            }
            sessionStore.reset()
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
