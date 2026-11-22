package com.ugocalc.app.ui.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ugocalc.app.R
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicLoadLevel
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.DailyGlycemicTimelineCard
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.MacroProgressRing
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.isDarkSurfaceTheme
import com.ugocalc.app.ui.theme.UgoSemanticColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onNavigateToModelSelection: () -> Unit,
    onNavigateDirectlyToCamera: (modelIds: List<String>) -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToBarcodeScan: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onOpenHistoryEntry: (Long) -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToTables: () -> Unit,
    /** Preferito con peso già noto: il totale è già calcolato, si va dritti al risultato. */
    onFavoriteReadyForResult: () -> Unit,
    /** Preferito legato a cronologia o senza peso fissato: apre il passo Quantità. */
    onFavoriteNeedsQuantity: () -> Unit,
    /** Ripete un pasto recente precaricandolo come bozza nel passo Quantità. */
    onRepeatHistoryMeal: () -> Unit = {},
    onOpenGlycemicSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var showAiSetupDialog by remember { mutableStateOf(false) }
    var showOfflineAiDialog by remember { mutableStateOf(false) }
    var timelineExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val onLaunchPhotoAi: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when {
            uiState.tableCount == 0 -> onNavigateToTables()
            !uiState.hasAnyAiKey -> showAiSetupDialog = true
            !uiState.isOnline -> showOfflineAiDialog = true
            uiState.preferredModels.isNotEmpty() -> {
                viewModel.startAnalysisWithPreferredModels()
                onNavigateDirectlyToCamera(uiState.preferredModels.map { it.uid })
            }
            else -> {
                viewModel.startNewAnalysis()
                onNavigateToModelSelection()
            }
        }
    }

    val onLaunchManual: () -> Unit = {
        if (uiState.tableCount == 0) {
            onNavigateToTables()
        } else {
            viewModel.startNewAnalysis()
            onNavigateToManualEntry()
        }
    }

    val onFavoriteClick: (FavoriteDisplay) -> Unit = { favorite ->
        if (favorite.comboGrams != null) {
            viewModel.useComboFavoriteDirectly(favorite)
            onFavoriteReadyForResult()
        } else {
            scope.launch {
                viewModel.openFavoriteForQuantityEntry(favorite)
                onFavoriteNeedsQuantity()
            }
        }
    }

    val onRepeatMealClick: (Long) -> Unit = { historyId ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            if (viewModel.repeatHistoryMeal(historyId)) {
                onRepeatHistoryMeal()
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeHeader(
                sessionMode = uiState.sessionMode,
                onSelectMode = viewModel::setSessionMode,
            )
        },
    ) { padding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "greeting") {
                        HomeGreetingRow(
                            greeting = uiState.greeting,
                            weekMealsCount = uiState.weekMealsCount,
                        )
                    }
                    item(key = "hero") {
                        PhotoAiHeroCard(
                            uiState = uiState,
                            onClick = onLaunchPhotoAi,
                        )
                    }
                    item(key = "quick_actions") {
                        QuickActionsRow(
                            enabled = uiState.tableCount > 0,
                            onBarcodeClick = onNavigateToBarcodeScan,
                            onManualClick = onLaunchManual,
                        )
                    }
                    item(key = "today_summary") {
                        TodayNutritionCard(
                            mealsCount = uiState.todayMealsCount,
                            choG = uiState.todayChoG,
                            proteineG = uiState.todayProteineG,
                            grassiG = uiState.todayGrassiG,
                            glycemicLoad = uiState.todayGlycemicLoad,
                            onClick = onNavigateToHistory,
                        )
                    }
                    uiState.todayGlycemicTimeline?.takeIf { uiState.todayMealsCount > 0 }?.let { timeline ->
                        item(key = "today_glycemic_timeline") {
                            CollapsibleDailyTimelineSection(
                                timeline = timeline,
                                expanded = timelineExpanded,
                                onToggleExpand = { timelineExpanded = !timelineExpanded },
                                onOpenSettings = onOpenGlycemicSettings,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (shouldShowHomeBanner(uiState)) {
                        item(key = "status_banner") {
                            HomeStatusBanner(
                                uiState = uiState,
                                onOpenApiSettings = onNavigateToApiSettings,
                                onOpenTables = onNavigateToTables,
                            )
                        }
                    }
                    if (uiState.favoritesEnabled) {
                        item(key = "favorites_section") {
                            FavoritesSection(
                                favorites = uiState.favorites,
                                onAddClick = { showAddFavoriteDialog = true },
                                onFavoriteClick = onFavoriteClick,
                            )
                        }
                    }
                    item(key = "recent_meals_section") {
                        RecentMealsSection(
                            recentMeals = uiState.recentMeals,
                            onSeeAllClick = onNavigateToHistory,
                            onMealClick = onOpenHistoryEntry,
                            onRepeatClick = onRepeatMealClick,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1) Avviso di configurazione (se serve): è ciò che blocca l'azione principale.
                if (shouldShowHomeBanner(uiState)) {
                    item(key = "status_banner") {
                        HomeStatusBanner(
                            uiState = uiState,
                            onOpenApiSettings = onNavigateToApiSettings,
                            onOpenTables = onNavigateToTables,
                        )
                    }
                }

                // 2) Saluto + azione principale: subito visibili, senza scorrere.
                item(key = "greeting") {
                    HomeGreetingRow(
                        greeting = uiState.greeting,
                        weekMealsCount = uiState.weekMealsCount,
                    )
                }

                item(key = "hero_photo_ai") {
                    PhotoAiHeroCard(
                        uiState = uiState,
                        onClick = onLaunchPhotoAi,
                    )
                }

                item(key = "quick_actions") {
                    QuickActionsRow(
                        enabled = uiState.tableCount > 0,
                        onBarcodeClick = onNavigateToBarcodeScan,
                        onManualClick = onLaunchManual,
                    )
                }

                // 3) Riepilogo di oggi.
                item(key = "today_summary") {
                    TodayNutritionCard(
                        mealsCount = uiState.todayMealsCount,
                        choG = uiState.todayChoG,
                        proteineG = uiState.todayProteineG,
                        grassiG = uiState.todayGrassiG,
                        glycemicLoad = uiState.todayGlycemicLoad,
                        onClick = onNavigateToHistory,
                    )
                }

                uiState.todayGlycemicTimeline?.takeIf { uiState.todayMealsCount > 0 }?.let { timeline ->
                    item(key = "today_glycemic_timeline") {
                        CollapsibleDailyTimelineSection(
                            timeline = timeline,
                            expanded = timelineExpanded,
                            onToggleExpand = { timelineExpanded = !timelineExpanded },
                            onOpenSettings = onOpenGlycemicSettings,
                        )
                    }
                }

                // 4) Scorciatoie e storico recente.
                if (uiState.favoritesEnabled) {
                    item(key = "favorites_section") {
                        FavoritesSection(
                            favorites = uiState.favorites,
                            onAddClick = { showAddFavoriteDialog = true },
                            onFavoriteClick = onFavoriteClick,
                        )
                    }
                }
                item(key = "recent_meals_section") {
                    RecentMealsSection(
                        recentMeals = uiState.recentMeals,
                        onSeeAllClick = onNavigateToHistory,
                        onMealClick = onOpenHistoryEntry,
                        onRepeatClick = onRepeatMealClick,
                    )
                }
            }
        }
    }

    if (showAddFavoriteDialog) {
        AddComboFavoriteDialog(
            onDismiss = { showAddFavoriteDialog = false },
            onConfirm = { _, _, _, _ -> },
            onConfirmFull = { foodName, grams, cho, grassi, prot, gi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi, prot, gi)
                showAddFavoriteDialog = false
            },
        )
    }

    if (showAiSetupDialog) {
        AlertDialog(
            onDismissRequest = { showAiSetupDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            title = {
                Text(
                    text = "Configura una chiave AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Per riconoscere i piatti dalle foto serve almeno una chiave API (es. Google Gemini su AI Studio, gratuita per uso personale). In alternativa puoi usare subito l'inserimento manuale con i ${uiState.totalFoodCount} alimenti già disponibili offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiSetupDialog = false
                        onNavigateToApiSettings()
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Vai alle API")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showAiSetupDialog = false
                            onLaunchManual()
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Usa Manuale")
                    }
                    TextButton(onClick = { showAiSetupDialog = false }) {
                        Text("Chiudi")
                    }
                }
            },
        )
    }

    if (showOfflineAiDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineAiDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(UgoSemanticColors.warningContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = UgoSemanticColors.warning,
                    )
                }
            },
            title = {
                Text(
                    text = "Connessione assente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "L'analisi foto AI richiede una connessione internet. Puoi comunque calcolare subito CHO e Grassi con l'inserimento manuale e i tuoi preferiti offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOfflineAiDialog = false
                        onLaunchManual()
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Usa Manuale")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineAiDialog = false }) {
                    Text("Chiudi")
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    sessionMode: SessionMode,
    onSelectMode: (SessionMode) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ugocalc_logo),
                    contentDescription = "UgoCalc",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Text(
                    text = "UgoCalc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            SessionModeSegmentedPill(
                selectedMode = sessionMode,
                onSelect = onSelectMode,
            )
        }
    }
}

@Composable
private fun SessionModeSegmentedPill(
    selectedMode: SessionMode,
    onSelect: (SessionMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModePillOption(
                label = "Casa",
                icon = Icons.Rounded.Home,
                selected = selectedMode == SessionMode.CASA,
                onClick = { onSelect(SessionMode.CASA) },
            )
            ModePillOption(
                label = "Ristorante",
                icon = Icons.Rounded.Restaurant,
                selected = selectedMode == SessionMode.RISTORANTE,
                onClick = { onSelect(SessionMode.RISTORANTE) },
            )
        }
    }
}

@Composable
private fun ModePillOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "mode_pill_bg_$label",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "mode_pill_fg_$label",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HomeGreetingRow(
    greeting: String,
    weekMealsCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Text(
                text = if (weekMealsCount == 1) "1 pasto · 7 gg" else "$weekMealsCount pasti · 7 gg",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayNutritionCard(
    mealsCount: Int,
    choG: Double,
    proteineG: Double,
    grassiG: Double,
    glycemicLoad: Double,
    onClick: () -> Unit,
) {
    val todayLabel = remember {
        SimpleDateFormat("EEEE d MMM", Locale.ITALIAN)
            .format(Date())
            .replaceFirstChar { it.uppercase() }
    }
    val estimatedKcal = (choG * 4.0 + grassiG * 9.0 + proteineG * 4.0).roundToInt()

    UgoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Oggi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = todayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Cronologia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroProgressRing(
                choG = choG,
                grassiG = grassiG,
                proteineG = proteineG,
                estimatedKcal = estimatedKcal,
                size = 94.dp,
                strokeWidth = 10.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${formatMacroValue(choG)}g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    MacroPill(
                        label = "Prot",
                        value = "${formatMacroValue(proteineG)}g",
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                    )
                    MacroPill(
                        label = "Grassi",
                        value = "${formatMacroValue(grassiG)}g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$mealsCount ${if (mealsCount == 1) "pasto" else "pasti"} oggi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (glycemicLoad > 0.0) {
                            val glLevel = GlycemicCurveCalculator.classifyGlycemicLoad(glycemicLoad)
                            val (glBg, glFg) = when (glLevel) {
                                GlycemicLoadLevel.BASSO -> UgoSemanticColors.successContainer to UgoSemanticColors.onSuccessContainer
                                GlycemicLoadLevel.MEDIO -> UgoSemanticColors.warningContainer to UgoSemanticColors.onWarningContainer
                                GlycemicLoadLevel.ALTO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = glBg,
                            ) {
                                Text(
                                    text = "GL oggi ${"%.1f".format(Locale.ITALIAN, glycemicLoad)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = glFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        } else {
                            Text(
                                text = "GL oggi —",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDailyTimelineSection(
    timeline: com.ugocalc.app.domain.glycemic.DailyGlycemicTimeline,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onToggleExpand,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Andamento glicemico oggi · Picco ${timeline.peakMgDl.roundToInt()} mg/dL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Comprimi timeline" else "Espandi timeline",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            DailyGlycemicTimelineCard(
                timeline = timeline,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

private fun shouldShowHomeBanner(uiState: HomeUiState): Boolean =
    uiState.tableCount == 0 || !uiState.isOnline || !uiState.hasAnyAiKey

@Composable
private fun HomeStatusBanner(
    uiState: HomeUiState,
    onOpenApiSettings: () -> Unit,
    onOpenTables: () -> Unit,
) {
    when {
        uiState.tableCount == 0 -> StatusBanner(
            icon = Icons.Rounded.TableChart,
            title = "Nessuna tabella nutrizionale",
            subtitle = "Ripristina la tabella base CREA o importane una per iniziare i calcoli.",
            tone = BannerTone.WARNING,
            actionLabel = "Tabelle",
            onAction = onOpenTables,
        )
        !uiState.isOnline -> StatusBanner(
            icon = Icons.Rounded.CloudOff,
            title = "Sei offline",
            subtitle = "Manuale, preferiti e tabelle locali funzionano normalmente; la foto AI richiede rete.",
            tone = BannerTone.WARNING,
        )
        !uiState.hasAnyAiKey -> StatusBanner(
            icon = Icons.Rounded.AutoAwesome,
            title = "Foto AI non ancora configurata",
            subtitle = "Collega una chiave API (es. Gemini gratuito) per stimare i piatti da foto.",
            tone = BannerTone.INFO,
            actionLabel = "Configura",
            onAction = onOpenApiSettings,
        )
    }
}

@Composable
private fun PhotoAiHeroCard(
    uiState: HomeUiState,
    onClick: () -> Unit,
) {
    val isDark = isDarkSurfaceTheme()
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val gradientColors = if (isDark) {
        listOf(
            primaryContainer,
            lerp(primaryContainer, primary, 0.30f),
        )
    } else {
        listOf(
            primary,
            lerp(primary, Color.Black, 0.18f),
        )
    }
    val contentColor = if (isDark) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val statusChipText = when {
        uiState.tableCount == 0 -> "Tabella richiesta"
        !uiState.hasAnyAiKey -> "Richiede chiave AI"
        !uiState.isOnline -> "Offline"
        uiState.preferredModels.size == 1 -> uiState.preferredModels.first().displayName
        uiState.preferredModels.size > 1 -> "${uiState.preferredModels.size} modelli preferiti"
        else -> "Pronto · Scegli modello"
    }

    val subtitleText = when {
        uiState.tableCount == 0 -> "Aggiungi o ripristina una tabella nutrizionale per sbloccare l'analisi."
        !uiState.hasAnyAiKey -> "Scatta una foto del piatto per stimare pesi, CHO e Grassi automaticamente."
        !uiState.isOnline -> "Torna online per usare la stima fotografica oppure usa l'inserimento manuale."
        uiState.preferredModels.isNotEmpty() -> "Avvio diretto fotocamera con ${uiState.preferredModels.joinToString(", ") { it.displayName }}."
        else -> "Scatta una o più foto del piatto e ottieni la stima nutrizionale completa."
    }

    val ctaText = when {
        uiState.tableCount == 0 -> "Apri tabelle"
        !uiState.hasAnyAiKey -> "Configura chiave AI"
        !uiState.isOnline -> "Modalità offline"
        else -> "Scatta foto"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = if (isDark) BorderStroke(1.dp, primary.copy(alpha = 0.40f)) else null,
        shadowElevation = if (isDark) 2.dp else 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(contentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = contentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = statusChipText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = contentColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.24f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "CameraX Pro · Telemetria 3D",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = "Analizza Piatto con Foto AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.88f),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.18f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = ctaText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    enabled: Boolean,
    onBarcodeClick: () -> Unit,
    onManualClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard(
            icon = Icons.Rounded.QrCodeScanner,
            accent = MaterialTheme.colorScheme.secondary,
            title = "Barcode",
            subtitle = "Scansiona etichetta",
            enabled = enabled,
            onClick = onBarcodeClick,
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            icon = Icons.Rounded.EditNote,
            accent = MaterialTheme.colorScheme.primary,
            title = "Manuale",
            subtitle = "Cerca & componi",
            enabled = enabled,
            onClick = onManualClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UgoCard(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = if (enabled) 0.14f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun FavoritesSection(
    favorites: List<FavoriteDisplay>,
    onAddClick: () -> Unit,
    onFavoriteClick: (FavoriteDisplay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UgoSectionHeader(
            title = "Preferiti rapidi",
            subtitle = if (favorites.isNotEmpty()) "Tocca per calcolare subito" else null,
            actionLabel = "+ Aggiungi",
            onAction = onAddClick,
        )

        if (favorites.isEmpty()) {
            UgoCard(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Salva i tuoi piatti ricorrenti",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Aggiungi un alimento o una porzione fissa per calcolarli in un tocco.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Aggiungi preferito",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(favorites, key = { it.id }) { favorite ->
                    FavoriteQuickCard(
                        favorite = favorite,
                        onClick = { onFavoriteClick(favorite) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteQuickCard(
    favorite: FavoriteDisplay,
    onClick: () -> Unit,
) {
    UgoCard(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 176.dp, max = 236.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = favorite.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MacroPill(
                label = "CHO",
                value = "${formatMacroValue(favorite.choG)}g",
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
            if (favorite.proteineG > 0.0) {
                MacroPill(
                    label = "Prot",
                    value = "${formatMacroValue(favorite.proteineG)}g",
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
            }
            MacroPill(
                label = "Grassi",
                value = "${formatMacroValue(favorite.grassiG)}g",
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
            )
            if (favorite.glycemicLoad != null && favorite.glycemicLoad > 0.0) {
                MacroPill(
                    label = "GL",
                    value = "%.1f".format(Locale.ITALIAN, favorite.glycemicLoad),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                favorite.comboGrams != null -> "Porzione rapida · ${favorite.comboGrams.toInt()}g"
                favorite.isPerHundredGrams -> "Valori per 100g · scegli peso"
                else -> "Da cronologia · modifica pesi"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentMealsSection(
    recentMeals: List<AnalysisHistoryEntity>,
    onSeeAllClick: () -> Unit,
    onMealClick: (Long) -> Unit,
    onRepeatClick: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UgoSectionHeader(
            title = "Ultimi pasti",
            actionLabel = if (recentMeals.isNotEmpty()) "Vedi tutti" else null,
            onAction = if (recentMeals.isNotEmpty()) onSeeAllClick else null,
        )

        if (recentMeals.isEmpty()) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nessun pasto registrato",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "I tuoi pasti salvati appariranno qui con il riepilogo di CHO e Grassi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentMeals.forEach { meal ->
                    RecentMealRowCard(
                        meal = meal,
                        onClick = { onMealClick(meal.id) },
                        onRepeatClick = { onRepeatClick(meal.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentMealRowCard(
    meal: AnalysisHistoryEntity,
    onClick: () -> Unit,
    onRepeatClick: () -> Unit,
) {
    val isRistorante = meal.sessionMode == SessionMode.RISTORANTE.name
    val timeLabel = remember(meal.analyzedAtEpochMillis) {
        formatRelativeTimestamp(meal.analyzedAtEpochMillis)
    }

    UgoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRistorante) Icons.Rounded.Restaurant else Icons.Rounded.Home,
                    contentDescription = if (isRistorante) "Ristorante" else "Casa",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.foodTitle.ifBlank { "Pasto" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${formatMacroValue(meal.totalChoG)}g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    if (meal.totalProteineG > 0.0) {
                        MacroPill(
                            label = "Prot",
                            value = "${formatMacroValue(meal.totalProteineG)}g",
                            containerColor = UgoSemanticColors.proteinContainer,
                            contentColor = UgoSemanticColors.onProteinContainer,
                        )
                    }
                    MacroPill(
                        label = "Grassi",
                        value = "${formatMacroValue(meal.totalGrassiG)}g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                    )
                    if (meal.glycemicLoad > 0.0) {
                        MacroPill(
                            label = "GL",
                            value = "%.1f".format(Locale.ITALIAN, meal.glycemicLoad),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Surface(
                onClick = onRepeatClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = "Ripeti pasto",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Ripeti",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMacroValue(value: Double): String = when {
    value == 0.0 -> "0"
    value >= 100.0 -> "%.0f".format(Locale.ITALIAN, value)
    else -> "%.1f".format(Locale.ITALIAN, value)
}

private fun formatRelativeTimestamp(epochMillis: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val timePart = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(epochMillis))

    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Oggi, $timePart"
        sameYear && dayDiff == 1 -> "Ieri, $timePart"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale.ITALIAN).format(Date(epochMillis))
    }
}
