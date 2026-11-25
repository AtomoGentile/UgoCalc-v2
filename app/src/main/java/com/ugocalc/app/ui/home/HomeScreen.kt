package com.ugocalc.app.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.effectiveEffort
import com.ugocalc.app.domain.model.effortLabel
import com.ugocalc.app.domain.model.supportedEfforts
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.DailyGlycemicTimelineCard
import com.ugocalc.app.ui.components.EditorialCanvasShell
import com.ugocalc.app.ui.components.EditorialSearchBarRow
import com.ugocalc.app.ui.components.EditorialSectionLabel
import com.ugocalc.app.ui.components.EditorialTopBar
import com.ugocalc.app.ui.components.FloatingSplitPillActions
import com.ugocalc.app.ui.components.FramelessListRow
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.MacroProgressRing
import com.ugocalc.app.ui.components.ModelEffortBottomSheet
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.ModelSheetPage
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.animatedPressScale
import com.ugocalc.app.ui.components.buildHighlightedMatchText
import com.ugocalc.app.ui.components.formatCompactGrams
import com.ugocalc.app.ui.components.formatMealDisplayTitle
import com.ugocalc.app.ui.components.isDarkSurfaceTheme
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.ui.theme.editorialDisplayTextStyle
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
    var activeModelSheetPage by remember { mutableStateOf<ModelSheetPage?>(null) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var quickSearchQuery by rememberSaveable { mutableStateOf("") }
    var quickActionsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    BackHandler(enabled = isSearchActive || quickActionsExpanded) {
        if (quickActionsExpanded) {
            quickActionsExpanded = false
        } else {
            isSearchActive = false
            quickSearchQuery = ""
        }
    }

    val onLaunchPhotoAi: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        quickActionsExpanded = false
        val modelsToRun = uiState.activeModels
        when {
            uiState.tableCount == 0 -> onNavigateToTables()
            !uiState.hasAnyAiKey -> showAiSetupDialog = true
            !uiState.isOnline -> showOfflineAiDialog = true
            modelsToRun.isNotEmpty() -> {
                viewModel.startAnalysisWithPreferredModels()
                onNavigateDirectlyToCamera(modelsToRun.map { it.uid })
            }
            else -> {
                viewModel.startNewAnalysis()
                onNavigateToModelSelection()
            }
        }
    }

    val onLaunchManual: () -> Unit = {
        quickActionsExpanded = false
        if (uiState.tableCount == 0) {
            onNavigateToTables()
        } else {
            viewModel.startNewAnalysis()
            onNavigateToManualEntry()
        }
    }

    val onLaunchBarcode: () -> Unit = {
        quickActionsExpanded = false
        if (uiState.tableCount == 0) {
            onNavigateToTables()
        } else {
            onNavigateToBarcodeScan()
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

    val onToggleSessionMode: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val next = if (uiState.sessionMode == SessionMode.CASA) {
            SessionMode.RISTORANTE
        } else {
            SessionMode.CASA
        }
        viewModel.setSessionMode(next)
    }

    val openSheetOrGuard: (ModelSheetPage) -> Unit = { page ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        quickActionsExpanded = false
        when {
            !uiState.hasAnyAiKey -> showAiSetupDialog = true
            !uiState.isOnline -> showOfflineAiDialog = true
            else -> activeModelSheetPage = page
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheetSurfaceColor = MaterialTheme.colorScheme.surface

    EditorialCanvasShell(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 }) togetherWith
                        (fadeOut(tween(130)) + slideOutVertically(tween(130)) { -it / 4 })
                },
                label = "home_topbar_mode",
            ) { searching ->
                if (searching) {
                    EditorialSearchBarRow(
                        query = quickSearchQuery,
                        onQueryChange = { quickSearchQuery = it },
                        placeholder = "Cerca pasti o preferiti…",
                        onBack = {
                            isSearchActive = false
                            quickSearchQuery = ""
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                } else {
                    HomeEditorialTopBar(
                        sessionMode = uiState.sessionMode,
                        onOpenSearch = {
                            quickActionsExpanded = false
                            isSearchActive = true
                        },
                        onToggleMode = onToggleSessionMode,
                    )
                }
            }
        },
        overlayContent = {
            AnimatedVisibility(
                visible = !isSearchActive,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { it / 2 } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(130)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                // Scrim sfumato continuo dal bordo del composer fino alla pillola di navigazione,
                // così nessun pasto in scorrimento trapela nello spazio tra card e pillola.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.16f to sheetSurfaceColor.copy(alpha = 0.88f),
                                0.42f to sheetSurfaceColor,
                                1f to sheetSurfaceColor,
                            ),
                        )
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp, bottom = 82.dp),
                ) {
                    HomeChatComposerCard(
                        uiState = uiState,
                        quickActionsExpanded = quickActionsExpanded,
                        onToggleQuickActions = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            quickActionsExpanded = !quickActionsExpanded
                        },
                        onPromptNoteChange = viewModel::setPromptNote,
                        onManualClick = onLaunchManual,
                        onBarcodeClick = onLaunchBarcode,
                        onAddFavoriteClick = {
                            quickActionsExpanded = false
                            showAddFavoriteDialog = true
                        },
                        onOpenModelPicker = { openSheetOrGuard(ModelSheetPage.MODEL) },
                        onOpenEffortPicker = { openSheetOrGuard(ModelSheetPage.EFFORT) },
                        onPhotoAiClick = onLaunchPhotoAi,
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "home_canvas_mode",
        ) { searching ->
            if (searching) {
                HomeQuickSearchContent(
                    query = quickSearchQuery,
                    favorites = uiState.favorites,
                    meals = uiState.searchableMeals,
                    bottomPadding = navBarBottom + 108.dp,
                    onFavoriteClick = { fav ->
                        isSearchActive = false
                        quickSearchQuery = ""
                        onFavoriteClick(fav)
                    },
                    onMealClick = { id ->
                        isSearchActive = false
                        quickSearchQuery = ""
                        onOpenHistoryEntry(id)
                    },
                )
            } else if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(top = 18.dp, bottom = navBarBottom + 244.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(top = 18.dp, bottom = navBarBottom + 244.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = navBarBottom + 256.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
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
    }

    activeModelSheetPage?.let { initialPage ->
        ModelEffortBottomSheet(
            availableModels = uiState.availableModels,
            selectedModels = uiState.activeModels,
            currentEffort = uiState.aiEffort,
            onSelectSingleModel = { model -> viewModel.selectQuickModel(model) },
            onToggleMultiModel = { model -> viewModel.toggleQuickModel(model) },
            onSelectEffort = { effort -> viewModel.setAiEffort(effort) },
            onOpenFullModelPicker = {
                viewModel.startNewAnalysis()
                onNavigateToModelSelection()
            },
            onOpenApiSettings = onNavigateToApiSettings,
            onDismiss = { activeModelSheetPage = null },
            initialPage = initialPage,
        )
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
                    shape = RoundedCornerShape(50),
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
                        shape = RoundedCornerShape(50),
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
                    shape = RoundedCornerShape(50),
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
private fun HomeEditorialTopBar(
    sessionMode: SessionMode,
    onOpenSearch: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val isDark = isDarkSurfaceTheme()
    val isRistorante = sessionMode == SessionMode.RISTORANTE

    EditorialTopBar(
        leading = {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (isDark) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                shadowElevation = if (isDark) 0.dp else 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.ugocalc_logo),
                        contentDescription = "UgoCalc",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                }
            }
        },
        center = {
            AnimatedContent(
                targetState = sessionMode,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    )) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = 0.90f, animationSpec = tween(140)))
                },
                label = "home_mode_caption",
            ) { mode ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .animatedPressScale(interaction, pressedScale = 0.94f)
                        .clip(RoundedCornerShape(50))
                        .clickable(interactionSource = interaction, indication = null, onClick = onToggleMode)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = if (mode == SessionMode.RISTORANTE) "Ristorante" else "Casa",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
            }
        },
        trailing = {
            FloatingSplitPillActions(
                leftIcon = Icons.Rounded.Search,
                leftDescription = "Cerca pasti e preferiti",
                onLeftClick = onOpenSearch,
                rightIcon = if (isRistorante) Icons.Rounded.Restaurant else Icons.Rounded.Home,
                rightDescription = if (isRistorante) {
                    "Modalità Ristorante attiva, tocca per passare a Casa"
                } else {
                    "Modalità Casa attiva, tocca per passare a Ristorante"
                },
                onRightClick = onToggleMode,
                rightActive = true,
                activeColor = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

/**
 * Vera card Composer stile Claude flottante sopra la pillola di navigazione:
 * - Riga superiore: campo chat reale per aggiungere note al prompt Vision AI (V1 e V2)
 * - Riga inferiore: `+` animato (apre azioni rapide Manuale / Barcode / Preferito),
 *   chip Modello (apre scelta singola/multi-modello), chip Effort reale del modello,
 *   e CTA circolare primario Foto AI che avvia esattamente i modelli mostrati + la nota chat.
 */
@Composable
private fun HomeChatComposerCard(
    uiState: HomeUiState,
    quickActionsExpanded: Boolean,
    onToggleQuickActions: () -> Unit,
    onPromptNoteChange: (String) -> Unit,
    onManualClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onAddFavoriteClick: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenEffortPicker: () -> Unit,
    onPhotoAiClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkSurfaceTheme()
    var isFocused by remember { mutableStateOf(false) }

    val activeModels = uiState.activeModels
    val primaryModel: AiModel? = activeModels.firstOrNull()
    val supportedEfforts = remember(activeModels) {
        val union = activeModels.flatMap { it.supportedEfforts() }.toSet()
        AiEffort.entries.filter { it in union }
    }
    val effectiveEffort: AiEffort? = remember(primaryModel, uiState.aiEffort, supportedEfforts) {
        if (supportedEfforts.isEmpty()) null
        else primaryModel?.effectiveEffort(uiState.aiEffort) ?: supportedEfforts.getOrNull(supportedEfforts.size / 2)
    }
    val realEffortLabel = remember(primaryModel, effectiveEffort) {
        if (effectiveEffort == null) null
        else primaryModel?.effortLabel(effectiveEffort) ?: effectiveEffort.label
    }

    val modelChipLabel = when {
        uiState.tableCount == 0 -> "Apri tabelle"
        !uiState.hasAnyAiKey -> "Configura AI"
        !uiState.isOnline -> "Offline"
        activeModels.size > 1 -> "${activeModels.size} modelli"
        primaryModel != null -> primaryModel.displayName
        else -> "Scegli modello"
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused || uiState.promptNote.isNotBlank() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            isDark -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.90f)
        },
        animationSpec = tween(180),
        label = "composer_border",
    )
    val plusRotation by animateFloatAsState(
        targetValue = if (quickActionsExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "plus_rotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Vassoio azioni rapide animato sopra il composer (+ -> Manuale / Barcode / Preferito)
        AnimatedVisibility(
            visible = quickActionsExpanded,
            enter = fadeIn(tween(150)) + slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            ) { it / 2 } + expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)),
            exit = fadeOut(tween(120)) + slideOutVertically(tween(130)) { it / 2 } + shrinkVertically(tween(140)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ComposerQuickActionPill(
                    icon = Icons.Rounded.EditNote,
                    label = "Manuale",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onManualClick,
                    modifier = Modifier.weight(1f),
                )
                ComposerQuickActionPill(
                    icon = Icons.Rounded.QrCodeScanner,
                    label = "Barcode",
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onBarcodeClick,
                    modifier = Modifier.weight(1f),
                )
                ComposerQuickActionPill(
                    icon = Icons.Rounded.StarOutline,
                    label = "Preferito",
                    accent = MaterialTheme.colorScheme.tertiary,
                    onClick = onAddFavoriteClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Card Composer principale
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
            border = BorderStroke(if (isFocused) 1.25.dp else 0.9.dp, borderColor),
            shadowElevation = if (isDark) 4.dp else 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Riga 1: vero campo chat spazioso in alto per arricchire il prompt Vision AI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 38.dp)
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (uiState.promptNote.isEmpty()) {
                            Text(
                                text = "Note sul piatto per l'AI (es. integrale, poco olio)…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = uiState.promptNote,
                            onValueChange = onPromptNoteChange,
                            maxLines = 3,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused },
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.promptNote.isNotEmpty(),
                        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.8f),
                    ) {
                        Surface(
                            onClick = { onPromptNoteChange("") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancella nota prompt",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Riga 2: controlli stile Claude (`+`, chip Modello, chip Effort reale, CTA Foto AI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val plusInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = onToggleQuickActions,
                        interactionSource = plusInteraction,
                        modifier = Modifier
                            .size(36.dp)
                            .animatedPressScale(plusInteraction, pressedScale = 0.88f),
                        shape = CircleShape,
                        color = if (quickActionsExpanded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        contentColor = if (quickActionsExpanded) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Azioni rapide: Manuale, Barcode, Preferito",
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = plusRotation },
                            )
                        }
                    }

                    // Gruppo sinistro/centrale: chip Modello + chip Effort reale affiancati senza sprechi di spazio
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val modelInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = onOpenModelPicker,
                            interactionSource = modelInteraction,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .animatedPressScale(modelInteraction, pressedScale = 0.95f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (primaryModel?.logoRes != null && activeModels.size == 1 && uiState.hasAnyAiKey) {
                                    Surface(
                                        modifier = Modifier.size(16.dp),
                                        shape = CircleShape,
                                        color = Color.White,
                                    ) {
                                        ModelLogoBox(
                                            logoRes = primaryModel.logoRes,
                                            displayName = primaryModel.displayName,
                                            subtitle = primaryModel.subtitle,
                                            modifier = Modifier.padding(2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = modelChipLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Seleziona modello",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        // Chip Effort reale (visibile solo se il modello scelto supporta effort!)
                        AnimatedVisibility(
                            visible = realEffortLabel != null && uiState.hasAnyAiKey && uiState.isOnline,
                            enter = fadeIn(tween(160)) + expandHorizontally(spring(stiffness = Spring.StiffnessMedium)),
                            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                        ) {
                            val effortInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = onOpenEffortPicker,
                                interactionSource = effortInteraction,
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.animatedPressScale(effortInteraction, pressedScale = 0.94f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = realEffortLabel.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Regola effort",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    // CTA circolare primario Foto AI (con pallino se è presente una nota nel prompt!)
                    val cameraInteraction = remember { MutableInteractionSource() }
                    Box(contentAlignment = Alignment.TopEnd) {
                        Surface(
                            onClick = onPhotoAiClick,
                            interactionSource = cameraInteraction,
                            modifier = Modifier
                                .size(40.dp)
                                .animatedPressScale(cameraInteraction, pressedScale = 0.89f),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = if (isDark) 0.dp else 3.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = "Analizza piatto con Foto AI",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (uiState.promptNote.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 1.dp, y = (-1).dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerQuickActionPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkSurfaceTheme()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.animatedPressScale(interaction, pressedScale = 0.94f),
        shape = RoundedCornerShape(50),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)),
        shadowElevation = if (isDark) 2.dp else 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun HomeQuickSearchContent(
    query: String,
    favorites: List<FavoriteDisplay>,
    meals: List<AnalysisHistoryEntity>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onFavoriteClick: (FavoriteDisplay) -> Unit,
    onMealClick: (Long) -> Unit,
) {
    val trimmed = query.trim()
    val matchedFavorites = remember(favorites, trimmed) {
        if (trimmed.isEmpty()) favorites.take(4)
        else favorites.filter { it.label.contains(trimmed, ignoreCase = true) }
    }
    val matchedMeals = remember(meals, trimmed) {
        if (trimmed.isEmpty()) meals.take(15)
        else meals.filter { it.foodTitle.contains(trimmed, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
    ) {
        if (matchedFavorites.isNotEmpty()) {
            item(key = "qs_fav_header") {
                EditorialSectionLabel(
                    title = "In evidenza · Preferiti",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            itemsIndexed(matchedFavorites, key = { _, fav -> "qs_fav_${fav.id}" }) { index, fav ->
                FramelessListRow(
                    onClick = { onFavoriteClick(fav) },
                    modifier = Modifier.animateItem(),
                    showDivider = index < matchedFavorites.lastIndex,
                    leading = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailing = {
                        Text(
                            text = "${formatCompactGrams(fav.choG)} CHO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = UgoSemanticColors.cho,
                        )
                    },
                ) {
                    Text(
                        text = buildHighlightedMatchText(
                            text = fav.label,
                            query = trimmed,
                            highlightColor = MaterialTheme.colorScheme.onSurface,
                            baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (trimmed.isEmpty()) 0.92f else 0.72f),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item(key = "qs_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (matchedMeals.isNotEmpty()) {
            item(key = "qs_meals_header") {
                EditorialSectionLabel(
                    title = if (trimmed.isEmpty()) "Pasti recenti" else "Risultati (${matchedMeals.size})",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            itemsIndexed(matchedMeals, key = { _, meal -> "qs_meal_${meal.id}" }) { index, meal ->
                val isRistorante = meal.sessionMode == SessionMode.RISTORANTE.name
                val compactTime = remember(meal.analyzedAtEpochMillis) {
                    formatCompactRelativeTime(meal.analyzedAtEpochMillis)
                }
                FramelessListRow(
                    onClick = { onMealClick(meal.id) },
                    modifier = Modifier.animateItem(),
                    showDivider = index < matchedMeals.lastIndex,
                    trailing = {
                        Icon(
                            imageVector = if (isRistorante) Icons.Rounded.Restaurant else Icons.Rounded.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = compactTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Text(
                        text = buildHighlightedMatchText(
                            text = formatMealDisplayTitle(meal.foodTitle),
                            query = trimmed,
                            highlightColor = MaterialTheme.colorScheme.onSurface,
                            baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (trimmed.isEmpty()) 0.92f else 0.72f),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "CHO ${formatCompactGrams(meal.totalChoG)} · Grassi ${formatCompactGrams(meal.totalGrassiG)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (matchedFavorites.isEmpty()) {
            item(key = "qs_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Nessun risultato per \"$trimmed\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Prova con un altro nome di pasto o preferito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    val estimatedKcal = (choG * 4.0 + grassiG * 9.0 + proteineG * 4.0).roundToInt()

    UgoCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MacroProgressRing(
                choG = choG,
                grassiG = grassiG,
                proteineG = proteineG,
                estimatedKcal = estimatedKcal,
                size = 86.dp,
                strokeWidth = 9.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Oggi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (mealsCount == 1) "1 pasto" else "$mealsCount pasti",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = formatCompactGrams(choG),
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                        compact = true,
                    )
                    MacroPill(
                        label = "Prot",
                        value = formatCompactGrams(proteineG),
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                        compact = true,
                    )
                    MacroPill(
                        label = "Grassi",
                        value = formatCompactGrams(grassiG),
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
                        compact = true,
                    )
                }

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
                            text = "Carico glicemico ${"%.1f".format(Locale.ITALIAN, glycemicLoad)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = glFg,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }
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
private fun FavoritesSection(
    favorites: List<FavoriteDisplay>,
    onAddClick: () -> Unit,
    onFavoriteClick: (FavoriteDisplay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorialSectionLabel(
            title = "Preferiti rapidi",
            actionLabel = "+ Aggiungi",
            onAction = onAddClick,
        )

        if (favorites.isEmpty()) {
            FramelessListRow(
                onClick = onAddClick,
                showDivider = false,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Aggiungi preferito",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            ) {
                Text(
                    text = "Salva i tuoi piatti ricorrenti",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Un tocco per calcolare subito le porzioni abituali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .widthIn(min = 166.dp, max = 224.dp)
            .animatedPressScale(interaction, pressedScale = 0.96f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = favorite.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MacroPill(
                    label = "CHO",
                    value = formatCompactGrams(favorite.choG),
                    containerColor = UgoSemanticColors.choContainer,
                    contentColor = UgoSemanticColors.onChoContainer,
                    compact = true,
                )
                if (favorite.proteineG > 0.0) {
                    MacroPill(
                        label = "P",
                        value = formatCompactGrams(favorite.proteineG),
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                        compact = true,
                    )
                }
                MacroPill(
                    label = "G",
                    value = formatCompactGrams(favorite.grassiG),
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
                    compact = true,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    favorite.comboGrams != null -> "${favorite.comboGrams.toInt()}g · rapido"
                    favorite.isPerHundredGrams -> "per 100g · scegli peso"
                    else -> "da cronologia"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentMealsSection(
    recentMeals: List<AnalysisHistoryEntity>,
    onSeeAllClick: () -> Unit,
    onMealClick: (Long) -> Unit,
    onRepeatClick: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorialSectionLabel(
            title = "Ultimi pasti",
            actionLabel = if (recentMeals.isNotEmpty()) "Vedi tutti" else null,
            onAction = if (recentMeals.isNotEmpty()) onSeeAllClick else null,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (recentMeals.isEmpty()) {
            FramelessListRow(
                onClick = {},
                showDivider = false,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                Text(
                    text = "Nessun pasto registrato",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "I tuoi pasti salvati appariranno qui con CHO e Grassi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column {
                recentMeals.forEachIndexed { index, meal ->
                    RecentMealEditorialRow(
                        meal = meal,
                        showDivider = index < recentMeals.lastIndex,
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
private fun RecentMealEditorialRow(
    meal: AnalysisHistoryEntity,
    showDivider: Boolean,
    onClick: () -> Unit,
    onRepeatClick: () -> Unit,
) {
    val isRistorante = meal.sessionMode == SessionMode.RISTORANTE.name
    val compactTime = remember(meal.analyzedAtEpochMillis) {
        formatCompactRelativeTime(meal.analyzedAtEpochMillis)
    }

    FramelessListRow(
        onClick = onClick,
        showDivider = showDivider,
        dividerStartPadding = 44.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRistorante) Icons.Rounded.Restaurant else Icons.Rounded.Home,
                    contentDescription = if (isRistorante) "Ristorante" else "Casa",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = compactTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val repeatInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onRepeatClick,
                    interactionSource = repeatInteraction,
                    modifier = Modifier.animatedPressScale(repeatInteraction, pressedScale = 0.92f),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = "Ripeti pasto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Ripeti",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        },
    ) {
        Text(
            text = formatMealDisplayTitle(meal.foodTitle),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            MacroPill(
                label = "CHO",
                value = formatCompactGrams(meal.totalChoG),
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
                compact = true,
            )
            if (meal.totalProteineG > 0.0) {
                MacroPill(
                    label = "P",
                    value = formatCompactGrams(meal.totalProteineG),
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                    compact = true,
                )
            }
            MacroPill(
                label = "G",
                value = formatCompactGrams(meal.totalGrassiG),
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
                compact = true,
            )
            if (meal.glycemicLoad > 0.0) {
                MacroPill(
                    label = "GL",
                    value = "%.1f".format(Locale.ITALIAN, meal.glycemicLoad),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
            }
        }
    }
}

private fun formatCompactRelativeTime(epochMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)

    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)}m"
        sameYear && dayDiff == 0 -> SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(epochMillis))
        sameYear && dayDiff == 1 -> "Ieri"
        hours < 7 * 24 -> "${(hours / 24).coerceAtLeast(1)}g"
        else -> SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date(epochMillis))
    }
}
