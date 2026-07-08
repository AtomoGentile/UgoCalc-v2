package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Modifica o creazione ricetta (§2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel()
) {
    LaunchedEffect(recipeId) {
        viewModel.loadRecipe(recipeId)
    }
    val state by viewModel.uiState

    var newIngName by remember { mutableStateOf("") }
    var newIngPerc by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == 0L) "Nuova Ricetta" else "Modifica Ricetta") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.save { onSaved() } },
                enabled = state.name.isNotBlank() && !state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (recipeId == 0L) "Crea Ricetta" else "Salva Modifiche")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.setDescription(it) },
                label = { Text("Descrizione (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            Text(text = "Ingredienti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            // Lista ingredienti aggiunti
            state.ingredients.forEachIndexed { index, ing ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = ing.name, modifier = Modifier.weight(1f))
                        Text(text = "${ing.percentage}%", modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { viewModel.removeIngredient(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Form aggiunta ingrediente
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newIngName,
                            onValueChange = { newIngName = it },
                            label = { Text("Nome ingrediente") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newIngPerc,
                            onValueChange = { newIngPerc = it },
                            label = { Text("%") },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            val perc = newIngPerc.toDoubleOrNull() ?: 0.0
                            if (newIngName.isNotBlank() && perc > 0) {
                                viewModel.addIngredient(newIngName, perc)
                                newIngName = ""
                                newIngPerc = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Aggiungi")
                    }
                }
            }
            
            val totalPerc = state.ingredients.sumOf { it.percentage }
            Text(
                text = "Totale: $totalPerc%",
                style = MaterialTheme.typography.bodySmall,
                color = if (totalPerc == 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            if (totalPerc != 100.0) {
                Text(
                    text = "Nota: il totale dovrebbe essere 100% per una ripartizione corretta.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
