package com.ugocalc.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.ui.auth.AccountSettingsScreen
import com.ugocalc.app.ui.auth.AuthScreen
import com.ugocalc.app.ui.auth.InviteRequestScreen
import com.ugocalc.app.ui.barcode.BarcodeScanScreen
import com.ugocalc.app.ui.bias.AmbiguityResolutionScreen
import com.ugocalc.app.ui.bias.BenchmarkDashboardScreen
import com.ugocalc.app.ui.bias.BenchmarkGroundTruthEntryScreen
import com.ugocalc.app.ui.bias.BiasEvolutionScreen
import com.ugocalc.app.ui.bias.GroundTruthEntryScreen
import com.ugocalc.app.ui.bias.UnifiedGroundTruthEntryScreen
import com.ugocalc.app.ui.bias.V2ResultScreen
import com.ugocalc.app.ui.camera.CameraCaptureScreen
import com.ugocalc.app.ui.components.isDarkSurfaceTheme
import com.ugocalc.app.ui.finalresult.FinalResultScreen
import com.ugocalc.app.ui.foodhub.FoodHubScreen
import com.ugocalc.app.ui.history.HistoryDetailScreen
import com.ugocalc.app.ui.history.HistoryScreen
import com.ugocalc.app.ui.home.HomeScreen
import com.ugocalc.app.ui.manualentry.ManualFoodEntryScreen
import com.ugocalc.app.ui.modelselect.ModelSelectionScreen
import com.ugocalc.app.ui.onboarding.OnboardingScreen
import com.ugocalc.app.ui.recipes.RecipeDetailScreen
import com.ugocalc.app.ui.recipes.RecipeEditScreen
import com.ugocalc.app.ui.recipes.RecipeListScreen
import com.ugocalc.app.ui.results.VisionResultsScreen
import com.ugocalc.app.ui.settings.AccessibilitySettingsScreen
import com.ugocalc.app.ui.settings.AllModelsPickerScreen
import com.ugocalc.app.ui.settings.ApiSettingsScreen
import com.ugocalc.app.ui.settings.BackupScreen
import com.ugocalc.app.ui.settings.ChangelogScreen
import com.ugocalc.app.ui.settings.ConfidenceSettingsScreen
import com.ugocalc.app.ui.settings.CostManagementScreen
import com.ugocalc.app.ui.settings.FavoritesSettingsScreen
import com.ugocalc.app.ui.settings.GlycemicProfileSettingsScreen
import com.ugocalc.app.ui.settings.HistoryChartSettingsScreen
import com.ugocalc.app.ui.settings.NormalizationModelSettingsScreen
import com.ugocalc.app.ui.settings.PermissionsSettingsScreen
import com.ugocalc.app.ui.settings.PreferredModelSettingsScreen
import com.ugocalc.app.ui.settings.ScaleCalibrationScreen
import com.ugocalc.app.ui.settings.SettingsScreen
import com.ugocalc.app.ui.settings.SettingsViewModel
import com.ugocalc.app.ui.settings.ShadowModelSettingsScreen
import com.ugocalc.app.ui.settings.tables.DatabaseSearchScreen
import com.ugocalc.app.ui.settings.tables.OfficialTablesListScreen
import com.ugocalc.app.ui.settings.tables.TableEditorScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.HOME, "Home", Icons.Rounded.Home),
    TopLevelDestination(Routes.HISTORY, "Cronologia", Icons.Rounded.History),
    TopLevelDestination(Routes.FOOD_HUB, "Alimenti", Icons.Rounded.RestaurantMenu),
    TopLevelDestination(Routes.SETTINGS, "Impostazioni", Icons.Rounded.Settings),
)

private val TOP_LEVEL_ROUTES = TOP_LEVEL_DESTINATIONS.map { it.route }.toSet()

@Composable
fun UgoCalcNavHost(
    navController: NavHostController,
    startDestination: String = Routes.HOME,
    isCognitoAuthenticated: Boolean = false,
    hasUpdateAvailable: Boolean = false,
    pendingInviteToken: String? = null,
    onInviteTokenConsumed: () -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    resolveCameraModels: (List<String>) -> List<AiModel> = { ids ->
        val idSet = ids.toSet()
        AiModelCatalog.ALL.filter { it.uid in idSet || it.id in idSet }.distinctBy { it.uid }
    },
) {
    // Se c'è un token di invito, naviga alla schermata di richiesta accesso solo DOPO l'autenticazione Cognito
    LaunchedEffect(pendingInviteToken, isCognitoAuthenticated) {
        if (pendingInviteToken != null && isCognitoAuthenticated) {
            navController.navigate(Routes.inviteRequestRoute(pendingInviteToken)) {
                launchSingleTop = true
            }
            onInviteTokenConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    /** Naviga verso una destinazione principale dei 4 tab mantenendo HOME come radice pulita. */
    val navigateTopLevel: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    /** Ritorna indietro in modo sicuro, evitando di svuotare il backstack e finire in uno schermo vuoto. */
    val safeBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val targetIsTop = targetState.destination.route in TOP_LEVEL_ROUTES
                val sourceIsTop = initialState.destination.route in TOP_LEVEL_ROUTES
                if (targetIsTop && sourceIsTop) {
                    fadeIn(tween(210)) + scaleIn(initialScale = 0.985f, animationSpec = tween(210))
                } else {
                    slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(220))
                }
            },
            exitTransition = {
                val targetIsTop = targetState.destination.route in TOP_LEVEL_ROUTES
                val sourceIsTop = initialState.destination.route in TOP_LEVEL_ROUTES
                if (targetIsTop && sourceIsTop) {
                    fadeOut(tween(150))
                } else {
                    slideOutHorizontally(tween(220)) { -it / 10 } + fadeOut(tween(180))
                }
            },
            popEnterTransition = {
                slideInHorizontally(tween(240)) { -it / 8 } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(190))
            },
        ) {

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onStartApp = {
                        onCompleteOnboarding()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onConfigureAi = {
                        onCompleteOnboarding()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate(Routes.SETTINGS_API) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToModelSelection = { navController.navigate(Routes.MODEL_SELECTION) },
                    onNavigateDirectlyToCamera = { modelIds ->
                        navController.navigate(Routes.cameraCaptureRoute(modelIds))
                    },
                    onNavigateToManualEntry = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                    onNavigateToBarcodeScan = { navController.navigate(Routes.BARCODE_SCAN) },
                    onNavigateToHistory = { navigateTopLevel(Routes.HISTORY) },
                    onOpenHistoryEntry = { id -> navController.navigate(Routes.historyDetailRoute(id)) },
                    onNavigateToApiSettings = { navController.navigate(Routes.SETTINGS_API) },
                    onNavigateToTables = { navController.navigate(Routes.SETTINGS_TABLES_LIST) },
                    onFavoriteReadyForResult = { navController.navigate(Routes.FINAL_RESULT) },
                    onFavoriteNeedsQuantity = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                    onRepeatHistoryMeal = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                    onOpenGlycemicSettings = { navController.navigate(Routes.GLYCEMIC_PROFILE_SETTINGS) },
                )
            }

            composable(Routes.FOOD_HUB) {
                FoodHubScreen(
                    onOpenTables = { navController.navigate(Routes.SETTINGS_TABLES_LIST) },
                    onOpenRecipes = { navController.navigate(Routes.SETTINGS_RECIPES) },
                    onOpenFavorites = { navController.navigate(Routes.SETTINGS_FAVORITES) },
                    onStartManualEntry = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                )
            }

            composable(Routes.MANUAL_FOOD_ENTRY) {
                ManualFoodEntryScreen(
                    onProceed = {
                        navController.navigate(Routes.FINAL_RESULT) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onBack = safeBack,
                )
            }

            composable(Routes.BARCODE_SCAN) {
                BarcodeScanScreen(
                    onBack = safeBack,
                    onEntryReady = {
                        navController.navigate(Routes.MANUAL_FOOD_ENTRY) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }

            composable(Routes.MODEL_SELECTION) {
                ModelSelectionScreen(
                    onNext = { modelIds -> navController.navigate(Routes.cameraCaptureRoute(modelIds)) },
                    onBack = safeBack,
                    onOpenApiSettings = { navController.navigate(Routes.SETTINGS_API) },
                )
            }

            composable(
                route = Routes.CAMERA_CAPTURE,
                arguments = listOf(navArgument("modelIds") { type = NavType.StringType }),
            ) { backStackEntry ->
                val modelIds = backStackEntry.arguments?.getString("modelIds").orEmpty()
                    .split(",").filter { it.isNotBlank() }.map { android.net.Uri.decode(it) }
                val models = resolveCameraModels(modelIds)
                CameraCaptureScreen(
                    selectedModels = models,
                    onAnalysisReady = {
                        navController.navigate(Routes.VISION_RESULTS) {
                            launchSingleTop = true
                        }
                    },
                    onCancelled = safeBack,
                )
            }

            composable(Routes.VISION_RESULTS) {
                VisionResultsScreen(
                    onBack = safeBack,
                    onChoReceived = { navController.navigate(Routes.FINAL_RESULT) },
                    onRetry = {
                        if (!navController.popBackStack(Routes.CAMERA_CAPTURE, false)) {
                            safeBack()
                        }
                    },
                    onNavigateToV2Details = { navController.navigate(Routes.v2ResultRoute()) },
                    onNavigateToAmbiguity = { navController.navigate(Routes.AMBIGUITY_RESOLUTION) },
                    onOpenApiSettings = { navController.navigate(Routes.SETTINGS_API) },
                )
            }

            composable(Routes.FINAL_RESULT) {
                FinalResultScreen(
                    onBack = {
                        // Torna direttamente alla galleria foto se proveniamo dalla fotocamera;
                        // altrimenti safeBack standard (es. inserimento manuale o preferito).
                        if (!navController.popBackStack(Routes.CAMERA_CAPTURE, false)) {
                            safeBack()
                        }
                    },
                    onSaved = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onRegisterGroundTruth = { navController.navigate(Routes.UNIFIED_GROUND_TRUTH_ENTRY) },
                    onRegisterBenchmarkGroundTruth = { navController.navigate(Routes.UNIFIED_GROUND_TRUTH_ENTRY) },
                    onOpenGlycemicSettings = { navController.navigate(Routes.GLYCEMIC_PROFILE_SETTINGS) },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = safeBack,
                    onOpenEntry = { id -> navController.navigate(Routes.historyDetailRoute(id)) },
                    onOpenGlycemicSettings = { navController.navigate(Routes.GLYCEMIC_PROFILE_SETTINGS) },
                    showBackButton = false,
                )
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("historyId") { type = NavType.StringType }),
            ) {
                HistoryDetailScreen(
                    onBack = safeBack,
                    onOpenV2Details = { recordId -> navController.navigate(Routes.v2ResultRoute(recordId)) },
                    onOpenGlycemicSettings = { navController.navigate(Routes.GLYCEMIC_PROFILE_SETTINGS) },
                )
            }

            composable(Routes.BIAS_EVOLUTION) {
                BiasEvolutionScreen(
                    onBack = safeBack,
                    onOpenGroundTruthEntry = { navController.navigate(Routes.UNIFIED_GROUND_TRUTH_ENTRY) },
                    onOpenBenchmarkDashboard = { navController.navigate(Routes.BENCHMARK_DASHBOARD) },
                    onOpenV2Details = { recordId -> navController.navigate(Routes.v2ResultRoute(recordId)) },
                )
            }
            composable(Routes.GROUND_TRUTH_ENTRY) { GroundTruthEntryScreen(onBack = safeBack) }
            composable(Routes.BENCHMARK_GROUND_TRUTH_ENTRY) { BenchmarkGroundTruthEntryScreen(onBack = safeBack) }
            composable(Routes.UNIFIED_GROUND_TRUTH_ENTRY) { UnifiedGroundTruthEntryScreen(onBack = safeBack) }
            composable(Routes.BENCHMARK_DASHBOARD) { BenchmarkDashboardScreen(onBack = safeBack) }

            composable(Routes.SETTINGS_CHANGELOG) { ChangelogScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_SCALE_CALIBRATIONS) { ScaleCalibrationScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_RECIPES) {
                RecipeListScreen(
                    onRecipeSelected = { recipeId -> navController.navigate(Routes.recipeDetailRoute(recipeId)) },
                    onAddRecipe = { navController.navigate(Routes.recipeEditRoute(0L)) },
                    onBack = safeBack,
                )
            }
            composable(
                route = Routes.SETTINGS_RECIPE_DETAIL,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("recipeId")?.toLongOrNull() ?: 0L
                RecipeDetailScreen(
                    recipeId = id,
                    onEdit = { editId -> navController.navigate(Routes.recipeEditRoute(editId)) },
                    onBack = safeBack,
                )
            }
            composable(
                route = Routes.SETTINGS_RECIPE_EDIT,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("recipeId")?.toLongOrNull() ?: 0L
                RecipeEditScreen(
                    recipeId = id,
                    onSaved = { navController.popBackStack() },
                    onBack = safeBack,
                )
            }
            composable(Routes.SETTINGS_BACKUP) { BackupScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_COST_MANAGEMENT) { CostManagementScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_HISTORY_CHART) { HistoryChartSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_API) {
                ApiSettingsScreen(
                    onBack = safeBack,
                    onNavigateToModelsPicker = { navController.navigate(Routes.SETTINGS_API_MODELS_PICKER) },
                )
            }
            composable(Routes.SETTINGS_API_MODELS_PICKER) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val availableModels by viewModel.availableModels.collectAsState()
                val state by viewModel.state.collectAsState()
                AllModelsPickerScreen(
                    onBack = safeBack,
                    models = availableModels,
                    currentSelection = state.userSettings.enabledAnalysisModelIds,
                    onConfirm = { viewModel.saveAllEnabledModels(it) },
                )
            }
            composable(Routes.SETTINGS_FAVORITES) { FavoritesSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_PERMISSIONS) { PermissionsSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_ACCESSIBILITY) { AccessibilitySettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_PREFERRED_MODEL) { PreferredModelSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_NORMALIZATION_MODEL) { NormalizationModelSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_SHADOW_MODEL) { ShadowModelSettingsScreen(onBack = safeBack) }
            composable(Routes.SETTINGS_CONFIDENCE) { ConfidenceSettingsScreen(onBack = safeBack) }
            composable(Routes.GLYCEMIC_PROFILE_SETTINGS) { GlycemicProfileSettingsScreen(onBack = safeBack) }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = safeBack,
                    onOpenApi = { navController.navigate(Routes.SETTINGS_API) },
                    onOpenFavorites = { navController.navigate(Routes.SETTINGS_FAVORITES) },
                    onOpenPermissions = { navController.navigate(Routes.SETTINGS_PERMISSIONS) },
                    onOpenAccessibility = { navController.navigate(Routes.SETTINGS_ACCESSIBILITY) },
                    onOpenPreferredModel = { navController.navigate(Routes.SETTINGS_PREFERRED_MODEL) },
                    onOpenNormalizationModel = { navController.navigate(Routes.SETTINGS_NORMALIZATION_MODEL) },
                    onOpenShadowModel = { navController.navigate(Routes.SETTINGS_SHADOW_MODEL) },
                    onOpenConfidence = { navController.navigate(Routes.SETTINGS_CONFIDENCE) },
                    onOpenTables = { navController.navigate(Routes.SETTINGS_TABLES_LIST) },
                    onOpenChangelog = { navController.navigate(Routes.SETTINGS_CHANGELOG) },
                    onOpenHistoryChart = { navController.navigate(Routes.SETTINGS_HISTORY_CHART) },
                    onOpenScaleCalibrations = { navController.navigate(Routes.SETTINGS_SCALE_CALIBRATIONS) },
                    onOpenRecipes = { navController.navigate(Routes.SETTINGS_RECIPES) },
                    onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                    onOpenAccount = { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                    onOpenCostManagement = { navController.navigate(Routes.SETTINGS_COST_MANAGEMENT) },
                    onOpenBiasEvolution = { navController.navigate(Routes.BIAS_EVOLUTION) },
                    onOpenGlycemicProfile = { navController.navigate(Routes.GLYCEMIC_PROFILE_SETTINGS) },
                    hasUpdateAvailable = hasUpdateAvailable,
                    showBackButton = false,
                )
            }

            composable(Routes.SETTINGS_TABLES_LIST) {
                OfficialTablesListScreen(
                    onBack = safeBack,
                    onOpenTable = { tableId -> navController.navigate(Routes.tableEditorRoute(tableId)) },
                    onOpenSearch = { navController.navigate(Routes.SETTINGS_TABLES_SEARCH) },
                )
            }

            composable(Routes.SETTINGS_TABLES_SEARCH) { DatabaseSearchScreen(onBack = safeBack) }

            composable(
                Routes.SETTINGS_TABLE_EDITOR,
                arguments = listOf(navArgument("tableId") { type = NavType.StringType }),
            ) {
                TableEditorScreen(onBack = safeBack)
            }

            composable(
                route = Routes.V2_RESULT,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.StringType
                        nullable = false
                    },
                ),
            ) {
                V2ResultScreen(onBack = safeBack)
            }
            composable(Routes.AMBIGUITY_RESOLUTION) { AmbiguityResolutionScreen(onBack = safeBack) }

            composable(Routes.AUTH) {
                val canNavigateBack = navController.previousBackStackEntry != null
                AuthScreen(
                    onAuthenticated = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.AUTH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onBack = if (canNavigateBack) safeBack else null,
                )
            }

            composable(Routes.SETTINGS_ACCOUNT) {
                AccountSettingsScreen(
                    onBack = safeBack,
                    onSignedOut = safeBack,
                    onOpenAuth = {
                        navController.navigate(Routes.AUTH) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.INVITE_REQUEST,
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
            ) { backStackEntry ->
                val token = backStackEntry.arguments?.getString("token").orEmpty()
                InviteRequestScreen(
                    token = token,
                    onApproved = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = safeBack,
                )
            }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            UgoBottomNavigationBar(
                currentRoute = currentRoute,
                onSelectRoute = navigateTopLevel,
                badgeRoutes = if (hasUpdateAvailable) setOf(Routes.SETTINGS) else emptySet(),
            )
        }
    }
}

@Composable
private fun UgoBottomNavigationBar(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    badgeRoutes: Set<String> = emptySet(),
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isDarkSurfaceTheme()

    val selectedIndex = TOP_LEVEL_DESTINATIONS
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubIndex by remember { mutableIntStateOf(selectedIndex) }
    var barWidthPx by remember { mutableIntStateOf(1) }

    val displayedIndex = if (isScrubbing) scrubIndex else selectedIndex
    val activeDestination = TOP_LEVEL_DESTINATIONS[displayedIndex]

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.97f)
    }
    val pillColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val scrimSurface = MaterialTheme.colorScheme.surface

    fun indexForX(x: Float): Int {
        val count = TOP_LEVEL_DESTINATIONS.size
        if (barWidthPx <= 0) return selectedIndex
        val slotW = barWidthPx.toFloat() / count.toFloat()
        return (x / slotW).toInt().coerceIn(0, count - 1)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to scrimSurface.copy(alpha = 0.86f),
                    0.75f to scrimSurface.copy(alpha = 0.97f),
                    1f to scrimSurface,
                ),
            )
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Barra inferiore a PILLOLA FLOTTANTE staccata dai bordi su overlay trasparente,
        // con pillola attiva elastica, scrub orizzontale e tooltip scuro flottante.
        Surface(
            modifier = Modifier
                .padding(horizontal = 30.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(50),
            color = containerColor,
            shadowElevation = if (isDark) 6.dp else 10.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(
                width = 0.85.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.70f else 0.80f),
            ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
                    .pointerInput(selectedIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isScrubbing = true
                                val nextIdx = indexForX(offset.x)
                                scrubIndex = nextIdx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = {
                                val targetRoute = TOP_LEVEL_DESTINATIONS[scrubIndex].route
                                isScrubbing = false
                                onSelectRoute(targetRoute)
                            },
                            onDragCancel = {
                                isScrubbing = false
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val nextIdx = indexForX(change.position.x)
                                if (nextIdx != scrubIndex) {
                                    scrubIndex = nextIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                        )
                    },
            ) {
                val slotWidth = maxWidth / TOP_LEVEL_DESTINATIONS.size
                val pillWidth = (slotWidth - 8.dp).coerceIn(48.dp, 68.dp)
                val pillHeight = 44.dp
                val pillTargetX = slotWidth * displayedIndex + (slotWidth - pillWidth) / 2
                val animatedPillX by animateDpAsState(
                    targetValue = pillTargetX,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "bottom_bar_pill_x",
                )

                // Pillola elastica scorrevole dietro l'icona selezionata / trascinata
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillX)
                        .align(Alignment.CenterStart)
                        .size(width = pillWidth, height = pillHeight)
                        .clip(RoundedCornerShape(50))
                        .background(pillColor),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TOP_LEVEL_DESTINATIONS.forEachIndexed { index, destination ->
                        val isActive = index == displayedIndex
                        val iconTint by animateColorAsState(
                            targetValue = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            },
                            animationSpec = tween(160),
                            label = "nav_icon_${destination.route}",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = when {
                                isActive && isScrubbing -> 1.18f
                                isActive -> 1.06f
                                else -> 0.96f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "nav_scale_${destination.route}",
                        )
                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics {
                                    role = Role.Tab
                                    selected = currentRoute == destination.route
                                    contentDescription = destination.label
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    if (currentRoute != destination.route) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectRoute(destination.route)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(23.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                )
                                if (destination.route in badgeRoutes) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tooltip scuro flottante sopra la tab evidenziata durante lo scrub orizzontale
        val tooltipTargetX = remember(displayedIndex, barWidthPx) {
            val count = TOP_LEVEL_DESTINATIONS.size
            val slotW = barWidthPx.toFloat() / count.toFloat()
            (slotW * (displayedIndex + 0.5f) - barWidthPx / 2f).roundToInt()
        }
        val animatedTooltipOffsetX by animateFloatAsState(
            targetValue = tooltipTargetX.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "tooltip_x",
        )

        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(tween(110)) + scaleIn(
                initialScale = 0.84f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            ) + slideInVertically(tween(130)) { it / 2 },
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120)) +
                slideOutVertically(tween(120)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp)
                .offset { IntOffset(animatedTooltipOffsetX.roundToInt(), 0) },
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = activeDestination.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
