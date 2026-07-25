package com.ugocalc.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.ugocalc.app.ui.finalresult.FinalResultScreen
import com.ugocalc.app.ui.history.HistoryDetailScreen
import com.ugocalc.app.ui.history.HistoryScreen
import com.ugocalc.app.ui.home.HomeScreen
import com.ugocalc.app.ui.manualentry.ManualFoodEntryScreen
import com.ugocalc.app.ui.modelselect.ModelSelectionScreen
import com.ugocalc.app.ui.results.VisionResultsScreen
import com.ugocalc.app.ui.settings.AccessibilitySettingsScreen
import com.ugocalc.app.ui.settings.ApiSettingsScreen
import com.ugocalc.app.ui.settings.BackupScreen
import com.ugocalc.app.ui.settings.ChangelogScreen
import com.ugocalc.app.ui.settings.ScaleCalibrationScreen
import com.ugocalc.app.ui.settings.ConfidenceSettingsScreen
import com.ugocalc.app.ui.recipes.RecipeListScreen
import com.ugocalc.app.ui.recipes.RecipeDetailScreen
import com.ugocalc.app.ui.recipes.RecipeEditScreen
import com.ugocalc.app.ui.settings.FavoritesSettingsScreen
import com.ugocalc.app.ui.settings.HistoryChartSettingsScreen
import com.ugocalc.app.ui.settings.PermissionsSettingsScreen
import com.ugocalc.app.ui.settings.PreferredModelSettingsScreen
import com.ugocalc.app.ui.settings.ShadowModelSettingsScreen
import com.ugocalc.app.ui.settings.SettingsScreen
import com.ugocalc.app.ui.settings.tables.DatabaseSearchScreen
import com.ugocalc.app.ui.settings.tables.OfficialTablesListScreen
import com.ugocalc.app.ui.settings.tables.TableEditorScreen

@Composable
fun UgoCalcNavHost(
    navController: NavHostController,
    startAuthenticated: Boolean = false,
    pendingInviteToken: String? = null,
    onInviteTokenConsumed: () -> Unit = {},
) {
    val startDestination = if (startAuthenticated) Routes.HOME else Routes.AUTH

    // Se c'è un token di invito, naviga subito alla schermata di richiesta accesso
    LaunchedEffect(pendingInviteToken) {
        if (pendingInviteToken != null) {
            navController.navigate(Routes.inviteRequestRoute(pendingInviteToken)) {
                launchSingleTop = true
            }
            onInviteTokenConsumed()
        }
    }

    /** Naviga verso una destinazione principale pulendo lo stack ed evitando duplicati (§ bug fix backstack). */
    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Ritorna indietro in modo sicuro, evitando di svuotare il backstack e finire in uno schermo vuoto. */
    val safeBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToModelSelection = { navController.navigate(Routes.MODEL_SELECTION) },
                onNavigateDirectlyToCamera = { modelIds -> navController.navigate(Routes.cameraCaptureRoute(modelIds)) },
                onNavigateToManualEntry = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                onNavigateToBarcodeScan = { navController.navigate(Routes.BARCODE_SCAN) },
                onNavigateToHistory = { navigateTopLevel(Routes.HISTORY) },
                onNavigateToBiasEvolution = { navigateTopLevel(Routes.BIAS_EVOLUTION) },
                onNavigateToSettings = { navigateTopLevel(Routes.SETTINGS) },
                onFavoriteReadyForResult = { navController.navigate(Routes.FINAL_RESULT) },
                onFavoriteNeedsQuantity = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
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
            )
        }

        composable(
            route = Routes.CAMERA_CAPTURE,
            arguments = listOf(navArgument("modelIds") { type = NavType.StringType }),
        ) { backStackEntry ->
            val modelIds = backStackEntry.arguments?.getString("modelIds").orEmpty()
                .split(",").filter { it.isNotBlank() }.map { android.net.Uri.decode(it) }
            val models: List<AiModel> = AiModelCatalog.ALL.filter { it.id in modelIds }
            CameraCaptureScreen(
                selectedModels = models,
                onAnalysisReady = {
                    navController.navigate(Routes.VISION_RESULTS) {
                        popUpTo(Routes.MODEL_SELECTION)
                    }
                },
                onCancelled = safeBack,
            )
        }

        composable(Routes.VISION_RESULTS) {
            VisionResultsScreen(
                onBack = safeBack,
                onChoReceived = { navController.navigate(Routes.FINAL_RESULT) },
                onRetry = { navController.popBackStack(Routes.CAMERA_CAPTURE, false) },
            )
        }

        composable(Routes.FINAL_RESULT) {
            FinalResultScreen(
                onBack = {
                    // Richiesta utente: tornare direttamente alla galleria foto (§2.4 punto 6).
                    // Se non veniamo dalla fotocamera (es. inserimento manuale), safeBack standard.
                    if (!navController.popBackStack(Routes.CAMERA_CAPTURE, false)) {
                        safeBack()
                    }
                },
                onSaved = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
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
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("historyId") { type = NavType.StringType }),
        ) {
            HistoryDetailScreen(
                onBack = safeBack,
                onOpenV2Details = { recordId -> navController.navigate(Routes.v2ResultRoute(recordId)) }
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
                onBack = safeBack
            )
        }
        composable(
            route = Routes.SETTINGS_RECIPE_DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("recipeId")?.toLongOrNull() ?: 0L
            RecipeDetailScreen(recipeId = id, onEdit = { editId -> navController.navigate(Routes.recipeEditRoute(editId)) }, onBack = safeBack)
        }
        composable(
            route = Routes.SETTINGS_RECIPE_EDIT,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("recipeId")?.toLongOrNull() ?: 0L
            RecipeEditScreen(recipeId = id, onSaved = { navController.popBackStack() }, onBack = safeBack)
        }
        composable(Routes.SETTINGS_BACKUP) { BackupScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_HISTORY_CHART) { HistoryChartSettingsScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_API) { ApiSettingsScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_FAVORITES) { FavoritesSettingsScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_PERMISSIONS) { PermissionsSettingsScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_ACCESSIBILITY) { AccessibilitySettingsScreen(onBack = safeBack) }
        composable(Routes.SETTINGS_PREFERRED_MODEL) { PreferredModelSettingsScreen(onBack = safeBack) }
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
                onOpenShadowModel = { navController.navigate(Routes.SETTINGS_SHADOW_MODEL) },
                onOpenConfidence = { navController.navigate(Routes.SETTINGS_CONFIDENCE) },
                onOpenTables = { navController.navigate(Routes.SETTINGS_TABLES_LIST) },
                onOpenChangelog = { navController.navigate(Routes.SETTINGS_CHANGELOG) },
                onOpenHistoryChart = { navController.navigate(Routes.SETTINGS_HISTORY_CHART) },
                onOpenScaleCalibrations = { navController.navigate(Routes.SETTINGS_SCALE_CALIBRATIONS) },
                onOpenRecipes = { navController.navigate(Routes.SETTINGS_RECIPES) },
                onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                onOpenAccount = { navController.navigate(Routes.SETTINGS_ACCOUNT) },
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

        composable(Routes.SETTINGS_TABLE_EDITOR, arguments = listOf(navArgument("tableId") { type = NavType.StringType })) {
            TableEditorScreen(onBack = safeBack)
        }

        composable(
            route = Routes.V2_RESULT,
            arguments = listOf(navArgument("recordId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            V2ResultScreen(onBack = safeBack)
        }
        composable(Routes.AMBIGUITY_RESOLUTION) { AmbiguityResolutionScreen(onBack = safeBack) }

        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS_ACCOUNT) {
            AccountSettingsScreen(
                onBack = safeBack,
                onSignedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
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
                    }
                },
                onBack = safeBack,
            )
        }
    }
}
