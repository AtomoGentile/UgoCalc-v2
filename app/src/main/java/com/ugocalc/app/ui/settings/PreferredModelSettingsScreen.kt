package com.ugocalc.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Impostazioni > Modelli preferiti (§2.2): se ne selezioni almeno uno, "Analizza" in Home salta
 * del tutto la schermata di scelta modello e avvia subito l'analisi con questi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferredModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredModels = remember(searchQuery, availableModels) {
        availableModels.filter { modelMatchesQuery(it, searchQuery) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Modelli preferiti") },
            navigationIcon = { BackIconButton(onBack) },
            colors = ugoTopAppBarColors(),
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Se selezioni uno o più modelli, l'analisi partirà subito con questi, saltando la schermata di scelta modello. Senza nessuno selezionato, ti verrà chiesto ogni volta.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cerca modello…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredModels, key = { it.uid }) { model ->
                    val isSelected = model.id in state.userSettings.preferredModelIds
                    val onClick = remember(model.id) { { viewModel.togglePreferredModel(model.id) } }
                    PreferredModelCard(
                        model = model,
                        isSelected = isSelected,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferredModelCard(model: AiModel, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val shape = remember { RoundedCornerShape(12.dp) }
    val cardModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.9f)
        .border(borderWidth, borderColor, shape)
    val elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)

    Card(
        onClick = onClick,
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = elevation,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModelLogoBox(
                    logoRes = model.logoRes,
                    displayName = model.displayName,
                    subtitle = model.subtitle,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF1C1B1F),
                )
                if (model.subtitle.isNotEmpty()) {
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
