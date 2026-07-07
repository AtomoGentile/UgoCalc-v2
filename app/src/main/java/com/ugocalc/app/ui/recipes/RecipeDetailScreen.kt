package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel

/** Simple screen displaying a single recipe's details. */
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onEdit: (Long) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val viewModel: RecipeDetailViewModel = hiltViewModel()
    val recipe = viewModel.recipeById(recipeId).collectAsState(initial = null).value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Recipe Detail")
            if (recipe != null) {
                Text(text = recipe.name)
                if (recipe.description.isNotBlank()) {
                    Text(text = recipe.description)
                }
                Text(text = "Ingredients:")
                recipe.ingredients.forEach { ing ->
                    Text(text = "- ${ing.name}: ${ing.percentage}%")
                }
                Row {
                    TextButton(onClick = { onEdit(recipe.id) }) {
                        Text("Modifica")
                    }
                }
            } else {
                Text(text = "Recipe not found")
            }
    }
}
