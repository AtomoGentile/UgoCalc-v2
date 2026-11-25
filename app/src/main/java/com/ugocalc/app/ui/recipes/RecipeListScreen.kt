package com.ugocalc.app.ui.recipes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.FloatingCircleIconButton
import com.ugocalc.app.ui.components.FramelessListRow

/** Schermata lista ricette (§2.2). */
@Composable
fun RecipeListScreen(
    onRecipeSelected: (Long) -> Unit,
    onAddRecipe: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsState()

    EditorialDetailScaffold(
        title = "Le mie Ricette",
        onBack = onBack,
        trailing = {
            FloatingCircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Aggiungi ricetta",
                onClick = onAddRecipe,
                size = 40.dp,
            )
        },
    ) {
        if (recipes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    title = "Nessuna ricetta salvata",
                    message = "Crea le tue ricette per ripartire automaticamente gli ingredienti nei piatti composti.",
                    actionLabel = "Nuova ricetta",
                    onAction = onAddRecipe,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
                ) {
                    itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
                        FramelessListRow(
                            onClick = { onRecipeSelected(recipe.id) },
                            showDivider = index < recipes.lastIndex,
                            trailing = {
                                Text(
                                    text = if (recipe.ingredients.size == 1) {
                                        "1 ingr."
                                    } else {
                                        "${recipe.ingredients.size} ingr."
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        ) {
                            Text(
                                text = recipe.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (recipe.description.isNotBlank()) {
                                Text(
                                    text = recipe.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                FloatingCircleIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Nuova ricetta",
                    onClick = onAddRecipe,
                    size = 54.dp,
                    iconSize = 24.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                )
            }
        }
    }
}
