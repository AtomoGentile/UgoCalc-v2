package com.ugocalc.app.ui.finalresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
)

@HiltViewModel
class FinalResultViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
    private val historyRepository: HistoryRepository,
    private val tableRepository: TableRepository,
    private val aiOrchestrator: AiOrchestrator,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinalResultUiState())
    val uiState: StateFlow<FinalResultUiState> = _uiState

    init {
        sessionStore.session.onEach { session ->
            val totals = session.totals
            val defaultTitle = totals?.lines?.maxByOrNull { it.recognizedFood.pesoStimatoG }?.recognizedFood?.nome ?: "Piatto"
            _uiState.value = _uiState.value.copy(
                totals = totals,
                foodTitle = _uiState.value.foodTitle.ifBlank { defaultTitle },
                modelNames = session.selectedModels.map { it.displayName },
            )
        }.launchIn(viewModelScope)
    }

    fun setFoodTitle(title: String) {
        _uiState.value = _uiState.value.copy(foodTitle = title)
    }

    /**
     * Correzione manuale di un abbinamento (§ ricerca tra i cibi del database): l'utente ha
     * cercato e scelto direttamente la voce di tabella corretta, quindi il nuovo abbinamento è
     * per definizione certo — sostituisce quello (sbagliato o assente) trovato dall'AI. I totali
     * si ricalcolano automaticamente perché [init] resta in ascolto di `sessionStore.session`.
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
     * L'utente corregge il nome che l'AI ha riconosciuto (es. l'AI dice "pane" ma è "pasta"):
     * l'abbinamento precedente (se c'era) riguardava il nome sbagliato, quindi non ha più senso —
     * la riga torna "senza corrispondenza" finché l'utente non la ricerca di nuovo (manualmente o
     * online) col nome corretto.
     */
    fun editRecognizedFoodName(lineIndex: Int, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val original = totals.lines[lineIndex]
        if (trimmed == original.recognizedFood.nome) return
        val updatedLine = original.copy(
            recognizedFood = original.recognizedFood.copy(nome = trimmed),
            match = null,
            tableEntry = null,
        )
        val newLines = totals.lines.toMutableList().apply { this[lineIndex] = updatedLine }
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
        viewModelScope.launch {
            val historyId = historyRepository.saveAnalysis(_uiState.value.foodTitle, totals, _uiState.value.modelNames)
            if (_uiState.value.favoriteRequested) {
                val nextOrder = favoritesRepository.observeFavorites().first().size
                favoritesRepository.addFavorite(historyId, _uiState.value.foodTitle, nextOrder)
            }
            sessionStore.reset()
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
