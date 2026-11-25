package com.ugocalc.app.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Color
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
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.buildHighlightedMatchText
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
    var showModelSheet by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var quickSearchQuery by rememberSaveable { mutableStateOf("") }
    var timelineExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        quickSearchQuery = ""
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

    val onToggleSessionMode: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val next = if (uiState.sessionMode == SessionMode.CASA) {
            SessionMode.RISTORANTE
        } else {
            SessionMode.CASA
        }
        viewModel.setSessionMode(next)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    EditorialCanvasShell(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
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
                        onOpenSearch = { isSearchActive = true },
                        onToggleMode = onToggleSessionMode,
                    )
                }
            }
        },
        overlayContent = {
            if (!isSearchActive) {
                HomeFloatingActionDock(
                    uiState = uiState,
                    onManualClick = onLaunchManual,
                    onBarcodeClick = {
                        if (uiState.tableCount == 0) onNavigateToTables() else onNavigateToBarcodeScan()
                    },
                    onOpenModelSheet = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!uiState.hasAnyAiKey) {
                            showAiSetupDialog = true
                        } else if (!uiState.isOnline) {
                            showOfflineAiDialog = true
                        } else {
                            showModelSheet = true
                        }
                    },
                    onPhotoAiClick = onLaunchPhotoAi,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 72.dp),
                )
            }
        },
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "home_canvas_mode",
        ) { searching ->
            if (searching) {
                HomeQuickSearchContent(
                    query = quickSearchQuery,
                    favorites = uiState.favorites,
                    meals = uiState.searchableMeals,
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
                    onRepeatMealClick = { id ->
                        isSearchActive = false
                        quickSearchQuery = ""
                        onRepeatMealClick(id)
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
                        contentPadding = PaddingValues(top = 18.dp, bottom = 152.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "greeting") {
                            HomeEditorialGreeting(
                                greeting = uiState.greeting,
                                sessionMode = uiState.sessionMode,
                                weekMealsCount = uiState.weekMealsCount,
                            )
                        }
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
                        uiState.todayGlycemicTimeline?.let { timeline ->
                            item(key = "today_timeline") {
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
                        contentPadding = PaddingValues(top = 18.dp, bottom = 152.dp),
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
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 156.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 1) Saluto editoriale Serif (stile "How can I help you today?")
                    item(key = "greeting") {
                        HomeEditorialGreeting(
                            greeting = uiState.greeting,
                            sessionMode = uiState.sessionMode,
                            weekMealsCount = uiState.weekMealsCount,
                        )
                    }

                    // 2) Avviso di configurazione (solo se necessario)
                    if (shouldShowHomeBanner(uiState)) {
                        item(key = "status_banner") {
                            HomeStatusBanner(
                                uiState = uiState,
                                onOpenApiSettings = onNavigateToApiSettings,
                                onOpenTables = onNavigateToTables,
                            )
                        }
                    }

                    // 3) Riepilogo nutrizionale di oggi + timeline glicemica
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

                    uiState.todayGlycemicTimeline?.let { timeline ->
                        item(key = "today_timeline") {
                            CollapsibleDailyTimelineSection(
                                timeline = timeline,
                                expanded = timelineExpanded,
                                onToggleExpand = { timelineExpanded = !timelineExpanded },
                                onOpenSettings = onOpenGlycemicSettings,
                            )
                        }
                    }

                    // 4) Preferiti rapidi ("In evidenza")
                    if (uiState.favoritesEnabled) {
                        item(key = "favorites_section") {
                            FavoritesSection(
                                favorites = uiState.favorites,
                                onAddClick = { showAddFavoriteDialog = true },
                                onFavoriteClick = onFavoriteClick,
                            )
                        }
                    }

                    // 5) Ultimi pasti (lista editoriale ariosa senza card pesanti)
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

    if (showModelSheet) {
        ModelEffortBottomSheet(
            availableModels = uiState.availableModels,
            selectedModels = uiState.preferredModels,
            currentEffort = uiState.aiEffort,
            onSelectModel = { model -> viewModel.selectQuickModel(model) },
            onSelectEffort = { effort -> viewModel.setAiEffort(effort) },
            onOpenFullModelPicker = {
                viewModel.startNewAnalysis()
                onNavigateToModelSelection()
            },
            onOpenApiSettings = onNavigateToApiSettings,
            onDismiss = { showModelSheet = false },
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
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180))) togetherWith
                        (fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140)))
                },
                label = "home_mode_caption",
            ) { mode ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onToggleMode)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = if (mode == SessionMode.RISTORANTE) "Ristorante" else "Casa",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun HomeEditorialGreeting(
    greeting: String,
    sessionMode: SessionMode,
    weekMealsCount: Int,
) {
    val todayLabel = remember {
        SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
            .format(Date())
            .replaceFirstChar { it.uppercase() }
    }
    val modeLabel = if (sessionMode == SessionMode.RISTORANTE) "Ristorante" else "Casa"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = todayLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Text(
                    text = if (weekMealsCount == 1) "1 pasto · 7 gg" else "$weekMealsCount pasti · 7 gg",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Text(
            text = "$greeting.",
            style = editorialDisplayTextStyle(MaterialTheme.typography.headlineLarge),
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "Scatta il piatto o aggiungi gli alimenti · $modeLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barra flottante rapida in basso stile "Ask Claude":
 * - `+` a sinistra: inserimento manuale
 * - Chip centrale `Modello · Effort ∨`: apre il bottom sheet selettore Modello/Effort
 * - Icona Barcode a destra: scanner etichetta
 * - Cerchio CTA primario a destra: Foto AI
 */
@Composable
private fun HomeFloatingActionDock(
    uiState: HomeUiState,
    onManualClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onPhotoAiClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkSurfaceTheme()
    val primaryModel: AiModel? = uiState.preferredModels.firstOrNull() ?: uiState.availableModels.firstOrNull()
    val supportsEffort = remember(primaryModel) { primaryModel?.supportedEfforts()?.isNotEmpty() == true }
    val effectiveEffort: AiEffort? = remember(primaryModel, uiState.aiEffort, supportsEffort) {
        if (supportsEffort && primaryModel != null) primaryModel.effectiveEffort(uiState.aiEffort) else null
    }

    val modelChipLabel = when {
        uiState.tableCount == 0 -> "Apri tabelle"
        !uiState.hasAnyAiKey -> "Configura AI"
        !uiState.isOnline -> "Offline"
        uiState.preferredModels.size > 1 -> "${uiState.preferredModels.size} modelli"
        primaryModel != null -> primaryModel.displayName
        else -> "Scegli modello"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = 0.85.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.70f else 0.85f),
        ),
        shadowElevation = if (isDark) 2.dp else 7.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // `+` circolare (Inserimento manuale)
            Surface(
                onClick = onManualClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Inserimento manuale",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Chip centrale Modello + Effort stile Claude
            Surface(
                onClick = onOpenModelSheet,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = modelChipLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (effectiveEffort != null && uiState.hasAnyAiKey && uiState.isOnline) {
                        Text(
                            text = "· ${effectiveEffort.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cambia modello o effort",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Icona Barcode discreta (posizione microfono di Claude)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBarcodeClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = "Scansiona barcode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // CTA circolare primario (Foto AI — Emerald in Casa, Bordeaux in Ristorante)
            Surface(
                onClick = onPhotoAiClick,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = if (isDark) 0.dp else 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Analizza piatto con Foto AI",
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeQuickSearchContent(
    query: String,
    favorites: List<FavoriteDisplay>,
    meals: List<AnalysisHistoryEntity>,
    onFavoriteClick: (FavoriteDisplay) -> Unit,
    onMealClick: (Long) -> Unit,
    onRepeatMealClick: (Long) -> Unit,
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
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
                            text = "${formatMacroValue(fav.choG)}g CHO",
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
                            text = meal.foodTitle.ifBlank { "Pasto" },
                            query = trimmed,
                            highlightColor = MaterialTheme.colorScheme.onSurface,
                            baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (trimmed.isEmpty()) 0.92f else 0.72f),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "CHO ${formatMacroValue(meal.totalChoG)}g · Grassi ${formatMacroValue(meal.totalGrassiG)}g",
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
        modifier = Modifier.fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MacroPill(
                        label = "CHO",
                        value = "${formatMacroValue(choG)}g",
                        containerColor = UgoSemanticColors.choContainer,
                        contentColor = UgoSemanticColors.onChoContainer,
                    )
                    if (proteineG > 0.0) {
                        MacroPill(
                            label = "Prot",
                            value = "${formatMacroValue(proteineG)}g",
                            containerColor = UgoSemanticColors.proteinContainer,
                            contentColor = UgoSemanticColors.onProteinContainer,
                        )
                    }
                    MacroPill(
                        label = "Grassi",
                        value = "${formatMacroValue(grassiG)}g",
                        containerColor = UgoSemanticColors.fatContainer,
                        contentColor = UgoSemanticColors.onFatContainer,
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
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)),
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
                    text = "Andamento glicemico · Picco ${timeline.peakMgDl.roundToInt()} mg/dL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
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
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 166.dp, max = 224.dp),
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                        label = "P",
                        value = "${formatMacroValue(favorite.proteineG)}g",
                        containerColor = UgoSemanticColors.proteinContainer,
                        contentColor = UgoSemanticColors.onProteinContainer,
                    )
                }
                MacroPill(
                    label = "G",
                    value = "${formatMacroValue(favorite.grassiG)}g",
                    containerColor = UgoSemanticColors.fatContainer,
                    contentColor = UgoSemanticColors.onFatContainer,
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
                Surface(
                    onClick = onRepeatClick,
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
            text = meal.foodTitle.ifBlank { "Pasto" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            MacroPill(
                label = "CHO",
                value = "${formatMacroValue(meal.totalChoG)}g",
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
            if (meal.totalProteineG > 0.0) {
                MacroPill(
                    label = "P",
                    value = "${formatMacroValue(meal.totalProteineG)}g",
                    containerColor = UgoSemanticColors.proteinContainer,
                    contentColor = UgoSemanticColors.onProteinContainer,
                )
            }
            MacroPill(
                label = "G",
                value = "${formatMacroValue(meal.totalGrassiG)}g",
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
            )
            if (meal.glycemicLoad > 0.0) {
                MacroPill(
                    label = "GL",
                    value = "%.1f".format(Locale.ITALIAN, meal.glycemicLoad),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMacroValue(value: Double): String = when {
    value == 0.0 -> "0"
    value >= 100.0 -> "%.0f".format(Locale.ITALIAN, value)
    else -> "%.1f".format(Locale.ITALIAN, value)
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
