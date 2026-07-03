package com.ugocalc.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.FoodSearchResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fascia CHO per 100g del cursore filtro nella ricerca (§ richiesta utente). */
const val FOOD_SEARCH_CHO_FILTER_MAX = 100f

/**
 * Componente riusabile per cercare un alimento tra tutte le tabelle di valori ufficiali
 * dell'utente (composizione manuale di un pasto, correzione di un abbinamento AI, ricerca libera
 * nel database): non fa chiamate AI, cerca solo per nome tra le voci già normalizzate e salvate
 * in Room. A campo vuoto mostra le "Ultime ricerche" (§ richiesta utente: rinominato da "Più
 * ricercate" perché di fatto mostra la cronologia, non una classifica per frequenza), con
 * possibilità di fissare una voce in cima con lo spillo.
 *
 * [fillAvailableHeight]: di default la lista risultati è alta al massimo 320dp, adatta a un
 * bottom sheet/dialog dove lo spazio verticale non è garantito. Attivalo quando il componente è
 * l'unico contenuto di una schermata a schermo intero (es. ricerca libera), così la lista occupa
 * tutto lo spazio residuo invece di lasciare area vuota sotto.
 *
 * [showChoFilter] (§ richiesta utente "filtri nella ricerca"): mostra un cursore per restringere i
 * risultati per CHO/100g. Disattivo di default nei picker piccoli (dialog/bottom sheet), dove
 * aggiungerebbe solo ingombro.
 *
 * [selectedEntryIds]/[onToggleSelect] (§ richiesta utente "nella ricerca manuale, lascia fare solo
 * la selezione"): se entrambi presenti, il tap su un risultato non chiama più [onSelect] ma
 * aggiunge/toglie l'alimento dalla selezione (mostrando una casella di spunta), lasciando al
 * chiamante il compito di far seguire un passo separato per le quantità.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchPicker(
    onSelect: (NormalizedFoodEntry) -> Unit,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
    /** Se presente, mostra una stellina per risultato per aggiungerlo ai preferiti (§ richiesta utente). */
    onFavorite: ((NormalizedFoodEntry) -> Unit)? = null,
    showChoFilter: Boolean = false,
    selectedEntryIds: Set<Long>? = null,
    onToggleSelect: ((FoodSearchResult) -> Unit)? = null,
    viewModel: FoodSearchPickerViewModel = hiltViewModel(),
) {
    val selectionMode = selectedEntryIds != null && onToggleSelect != null
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val mostSearched by viewModel.mostSearched.collectAsState()
    val choRange by viewModel.choRange.collectAsState()
    val filteredResults = if (showChoFilter) results.filter { it.entry.choPer100g.toFloat() in choRange } else results
    val filteredMostSearched = if (showChoFilter) mostSearched.filter { it.entry.choPer100g.toFloat() in choRange } else mostSearched

    Column(modifier = modifier.fillMaxWidth().let { if (fillAvailableHeight) it.fillMaxHeight() else it }) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text("Cerca un alimento nelle tue tabelle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showChoFilter) {
            Text(
                "CHO per 100g: ${choRange.start.toInt()}–${choRange.endInclusive.toInt()} g",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            RangeSlider(
                value = choRange,
                onValueChange = viewModel::setChoRange,
                valueRange = 0f..FOOD_SEARCH_CHO_FILTER_MAX,
            )
        }
        val resultsModifier = if (fillAvailableHeight) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().heightIn(max = 320.dp)
        }
        if (query.isBlank()) {
            if (filteredMostSearched.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                ) {
                    Text(
                        "Ultime ricerche",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.clearMostSearched() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Cancella ultime ricerche")
                    }
                }
                LazyColumn(modifier = resultsModifier) {
                    items(filteredMostSearched, key = { it.entryId }) { result ->
                        FoodResultRow(
                            result = result,
                            onFavorite = onFavorite,
                            showPin = true,
                            selectionMode = selectionMode,
                            selected = selectedEntryIds?.contains(result.entryId) == true,
                            onClick = {
                                if (selectionMode) {
                                    onToggleSelect!!(result)
                                } else {
                                    viewModel.selectResult(result); onSelect(result.entry)
                                }
                            },
                            onTogglePin = { viewModel.togglePin(result.entryId) },
                        )
                    }
                }
            }
        } else {
            if (filteredResults.isEmpty()) {
                Text(
                    "Nessun alimento trovato con questo nome nelle tue tabelle",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            LazyColumn(modifier = resultsModifier) {
                items(filteredResults, key = { it.entryId }) { result ->
                    FoodResultRow(
                        result = result,
                        onFavorite = onFavorite,
                        showPin = false,
                        selectionMode = selectionMode,
                        selected = selectedEntryIds?.contains(result.entryId) == true,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelect!!(result)
                            } else {
                                viewModel.selectResult(result); onSelect(result.entry)
                            }
                        },
                        onTogglePin = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodResultRow(
    result: FoodSearchResult,
    onFavorite: ((NormalizedFoodEntry) -> Unit)?,
    showPin: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(result.entry.nome, fontWeight = FontWeight.Bold)
                Text(
                    "CHO ${"%.1f".format(result.entry.choPer100g)} g · Grassi ${"%.1f".format(result.entry.grassiPer100g)} g per 100g — ${result.tableDisplayName}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (showPin) {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        if (result.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (result.pinned) "Non fissare più" else "Fissa in cima",
                        // § richiesta utente "espandi l'utilizzo del terziario, con qualche
                        // selettore": stesso ruolo della stellina preferiti, colore acceso solo da fissata.
                        tint = if (result.pinned) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    )
                }
            }
            if (onFavorite != null) {
                IconButton(onClick = { onFavorite(result.entry) }) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = "Aggiungi ai preferiti",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FoodSearchPickerViewModel @Inject constructor(
    private val tableRepository: TableRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<FoodSearchResult>>(emptyList())
    val results: StateFlow<List<FoodSearchResult>> = _results

    private val _mostSearched = MutableStateFlow<List<FoodSearchResult>>(emptyList())
    val mostSearched: StateFlow<List<FoodSearchResult>> = _mostSearched

    private val _choRange = MutableStateFlow(0f..FOOD_SEARCH_CHO_FILTER_MAX)
    val choRange: StateFlow<ClosedFloatingPointRange<Float>> = _choRange

    fun setChoRange(range: ClosedFloatingPointRange<Float>) {
        _choRange.value = range
    }

    init {
        // Debounce per non interrogare il database ad ogni singola battuta di tastiera.
        _query
            .debounce(250)
            .onEach { q -> _results.value = tableRepository.searchEntries(q) }
            .launchIn(viewModelScope)

        viewModelScope.launch { refreshMostSearched() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Registra la scelta di un risultato (§ "Più ricercate"), aggiornando subito la classifica. */
    fun selectResult(result: FoodSearchResult) {
        viewModelScope.launch {
            tableRepository.recordSearchSelection(result.entryId)
            refreshMostSearched()
        }
    }

    fun togglePin(entryId: Long) {
        viewModelScope.launch {
            tableRepository.toggleSearchPin(entryId)
            refreshMostSearched()
        }
    }

    /** "Cancella ultime ricerche" (§ richiesta utente): tiene le voci fissate con lo spillo. */
    fun clearMostSearched() {
        viewModelScope.launch {
            tableRepository.clearMostSearched()
            refreshMostSearched()
        }
    }

    private suspend fun refreshMostSearched() {
        _mostSearched.value = tableRepository.getMostSearched()
    }
}
