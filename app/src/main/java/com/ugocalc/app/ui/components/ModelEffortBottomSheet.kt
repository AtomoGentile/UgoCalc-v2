package com.ugocalc.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ugocalc.app.R
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.apiEffortSummary
import com.ugocalc.app.domain.model.effectiveEffort
import com.ugocalc.app.domain.model.effortDescription
import com.ugocalc.app.domain.model.effortLabel
import com.ugocalc.app.domain.model.recommendedTier
import com.ugocalc.app.domain.model.supportedEfforts

enum class ModelSheetPage { MODEL, EFFORT }

/**
 * Bottom sheet selettore Modello & Effort ispirato alla nuova UI di Claude:
 * - Selezione singola (1 tocco) o Multi-modello direttamente dalla card in Home
 * - Filtri rapidi per brand (Gemini, Claude, GPT, ...) + ricerca live
 * - Indicatori effort reali per modello (`· | · · ·` + `Minimal / Low / Medium / High / XHigh / Max >`)
 * - Transizione interna verso lo slider tattile a pillola scura con etichette ufficiali del provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEffortBottomSheet(
    availableModels: List<AiModel>,
    selectedModels: List<AiModel>,
    currentEffort: AiEffort,
    onSelectSingleModel: (AiModel) -> Unit,
    onToggleMultiModel: (AiModel) -> Unit,
    onSelectEffort: (AiEffort) -> Unit,
    onOpenFullModelPicker: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onDismiss: () -> Unit,
    initialPage: ModelSheetPage = ModelSheetPage.MODEL,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember(initialPage) { mutableStateOf(initialPage) }
    var multiSelectMode by remember { mutableStateOf(selectedModels.size > 1) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    val primarySelected = selectedModels.firstOrNull() ?: availableModels.firstOrNull()
    val activeEfforts = remember(selectedModels, primarySelected) {
        val pool = selectedModels.ifEmpty { listOfNotNull(primarySelected) }
        val union = pool.flatMap { it.supportedEfforts() }.toSet()
        AiEffort.entries.filter { it in union }
    }
    val effectiveCurrentEffort = remember(primarySelected, currentEffort, activeEfforts) {
        primarySelected?.effectiveEffort(currentEffort)
            ?: activeEfforts.firstOrNull { it == currentEffort }
            ?: activeEfforts.getOrNull(activeEfforts.size / 2)
            ?: currentEffort
    }

    val brands = remember(availableModels) {
        availableModels.map { quickBrandName(it) }.distinct()
    }

    val filteredModels = remember(availableModels, selectedBrand, searchQuery) {
        val brandPool = if (selectedBrand == null) {
            availableModels
        } else {
            availableModels.filter { quickBrandName(it) == selectedBrand }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            brandPool
        } else {
            brandPool.filter {
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
            // Header del bottom sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
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
                    text = if (currentPage == ModelSheetPage.MODEL) {
                        if (selectedModels.size > 1) "Modelli (${selectedModels.size})" else "Modello"
                    } else {
                        if (selectedModels.size > 1) {
                            "Effort · ${selectedModels.size} modelli"
                        } else {
                            "Effort · ${primarySelected?.displayName ?: "AI"}"
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
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
                        (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(190))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(190))) togetherWith
                            (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(150)))
                    }
                },
                label = "model_effort_sheet_page",
            ) { page ->
                when (page) {
                    ModelSheetPage.MODEL -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = showSearch,
                                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(160)),
                                exit = shrinkVertically(tween(150)) + fadeOut(tween(120)),
                            ) {
                                EditorialSearchBarRow(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    placeholder = "Filtra modelli…",
                                    modifier = Modifier.padding(bottom = 10.dp),
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
                                // Barra modalità Singolo / Multi + filtri rapidi brand
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (availableModels.size > 1) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        ) {
                                            Row(modifier = Modifier.padding(3.dp)) {
                                                SheetModeToggleChip(
                                                    label = "Singolo",
                                                    selected = !multiSelectMode,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        multiSelectMode = false
                                                        primarySelected?.let { onSelectSingleModel(it) }
                                                    },
                                                )
                                                SheetModeToggleChip(
                                                    label = "Multi",
                                                    selected = multiSelectMode,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        multiSelectMode = true
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    if (brands.size > 1) {
                                        LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            item(key = "__all_brands__") {
                                                BrandMiniPill(
                                                    label = "Tutti",
                                                    selected = selectedBrand == null,
                                                    onClick = { selectedBrand = null },
                                                )
                                            }
                                            items(brands, key = { it }) { brand ->
                                                BrandMiniPill(
                                                    label = brand,
                                                    selected = selectedBrand == brand,
                                                    onClick = {
                                                        selectedBrand = if (selectedBrand == brand) null else brand
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

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
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                ) {
                                    itemsIndexed(
                                        items = filteredModels,
                                        key = { _, model -> model.uid },
                                    ) { index, model ->
                                        val isSelected = model.uid in selectedUids
                                        val isPrimary = model.uid == primaryUid
                                        val distance = if (primaryIndex >= 0 && !multiSelectMode) {
                                            kotlin.math.abs(index - primaryIndex)
                                        } else {
                                            0
                                        }
                                        val rowAlpha = when {
                                            isSelected || multiSelectMode -> 1f
                                            distance <= 1 -> 0.88f
                                            distance == 2 -> 0.72f
                                            else -> 0.60f
                                        }
                                        val modelEfforts = remember(model.uid, model.thinkingSupported) { model.supportedEfforts() }
                                        val resolvedEffort = remember(model.uid, currentEffort, modelEfforts) {
                                            model.effectiveEffort(currentEffort) ?: modelEfforts.firstOrNull()
                                        }
                                        val rowBg by animateColorAsState(
                                            targetValue = if (isSelected) {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(160),
                                            label = "sheet_model_bg_${model.uid}",
                                        )
                                        val rowInteraction = remember { MutableInteractionSource() }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem()
                                                .animatedPressScale(rowInteraction, pressedScale = 0.98f)
                                                .clip(RoundedCornerShape(50))
                                                .background(rowBg)
                                                .clickable(
                                                    interactionSource = rowInteraction,
                                                    indication = null,
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (multiSelectMode) {
                                                        onToggleMultiModel(model)
                                                    } else {
                                                        onSelectSingleModel(model)
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                                    AnnotatedString(model.displayName)
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

                                            if (!multiSelectMode && isPrimary && modelEfforts.isNotEmpty() && resolvedEffort != null) {
                                                EffortDotsIndicator(
                                                    efforts = modelEfforts,
                                                    selectedEffort = resolvedEffort,
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
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
                                                        text = model.effortLabel(resolvedEffort),
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
                                            } else if (multiSelectMode) {
                                                val checkBg by animateColorAsState(
                                                    targetValue = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    animationSpec = tween(150),
                                                    label = "multi_check_bg_${model.uid}",
                                                )
                                                Surface(
                                                    modifier = Modifier.size(22.dp),
                                                    shape = CircleShape,
                                                    color = checkBg,
                                                    border = if (!isSelected) {
                                                        BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    } else null,
                                                ) {
                                                    if (isSelected) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Check,
                                                                contentDescription = "Selezionato",
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Descrizione sintetica + metadati AI Studio del modello attivo
                                primarySelected?.let { sel ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val aiStudioBadge = when (sel.thinkingSupported) {
                                        true -> " · Thinking verificato da AI Studio"
                                        false -> " · Modello senza thinking"
                                        null -> ""
                                    }
                                    Text(
                                        text = sel.recommendedTier().hint + aiStudioBadge,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
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
                                            text = "Vista completa",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }

                                    if (activeEfforts.isNotEmpty()) {
                                        val effLabel = primarySelected?.effortLabel(effectiveCurrentEffort)
                                            ?: effectiveCurrentEffort.label
                                        TextButton(
                                            onClick = { currentPage = ModelSheetPage.EFFORT },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "Effort: $effLabel",
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
                                    showDescription = selectedModels.size <= 1,
                                    labelForEffort = { eff ->
                                        if (selectedModels.size > 1) eff.label
                                        else primarySelected?.effortLabel(eff) ?: eff.label
                                    },
                                    descriptionForEffort = { eff ->
                                        primarySelected?.effortDescription(eff) ?: eff.description
                                    },
                                )

                                if (selectedModels.size > 1) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            0.75.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Text(
                                                text = "Parametro reale inviato a ciascun modello",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            selectedModels.forEach { model ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(
                                                        text = model.displayName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = model.apiEffortSummary(currentEffort),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (model.supportedEfforts().isEmpty()) {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        },
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetModeToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(150),
        label = "sheet_mode_bg_$label",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun BrandMiniPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(140),
        label = "brand_pill_$label",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(140),
        label = "brand_fg_$label",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

private fun quickBrandName(model: AiModel): String = when (model.logoRes) {
    R.drawable.logogemini -> "Gemini"
    R.drawable.logoclaude -> "Claude"
    R.drawable.gpt -> "GPT"
    R.drawable.deepseek_logo -> "DeepSeek"
    R.drawable.kimi_ai_logo_hd -> "Kimi"
    R.drawable.nova -> "Nova"
    R.drawable.facebook_llama_logo_3dcc -> "Llama"
    R.drawable.mistral -> "Mistral"
    R.drawable.qwen_logo_png_seeklogo_611723 -> "Qwen"
    else -> model.subtitle.ifBlank { "Altro" }
}
