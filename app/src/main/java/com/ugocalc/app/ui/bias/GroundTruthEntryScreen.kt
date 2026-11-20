package com.ugocalc.app.ui.bias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registrazione del peso vero per un'analisi V2 già salvata (piano V2 §7, §8, §13 punto 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundTruthEntryScreen(
    onBack: () -> Unit,
    viewModel: GroundTruthEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registra peso vero") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                StatusBanner(
                    icon = Icons.Default.Info,
                    title = "Chiusura ciclo di apprendimento",
                    subtitle = "Confronta il peso stimato dal motore V2 con quello reale per aggiornare i fattori di correzione BIAS.",
                    tone = BannerTone.INFO,
                )
            }

            if (!uiState.consentGiven) {
                item(key = "consent_warning") {
                    StatusBanner(
                        icon = Icons.Default.Warning,
                        title = "Consenso BIAS disattivato",
                        subtitle = "Puoi registrare il peso vero, ma nessun fattore verrà calcolato finché non attivi il consenso.",
                        tone = BannerTone.WARNING,
                    )
                }
            }

            if (uiState.loading) {
                item(key = "loading") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.pending.isEmpty()) {
                item(key = "empty") {
                    EmptyStateMessage(
                        icon = Icons.Default.Scale,
                        title = "Nessuna analisi in attesa",
                        message = "Non ci sono analisi V2 in attesa di conferma peso.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            } else {
                items(uiState.pending, key = { it.record.id }) { entry ->
                    PendingRecordCard(
                        entry = entry,
                        onSubmit = { pesiG, livello -> viewModel.submit(entry.record.id, pesiG, livello) },
                        onRequestDelete = { pendingDeleteId = entry.record.id },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        UgoConfirmDialog(
            title = "Elimina analisi in attesa",
            message = "Vuoi eliminare questa analisi dalla lista dei pesi da confermare?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDeleteId = null
                viewModel.delete(id)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun PendingRecordCard(
    entry: PendingGroundTruthRecord,
    onSubmit: (Map<String, Double>, GroundTruthLevel) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.ITALIAN) }
    var livello by remember { mutableStateOf(GroundTruthLevel.BRONZE) }
    val pesiInput = remember(entry.record.id) { mutableStateOf(entry.stime.associate { it.nome to "" }) }

    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = dateFormat.format(Date(entry.record.analyzedAtEpochMillis)),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Modello: ${entry.record.aiModelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        entry.stime.forEach { stima ->
            OutlinedTextField(
                value = pesiInput.value[stima.nome].orEmpty(),
                onValueChange = { value -> pesiInput.value = pesiInput.value + (stima.nome to value) },
                label = { Text("${stima.nome} (stimato ${stima.pesoCrudoG.toInt()} g crudo)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        GroundTruthLevelSelector(livello, onSelect = { livello = it })

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val pesiG = pesiInput.value.mapNotNull { (nome, raw) ->
                        raw.parseLocaleDoubleOrNull()?.takeIf { it >= 0.0 }?.let { nome to it }
                    }.toMap()
                    onSubmit(pesiG, livello)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Salva peso vero")
            }
            TextButton(
                onClick = onRequestDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Elimina")
            }
        }
    }
}
