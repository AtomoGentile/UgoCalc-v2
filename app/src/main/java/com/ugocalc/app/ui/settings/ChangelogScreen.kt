package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.Changelog
import com.ugocalc.app.domain.model.ModuleVersions
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Impostazioni > Software e aggiornamenti: spiega come funzionano gli aggiornamenti di UgoCalc
 * (app privata, non su Play Store), distribuiti via AWS, e mostra lo storico ([Changelog.ENTRIES],
 * più recente in cima — vuoto finché non esce il primo aggiornamento dopo la 1.0.0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit, viewModel: ChangelogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Software e aggiornamenti") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        "UgoCalc non è pubblicata sul Play Store: è un'app privata a uso personale. " +
                            "Gli aggiornamenti vengono distribuiti via AWS e scaricati automaticamente al primo avvio.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Ad ogni aggiornamento installato, l'app mostra in automatico un riepilogo delle " +
                            "novità al primo avvio. Lo stesso elenco resta consultabile qui sotto, con lo " +
                            "storico completo delle versioni rilasciate, più recente in cima.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    )
                }
                HorizontalDivider()
            }
            Changelog.ENTRIES.forEach { entry ->
                item(key = "version_${entry.version}") {
                    Text(
                        "Versione ${entry.version}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(entry.highlights) { highlight ->
                    Text(
                        "• $highlight",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                entry.moduleVersions?.let { versions ->
                    item(key = "versions_${entry.version}") {
                        ModuleVersionsBlock(versions)
                    }
                }
                item(key = "divider_${entry.version}") {
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}


@Composable
private fun ModuleVersionsBlock(versions: ModuleVersions) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("Versioni modulo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        versions.inferenceEngine?.let { Text("InferenceEngine: $it", style = MaterialTheme.typography.bodySmall) }
        versions.perceptionPrompt?.let { Text("PerceptionPrompt: $it", style = MaterialTheme.typography.bodySmall) }
        versions.physicalData?.let { Text("PhysicalData: $it", style = MaterialTheme.typography.bodySmall) }
        versions.biasCalculator?.let { Text("BiasCalculator: $it", style = MaterialTheme.typography.bodySmall) }
        versions.ambiguityResolver?.let { Text("AmbiguityResolver: $it", style = MaterialTheme.typography.bodySmall) }
        versions.matchStep?.let { Text("MatchStep: $it", style = MaterialTheme.typography.bodySmall) }
    }
}
