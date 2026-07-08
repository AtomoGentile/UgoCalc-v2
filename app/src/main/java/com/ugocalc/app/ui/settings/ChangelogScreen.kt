package com.ugocalc.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.repository.UpdateCheckResult
import com.ugocalc.app.domain.model.Changelog
import com.ugocalc.app.domain.model.ModuleVersions
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Impostazioni > Software e aggiornamenti: spiega come funzionano gli aggiornamenti di UgoCalc
 * (app privata, non su Play Store), permette di controllare su richiesta se è disponibile una
 * versione più recente sul repository GitHub ([ChangelogViewModel], mai un controllo automatico
 * in background), e mostra lo storico ([Changelog.ENTRIES], più recente in cima — vuoto finché non
 * esce il primo aggiornamento dopo la 1.0.0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit, viewModel: ChangelogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Software e aggiornamenti") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(
                        "UgoCalc non è pubblicata sul Play Store: è un'app privata a uso personale. " +
                            "Gli aggiornamenti vengono distribuiti tramite Firebase App Distribution, oppure " +
                            "scaricando l'ultima release pubblicata su GitHub — il controllo qui sotto legge " +
                            "direttamente da lì.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Ad ogni aggiornamento installato, l'app mostra in automatico un riepilogo delle " +
                            "novità al primo avvio. Lo stesso elenco resta consultabile qui sotto, con lo " +
                            "storico completo delle versioni rilasciate, più recente in cima.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { viewModel.checkForUpdate() }, enabled = !uiState.checkingForUpdate) {
                            Text("Controlla aggiornamenti su GitHub")
                        }
                        if (uiState.checkingForUpdate) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    UpdateResultSection(
                        result = uiState.updateResult,
                        onDownload = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    )
                }
                HorizontalDivider()
            }
            items(Changelog.ENTRIES) { entry ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("Versione ${entry.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    entry.highlights.forEach { highlight ->
                        Text("• $highlight", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    entry.moduleVersions?.let { versions ->
                        ModuleVersionsBlock(versions)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Esito del controllo aggiornamenti (§ richiesta utente): disponibile, già aggiornato, o errore — mai un dialog invadente, solo testo in linea sotto il pulsante. */
@Composable
private fun UpdateResultSection(result: UpdateCheckResult?, onDownload: (String) -> Unit) {
    when (result) {
        null -> Unit
        UpdateCheckResult.UpToDate -> {
            Text(
                "Hai già l'ultima versione (${BuildConfig.VERSION_NAME}).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        is UpdateCheckResult.Error -> {
            Text(
                result.message,
                color = Color(0xFFC62828),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        is UpdateCheckResult.UpdateAvailable -> {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Nuova versione disponibile: ${result.info.version}", fontWeight = FontWeight.Bold)
                    if (result.info.releaseNotes.isNotBlank()) {
                        Text(result.info.releaseNotes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { onDownload(result.info.apkDownloadUrl ?: result.info.htmlUrl) }) {
                            Text(if (result.info.apkDownloadUrl != null) "Scarica l'APK" else "Apri la release su GitHub")
                        }
                    }
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
