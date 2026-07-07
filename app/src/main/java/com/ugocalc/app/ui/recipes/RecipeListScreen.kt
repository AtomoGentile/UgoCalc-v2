package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel


/** Simple UI to display user‑defined recipes. */
@Composable
fun RecipeListScreen(
    onRecipeSelected: (Long) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val viewModel: RecipeListViewModel = hiltViewModel()
    val recipes = viewModel.recipes.collectAsState(initial = emptyList()).value

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(recipes) { recipe ->
            Column(modifier = Modifier.clickable { onRecipeSelected(recipe.id) }) {
                Text(text = recipe.name)
                if (recipe.description.isNotBlank()) {
                    Text(text = recipe.description)
                }
            }
        }
    }
}
