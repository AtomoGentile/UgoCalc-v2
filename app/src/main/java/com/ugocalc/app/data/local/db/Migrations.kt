package com.ugocalc.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Aggiunge `normalizedChunkCount` per la ripresa della normalizzazione tabelle (vedi TableRepository). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE official_tables ADD COLUMN normalizedChunkCount INTEGER NOT NULL DEFAULT 0")
    }
}

/** Aggiunge i campi per i preferiti "combinazione standard" alimento+peso (vedi FavoriteShortcutEntity). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorite_shortcuts ADD COLUMN comboGrams REAL")
        db.execSQL("ALTER TABLE favorite_shortcuts ADD COLUMN comboChoPer100g REAL")
        db.execSQL("ALTER TABLE favorite_shortcuts ADD COLUMN comboGrassiPer100g REAL")
    }
}

/** Nuova tabella per "Più ricercate" nella ricerca manuale, con possibilità di fissare una voce (vedi SearchFrequencyEntity). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_frequency` (
                `entryId` INTEGER NOT NULL,
                `searchCount` INTEGER NOT NULL,
                `lastSearchedAtEpochMillis` INTEGER NOT NULL,
                `pinned` INTEGER NOT NULL,
                PRIMARY KEY(`entryId`),
                FOREIGN KEY(`entryId`) REFERENCES `normalized_food_entries`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/** M8 (piano V2 §5, §8): tabella di telemetria per l'archivio di apprendimento della V2 (vedi V2AnalysisRecordEntity). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `v2_analysis_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `analyzedAtEpochMillis` INTEGER NOT NULL,
                `sessionMode` TEXT NOT NULL,
                `aiModelId` TEXT NOT NULL,
                `schemaVersion` INTEGER NOT NULL,
                `sceneGraphJson` TEXT NOT NULL,
                `weightEstimatesJson` TEXT NOT NULL,
                `verificationReportJson` TEXT NOT NULL,
                `nutritionReportJson` TEXT NOT NULL,
                `arbitratoMotivoMancato` TEXT,
                `groundTruthPesiJson` TEXT,
                `groundTruthLivello` TEXT
            )
            """.trimIndent()
        )
    }
}

/** §8: fattori di correzione appresi, uno per segmento esatto (modalità, strategia, modello) (vedi BiasCorrectionEntity). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bias_corrections` (
                `sessionMode` TEXT NOT NULL,
                `strategy` TEXT NOT NULL,
                `aiModelId` TEXT NOT NULL,
                `factor` REAL NOT NULL,
                `sampleCount` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`sessionMode`, `strategy`, `aiModelId`)
            )
            """.trimIndent()
        )
    }
}
