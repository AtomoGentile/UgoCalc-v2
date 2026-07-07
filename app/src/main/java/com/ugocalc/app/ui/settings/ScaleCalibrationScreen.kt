package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/**
 * Schermata per visualizzare, aggiungere e rimuovere calibrazioni di scala (§2.2).
 * Migliorata con controlli più chiari e layout adattivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(onBack: () -> Unit, viewModel: ScaleCalibrationViewModel = hiltViewModel()) {
    val calibrations = viewModel.calibrations

    var newName by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var newCapacity by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ScaleAnchorType.STOVIGLIA_CALIBRATA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrazioni scala") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Lista delle calibrazioni esistenti
            if (calibrations.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nessun riferimento salvato", color = UgoGrey)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(calibrations) { cal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (cal.type == ScaleAnchorType.STOVIGLIA_CALIBRATA.name) "Stoviglia" else "Rif. Corporeo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = cal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(text = "${cal.valueCm} cm", style = MaterialTheme.typography.bodyMedium)
                                    cal.capacityMl?.let { capacity ->
                                        Text(text = "Capacità: $capacity ml", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteCalibration(cal.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Form di inserimento nuova calibrazione
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 8.dp)) {
                    Text("Aggiungi nuova", style = MaterialTheme.typography.titleSmall)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Selezione tipo con SegmentedButton per chiarezza e dimensioni uniformi
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedType == ScaleAnchorType.STOVIGLIA_CALIBRATA,
                            onClick = { selectedType = ScaleAnchorType.STOVIGLIA_CALIBRATA },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Stoviglia", maxLines = 1) }
                        )
                        SegmentedButton(
                            selected = selectedType == ScaleAnchorType.RIFERIMENTO_CORPOREO,
                            onClick = { selectedType = ScaleAnchorType.RIFERIMENTO_CORPOREO },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Corporeo", maxLines = 1) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nome (es. Piatto fondo)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            label = { Text("Cm") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newCapacity,
                            onValueChange = { newCapacity = it },
                            label = { Text("Ml (opz)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            val value = newValue.replace(",", ".").toDoubleOrNull() ?: return@Button
                            val capacity = newCapacity.replace(",", ".").toDoubleOrNull()
                            viewModel.addCalibration(selectedType, newName, value, capacity)
                            newName = ""
                            newValue = ""
                            newCapacity = ""
                        },
                        enabled = newName.isNotBlank() && newValue.isNotBlank(),
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                    ) {
                        Text("Salva Calibrazione")
                    }
                }
            }
        }
    }
}
