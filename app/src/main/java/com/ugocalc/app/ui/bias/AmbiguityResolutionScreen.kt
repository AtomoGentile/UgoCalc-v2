package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import com.ugocalc.app.domain.inference.model.AmbiguityResolution
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Trasparenza sulle decisioni di sicurezza (§5 M4 punto 2, §13 punto 2): elenca gli alimenti
 * identificati in modo ambiguo e spiega perché è stata scelta una certa alternativa (regola
 * "CHO più alto" per cautela clinica).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguityResolutionScreen(
    onBack: () -> Unit,
    viewModel: V2ResultViewModel = hiltViewModel() // Riusiamo lo stesso ViewModel semplice
) {
    val session by viewModel.session.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambiguità Risolte") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "Quando l'IA non è sicura tra più alimenti simili, UgoCalc sceglie l'alternativa con " +
                            "più carboidrati per evitare di sottostimare l'insulina necessaria. " +
                            "Qui vedi quali alimenti sono stati corretti per sicurezza.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                HorizontalDivider()
            }

            if (session.v2AmbiguitaRisolte.isEmpty()) {
                item {
                    Text(
                        "Nessun'ambiguità è stata rilevata o risolta in questa analisi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UgoGrey,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(session.v2AmbiguitaRisolte) { risoluzione ->
                    AmbiguityResolutionCard(risoluzione)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AmbiguityResolutionCard(risoluzione: AmbiguityResolution) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Alimento originale: ${risoluzione.nomeOriginale}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Scelta cautelativa: ${risoluzione.nomeScelto}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Contenuto CHO: ${risoluzione.choPer100g} g / 100g",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Motivazione: Tra le alternative identificate, questa è quella con il valore di carboidrati più alto nelle tue tabelle.",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
