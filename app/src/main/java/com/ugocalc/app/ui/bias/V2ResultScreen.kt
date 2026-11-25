package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.parsePositiveLocaleDoubleOrNull

/**
 * Dettaglio tecnico della stima V2 (piano V2 §13.1): mostra la catena di calcolo completa
 * per ogni alimento, inclusi i valori geometrici e le fonti dei dati fisici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2ResultScreen(
    onBack: () -> Unit,
    viewModel: V2ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = "Dettaglio Tecnico V2",
        onBack = onBack,
        trailing = {
            com.ugocalc.app.ui.components.FloatingCircleIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Elimina analisi",
                onClick = { showDeleteConfirm = true },
                contentColor = MaterialTheme.colorScheme.error,
                size = 40.dp,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro_banner") {
                StatusBanner(
                    icon = Icons.Default.Science,
                    title = "Motore Fisico M3",
                    subtitle = "Ogni valore numerico deriva da misure visive (M1) o tabelle di densità locali.",
                    tone = BannerTone.INFO,
                )
            }

            if (uiState.reasoning.isNotEmpty()) {
                item(key = "reasoning_card") {
                    UgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        StatusBanner(
                            icon = Icons.Default.Psychology,
                            title = "Ragionamento AI",
                            subtitle = uiState.reasoning,
                            tone = BannerTone.INFO,
                        )
                    }
                }
            }

            if (uiState.v2Stime.isEmpty()) {
                item(key = "empty_v2") {
                    EmptyStateMessage(
                        icon = Icons.Default.Science,
                        title = "Nessun dato V2 disponibile",
                        message = "Non ci sono stime V2 registrate per questa sessione.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(uiState.v2Stime, key = { it.nome }) { stima ->
                    V2WeightEstimateCard(stima = stima, onUpdateDensity = viewModel::updateDensity)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        UgoConfirmDialog(
            title = "Elimina analisi V2",
            message = "Vuoi eliminare questo record tecnico V2?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteRecord(onDeleted = onBack)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun V2WeightEstimateCard(
    stima: WeightEstimate,
    onUpdateDensity: (String, Double) -> Unit,
) {
    var showDensityDialog by remember { mutableStateOf(false) }
    var newDensityText by remember { mutableStateOf("") }
    var densityError by remember { mutableStateOf<String?>(null) }

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = stima.nome,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (stima.ragionamento.isNotEmpty()) {
            Text(
                text = stima.ragionamento,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MacroPill(
                label = "Crudo",
                value = "${stima.pesoCrudoG.toInt()} g",
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
            MacroPill(
                label = "Cotto",
                value = "${stima.pesoG.toInt()} g",
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
            )
            MacroPill(
                label = "Range",
                value = "${stima.intervallo.minG.toInt()}–${stima.intervallo.maxG.toInt()} g",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Strategia: ${stima.strategia.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        CalculationChainBlock(stima.catenaCalcolo)

        if (stima.usoDefaultFisico) {
            Spacer(modifier = Modifier.height(10.dp))
            StatusBanner(
                icon = Icons.Default.Warning,
                title = "Densità di default",
                subtitle = "Puoi inserire un valore reale per migliorare le stime future.",
                tone = BannerTone.WARNING,
                actionLabel = "Correggi",
                onAction = {
                    val currentDensity = stima.catenaCalcolo.valori["densità"] ?: 1.0
                    newDensityText = currentDensity.toString()
                    densityError = null
                    showDensityDialog = true
                },
            )
        }
    }

    if (showDensityDialog) {
        AlertDialog(
            onDismissRequest = { showDensityDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Correggi densità per ${stima.nome}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Inserisci la densità in g/cm³ (es. 0,82 per riso). Questo valore verrà salvato e usato per le prossime analisi di questo alimento.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    OutlinedTextField(
                        value = newDensityText,
                        onValueChange = { newDensityText = it; densityError = null },
                        label = { Text("Densità (g/cm³)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    densityError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val density = newDensityText.parsePositiveLocaleDoubleOrNull()
                        if (density == null) {
                            densityError = "Inserisci una densità valida maggiore di zero."
                            return@Button
                        }
                        onUpdateDensity(stima.nome, density)
                        showDensityDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showDensityDialog = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun CalculationChainBlock(chain: CalculationChain) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text("Catena di calcolo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Formula: ${chain.formula}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "Valori:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        chain.valori.forEach { (nome, valore) ->
            Text("• $nome: $valore", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "Fonti:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        chain.fonti.forEach { fonte ->
            Text("• ${fonte.fattore}: ${fonte.valore} (${fonte.fonte})", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "Sintesi: ${chain.sintesi}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
