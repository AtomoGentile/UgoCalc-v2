package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

/**
 * Trasparenza sulle decisioni di sicurezza (§5 M4 punto 2, §13 punto 2): elenca gli alimenti
 * identificati in modo ambiguo e spiega perché è stata scelta una certa alternativa (regola
 * "CHO più alto" per cautela clinica).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguityResolutionScreen(
    onBack: () -> Unit,
    viewModel: V2ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambiguità risolte") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "clinical_rule_banner") {
                StatusBanner(
                    icon = Icons.Default.VerifiedUser,
                    title = "Cautela clinica attiva",
                    subtitle = "Quando l'IA è incerta tra più alimenti simili, UgoCalc sceglie l'alternativa con più carboidrati per evitare sottostime.",
                    tone = BannerTone.INFO,
                )
            }

            if (uiState.ambiguities.isEmpty()) {
                item(key = "empty_ambiguities") {
                    EmptyStateMessage(
                        icon = Icons.Default.CheckCircle,
                        title = "Nessuna ambiguità rilevata",
                        message = "Tutti gli alimenti in questa analisi sono stati abbinati senza ambiguità.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(uiState.ambiguities, key = { "${it.nomeOriginale}_${it.nomeScelto}" }) { risoluzione ->
                    AmbiguityResolutionCard(risoluzione)
                }
            }
        }
    }
}

@Composable
private fun AmbiguityResolutionCard(risoluzione: AmbiguityResolution) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "Alimento rilevato: ${risoluzione.nomeOriginale}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Scelta cautelativa: ${risoluzione.nomeScelto}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MacroPill(
            label = "CHO / 100g",
            value = "${risoluzione.choPer100g} g",
            containerColor = UgoSemanticColors.choContainer,
            contentColor = UgoSemanticColors.onChoContainer,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tra le alternative identificate, questa è quella con il valore di carboidrati più alto nelle tue tabelle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
