package com.ugocalc.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToModelSelection: () -> Unit,
    onNavigateDirectlyToCamera: (modelIds: List<String>) -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToBarcodeScan: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBiasEvolution: () -> Unit,
    onNavigateToSettings: () -> Unit,
    /** Preferito con peso già noto (§ richiesta utente): il totale è già stato calcolato, si va dritti al risultato. */
    onFavoriteReadyForResult: () -> Unit,
    /** Preferito legato a cronologia o senza peso fissato (§ richiesta utente "portano alla sezione quantità"). */
    onFavoriteNeedsQuantity: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAddFavoriteDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Larghezza ridotta rispetto al default (~360dp): con solo 3 voci brevi, il menu
            // occupava molto più spazio di quanto servisse.
            // Niente pillola piena dietro la voce selezionata: quella forma arrotondata, riempita
            // di un colore diverso dallo sfondo, creava un contrasto sgradevole con qualunque
            // sfondo scelto dall'utente (§ segnalazione utente, screenshot con sfondo scuro).
            // La selezione si vede solo dal testo/icona in colore primario, senza nessun blocco
            // colorato che possa stonare.
            val itemColors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            ModalDrawerSheet(modifier = Modifier.width(240.dp)) {
                Text("UgoCalc", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                NavigationDrawerItem(
                    label = { Text("Analisi", fontWeight = FontWeight.Bold) },
                    selected = true,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() } },
                )
                NavigationDrawerItem(
                    label = { Text("BIAS ed evoluzione del comportamento") },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToBiasEvolution() },
                )
                NavigationDrawerItem(
                    label = { Text("Cronologia") },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToHistory() },
                )
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("UgoCalc", fontWeight = FontWeight.Bold) },
                    colors = ugoTopAppBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Default.History, contentDescription = "Cronologia")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                        }
                    },
                )
            },
        ) { padding: PaddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(modifier = Modifier.padding(top = 48.dp, bottom = 24.dp)) {
                    AnalyzeButton(state = uiState.analyzeButtonState, onClick = {
                        val preferred = uiState.preferredModels
                        if (preferred.isNotEmpty()) {
                            viewModel.startAnalysisWithPreferredModels()
                            onNavigateDirectlyToCamera(preferred.map { it.id })
                        } else {
                            viewModel.startNewAnalysis()
                            onNavigateToModelSelection()
                        }
                    })
                }

                OutlinedButton(
                    enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                    onClick = {
                        viewModel.startNewAnalysis()
                        onNavigateToManualEntry()
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                ) { Text("Aggiungi alimenti manualmente") }

                if (uiState.analyzeButtonState == AnalyzeButtonState.DISABLED_NO_TABLE) {
                    Text(
                        text = "Carica una tabella di valori ufficiali nelle Impostazioni per iniziare",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else if (uiState.analyzeButtonState == AnalyzeButtonState.ENABLED_OFFLINE) {
                    Text(
                        text = "Nessuna connessione: nessun modello sarà selezionabile finché non torni online",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                // "In basso ma non troppo" (§ richiesta utente): subito sotto ai controlli di
                // analisi principali, non in fondo del tutto dove finirebbe nascosto sotto i
                // preferiti quando la lista è lunga.
                OutlinedButton(
                    enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                    onClick = onNavigateToBarcodeScan,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Scansiona codice a barre")
                }

                if (uiState.favoritesEnabled) {
                    IconButton(
                        onClick = { showAddFavoriteDialog = true },
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi preferito dal database")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(uiState.favorites) { favorite ->
                            // Bug fix: card più compatta (§ segnalazione utente "falli più
                            // compatti"): nome ed macro sulla stessa riga invece di impilati, meno
                            // padding, così se ne vedono di più senza scorrere.
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        if (favorite.comboGrams != null) {
                                            viewModel.useComboFavoriteDirectly(favorite)
                                            onFavoriteReadyForResult()
                                        } else {
                                            scope.launch {
                                                viewModel.openFavoriteForQuantityEntry(favorite)
                                                onFavoriteNeedsQuantity()
                                            }
                                        }
                                    },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        favorite.label,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    )
                                    Text(
                                        if (favorite.isPerHundredGrams) {
                                            "CHO ${favorite.choG.toInt()}g · Grassi ${favorite.grassiG.toInt()}g/100g"
                                        } else {
                                            "CHO ${favorite.choG.toInt()}g · Grassi ${favorite.grassiG.toInt()}g"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFavoriteDialog) {
        AddComboFavoriteDialog(
            onDismiss = { showAddFavoriteDialog = false },
            onConfirm = { foodName, grams, cho, grassi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi)
                showAddFavoriteDialog = false
            },
        )
    }
}

@Composable
private fun AnalyzeButton(state: AnalyzeButtonState, onClick: () -> Unit) {
    val color = when (state) {
        AnalyzeButtonState.ENABLED_ONLINE -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        AnalyzeButtonState.ENABLED_OFFLINE -> androidx.compose.material3.MaterialTheme.colorScheme.error
        AnalyzeButtonState.DISABLED_NO_TABLE -> UgoGrey
    }
    Box(
        modifier = Modifier
            .size(160.dp)
            .background(color, CircleShape)
            .clickable(enabled = state != AnalyzeButtonState.DISABLED_NO_TABLE) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("Analizza", color = androidx.compose.ui.graphics.Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}
