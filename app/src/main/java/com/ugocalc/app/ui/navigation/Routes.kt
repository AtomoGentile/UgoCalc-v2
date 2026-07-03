package com.ugocalc.app.ui.navigation

/** Tutte le destinazioni di navigazione dell'app (§2: struttura schermate). */
object Routes {
    const val HOME = "home"
    const val MODEL_SELECTION = "model_selection"
    const val MANUAL_FOOD_ENTRY = "manual_food_entry"
    const val BARCODE_SCAN = "barcode_scan"
    const val CAMERA_CAPTURE = "camera_capture/{modelIds}"
    const val VISION_RESULTS = "vision_results"
    const val FINAL_RESULT = "final_result"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail/{historyId}"

    const val SETTINGS = "settings"
    const val SETTINGS_API = "settings_api"
    const val SETTINGS_FAVORITES = "settings_favorites"
    const val SETTINGS_PERMISSIONS = "settings_permissions"
    const val SETTINGS_ACCESSIBILITY = "settings_accessibility"
    const val SETTINGS_PREFERRED_MODEL = "settings_preferred_model"
    const val SETTINGS_CONFIDENCE = "settings_confidence"
    const val SETTINGS_TABLES_LIST = "settings_tables_list"
    const val SETTINGS_TABLES_SEARCH = "settings_tables_search"
    const val SETTINGS_CHANGELOG = "settings_changelog"
    const val SETTINGS_HISTORY_CHART = "settings_history_chart"
    const val SETTINGS_TABLE_EDITOR = "settings_table_editor/{tableId}"

    /**
     * Gli id modello possono contenere "/" (es. "openai/gpt-4o" per GitHub Models): senza
     * Uri.encode, Navigation Compose spezzerebbe la route su quello slash invece di trattarlo come
     * parte del segmento {modelIds}, con un crash "destination... cannot be found".
     */
    fun cameraCaptureRoute(modelIds: List<String>) =
        "camera_capture/${modelIds.joinToString(",") { android.net.Uri.encode(it) }}"
    fun historyDetailRoute(historyId: Long) = "history_detail/$historyId"
    fun tableEditorRoute(tableId: Long) = "settings_table_editor/$tableId"
}
