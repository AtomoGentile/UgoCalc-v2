package com.ugocalc.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.NormalizedFoodEntry

/**
 * Preferito "da database" (§ richiesta utente): cerca l'alimento, poi il peso è facoltativo —
 * l'utente sceglie se indicarlo (per vedere CHO/grassi già calcolati per quella quantità) o
 * lasciarlo vuoto (il preferito mostra i valori per 100g).
 */
@Composable
fun AddComboFavoriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (foodName: String, grams: Double?, choPer100g: Double, grassiPer100g: Double) -> Unit,
    /** Se già noto (es. stellina su un risultato di ricerca), salta lo step di ricerca e chiede solo il peso. */
    initialEntry: NormalizedFoodEntry? = null,
) {
    var selectedEntry by remember { mutableStateOf(initialEntry) }
    var gramsText by remember { mutableStateOf("") }
    val entry = selectedEntry
    val grams = gramsText.toDoubleOrNull()
    val gramsValid = gramsText.isBlank() || (grams != null && grams > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi da database") },
        text = {
            if (entry == null) {
                FoodSearchPicker(onSelect = { selectedEntry = it })
            } else {
                Column {
                    Text(entry.nome, fontWeight = FontWeight.Bold)
                    Text(
                        "CHO ${"%.1f".format(entry.choPer100g)} g · Grassi ${"%.1f".format(entry.grassiPer100g)} g per 100g",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Peso in grammi (facoltativo)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !gramsValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Lascia vuoto per salvare il preferito senza un peso specifico.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = entry != null && gramsValid,
                onClick = { onConfirm(entry!!.nome, if (gramsText.isBlank()) null else grams, entry.choPer100g, entry.grassiPer100g) },
            ) { Text("Aggiungi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
