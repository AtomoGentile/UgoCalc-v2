package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey

/** Dettaglio ricetta (§2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(recipeId) {
        if (recipeId > 0) {
            viewModel.loadRecipe(recipeId)
        }
    }
    val recipe by viewModel.recipe.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "Dettaglio Ricetta") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        },
        floatingActionButton = {
            if (recipe != null) {
                FloatingActionButton(
                    onClick = { onEdit(recipeId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica ricetta")
                }
            }
        }
    ) { padding ->
        if (recipe == null && recipeId > 0) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (recipe == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ricetta non trovata", color = UgoGrey)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text("Torna alla lista") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                recipe?.let { r ->
                    Text(text = r.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (r.description.isNotBlank()) {
                        Text(text = r.description, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    
                    Text(text = "Ingredienti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    r.ingredients.forEach { ing ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = ing.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(text = "${ing.percentage}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    if (r.ingredients.isEmpty()) {
                        Text("Nessun ingrediente specificato", style = MaterialTheme.typography.bodyMedium, color = UgoGrey)
                    }
                }
            }
        }
    }
}
