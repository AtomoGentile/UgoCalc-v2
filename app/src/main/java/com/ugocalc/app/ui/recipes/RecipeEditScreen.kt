package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.util.parsePositiveLocaleDoubleOrNull
import kotlin.math.abs

/** Modifica o creazione ricetta (§2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) {
        viewModel.loadRecipe(recipeId)
    }
    val state by viewModel.uiState

    var newIngName by remember { mutableStateOf("") }
    var newIngPerc by remember { mutableStateOf("") }
    var ingredientError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == 0L) "Nuova Ricetta" else "Modifica Ricetta") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Button(
                        onClick = { viewModel.save { onSaved() } },
                        enabled = state.name.isNotBlank() && !state.isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (recipeId == 0L) "Crea Ricetta" else "Salva Modifiche")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                UgoSectionHeader(title = "Informazioni ricetta")
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.setName(it) },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.setDescription(it) },
                    label = { Text("Descrizione (opzionale)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            UgoSectionHeader(
                title = "Ingredienti",
                subtitle = "${state.ingredients.size} inseriti",
            )

            state.ingredients.forEachIndexed { index, ing ->
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = ing.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        MacroPill(
                            label = "Quota",
                            value = "${"%.1f".format(ing.percentage)}%",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        IconButton(onClick = { viewModel.removeIngredient(index) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Rimuovi",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    text = "Aggiungi ingrediente",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newIngName,
                        onValueChange = { newIngName = it; ingredientError = null },
                        label = { Text("Nome ingrediente") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = newIngPerc,
                        onValueChange = { newIngPerc = it; ingredientError = null },
                        label = { Text("%") },
                        modifier = Modifier.width(92.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                ingredientError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Button(
                    onClick = {
                        val name = newIngName.trim()
                        val perc = newIngPerc.parsePositiveLocaleDoubleOrNull()
                        if (name.isBlank()) {
                            ingredientError = "Inserisci il nome dell'ingrediente."
                            return@Button
                        }
                        if (perc == null) {
                            ingredientError = "Inserisci una percentuale valida maggiore di zero."
                            return@Button
                        }
                        viewModel.addIngredient(name, perc)
                        newIngName = ""
                        newIngPerc = ""
                        ingredientError = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End).padding(top = 10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi")
                }
            }

            val totalPerc = state.ingredients.sumOf { it.percentage }
            val isComplete = abs(totalPerc - 100.0) < 0.01
            StatusBanner(
                icon = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                title = "Totale: ${"%.1f".format(totalPerc)}%",
                subtitle = if (isComplete) {
                    "La somma raggiunge il 100%."
                } else {
                    "Il totale dovrebbe essere 100% per una ripartizione corretta."
                },
                tone = if (isComplete) BannerTone.SUCCESS else BannerTone.WARNING,
            )
        }
    }
}
