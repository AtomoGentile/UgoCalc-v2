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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Button
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
import com.ugocalc.app.ui.components.GlassmorphicCard
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
            val primaryContainer = MaterialTheme.colorScheme.primaryContainer
            val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
            val itemColors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = primaryContainer,
                unselectedContainerColor = Color.Transparent,
                selectedTextColor = onPrimaryContainer,
                selectedIconColor = onPrimaryContainer,
            )
            ModalDrawerSheet(
                drawerState = drawerState,
                modifier = Modifier.width(240.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    "UgoCalc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Analisi", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    selected = true,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() } },
                )
                NavigationDrawerItem(
                    label = { Text("BIAS ed evoluzione") },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToBiasEvolution() },
                )
                NavigationDrawerItem(
                    label = { Text("Cronologia") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    selected = false,
                    colors = itemColors,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToHistory() },
                )
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
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
                Surface(
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                ) {
                    TopAppBar(
                        title = { Text("UgoCalc", fontWeight = FontWeight.Bold) },
                        colors = ugoTopAppBarColors(),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
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
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.startNewAnalysis()
                                    onNavigateToManualEntry()
                                },
                                enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Manuale")
                            }
                            FilledTonalButton(
                                onClick = onNavigateToBarcodeScan,
                                enabled = uiState.analyzeButtonState != AnalyzeButtonState.DISABLED_NO_TABLE,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Barcode")
                            }
                        }
                    }
                }
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
                        if (uiState.favoritesEnabled) {
                            item {
                                GlassmorphicCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Preferiti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { showAddFavoriteDialog = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = "Aggiungi", modifier = Modifier.size(20.dp))
                                        }
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
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    item {
                        Box(modifier = Modifier.padding(bottom = 32.dp)) {
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
                        if (uiState.analyzeButtonState == AnalyzeButtonState.DISABLED_NO_TABLE) {
                            Text(
                                text = "Carica una tabella di valori ufficiali nelle Impostazioni per iniziare",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        } else if (uiState.analyzeButtonState == AnalyzeButtonState.ENABLED_OFFLINE) {
                            Text(
                                text = "Nessuna connessione: nessun modello sarà selezionabile finché non torni online",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                    }

                    if (uiState.favoritesEnabled) {
                        item {
                            GlassmorphicCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Preferiti", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { showAddFavoriteDialog = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Aggiungi", modifier = Modifier.size(20.dp))
                                    }
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
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                favorite.label,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (favorite.isPerHundredGrams) {
                    "CHO ${favorite.choG.toInt()}g · Grassi ${favorite.grassiG.toInt()}g / 100g"
                } else {
                    "CHO ${favorite.choG.toInt()}g · Grassi ${favorite.grassiG.toInt()}g"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AnalyzeButton(state: AnalyzeButtonState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = when (state) {
        AnalyzeButtonState.ENABLED_ONLINE -> MaterialTheme.colorScheme.primary
        AnalyzeButtonState.ENABLED_OFFLINE -> MaterialTheme.colorScheme.error
        AnalyzeButtonState.DISABLED_NO_TABLE -> UgoGrey
    }
    val pulse = rememberInfiniteTransition(label = "aura_pulse")
    // Onda 1: parte subito
    val wave1 by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )
    // Onda 2: sfasata di metà ciclo
    val wave2 by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(2000),
        ),
        label = "wave2",
    )
    Box(
        modifier = modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        listOf(wave1, wave2).forEach { t ->
            val waveSizeDp = 160f + t * 100f
            val waveAlpha = when {
                t < 0.5f -> 0.25f * (t * 2f)
                else -> 0.25f * (2f - t * 2f)
            }
            Box(
                modifier = Modifier
                    .size(waveSizeDp.dp)
                    .background(color.copy(alpha = waveAlpha), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(color, CircleShape)
                .clickable(enabled = state != AnalyzeButtonState.DISABLED_NO_TABLE) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Analizza", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}
