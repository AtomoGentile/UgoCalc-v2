package com.ugocalc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddComboFavoriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) -> Unit,
    initialEntry: NormalizedFoodEntry? = null,
) {
    var selectedEntry by remember { mutableStateOf(initialEntry) }
    var gramsText by remember { mutableStateOf("") }
    val entry = selectedEntry
    val grams = gramsText.parseLocaleDoubleOrNull()
    val gramsValid = gramsText.isBlank() || (grams != null && grams > 0.0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Aggiungi preferito",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (entry == null) {
                    FoodSearchPicker(onSelect = { selectedEntry = it })
                } else {
                    UgoCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = entry.nome,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (initialEntry == null) {
                                    TextButton(onClick = { selectedEntry = null }) {
                                        Text("Cambia")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MacroPill(
                                    label = "CHO",
                                    value = "${"%.1f".format(entry.choPer100g)}g/100g",
                                    containerColor = UgoSemanticColors.choContainer,
                                    contentColor = UgoSemanticColors.onChoContainer,
                                )
                                MacroPill(
                                    label = "Grassi",
                                    value = "${"%.1f".format(entry.grassiPer100g)}g/100g",
                                    containerColor = UgoSemanticColors.fatContainer,
                                    contentColor = UgoSemanticColors.onFatContainer,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Peso in grammi (facoltativo)") },
                        placeholder = { Text("es. 80") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = !gramsValid,
                        supportingText = {
                            Text(
                                if (!gramsValid) {
                                    "Inserisci un peso positivo o lascia vuoto."
                                } else {
                                    "Lascia vuoto per salvare senza peso fisso."
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annulla")
                    }
                    if (entry != null) {
                        Button(
                            enabled = gramsValid,
                            onClick = {
                                onConfirm(
                                    entry.nome,
                                    if (gramsText.isBlank()) null else grams,
                                    entry.choPer100g,
                                    entry.grassiPer100g,
                                )
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Aggiungi")
                        }
                    }
                }
            }
        }
    }
}
