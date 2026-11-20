package com.ugocalc.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSettingsScreen(
    onBack: () -> Unit,
    viewModel: FavoritesSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var showHistoryPicker by remember { mutableStateOf(false) }
    var showComboDialog by remember { mutableStateOf(false) }
    var pendingDeleteFavorite by remember { mutableStateOf<FavoriteShortcutEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferiti") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi preferito")
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Da cronologia") },
                        onClick = { showAddMenu = false; showHistoryPicker = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Da database") },
                        onClick = { showAddMenu = false; showComboDialog = true },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Mostra i preferiti in home",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Le scorciatoie selezionate appaiono in Homepage, sotto al pulsante Analizza.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settingsState.userSettings.favoritesEnabled,
                        onCheckedChange = { settingsViewModel.setFavoritesEnabled(it) },
                    )
                }
            }

            UgoSectionHeader(
                title = "Scorciatoie salvate",
                subtitle = "${state.favorites.size} preferiti",
            )

            if (state.favorites.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Default.Star,
                    title = "Nessun preferito salvato",
                    message = "Tocca + per aggiungere un pasto dalla cronologia o un alimento dal database.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.favorites, key = { it.id }) { favorite ->
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Text(
                                    text = favorite.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { pendingDeleteFavorite = favorite }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Rimuovi",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistoryPicker) {
        AlertDialog(
            onDismissRequest = { showHistoryPicker = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Aggiungi da cronologia") },
            text = {
                if (state.history.isEmpty()) {
                    Text(
                        "Nessun pasto in cronologia disponibile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(state.history, key = { it.id }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.foodTitle) },
                                supportingContent = {
                                    Text("CHO ${"%.1f".format(entry.totalChoG)}g · Grassi ${"%.1f".format(entry.totalGrassiG)}g")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addFavorite(entry)
                                        showHistoryPicker = false
                                    },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showHistoryPicker = false }) { Text("Chiudi") }
            },
        )
    }

    if (showComboDialog) {
        AddComboFavoriteDialog(
            onDismiss = { showComboDialog = false },
            onConfirm = { foodName, grams, cho, grassi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi)
                showComboDialog = false
            },
        )
    }

    pendingDeleteFavorite?.let { fav ->
        UgoConfirmDialog(
            title = "Rimuovi preferito",
            message = "Vuoi rimuovere \"${fav.label}\" dalle scorciatoie?",
            confirmLabel = "Rimuovi",
            isDestructive = true,
            onConfirm = {
                pendingDeleteFavorite = null
                viewModel.removeFavorite(fav)
            },
            onDismiss = { pendingDeleteFavorite = null },
        )
    }
}
