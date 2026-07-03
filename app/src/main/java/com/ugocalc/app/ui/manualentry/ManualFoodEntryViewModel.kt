package com.ugocalc.app.ui.manualentry

import androidx.lifecycle.ViewModel
import com.ugocalc.app.data.repository.FoodSearchResult
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** I due passi del flusso (§ richiesta utente "lascia fare solo la selezione, poi un menù per la quantità"). */
enum class ManualEntryStep { SELECT, QUANTITY }

/**
 * Un alimento scelto tramite [com.ugocalc.app.ui.components.FoodSearchPicker], col peso ancora da
 * confermare/modificare. [entryId] è nullo per le righe arrivate da fuori dalla ricerca (scanner
 * codici a barre, preferiti): serve solo per far quadrare la selezione quando si torna al passo SELECT.
 */
data class ManualFoodRow(
    val entryId: Long? = null,
    val entry: NormalizedFoodEntry,
    val gramsText: String = "100",
) {
    val gramsOrNull: Double? get() = gramsText.toDoubleOrNull()?.takeIf { it > 0 }
}

data class ManualFoodEntryUiState(
    val step: ManualEntryStep = ManualEntryStep.SELECT,
    val selected: List<FoodSearchResult> = emptyList(),
    val rows: List<ManualFoodRow> = emptyList(),
    /** true se si è arrivati già nel passo Quantità senza passare dalla selezione (scanner, preferiti): "Indietro" deve uscire subito invece di mostrare un passo Seleziona vuoto. */
    val skipSelectStep: Boolean = false,
) {
    val canProceed: Boolean get() = rows.isNotEmpty() && rows.all { it.gramsOrNull != null }
}

/**
 * Composizione manuale di un pasto (§ ricerca tra i cibi del database): l'utente cerca e sceglie
 * direttamente le voci di tabella (passo SELEZIONA), poi indica il peso di ognuna (passo
 * QUANTITÀ) — nessuna chiamata AI, ogni riga è già un abbinamento certo, il risultato va dritto
 * alla schermata finale.
 */
@HiltViewModel
class ManualFoodEntryViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualFoodEntryUiState())
    val uiState: StateFlow<ManualFoodEntryUiState> = _uiState

    init {
        // Righe già quantificate (§ preferiti che riaprono la modifica quantità) hanno precedenza
        // sull'alimento singolo dello scanner codici a barre: sono alternative, mai entrambe presenti.
        val pendingRows = sessionStore.consumePendingManualRows()
        val pendingEntry = sessionStore.consumePendingManualEntry()
        when {
            pendingRows != null -> _uiState.value = ManualFoodEntryUiState(
                step = ManualEntryStep.QUANTITY,
                rows = pendingRows.map { ManualFoodRow(entry = it.entry, gramsText = formatGrams(it.grams)) },
                skipSelectStep = true,
            )
            pendingEntry != null -> _uiState.value = ManualFoodEntryUiState(
                step = ManualEntryStep.QUANTITY,
                rows = listOf(ManualFoodRow(entry = pendingEntry)),
                skipSelectStep = true,
            )
        }
    }

    private fun formatGrams(grams: Double): String =
        if (grams == grams.toLong().toDouble()) grams.toLong().toString() else "%.1f".format(grams)

    /** Tap su un risultato nel passo SELEZIONA: aggiunge/toglie dalla selezione, non aggiunge ancora nulla alla lista quantità. */
    fun toggleSelect(result: FoodSearchResult) {
        _uiState.update { state ->
            val exists = state.selected.any { it.entryId == result.entryId }
            state.copy(selected = if (exists) state.selected.filterNot { it.entryId == result.entryId } else state.selected + result)
        }
    }

    /** "Continua": passa al passo QUANTITÀ, riusando il peso già inserito per le voci ancora selezionate. */
    fun confirmSelection() {
        _uiState.update { state ->
            val existingByEntryId = state.rows.filter { it.entryId != null }.associateBy { it.entryId }
            val newRows = state.selected.map { result ->
                existingByEntryId[result.entryId] ?: ManualFoodRow(entryId = result.entryId, entry = result.entry)
            }
            state.copy(step = ManualEntryStep.QUANTITY, rows = newRows)
        }
    }

    /** "Indietro" dal passo QUANTITÀ: torna a SELEZIONA mantenendo la selezione corrente. */
    fun backToSelection() {
        _uiState.update { it.copy(step = ManualEntryStep.SELECT) }
    }

    fun removeFood(index: Int) {
        _uiState.update { state ->
            val removed = state.rows.getOrNull(index)
            val newSelected = if (removed?.entryId != null) state.selected.filterNot { it.entryId == removed.entryId } else state.selected
            state.copy(rows = state.rows.filterIndexed { i, _ -> i != index }, selected = newSelected)
        }
    }

    fun setGrams(index: Int, gramsText: String) {
        _uiState.update { state ->
            state.copy(rows = state.rows.mapIndexed { i, row -> if (i == index) row.copy(gramsText = gramsText) else row })
        }
    }

    /** "Calcola": pubblica direttamente i totali in sessione, come farebbe normalmente il Passaggio 3. */
    fun confirmAndProceed(): Boolean {
        val state = _uiState.value
        if (!state.canProceed) return false
        val lines = state.rows.map { row ->
            val grams = row.gramsOrNull!!
            CalculatedFoodLine(
                recognizedFood = RecognizedFood(
                    nome = row.entry.nome,
                    pesoStimatoG = grams,
                    commento = "Inserito manualmente",
                    confidenza = Confidence.ALTO,
                ),
                match = FoodMatch(
                    nomeRiconosciuto = row.entry.nome,
                    voceTabellaAbbinata = row.entry.nome,
                    nessunaCorrispondenza = false,
                    confidenzaAbbinamento = Confidence.ALTO,
                ),
                tableEntry = row.entry,
            )
        }
        sessionStore.setTotals(AnalysisTotals(lines))
        return true
    }
}
