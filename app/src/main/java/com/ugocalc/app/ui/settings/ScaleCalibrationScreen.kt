package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.ui.components.BackIconButton

/**
 * Schermata per visualizzare, aggiungere e rimuovere calibrazioni di scala.
 * Accessibile da Settings → "Calibrazioni scale".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(onBack: () -> Unit, viewModel: ScaleCalibrationViewModel = hiltViewModel()) {
    val calibrations = viewModel.calibrations

    // Stato temporaneo per il form di aggiunta
    var newName by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var newCapacity by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ScaleAnchorType.STOVIGLIA_CALIBRATA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrazioni scala") },
                navigationIcon = { BackIconButton(onBack) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Lista delle calibrazioni esistenti
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(calibrations) { cal ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cal.type}: ${cal.name}")
                                Text("Valore: ${cal.valueCm} cm")
                                cal.capacityMl?.let { Text("Capacità: $it ml") }
                            }
                            Button(onClick = { viewModel.deleteCalibration(cal.id) }) {
                                Text("Elimina")
                            }
                        }
                    }
                }
            }
            // Form di inserimento nuova calibrazione
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Aggiungi nuova calibrazione", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                // Selezione tipo
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ScaleAnchorType.values().forEach { type ->
                        Button(
                            onClick = { selectedType = type },
                            enabled = selectedType != type,
                            modifier = Modifier.padding(end = 4.dp)
                        ) { Text(type.name) }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Valore in cm") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = newCapacity,
                    onValueChange = { newCapacity = it },
                    label = { Text("Capacità in ml (opzionale)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = {
                        val value = newValue.toDoubleOrNull() ?: return@Button
                        val capacity = newCapacity.toDoubleOrNull()
                        viewModel.addCalibration(selectedType, newName, value, capacity)
                        newName = ""
                        newValue = ""
                        newCapacity = ""
                    },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                ) {
                    Text("Aggiungi")
                }
            }
        }
    }
}
