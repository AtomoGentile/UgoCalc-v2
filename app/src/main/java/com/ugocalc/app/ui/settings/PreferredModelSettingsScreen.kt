package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import com.ugocalc.app.ui.components.BackIconButton

/**
 * Impostazioni > Modelli preferiti (§2.2): se ne selezioni almeno uno, "Analizza" in Home salta
 * del tutto la schermata di scelta modello e avvia subito l'analisi con questi (§ modello
 * preferito esteso: più di una scelta possibile, non un singolo modello).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferredModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Modelli preferiti") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Se selezioni uno o più modelli, l'analisi partirà subito con questi, saltando la schermata di scelta modello. Senza nessuno selezionato, ti verrà chiesto ogni volta.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            viewModel.availableModels.forEach { model ->
                val isSelected = model.id in state.userSettings.preferredModelIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .toggleable(
                            value = isSelected,
                            onValueChange = { viewModel.togglePreferredModel(model.id) },
                        )
                        .padding(horizontal = 4.dp),
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.scale(1.3f))
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
