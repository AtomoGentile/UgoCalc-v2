package com.ugocalc.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.ui.barcode.BarcodeScanScreen
import com.ugocalc.app.ui.bias.BenchmarkDashboardScreen
import com.ugocalc.app.ui.bias.BenchmarkGroundTruthEntryScreen
import com.ugocalc.app.ui.bias.BiasEvolutionScreen
import com.ugocalc.app.ui.bias.GroundTruthEntryScreen
import com.ugocalc.app.ui.camera.CameraCaptureScreen
import com.ugocalc.app.ui.finalresult.FinalResultScreen
import com.ugocalc.app.ui.history.HistoryDetailScreen
import com.ugocalc.app.ui.history.HistoryScreen
import com.ugocalc.app.ui.home.HomeScreen
import com.ugocalc.app.ui.manualentry.ManualFoodEntryScreen
import com.ugocalc.app.ui.modelselect.ModelSelectionScreen
import com.ugocalc.app.ui.overlaytest.OverlayTestScreen
import com.ugocalc.app.ui.results.VisionResultsScreen
import com.ugocalc.app.ui.settings.AccessibilitySettingsScreen
import com.ugocalc.app.ui.settings.ApiSettingsScreen
import com.ugocalc.app.ui.settings.BackupScreen
import com.ugocalc.app.ui.settings.ChangelogScreen
import com.ugocalc.app.ui.settings.ConfidenceSettingsScreen
import com.ugocalc.app.ui.settings.FavoritesSettingsScreen
import com.ugocalc.app.ui.settings.HistoryChartSettingsScreen
import com.ugocalc.app.ui.settings.PermissionsSettingsScreen
import com.ugocalc.app.ui.settings.PreferredModelSettingsScreen
import com.ugocalc.app.ui.settings.SettingsScreen
import com.ugocalc.app.ui.settings.tables.DatabaseSearchScreen
import com.ugocalc.app.ui.settings.tables.OfficialTablesListScreen
import com.ugocalc.app.ui.settings.tables.TableEditorScreen

@Composable
fun UgoCalcNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToModelSelection = { navController.navigate(Routes.MODEL_SELECTION) },
                onNavigateDirectlyToCamera = { modelIds -> navController.navigate(Routes.cameraCaptureRoute(modelIds)) },
                onNavigateToManualEntry = { navController.navigate(Routes.MANUAL_FOOD_ENTRY) },
                onNavigateToBarcodeScan = { navController.navigate(Routes.BARCODE_SCAN) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBiasEvolution = { navController.navigate(Routes.BIAS_EVOLUTION) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
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
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.BARCODE_SCAN) {
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onCancelled = { navController.popBackStack() },
            )
        }

        composable(Routes.VISION_RESULTS) {
            VisionResultsScreen(
                onBack = { navController.popBackStack() },
                onChoReceived = { navController.navigate(Routes.FINAL_RESULT) },
                onRetry = { navController.popBackStack(Routes.CAMERA_CAPTURE, false) },
            )
        }

        composable(Routes.FINAL_RESULT) {
            FinalResultScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onRegisterGroundTruth = { navController.navigate(Routes.GROUND_TRUTH_ENTRY) },
                onRegisterBenchmarkGroundTruth = { navController.navigate(Routes.BENCHMARK_GROUND_TRUTH_ENTRY) },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { id -> navController.navigate(Routes.historyDetailRoute(id)) },
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("historyId") { type = NavType.StringType }),
        ) {
            HistoryDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BIAS_EVOLUTION) {
            BiasEvolutionScreen(
                onBack = { navController.popBackStack() },
                onOpenGroundTruthEntry = { navController.navigate(Routes.GROUND_TRUTH_ENTRY) },
                onOpenBenchmarkGroundTruthEntry = { navController.navigate(Routes.BENCHMARK_GROUND_TRUTH_ENTRY) },
                onOpenBenchmarkDashboard = { navController.navigate(Routes.BENCHMARK_DASHBOARD) },
            )
        }
        composable(Routes.GROUND_TRUTH_ENTRY) { GroundTruthEntryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.BENCHMARK_GROUND_TRUTH_ENTRY) { BenchmarkGroundTruthEntryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.BENCHMARK_DASHBOARD) { BenchmarkDashboardScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenApi = { navController.navigate(Routes.SETTINGS_API) },
                onOpenFavorites = { navController.navigate(Routes.SETTINGS_FAVORITES) },
                onOpenPermissions = { navController.navigate(Routes.SETTINGS_PERMISSIONS) },
                onOpenAccessibility = { navController.navigate(Routes.SETTINGS_ACCESSIBILITY) },
                onOpenPreferredModel = { navController.navigate(Routes.SETTINGS_PREFERRED_MODEL) },
                onOpenConfidence = { navController.navigate(Routes.SETTINGS_CONFIDENCE) },
                onOpenTables = { navController.navigate(Routes.SETTINGS_TABLES_LIST) },
                onOpenChangelog = { navController.navigate(Routes.SETTINGS_CHANGELOG) },
                onOpenHistoryChart = { navController.navigate(Routes.SETTINGS_HISTORY_CHART) },
                onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                onOpenOverlayTest = { navController.navigate(Routes.OVERLAY_TEST) },
            )
        }

        composable(Routes.OVERLAY_TEST) { OverlayTestScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.SETTINGS_CHANGELOG) { ChangelogScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_HISTORY_CHART) { HistoryChartSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_API) { ApiSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_FAVORITES) { FavoritesSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_PERMISSIONS) { PermissionsSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_ACCESSIBILITY) { AccessibilitySettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_PREFERRED_MODEL) { PreferredModelSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS_CONFIDENCE) { ConfidenceSettingsScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.SETTINGS_TABLES_LIST) {
            OfficialTablesListScreen(
                onBack = { navController.popBackStack() },
                onOpenTable = { tableId -> navController.navigate(Routes.tableEditorRoute(tableId)) },
                onOpenSearch = { navController.navigate(Routes.SETTINGS_TABLES_SEARCH) },
            )
        }

        composable(Routes.SETTINGS_TABLES_SEARCH) { DatabaseSearchScreen(onBack = { navController.popBackStack() }) }

        composable(
            route = Routes.SETTINGS_TABLE_EDITOR,
            arguments = listOf(navArgument("tableId") { type = NavType.StringType }),
        ) {
            TableEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
