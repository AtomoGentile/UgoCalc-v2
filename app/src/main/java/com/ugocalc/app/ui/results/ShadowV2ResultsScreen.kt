package com.ugocalc.app.ui.results

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowV2ResultsScreen(
    onBack: () -> Unit,
    viewModel: VisionResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Risultati V2 Shadow") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.shadowV2Results.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Nessun risultato V2 shadow disponibile",
                        style = MaterialTheme.typography.titleMedium,
                        color = UgoRed,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(uiState.shadowV2Results.values.toList()) { shadowResult ->
                        ShadowV2ResultCard(shadowResult)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShadowV2ResultCard(shadowResult: AnalysisSessionStore.V2ShadowResult) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(shadowResult.model.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text(shadowResult.model.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            when (val outcome = shadowResult.outcome) {
                is AnalysisSessionStore.PipelineOutcomeForShadow.Success -> {
                    Text("✓ Successo", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("CHO totali: ${outcome.totals.totalChoG.toInt()}g", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("Grassi totali: ${outcome.totals.totalGrassiG.toInt()}g", style = MaterialTheme.typography.bodySmall)

                    outcome.stime.forEach { stima ->
                        Text(
                            "• ${stima.nome} — ${stima.pesoG.toInt()}g",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (outcome.ambiguita.isNotEmpty()) {
                        Text(
                            "Ambiguità risolte: ${outcome.ambiguita.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                is AnalysisSessionStore.PipelineOutcomeForShadow.Failure -> {
                    Text("✗ Errore", style = MaterialTheme.typography.bodySmall, color = UgoRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(outcome.reason, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}
