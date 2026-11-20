package com.ugocalc.app.ui.navigation

/** Tutte le destinazioni di navigazione dell'app (§2: struttura schermate). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val FOOD_HUB = "food_hub"
    const val MODEL_SELECTION = "model_selection"
    const val MANUAL_FOOD_ENTRY = "manual_food_entry"
    const val BARCODE_SCAN = "barcode_scan"
    const val CAMERA_CAPTURE = "camera_capture/{modelIds}"
    const val VISION_RESULTS = "vision_results"
    const val FINAL_RESULT = "final_result"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail/{historyId}"
    const val BIAS_EVOLUTION = "bias_evolution"
    const val GROUND_TRUTH_ENTRY = "ground_truth_entry"
    const val BENCHMARK_GROUND_TRUTH_ENTRY = "benchmark_ground_truth_entry"
    const val UNIFIED_GROUND_TRUTH_ENTRY = "unified_ground_truth_entry"
    const val BENCHMARK_DASHBOARD = "benchmark_dashboard"
    const val V2_RESULT = "v2_result/{recordId}"
    fun v2ResultRoute(recordId: Long? = null) = if (recordId == null) "v2_result/null" else "v2_result/$recordId"
    const val AMBIGUITY_RESOLUTION = "ambiguity_resolution"

    const val SETTINGS = "settings"
    const val SETTINGS_API = "settings_api"
    const val SETTINGS_API_MODELS_PICKER = "settings_api_models_picker"
    const val SETTINGS_FAVORITES = "settings_favorites"
    const val SETTINGS_PERMISSIONS = "settings_permissions"
    const val SETTINGS_ACCESSIBILITY = "settings_accessibility"
    const val SETTINGS_PREFERRED_MODEL = "settings_preferred_model"
    const val SETTINGS_NORMALIZATION_MODEL = "settings_normalization_model"
    const val SETTINGS_SHADOW_MODEL = "settings_shadow_model"
    const val SETTINGS_CONFIDENCE = "settings_confidence"
    const val SETTINGS_TABLES_LIST = "settings_tables_list"
    const val SETTINGS_TABLES_SEARCH = "settings_tables_search"
    const val SETTINGS_CHANGELOG = "settings_changelog"
    const val SETTINGS_HISTORY_CHART = "settings_history_chart"
    const val SETTINGS_SCALE_CALIBRATIONS = "settings_scale_calibrations"
    const val SETTINGS_RECIPES = "settings_recipes"
    const val SETTINGS_RECIPE_DETAIL = "settings_recipe_detail/{recipeId}"
    const val SETTINGS_RECIPE_EDIT = "settings_recipe_edit/{recipeId}"
    fun recipeDetailRoute(recipeId: Long) = "settings_recipe_detail/$recipeId"
    fun recipeEditRoute(recipeId: Long) = "settings_recipe_edit/$recipeId"
    const val SETTINGS_TABLE_EDITOR = "settings_table_editor/{tableId}"
    const val SETTINGS_BACKUP = "settings_backup"
    const val SETTINGS_COST_MANAGEMENT = "settings_cost_management"

    const val AUTH = "auth"
    const val SETTINGS_ACCOUNT = "settings_account"
    const val INVITE_REQUEST = "invite_request/{token}"
    fun inviteRequestRoute(token: String) = "invite_request/$token"

    /**
     * Gli id modello possono contenere "/" (es. ARN di AWS Bedrock): senza
     * Uri.encode, Navigation Compose spezzerebbe la route su quello slash invece di trattarlo come
     * parte del segmento {modelIds}, con un crash "destination... cannot be found".
     */
    fun cameraCaptureRoute(modelIds: List<String>) =
        "camera_capture/${modelIds.joinToString(",") { android.net.Uri.encode(it) }}"
    fun historyDetailRoute(historyId: Long) = "history_detail/$historyId"
    fun tableEditorRoute(tableId: Long) = "settings_table_editor/$tableId"
}
