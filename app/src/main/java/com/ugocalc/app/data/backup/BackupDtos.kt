package com.ugocalc.app.data.backup

import kotlinx.serialization.Serializable

/**
 * Formato dell'archivio di backup unico (piano V2 §17): storico, tabelle, fattori di correzione
 * del bias e preferiti in un solo file JSON versionato. Mai le chiavi API (§17 "per scelta
 * esplicita di sicurezza"). DTO separati dalle entity Room di proposito: se lo schema del database
 * cambia, queste classi restano stabili finché non si decide esplicitamente di incrementare
 * [UgoCalcBackupArchive.schemaVersion] — nessuna rottura silenziosa importando un file esportato da
 * una versione precedente dell'app.
 */
@Serializable
data class UgoCalcBackupArchive(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val history: List<HistoryEntryDto>,
    val v2Records: List<V2AnalysisRecordDto>,
    val scaleCalibrations: List<ScaleCalibrationDto>,
    val tables: List<TableDto>,
    val biasCorrections: List<BiasCorrectionDto>,
    val favorites: List<FavoriteDto>,
    val recipes: List<RecipeDto> = emptyList(),
) {
    companion object {
        /** Versione corrente del formato di backup (§17), indipendente dallo schema del database Room. */
        const val SCHEMA_VERSION = 5
    }
}

@Serializable
data class HistoryEntryDto(
    val foodTitle: String,
    val analyzedAtEpochMillis: Long,
    val totalChoG: Double,
    val totalGrassiG: Double,
    val modelDisplayNamesJson: String,
    val detailsJson: String,
)

@Serializable
data class V2AnalysisRecordDto(
    val analyzedAtEpochMillis: Long,
    val sessionMode: String,
    val aiModelId: String,
    val schemaVersion: Int,
    val sceneGraphJson: String,
    val weightEstimatesJson: String,
    val verificationReportJson: String,
    val nutritionReportJson: String,
    val arbitratoMotivoMancato: String?,
    val groundTruthPesiJson: String?,
    val groundTruthLivello: String?,
    val sessionId: String?,
)

@Serializable
data class ScaleCalibrationDto(
    val sessionMode: String = "CASA",
    val type: String,
    val name: String,
    val valueCm: Double,
    val capacityMl: Double? = null,
    val widthCm: Double? = null,
    val heightCm: Double? = null,
    val imagePath: String? = null,
)

/** [entries] sono le sole righe già normalizzate: il documento originale caricato dall'utente non viene esportato (§17, §7 nota su storedFilePath). */
@Serializable
data class TableDto(
    val displayName: String,
    val fileType: String,
    val uploadedAtEpochMillis: Long,
    val priorityOrder: Int,
    val rawExtractedText: String,
    val normalizationStatus: String,
    val noteConversione: String,
    val discardedRowsJson: String,
    val normalizationErrorMessage: String?,
    val normalizedChunkCount: Int,
    val entries: List<FoodEntryDto>,
)

@Serializable
data class FoodEntryDto(
    val nome: String,
    val choPer100g: Double,
    val grassiPer100g: Double,
    val rowOrder: Int,
    val isManuallyEdited: Boolean,
)

@Serializable
data class BiasCorrectionDto(
    val sessionMode: String? = null, // opzionale: presente nei backup < schema 6, ignorato in import
    val strategy: String,
    val aiModelId: String,
    val foodCategory: String,
    val factor: Double,
    val sampleCount: Int,
    val updatedAtEpochMillis: Long,
)

/**
 * [historyEntryIndex] è la posizione nella lista [UgoCalcBackupArchive.history] dello stesso
 * archivio, non un id di database: dopo una sostituzione totale la cronologia viene reinserita con
 * id autogenerati nuovi, quindi il legame va per posizione, non per id originale. Null per i
 * preferiti "combinazione standard" indipendenti dalla cronologia (comboGrams valorizzato).
 */
@Serializable
data class FavoriteDto(
    val historyEntryIndex: Int?,
    val label: String,
    val sortOrder: Int,
    val comboGrams: Double?,
    val comboChoPer100g: Double?,
    val comboGrassiPer100g: Double?,
)

@Serializable
data class RecipeDto(
    val sessionMode: String,
    val name: String,
    val description: String,
    val ingredients: List<RecipeIngredientDto>,
)

@Serializable
data class RecipeIngredientDto(
    val name: String,
    val percentage: Double,
)

/** Conteggi da mostrare all'utente prima di confermare l'import (§17 "sostituzione totale con conferma esplicita"). */
data class BackupSummary(
    val historyCount: Int,
    val v2RecordsCount: Int,
    val scaleCalibrationsCount: Int,
    val tablesCount: Int,
    val biasCorrectionsCount: Int,
    val favoritesCount: Int,
    val recipesCount: Int = 0,
)

/** Esito della validazione di un archivio prima di applicarlo (§17, stessa disciplina di versionamento di §7). */
sealed interface ImportValidation {
    data class Ok(val summary: BackupSummary) : ImportValidation
    data class SchemaTooNew(val found: Int, val supported: Int) : ImportValidation
    data class Invalid(val reason: String) : ImportValidation
}
