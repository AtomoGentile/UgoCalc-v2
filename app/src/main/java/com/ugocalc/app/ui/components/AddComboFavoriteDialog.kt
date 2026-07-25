package com.ugocalc.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun AddComboFavoriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) -> Unit,
    initialEntry: NormalizedFoodEntry? = null,
) {
    var selectedEntry by remember { mutableStateOf(initialEntry) }
    var gramsText by remember { mutableStateOf("") }
    val entry = selectedEntry
    val grams = gramsText.toDoubleOrNull()
    val gramsValid = gramsText.isBlank() || (grams != null && grams > 0)

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Aggiungi preferito",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                if (entry == null) {
                    FoodSearchPicker(onSelect = { selectedEntry = it })
                } else {
                    Text(
                        entry.nome,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "CHO ${"%.1f".format(entry.choPer100g)} g · Grassi ${"%.1f".format(entry.grassiPer100g)} g per 100g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                    )
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Peso in grammi (facoltativo)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !gramsValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text(
                        "Lascia vuoto per salvare senza peso fisso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Annulla") }
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
                        ) { Text("Aggiungi") }
                    }
                }
            }
        }
    }
}
