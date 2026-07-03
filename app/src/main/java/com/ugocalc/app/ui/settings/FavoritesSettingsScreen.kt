package com.ugocalc.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BackIconButton
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Preferiti") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    // Bug fix: secondaryContainer è un tono sfumato verso bianco/nero, non il
                    // colore secondario scelto dall'utente in Accessibilità (vedi Theme.kt).
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondary,
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Mostra i preferiti in home", modifier = Modifier.weight(1f))
                Switch(
                    checked = settingsState.userSettings.favoritesEnabled,
                    onCheckedChange = { settingsViewModel.setFavoritesEnabled(it) },
                )
            }
            Text(
                "Le scorciatoie selezionate appaiono in Homepage, sotto al pulsante Analizza.",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            LazyColumn {
                items(state.favorites, key = { it.id }) { favorite ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(favorite.label, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeFavorite(favorite) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Rimuovi")
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
            title = { Text("Aggiungi da cronologia") },
            text = {
                LazyColumn {
                    items(state.history) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.foodTitle) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { viewModel.addFavorite(entry); showHistoryPicker = false },
                        )
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
}
