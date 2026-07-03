package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Impostazioni > Grafico cronologia (§ richiesta utente): attiva/disattiva il grafico CHO/grassi per pasto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryChartSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Grafico cronologia") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Mostra il grafico nella cronologia", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.userSettings.historyChartEnabled,
                    onCheckedChange = { viewModel.setHistoryChartEnabled(it) },
                )
            }
            Text(
                "Colonna per ogni analisi salvata, con CHO e grassi impilati in due colori. Puoi scegliere l'intervallo di tempo direttamente in Cronologia.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
