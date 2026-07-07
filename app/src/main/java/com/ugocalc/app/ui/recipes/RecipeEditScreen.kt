package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RecipeEditScreen(
    recipeId: Long = 0L,
    onSaved: (Long) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val viewModel: RecipeEditViewModel = hiltViewModel()
    LaunchedEffect(recipeId) { viewModel.loadRecipe(recipeId) }
    val state = viewModel.uiState.value

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        TextField(
            value = state.name,
            onValueChange = { viewModel.setName(it) },
            label = { Text("Nome ricetta") },
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = state.description,
            onValueChange = { viewModel.setDescription(it) },
            label = { Text("Descrizione") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        // Ingredient editing omitted for brevity
        Button(
            onClick = { viewModel.save(onSaved) },
            enabled = !state.isSaving,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(if (state.id == 0L) "Crea" else "Salva")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Indietro")
        }
    }
}
