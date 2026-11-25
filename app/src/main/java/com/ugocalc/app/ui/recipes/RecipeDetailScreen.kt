package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Dettaglio ricetta (§2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) {
        if (recipeId > 0) {
            viewModel.loadRecipe(recipeId)
        }
    }
    val recipe by viewModel.recipe.collectAsState()

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = recipe?.name ?: "Dettaglio Ricetta",
        onBack = onBack,
        trailing = {
            if (recipe != null) {
                com.ugocalc.app.ui.components.FloatingCircleIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Modifica ricetta",
                    onClick = { onEdit(recipeId) },
                    size = 40.dp,
                )
            }
        },
    ) {
        if (recipe == null && recipeId > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (recipe == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Ricetta non trovata",
                    message = "La ricetta richiesta non è più disponibile.",
                    actionLabel = "Torna alla lista",
                    onAction = onBack,
                )
            }
        } else {
            val r = recipe!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = r.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (r.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = r.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                UgoSectionHeader(
                    title = "Ingredienti",
                    subtitle = "${r.ingredients.size} componenti",
                )

                if (r.ingredients.isEmpty()) {
                    EmptyStateMessage(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Nessun ingrediente",
                        message = "Tocca modifica per aggiungere gli ingredienti con le relative percentuali.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                } else {
                    r.ingredients.forEach { ing ->
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = ing.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                                )
                                MacroPill(
                                    label = "Quota",
                                    value = "${"%.1f".format(ing.percentage)}%",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
