package com.ugocalc.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.R
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
    onNavigateToRecipes: () -> Unit,
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
            val primary = MaterialTheme.colorScheme.primary
            val itemColors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = Color.Transparent,
                unselectedContainerColor = Color.Transparent,
                selectedTextColor = primary,
                selectedIconColor = primary,
            )
            ModalDrawerSheet(modifier = Modifier.width(240.dp)) {
                Text(
                    "UgoCalc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
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
                    label = { Text("Ricette") },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToRecipes() },
                )
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                )
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ugocalc_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "Versione ${com.ugocalc.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = UgoGrey,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
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
                        IconButton(
                            onClick = { viewModel.setSessionMode(if (uiState.sessionMode == SessionMode.CASA) SessionMode.RISTORANTE else SessionMode.CASA) }
                        ) {
                            Icon(
                                if (uiState.sessionMode == SessionMode.CASA) Icons.Default.Home else Icons.Default.Restaurant,
                                contentDescription = if (uiState.sessionMode == SessionMode.CASA) "Modalità Casa" else "Modalità Ristorante"
                            )
                        }
                    },
                )
            },
        ) { padding: PaddingValues ->
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalyzeButton(
                        state = uiState.analyzeButtonState,
                        // Margine extra a sinistra in landscape (§ richiesta utente): evita che il
                        // pulsantone finisca proprio sotto la selfie cam o il punch-hole.
                        modifier = Modifier.padding(start = 32.dp).size(180.dp),
                        onClick = {
                            val preferred = uiState.preferredModels
                            if (preferred.isNotEmpty()) {
                                viewModel.startAnalysisWithPreferredModels()
                                onNavigateDirectlyToCamera(preferred.map { it.id })
                            } else {
                                viewModel.startNewAnalysis()
                                onNavigateToModelSelection()
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        item {
                            OutlinedButton(
                                enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                                onClick = {
                                    viewModel.startNewAnalysis()
                                    onNavigateToManualEntry()
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            ) { Text("Aggiungi alimenti manualmente") }
                        }

                        item {
                            OutlinedButton(
                                enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                                onClick = onNavigateToBarcodeScan,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Scansiona codice a barre")
                            }
                        }

                        if (uiState.favoritesEnabled) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Preferiti", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { showAddFavoriteDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                                    }
                                }
                            }
                            items(uiState.favorites, key = { it.id }) { favorite ->
                                FavoriteCard(
                                    favorite = favorite,
                                    onClick = {
                                        if (favorite.comboGrams != null) {
                                            viewModel.useComboFavoriteDirectly(favorite)
                                            onFavoriteReadyForResult()
                                        } else {
                                            scope.launch {
                                                viewModel.openFavoriteForQuantityEntry(favorite)
                                                onFavoriteNeedsQuantity()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    item {
                        Box(modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)) {
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
                    }

                    item {
                        OutlinedButton(
                            enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                            onClick = {
                                viewModel.startNewAnalysis()
                                onNavigateToManualEntry()
                            },
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) { Text("Aggiungi alimenti manualmente") }
                    }

                    item {
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
                    }

                    item {
                        OutlinedButton(
                            enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                            onClick = onNavigateToBarcodeScan,
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Scansiona codice a barre")
                        }
                    }

                    if (uiState.favoritesEnabled) {
                        item {
                            IconButton(
                                onClick = { showAddFavoriteDialog = true },
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Aggiungi preferito dal database")
                            }
                        }
                        items(uiState.favorites, key = { it.id }) { favorite ->
                            FavoriteCard(
                                favorite = favorite,
                                onClick = {
                                    if (favorite.comboGrams != null) {
                                        viewModel.useComboFavoriteDirectly(favorite)
                                        onFavoriteReadyForResult()
                                    } else {
                                        scope.launch {
                                            viewModel.openFavoriteForQuantityEntry(favorite)
                                            onFavoriteNeedsQuantity()
                                        }
                                    }
                                }
                            )
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
private fun FavoriteCard(favorite: FavoriteDisplay, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
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

@Composable
private fun AnalyzeButton(state: AnalyzeButtonState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = when (state) {
        AnalyzeButtonState.ENABLED_ONLINE -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        AnalyzeButtonState.ENABLED_OFFLINE -> androidx.compose.material3.MaterialTheme.colorScheme.error
        AnalyzeButtonState.DISABLED_NO_TABLE -> UgoGrey
    }
    Box(
        modifier = modifier
            .size(160.dp)
            .background(color, CircleShape)
            .clickable(enabled = state != AnalyzeButtonState.DISABLED_NO_TABLE) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("Analizza", color = androidx.compose.ui.graphics.Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}
