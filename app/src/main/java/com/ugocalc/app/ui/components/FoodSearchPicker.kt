package com.ugocalc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.FoodSearchResult
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fascia CHO per 100g del cursore filtro nella ricerca. */
const val FOOD_SEARCH_CHO_FILTER_MAX = 100f

/**
 * Componente riusabile per cercare un alimento tra tutte le tabelle nutrizionali salvate in Room.
 * A query vuota mostra "Ultime ricerche" (se presenti) e "Esplora alimenti" dalle tabelle locali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchPicker(
    onSelect: (NormalizedFoodEntry) -> Unit,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
    onFavorite: ((NormalizedFoodEntry) -> Unit)? = null,
    showChoFilter: Boolean = false,
    selectedEntryIds: Set<Long>? = null,
    onToggleSelect: ((FoodSearchResult) -> Unit)? = null,
    viewModel: FoodSearchPickerViewModel = hiltViewModel(),
) {
    val selectionMode = selectedEntryIds != null && onToggleSelect != null
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val mostSearched by viewModel.mostSearched.collectAsState()
    val browseEntries by viewModel.browseEntries.collectAsState()
    val choRange by viewModel.choRange.collectAsState()

    val filteredResults = if (showChoFilter) {
        results.filter { it.entry.choPer100g.toFloat() in choRange }
    } else {
        results
    }
    val filteredMostSearched = if (showChoFilter) {
        mostSearched.filter { it.entry.choPer100g.toFloat() in choRange }
    } else {
        mostSearched
    }
    val recentIds = remember(filteredMostSearched) { filteredMostSearched.map { it.entryId }.toSet() }
    val filteredBrowseEntries = remember(browseEntries, showChoFilter, choRange, recentIds) {
        val base = if (showChoFilter) {
            browseEntries.filter { it.entry.choPer100g.toFloat() in choRange }
        } else {
            browseEntries
        }
        base.filter { it.entryId !in recentIds }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFoodName by remember { mutableStateOf("") }
    var newFoodCho by remember { mutableStateOf("") }
    var newFoodGrassi by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { if (fillAvailableHeight) it.fillMaxHeight() else it },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Cerca alimento…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Pulisci ricerca",
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = {
                    newFoodName = query.trim()
                    newFoodCho = ""
                    newFoodGrassi = ""
                    showCreateDialog = true
                },
                modifier = Modifier.size(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crea nuovo alimento",
                )
            }
        }

        if (showChoFilter) {
            Spacer(modifier = Modifier.height(10.dp))
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filtro CHO per 100g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${choRange.start.toInt()}–${choRange.endInclusive.toInt()} g",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = UgoSemanticColors.cho,
                    )
                }
                RangeSlider(
                    value = choRange,
                    onValueChange = viewModel::setChoRange,
                    valueRange = 0f..FOOD_SEARCH_CHO_FILTER_MAX,
                )
            }
        }

        val resultsModifier = if (fillAvailableHeight) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().heightIn(max = 320.dp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (query.isBlank()) {
            if (filteredMostSearched.isEmpty() && filteredBrowseEntries.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Default.Restaurant,
                    title = "Nessun alimento disponibile",
                    message = "Tocca + per inserire un alimento manuale oppure importa una tabella nelle Impostazioni.",
                    modifier = resultsModifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = resultsModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (filteredMostSearched.isNotEmpty()) {
                        item(key = "__header_recent__") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            ) {
                                Text(
                                    text = "Ultime ricerche",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { viewModel.clearMostSearched() },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancella ultime ricerche",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        items(filteredMostSearched, key = { "recent_${it.entryId}" }) { result ->
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
                                        viewModel.selectResult(result)
                                        onSelect(result.entry)
                                    }
                                },
                                onTogglePin = { viewModel.togglePin(result.entryId) },
                            )
                        }
                    }

                    if (filteredBrowseEntries.isNotEmpty()) {
                        item(key = "__header_browse__") {
                            UgoSectionHeader(
                                title = "Esplora alimenti",
                                subtitle = "Dalle tue tabelle nutrizionali",
                                modifier = Modifier.padding(top = if (filteredMostSearched.isNotEmpty()) 8.dp else 2.dp),
                            )
                        }
                        items(filteredBrowseEntries, key = { "browse_${it.entryId}" }) { result ->
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
                                        viewModel.selectResult(result)
                                        onSelect(result.entry)
                                    }
                                },
                                onTogglePin = {},
                            )
                        }
                    }
                }
            }
        } else {
            if (filteredResults.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Default.SearchOff,
                    title = "Nessun risultato",
                    message = "Nessun alimento trovato per \"$query\". Puoi crearlo con il pulsante +.",
                    actionLabel = "Crea \"$query\"",
                    onAction = {
                        newFoodName = query.trim()
                        newFoodCho = ""
                        newFoodGrassi = ""
                        showCreateDialog = true
                    },
                    modifier = resultsModifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = resultsModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filteredResults, key = { "search_${it.entryId}" }) { result ->
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
                                    viewModel.selectResult(result)
                                    onSelect(result.entry)
                                }
                            },
                            onTogglePin = {},
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val nameValid = newFoodName.isNotBlank()
        val choParsed = if (newFoodCho.isBlank()) 0.0 else newFoodCho.parseLocaleDoubleOrNull()
        val grassiParsed = if (newFoodGrassi.isBlank()) 0.0 else newFoodGrassi.parseLocaleDoubleOrNull()
        val choValid = choParsed != null && choParsed >= 0.0
        val grassiValid = grassiParsed != null && grassiParsed >= 0.0
        val canSubmit = nameValid && choValid && grassiValid

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Crea nuovo alimento",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newFoodName,
                        onValueChange = { newFoodName = it },
                        label = { Text("Nome alimento") },
                        singleLine = true,
                        isError = !nameValid,
                        supportingText = if (!nameValid) {
                            { Text("Campo obbligatorio") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFoodCho,
                        onValueChange = { newFoodCho = it },
                        label = { Text("CHO per 100g") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        isError = !choValid,
                        supportingText = if (!choValid) {
                            { Text("Inserisci un numero non negativo") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFoodGrassi,
                        onValueChange = { newFoodGrassi = it },
                        label = { Text("Grassi per 100g") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        isError = !grassiValid,
                        supportingText = if (!grassiValid) {
                            { Text("Inserisci un numero non negativo") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Annulla")
                        }
                        Button(
                            enabled = canSubmit,
                            onClick = {
                                val cho = choParsed ?: 0.0
                                val grassi = grassiParsed ?: 0.0
                                viewModel.createManualFood(newFoodName.trim(), cho, grassi) { created ->
                                    if (selectionMode) {
                                        onToggleSelect!!(created)
                                    } else {
                                        viewModel.selectResult(created)
                                        onSelect(created.entry)
                                    }
                                }
                                showCreateDialog = false
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Crea")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val isDark = isDarkSurfaceTheme()
    val containerColor = when {
        selectionMode && selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.45f)
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val borderColor = if (selectionMode && selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }

    UgoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        containerColor = containerColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.entry.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${"%.1f".format(result.entry.choPer100g)}g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    MacroPill(
                        label = "Grassi",
                        value = "${"%.1f".format(result.entry.grassiPer100g)}g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                    )
                }
                if (result.tableDisplayName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${result.tableDisplayName} · per 100g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showPin) {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (result.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (result.pinned) "Non fissare più" else "Fissa in cima",
                        tint = if (result.pinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onFavorite != null) {
                IconButton(onClick = { onFavorite(result.entry) }) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
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

    private val _browseEntries = MutableStateFlow<List<FoodSearchResult>>(emptyList())
    val browseEntries: StateFlow<List<FoodSearchResult>> = _browseEntries

    private val _choRange = MutableStateFlow(0f..FOOD_SEARCH_CHO_FILTER_MAX)
    val choRange: StateFlow<ClosedFloatingPointRange<Float>> = _choRange

    fun setChoRange(range: ClosedFloatingPointRange<Float>) {
        _choRange.value = range
    }

    init {
        _query
            .debounce(250)
            .onEach { q -> _results.value = tableRepository.searchEntries(q) }
            .launchIn(viewModelScope)

        tableRepository.observeTables()
            .onEach { refreshAll() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch { refreshAll() }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Registra la scelta di un risultato, aggiornando la cronologia delle ricerche. */
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

    /** Cancella la cronologia delle ricerche mantenendo le voci fissate con lo spillo. */
    fun clearMostSearched() {
        viewModelScope.launch {
            tableRepository.clearMostSearched()
            refreshMostSearched()
        }
    }

    fun createManualFood(
        nome: String,
        cho: Double,
        grassi: Double,
        onCreated: (FoodSearchResult) -> Unit,
    ) {
        viewModelScope.launch {
            val created = tableRepository.addManualEntryWithResult(nome, cho, grassi)
            refreshBrowseEntries()
            if (_query.value.isNotBlank()) {
                _results.value = tableRepository.searchEntries(_query.value)
            }
            onCreated(created)
        }
    }

    private suspend fun refreshAll() {
        refreshMostSearched()
        refreshBrowseEntries()
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            _results.value = tableRepository.searchEntries(currentQuery)
        }
    }

    private suspend fun refreshMostSearched() {
        _mostSearched.value = tableRepository.getMostSearched()
    }

    private suspend fun refreshBrowseEntries() {
        _browseEntries.value = tableRepository.getBrowseEntries()
    }
}
