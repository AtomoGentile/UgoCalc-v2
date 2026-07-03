package com.ugocalc.app.ui.modelselect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Schermata di selezione modello (§2.4 punto 1): checkbox sui modelli disponibili. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    onNext: (modelIds: List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: ModelSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scegli i modelli") }, colors = ugoTopAppBarColors()) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (uiState.isOffline) {
                Text(
                    "Sei offline: nessun modello è selezionabile senza connessione a internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (uiState.availableModels.isEmpty()) {
                Text(
                    "Nessun modello disponibile. Configura una API key nelle Impostazioni > API, oppure connettiti a internet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(uiState.availableModels) { model ->
                    val isSelected = model.id in uiState.selectedModelIds
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                // L'intera riga è il bersaglio del tocco, non solo il quadratino
                                // della checkbox: onCheckedChange resta null per evitare due
                                // gestori di click sovrapposti sullo stesso elemento.
                                .toggleable(
                                    value = isSelected,
                                    onValueChange = { viewModel.toggleModel(model.id) },
                                )
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                modifier = Modifier.scale(1.3f),
                            )
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onBack) { Text("Indietro") }
                Button(
                    enabled = uiState.selectedModelIds.isNotEmpty(),
                    onClick = { onNext(viewModel.confirmSelectionAndProceed()) },
                ) { Text("Avanti") }
            }
        }
    }
}
