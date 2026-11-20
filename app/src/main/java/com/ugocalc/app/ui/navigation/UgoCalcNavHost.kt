package com.ugocalc.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                UgoBottomNavigationBar(
                    currentRoute = currentRoute,
                    onSelectRoute = navigateTopLevel,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
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
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = safeBack,
                    onOpenEntry = { id -> navController.navigate(Routes.historyDetailRoute(id)) },
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
    }
}

@Composable
private fun UgoBottomNavigationBar(
    currentRoute: String?,
    onSelectRoute: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isDarkSurfaceTheme()
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
        NavigationBar(
            containerColor = containerColor,
            tonalElevation = 0.dp,
        ) {
            TOP_LEVEL_DESTINATIONS.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelectRoute(destination.route)
                    },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
