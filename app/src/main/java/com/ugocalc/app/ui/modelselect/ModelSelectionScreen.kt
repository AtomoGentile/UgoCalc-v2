package com.ugocalc.app.ui.modelselect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.R
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.BrandFilterRow
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.ModernModelCard
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Schermata di selezione modello (§2.4 punto 1): griglia theme-aware con filtro brand e ricerca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    onNext: (modelIds: List<String>) -> Unit,
    onBack: () -> Unit,
    onOpenApiSettings: () -> Unit = {},
    viewModel: ModelSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSavingCredentials by viewModel.isSavingCredentials.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val groupedByBrand = remember(uiState.availableModels) {
        uiState.availableModels.groupBy { modelBrand(it) }
    }
    val activeBrands = remember(groupedByBrand) {
        groupedByBrand.keys.sortedBy { brandOrder(it) }
    }
    val brandCounts = remember(groupedByBrand) {
        groupedByBrand.mapValues { it.value.size }
    }

    LaunchedEffect(activeBrands) {
        if (selectedBrand != null && selectedBrand !in activeBrands) {
            selectedBrand = null
        }
    }

    val filteredModels = remember(uiState.availableModels, selectedBrand, searchQuery) {
        val brandPool = if (selectedBrand == null) {
            uiState.availableModels
        } else {
            uiState.availableModels.filter { modelBrand(it) == selectedBrand }
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            brandPool
        } else {
            brandPool.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
            }
        }
    }

    val selectedCount = uiState.selectedModelUids.size
    val canProceed = selectedCount > 0 && !isSavingCredentials && !uiState.isOffline

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scegli i modelli") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Button(
                        enabled = canProceed,
                        onClick = { onNext(viewModel.confirmSelectionAndProceed()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        val ctaLabel = when (selectedCount) {
                            0 -> "Seleziona almeno 1 modello"
                            1 -> "Continua (1 modello)"
                            else -> "Continua ($selectedCount modelli)"
                        }
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (uiState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }

            if (uiState.isOffline) {
                StatusBanner(
                    icon = Icons.Filled.WifiOff,
                    title = "Sei offline",
                    subtitle = "Connettiti a Internet per selezionare e utilizzare i modelli AI.",
                    tone = BannerTone.WARNING,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                EmptyStateMessage(
                    icon = Icons.Filled.WifiOff,
                    title = "Connessione assente",
                    message = "Puoi comunque usare l'inserimento manuale o lo scanner codici a barre dalla Home.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (uiState.availableModels.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Filled.Key,
                    title = "Nessun modello disponibile",
                    message = "Configura una chiave API o credenziale provider per avviare l'analisi fotografica.",
                    actionLabel = "Configura API",
                    onAction = onOpenApiSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cerca modello o provider…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Pulisci ricerca",
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (activeBrands.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    BrandFilterRow(
                        brands = activeBrands,
                        selectedBrand = selectedBrand,
                        counts = brandCounts,
                        onSelect = { selectedBrand = it },
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$selectedCount di ${uiState.availableModels.size} selezionati",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.selectOnlyOne() },
                            enabled = uiState.availableModels.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Solo 1")
                        }
                        TextButton(
                            onClick = { viewModel.selectAll() },
                            enabled = selectedCount < uiState.availableModels.size,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Seleziona tutti")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (filteredModels.isEmpty()) {
                    EmptyStateMessage(
                        icon = Icons.Filled.SearchOff,
                        title = "Nessun modello trovato",
                        message = "Nessun modello corrisponde ai filtri attuali.",
                        actionLabel = "Azzera filtri",
                        onAction = {
                            searchQuery = ""
                            selectedBrand = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                    ) {
                        items(filteredModels, key = { it.uid }, contentType = { "model_card" }) { model ->
                            val onClick = remember(model.uid) { { viewModel.toggleModel(model.uid) } }
                            ModernModelCard(
                                model = model,
                                isSelected = model.uid in uiState.selectedModelUids,
                                onClick = onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modelBrand(model: AiModel): String = when (model.logoRes) {
    R.drawable.gpt -> "GPT"
    R.drawable.logoclaude -> "Claude"
    R.drawable.logogemini -> "Gemini"
    R.drawable.kimi_ai_logo_hd -> "Kimi"
    R.drawable.deepseek_logo -> "DeepSeek"
    R.drawable.nova -> "Amazon Nova"
    R.drawable.facebook_llama_logo_3dcc -> "Llama"
    R.drawable.mistral -> "Mistral"
    R.drawable.qwen_logo_png_seeklogo_611723 -> "Qwen"
    R.drawable.gemma -> "Gemma"
    R.drawable.cohere_logo_png_seeklogo_513871 -> "Cohere"
    R.drawable.ai21_labs_logo -> "AI21"
    R.drawable.nvidialogo -> "Nvidia"
    R.drawable.minimax -> "MiniMax"
    R.drawable.writer -> "Writer"
    R.drawable.zai -> "Zhipu"
    R.drawable.twelvelabs -> "TwelveLabs"
    else -> "Altro"
}

private fun brandOrder(brand: String): Int = when (brand) {
    "GPT" -> 0
    "Claude" -> 1
    "Gemini" -> 2
    "Kimi" -> 3
    "DeepSeek" -> 4
    "Amazon Nova" -> 5
    "Llama" -> 6
    "Mistral" -> 7
    "Qwen" -> 8
    "Gemma" -> 9
    "Cohere" -> 10
    "AI21" -> 11
    "Nvidia" -> 12
    "MiniMax" -> 13
    "Writer" -> 14
    "Zhipu" -> 15
    "TwelveLabs" -> 16
    else -> 999
}
