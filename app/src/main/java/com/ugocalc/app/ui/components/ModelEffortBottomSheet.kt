package com.ugocalc.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.effectiveEffort
import com.ugocalc.app.domain.model.recommendedTier
import com.ugocalc.app.domain.model.supportedEfforts

private enum class ModelSheetPage { MODEL, EFFORT }

/**
 * Bottom sheet selettore Modello & Effort ispirato alla nuova UI di Claude:
 * - Vista "Modello" con selezione a pillola, indicatore effort a puntini (`· | · · ·`) e chevron `Medium >`
 * - Transizione interna verso "Effort" con slider tattile a pillola scura e tacche discrete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEffortBottomSheet(
    availableModels: List<AiModel>,
    selectedModels: List<AiModel>,
    currentEffort: AiEffort,
    onSelectModel: (AiModel) -> Unit,
    onSelectEffort: (AiEffort) -> Unit,
    onOpenFullModelPicker: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember { mutableStateOf(ModelSheetPage.MODEL) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val primarySelected = selectedModels.firstOrNull() ?: availableModels.firstOrNull()
    val activeEfforts = remember(primarySelected) {
        primarySelected?.supportedEfforts().orEmpty()
    }
    val effectiveCurrentEffort = remember(primarySelected, currentEffort) {
        primarySelected?.effectiveEffort(currentEffort) ?: currentEffort
    }

    val filteredModels = remember(availableModels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            availableModels
        } else {
            availableModels.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    it.subtitle.contains(q, ignoreCase = true) ||
                    it.id.contains(q, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
        ) {
            // Header del bottom sheet (X oppure ← + titolo centrato + ricerca opzionale)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
            ) {
                FloatingCircleIconButton(
                    icon = if (currentPage == ModelSheetPage.MODEL) {
                        Icons.Rounded.Close
                    } else {
                        Icons.AutoMirrored.Rounded.ArrowBack
                    },
                    contentDescription = if (currentPage == ModelSheetPage.MODEL) "Chiudi" else "Torna ai modelli",
                    onClick = {
                        if (currentPage == ModelSheetPage.EFFORT) {
                            currentPage = ModelSheetPage.MODEL
                        } else {
                            onDismiss()
                        }
                    },
                    size = 38.dp,
                    iconSize = 18.dp,
                    elevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                Text(
                    text = if (currentPage == ModelSheetPage.MODEL) "Modello" else "Effort",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )

                if (currentPage == ModelSheetPage.MODEL && availableModels.size > 4) {
                    FloatingCircleIconButton(
                        icon = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Cerca modello",
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        },
                        size = 38.dp,
                        iconSize = 18.dp,
                        elevation = 0.dp,
                        containerColor = if (showSearch) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = if (showSearch) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState == ModelSheetPage.EFFORT) {
                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(150)))
                    }
                },
                label = "model_effort_sheet_page",
            ) { page ->
                when (page) {
                    ModelSheetPage.MODEL -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(visible = showSearch) {
                                EditorialSearchBarRow(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    placeholder = "Filtra modelli…",
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }

                            if (availableModels.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "Nessun modello AI configurato",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Collega una chiave API nelle impostazioni per scegliere modello ed effort.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    TextButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenApiSettings()
                                        },
                                    ) {
                                        Text("Configura API")
                                    }
                                }
                            } else {
                                val selectedUids = remember(selectedModels) {
                                    selectedModels.map { it.uid }.toSet()
                                }
                                val primaryUid = primarySelected?.uid
                                val primaryIndex = filteredModels.indexOfFirst { it.uid == primaryUid }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 310.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                ) {
                                    itemsIndexed(
                                        items = filteredModels,
                                        key = { _, model -> model.uid },
                                    ) { index, model ->
                                        val isSelected = model.uid in selectedUids
                                        val isPrimary = model.uid == primaryUid
                                        val distance = if (primaryIndex >= 0) {
                                            kotlin.math.abs(index - primaryIndex)
                                        } else {
                                            0
                                        }
                                        val rowAlpha = when {
                                            isSelected -> 1f
                                            distance <= 1 -> 0.86f
                                            distance == 2 -> 0.68f
                                            else -> 0.56f
                                        }
                                        val modelEfforts = remember(model.uid) { model.supportedEfforts() }
                                        val rowBg by animateColorAsState(
                                            targetValue = if (isSelected) {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(160),
                                            label = "sheet_model_bg_${model.uid}",
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(50))
                                                .background(rowBg)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSelectModel(model)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                                .alpha(rowAlpha),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            if (model.logoRes != null) {
                                                Surface(
                                                    modifier = Modifier.size(24.dp),
                                                    shape = CircleShape,
                                                    color = Color.White,
                                                ) {
                                                    ModelLogoBox(
                                                        logoRes = model.logoRes,
                                                        displayName = model.displayName,
                                                        subtitle = model.subtitle,
                                                        modifier = Modifier.padding(3.dp),
                                                    )
                                                }
                                            }

                                            Text(
                                                text = if (searchQuery.isNotBlank()) {
                                                    buildHighlightedMatchText(
                                                        text = model.displayName,
                                                        query = searchQuery,
                                                        highlightColor = MaterialTheme.colorScheme.onSurface,
                                                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                } else {
                                                    androidx.compose.ui.text.AnnotatedString(model.displayName)
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )

                                            if (isPrimary && modelEfforts.isNotEmpty()) {
                                                val resolvedEffort = model.effectiveEffort(currentEffort) ?: modelEfforts.first()
                                                EffortDotsIndicator(
                                                    efforts = modelEfforts,
                                                    selectedEffort = resolvedEffort,
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentPage = ModelSheetPage.EFFORT
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Text(
                                                        text = resolvedEffort.label,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                    )
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                        contentDescription = "Regola effort",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            } else if (isSelected && selectedModels.size > 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                )
                                            }
                                        }
                                    }
                                }

                                // Descrizione sintetica del modello selezionato (stile Claude)
                                primarySelected?.let { sel ->
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = sel.recommendedTier().hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenFullModelPicker()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Multi-modello e filtri",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }

                                    if (activeEfforts.isNotEmpty()) {
                                        TextButton(
                                            onClick = { currentPage = ModelSheetPage.EFFORT },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "Effort: ${effectiveCurrentEffort.label}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ModelSheetPage.EFFORT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (activeEfforts.isEmpty()) {
                                Text(
                                    text = "Il modello selezionato non supporta livelli di effort configurabili.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 20.dp),
                                )
                            } else {
                                TactilePillEffortSlider(
                                    efforts = activeEfforts,
                                    selectedEffort = effectiveCurrentEffort,
                                    onSelectEffort = onSelectEffort,
                                    showHeaderLabel = true,
                                    showDescription = false,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "${effectiveCurrentEffort.description}. Un effort maggiore migliora la precisione sui piatti complessi, ma richiede più tempo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
