package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

/**
 * Impostazioni > Livello di confidenza (§2.2): toggle + sottomenu soglia (default 70%), visibile
 * solo se l'opzione è attiva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfidenceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Livello di confidenza") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Mostra livello di sicurezza", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.userSettings.confidenceIndicatorEnabled,
                    onCheckedChange = { viewModel.setConfidenceIndicatorEnabled(it) },
                )
            }

            if (state.userSettings.confidenceIndicatorEnabled) {
                Text(
                    "Soglia: ${state.userSettings.confidenceThresholdPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Slider(
                    value = state.userSettings.confidenceThresholdPercent.toFloat(),
                    onValueChange = { viewModel.setConfidenceThresholdPercent(it.toInt()) },
                    valueRange = 0f..100f,
                )
                Text(
                    "Le analisi con confidenza pari o superiore alla soglia vengono evidenziate con il colore primario dell'app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
