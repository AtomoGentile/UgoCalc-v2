package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Livello di confidenza") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
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
                            text = "Mostra avviso di bassa confidenza",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Evidenzia le stime quando l'incertezza è elevata",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.userSettings.confidenceIndicatorEnabled,
                        onCheckedChange = { viewModel.setConfidenceIndicatorEnabled(it) },
                    )
                }
            }

            if (state.userSettings.confidenceIndicatorEnabled) {
                var sliderThreshold by remember(state.userSettings.confidenceAlertThresholdPercent) {
                    mutableFloatStateOf(state.userSettings.confidenceAlertThresholdPercent.toFloat())
                }
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Soglia di allerta: ${sliderThreshold.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = sliderThreshold,
                        onValueChange = { sliderThreshold = it },
                        onValueChangeFinished = { viewModel.setConfidenceAlertThresholdPercent(sliderThreshold.toInt()) },
                        valueRange = 0f..100f,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusBanner(
                        icon = Icons.Filled.Verified,
                        title = "Come funziona",
                        subtitle = "Sotto il ${sliderThreshold.toInt()}% viene mostrato un avviso sulla stima di peso. Aumenta la soglia per ricevere avvisi più frequenti.",
                        tone = BannerTone.INFO,
                    )
                }
            }
        }
    }
}
