package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: RecipeDetailViewModel = hiltViewModel()
    Column {
        Text(text = "Recipe Detail for $recipeId")
        Button(onClick = { onEdit(recipeId) }) {
            Text("Modifica")
        }
        Button(onClick = onBack) {
            Text("Indietro")
        }
    }
}
