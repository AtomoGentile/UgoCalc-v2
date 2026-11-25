package com.ugocalc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.ugocalc.app.data.local.data.StarterFoodDatabase
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
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val matchesFilters: (FoodSearchResult) -> Boolean = { result ->
        (!showChoFilter || result.entry.choPer100g.toFloat() in choRange) &&
            (selectedCategory == null || StarterFoodDatabase.categoryForFoodName(result.entry.nome) == selectedCategory)
    }
    val filteredResults = remember(results, showChoFilter, choRange, selectedCategory) {
        results.filter(matchesFilters)
    }
    val filteredMostSearched = remember(mostSearched, showChoFilter, choRange, selectedCategory) {
        mostSearched.filter(matchesFilters)
    }
    val recentIds = remember(filteredMostSearched) { filteredMostSearched.map { it.entryId }.toSet() }
    val filteredBrowseEntries = remember(browseEntries, showChoFilter, choRange, selectedCategory, recentIds) {
        browseEntries.filter { it.entryId !in recentIds && matchesFilters(it) }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFoodName by remember { mutableStateOf("") }
    var newFoodCho by remember { mutableStateOf("") }
    var newFoodProteine by remember { mutableStateOf("") }
    var newFoodGrassi by remember { mutableStateOf("") }
    var newFoodGi by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { if (fillAvailableHeight) it.fillMaxHeight() else it },
    ) {
        EditorialSearchBarRow(
            query = query,
            onQueryChange = viewModel::setQuery,
            placeholder = "Cerca alimento…",
            trailingAction = {
                FloatingCircleIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Crea nuovo alimento",
                    onClick = {
                        newFoodName = query.trim()
                        newFoodCho = ""
                        newFoodProteine = ""
                        newFoodGrassi = ""
                        newFoodGi = ""
                        showCreateDialog = true
                    },
                    size = 44.dp,
                    iconSize = 21.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            },
        )

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

        Spacer(modifier = Modifier.height(8.dp))
        CategoryFilterChipsRow(
            selectedCategory = selectedCategory,
            onSelectCategory = viewModel::setCategory,
        )

        val resultsModifier = if (fillAvailableHeight) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().heightIn(max = 320.dp)
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val listBottomPadding = if (fillAvailableHeight) navBarBottom + 108.dp else 16.dp

        Spacer(modifier = Modifier.height(8.dp))

        if (query.isBlank()) {
            if (filteredMostSearched.isEmpty() && filteredBrowseEntries.isEmpty()) {
                val activeCat = selectedCategory
                EmptyStateMessage(
                    icon = Icons.Default.Restaurant,
                    title = if (activeCat != null) "Nessun alimento in ${activeCat.label}" else "Nessun alimento disponibile",
                    message = if (activeCat != null) {
                        "Prova a selezionare \"Tutti\" o a cambiare fascia CHO, oppure tocca + per crearne uno."
                    } else {
                        "Tocca + per inserire un alimento manuale oppure importa una tabella nelle Impostazioni."
                    },
                    actionLabel = if (activeCat != null) "Mostra tutti" else null,
                    onAction = if (activeCat != null) { { viewModel.setCategory(null) } } else null,
                    modifier = resultsModifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = resultsModifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = listBottomPadding),
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
                                highlightQuery = "",
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
                            val browseTitle = selectedCategory?.let {
                                "${it.label} (${filteredBrowseEntries.size})"
                            } ?: "Esplora alimenti (${filteredBrowseEntries.size})"
                            EditorialSectionLabel(
                                title = browseTitle,
                                modifier = Modifier.padding(top = if (filteredMostSearched.isNotEmpty()) 12.dp else 4.dp, bottom = 2.dp),
                            )
                        }
                        items(filteredBrowseEntries, key = { "browse_${it.entryId}" }) { result ->
                            FoodResultRow(
                                result = result,
                                highlightQuery = "",
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
                val activeCat = selectedCategory
                EmptyStateMessage(
                    icon = Icons.Default.SearchOff,
                    title = "Nessun risultato",
                    message = if (activeCat != null) {
                        "Nessun alimento trovato per \"$query\" in ${activeCat.label}. Puoi cambiare categoria o crearlo con +."
                    } else {
                        "Nessun alimento trovato per \"$query\". Puoi crearlo con il pulsante +."
                    },
                    actionLabel = "Crea \"$query\"",
                    onAction = {
                        newFoodName = query.trim()
                        newFoodCho = ""
                        newFoodProteine = ""
                        newFoodGrassi = ""
                        newFoodGi = ""
                        showCreateDialog = true
                    },
                    modifier = resultsModifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = resultsModifier,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = listBottomPadding),
                ) {
                    item(key = "__header_search_results__") {
                        val resultsTitle = selectedCategory?.let {
                            "${it.label} (${filteredResults.size})"
                        } ?: "Risultati (${filteredResults.size})"
                        EditorialSectionLabel(
                            title = resultsTitle,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                        )
                    }
                    items(filteredResults, key = { "search_${it.entryId}" }) { result ->
                        FoodResultRow(
                            result = result,
                            highlightQuery = query,
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
        val protParsed = if (newFoodProteine.isBlank()) 0.0 else newFoodProteine.parseLocaleDoubleOrNull()
        val grassiParsed = if (newFoodGrassi.isBlank()) 0.0 else newFoodGrassi.parseLocaleDoubleOrNull()
        val giTrimmed = newFoodGi.trim()
        val giParsed = if (giTrimmed.isEmpty()) null else giTrimmed.toIntOrNull()
        val choValid = choParsed != null && choParsed >= 0.0
        val protValid = protParsed != null && protParsed >= 0.0
        val grassiValid = grassiParsed != null && grassiParsed >= 0.0
        val giValid = giTrimmed.isEmpty() || (giParsed != null && giParsed in 0..100)
        val canSubmit = nameValid && choValid && protValid && grassiValid && giValid

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
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                ) {
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
                        value = newFoodProteine,
                        onValueChange = { newFoodProteine = it },
                        label = { Text("Proteine per 100g") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        isError = !protValid,
                        supportingText = if (!protValid) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFoodGi,
                        onValueChange = { newFoodGi = it },
                        label = { Text("Indice Glicemico (facoltativo, 0–100)") },
                        placeholder = { Text("es. 49") },
                        singleLine = true,
                        isError = !giValid,
                        supportingText = if (!giValid) {
                            { Text("Inserisci un intero tra 0 e 100") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                val prot = protParsed ?: 0.0
                                val grassi = grassiParsed ?: 0.0
                                viewModel.createManualFood(newFoodName.trim(), cho, grassi, prot, giParsed) { created ->
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

@Composable
private fun CategoryFilterChipsRow(
    selectedCategory: StarterFoodDatabase.ItalianFoodCategory?,
    onSelectCategory: (StarterFoodDatabase.ItalianFoodCategory?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item(key = "__all__") {
            val isSelected = selectedCategory == null
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(null) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = "Tutti",
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
        items(StarterFoodDatabase.ItalianFoodCategory.entries, key = { it.name }) { category ->
            val isSelected = selectedCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(if (isSelected) null else category) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
                label = {
                    Text(
                        text = category.label,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodResultRow(
    result: FoodSearchResult,
    highlightQuery: String = "",
    onFavorite: ((NormalizedFoodEntry) -> Unit)?,
    showPin: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val isDark = isDarkSurfaceTheme()
    val isSelectedHighlight = selectionMode && selected

    Surface(
        color = if (isSelectedHighlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.30f else 0.40f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FramelessListRow(
            onClick = onClick,
            showDivider = !isSelectedHighlight,
            leading = if (selectionMode) {
                {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                }
            } else null,
            trailing = {
                if (showPin) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (result.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (result.pinned) "Non fissare più" else "Fissa in cima",
                            tint = if (result.pinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (onFavorite != null) {
                    IconButton(onClick = { onFavorite(result.entry) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = "Aggiungi ai preferiti",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            },
        ) {
            Text(
                text = if (highlightQuery.isNotBlank()) {
                    buildHighlightedMatchText(
                        text = result.entry.nome,
                        query = highlightQuery,
                        highlightColor = MaterialTheme.colorScheme.onSurface,
                        baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                } else {
                    androidx.compose.ui.text.AnnotatedString(result.entry.nome)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlightQuery.isNotBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                MacroPill(
                    label = "CHO",
                    value = formatCompactGrams(result.entry.choPer100g),
                    containerColor = UgoSemanticColors.choContainer,
                    contentColor = UgoSemanticColors.onChoContainer,
                    compact = true,
                )
                if (result.entry.proteinePer100g > 0.0) {
                    MacroPill(
                        label = "P",
                        value = formatCompactGrams(result.entry.proteinePer100g),
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                        compact = true,
                    )
                }
                MacroPill(
                    label = "G",
                    value = formatCompactGrams(result.entry.grassiPer100g),
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
                    compact = true,
                )
                result.entry.indiceGlicemico?.let { gi ->
                    MacroPill(
                        label = "IG",
                        value = "$gi",
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        compact = true,
                    )
                }
            }
            if (result.tableDisplayName.isNotBlank()) {
                Text(
                    text = "${result.tableDisplayName} · per 100g",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
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

    private val _selectedCategory = MutableStateFlow<StarterFoodDatabase.ItalianFoodCategory?>(null)
    val selectedCategory: StateFlow<StarterFoodDatabase.ItalianFoodCategory?> = _selectedCategory

    fun setChoRange(range: ClosedFloatingPointRange<Float>) {
        _choRange.value = range
    }

    fun setCategory(category: StarterFoodDatabase.ItalianFoodCategory?) {
        _selectedCategory.value = category
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
        proteine: Double = 0.0,
        indiceGlicemico: Int? = null,
        onCreated: (FoodSearchResult) -> Unit,
    ) {
        viewModelScope.launch {
            val created = tableRepository.addManualEntryWithResult(nome, cho, grassi, proteine, indiceGlicemico)
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
        _browseEntries.value = tableRepository.getBrowseEntries(limit = 1000)
    }
}
