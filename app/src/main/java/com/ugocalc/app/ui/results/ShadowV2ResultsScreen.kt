package com.ugocalc.app.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowV2ResultsScreen(
    onBack: () -> Unit,
    viewModel: VisionResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risultati V2 Shadow") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.shadowV2Results.isEmpty()) {
                EmptyStateMessage(
                    icon = Icons.Filled.Analytics,
                    title = "Nessun risultato V2",
                    message = "Nessun risultato V2 shadow disponibile per questa sessione.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.shadowV2Results.values.toList(), key = { it.model.uid }) { shadowResult ->
                        ShadowV2ResultCard(shadowResult)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShadowV2ResultCard(shadowResult: AnalysisSessionStore.V2ShadowResult) {
    val isFailure = shadowResult.outcome is AnalysisSessionStore.PipelineOutcomeForShadow.Failure
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isFailure) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
            ) {
                ModelLogoBox(
                    logoRes = shadowResult.model.logoRes,
                    displayName = shadowResult.model.displayName,
                    subtitle = shadowResult.model.subtitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shadowResult.model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (shadowResult.model.subtitle.isNotBlank()) {
                    Text(
                        text = shadowResult.model.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (val outcome = shadowResult.outcome) {
            is AnalysisSessionStore.PipelineOutcomeForShadow.Success -> {
                StatusBanner(
                    icon = Icons.Filled.CheckCircle,
                    title = "Completato",
                    subtitle = if (outcome.ambiguita.isNotEmpty()) {
                        "Ambiguità risolte: ${outcome.ambiguita.size}"
                    } else {
                        "Stima V2 completata"
                    },
                    tone = BannerTone.SUCCESS,
                )
                Spacer(Modifier.height(10.dp))
                val shadowGl = GlycemicCurveCalculator.computeTotalGlycemicLoad(outcome.totals.lines)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${outcome.totals.totalChoG.toInt()} g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    MacroPill(
                        label = "Prot",
                        value = "${outcome.totals.totalProteineG.toInt()} g",
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                    )
                    MacroPill(
                        label = "Grassi",
                        value = "${outcome.totals.totalGrassiG.toInt()} g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                    )
                    if (shadowGl > 0.0) {
                        MacroPill(
                            label = "GL",
                            value = "%.1f".format(Locale.ITALIAN, shadowGl),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                outcome.stime.forEach { stima ->
                    Text(
                        text = "• ${stima.nome} — ${stima.pesoG.toInt()} g",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            is AnalysisSessionStore.PipelineOutcomeForShadow.Failure -> {
                StatusBanner(
                    icon = Icons.Filled.ErrorOutline,
                    title = "Errore V2",
                    subtitle = outcome.reason,
                    tone = BannerTone.ERROR,
                )
            }
        }
    }
}
