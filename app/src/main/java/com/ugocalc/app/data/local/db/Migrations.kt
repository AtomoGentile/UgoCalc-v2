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

/** Benchmark pesato V1 vs V2 (§10, §13 punto 5): nuova tabella `benchmark_meals` più il collegamento `sessionId` su `v2_analysis_records` (vedi BenchmarkMealEntity). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE v2_analysis_records ADD COLUMN sessionId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `benchmark_meals` (
                `sessionId` TEXT PRIMARY KEY NOT NULL,
                `capturedAtEpochMillis` INTEGER NOT NULL,
                `sessionMode` TEXT NOT NULL,
                `aiModelId` TEXT NOT NULL,
                `v1PesiPerAlimentoJson` TEXT,
                `v1NutrizionePerAlimentoJson` TEXT,
                `v2RecordId` INTEGER,
                `groundTruthPesiJson` TEXT,
                `groundTruthLivello` TEXT
            )
            """.trimIndent()
        )
    }
}

/**
 * Aggiunge la categoria alimentare alla chiave del bias (§8, §14): pasta e riso condividevano la
 * stessa strategia `DISTESA_SUL_PIATTO`, quindi un fattore tarato solo sulla pasta finiva applicato
 * anche al riso. La tabella viene ricreata da zero invece di migrata riga per riga: i fattori già
 * salvati mescolavano categorie diverse (dato mancante prima d'ora), quindi non sono recuperabili
 * correttamente — vengono ricalcolati da [com.ugocalc.app.data.repository.BiasCorrectionRepository.recalculateAll]
 * al prossimo avvio in foreground, dalle stesse osservazioni con ground truth già in `v2_analysis_records`,
 * questa volta segmentate anche per categoria.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS bias_corrections")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bias_corrections` (
                `sessionMode` TEXT NOT NULL,
                `strategy` TEXT NOT NULL,
                `aiModelId` TEXT NOT NULL,
                `foodCategory` TEXT NOT NULL,
                `factor` REAL NOT NULL,
                `sampleCount` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`sessionMode`, `strategy`, `aiModelId`, `foodCategory`)
            )
            """.trimIndent()
        )
    }
}

/** §13 punto 4: calibrazione persistente di stoviglie e riferimenti corporei (vedi ScaleCalibrationEntity). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `scale_calibrations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `valueCm` REAL NOT NULL,
                `capacityMl` REAL
            )
            """.trimIndent()
        )
    }
}

/** §13 punto 6: ricette per la ripartizione di alimenti composti (vedi RecipeEntity). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `percentage` REAL NOT NULL,
                FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId ON recipe_ingredients(recipeId)")
    }
}

/** Migrazione 14 -> 15: aggiunge `sessionMode` a ricette e calibrazioni per la separazione Casa/Ristorante. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scale_calibrations ADD COLUMN sessionMode TEXT NOT NULL DEFAULT 'CASA'")
        db.execSQL("ALTER TABLE recipes ADD COLUMN sessionMode TEXT NOT NULL DEFAULT 'CASA'")
    }
}

/** Shadow mode: aggiunge `failureReason` a `v2_analysis_records` per registrare i tentativi V2 non completati (NessunaFotoValida, FallbackAV1, SchemaNonSupportato, eccezioni). */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE v2_analysis_records ADD COLUMN failureReason TEXT")
    }
}

/** Inference sync: aggiunge `clientId` a `v2_analysis_records` per idempotenza cross-device. UUID generato al momento dell'insert; i record precedenti ricevono un UUID derivato dall'id locale. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE v2_analysis_records ADD COLUMN clientId TEXT")
        db.execSQL("UPDATE v2_analysis_records SET clientId = 'local-' || id WHERE clientId IS NULL")
    }
}

/**
 * Unifica i bias correction tra modalità CASA e RISTORANTE (§8): l'errore sistematico dell'AI
 * dipende dalla morfologia dell'alimento, non dal contesto di consumo — tenerli separati dimezzava
 * i dati per segmento. La tabella viene ricreata senza `sessionMode` nella chiave; i fattori si
 * ricalcolano automaticamente da `v2_analysis_records` (con dati da entrambe le modalità) al
 * prossimo `recalculateAll` in foreground, come già avvenuto per [MIGRATION_7_8].
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS bias_corrections")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bias_corrections` (
                `strategy` TEXT NOT NULL,
                `aiModelId` TEXT NOT NULL,
                `foodCategory` TEXT NOT NULL,
                `factor` REAL NOT NULL,
                `sampleCount` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`strategy`, `aiModelId`, `foodCategory`)
            )
            """.trimIndent()
        )
    }
}

/** Separazione cronologia Casa/Ristorante: aggiunge `sessionMode` con default 'CASA' per i record esistenti. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE analysis_history ADD COLUMN sessionMode TEXT NOT NULL DEFAULT 'CASA'")
    }
}

/** Tracciamento dei costi per API e modelli (M2-M7): nuova tabella `cost_transactions`. */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cost_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transactionAtEpochMillis` INTEGER NOT NULL,
                `stage` TEXT NOT NULL,
                `modelId` TEXT NOT NULL,
                `costUsd` REAL NOT NULL,
                `description` TEXT NOT NULL,
                `sessionId` TEXT
            )
            """.trimIndent()
        )
    }
}

/** CloudSync (Prompt 5): coda offline-first degli eventi da trasmettere al backend AWS. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cloud_sync_queue` (
                `eventId` TEXT PRIMARY KEY NOT NULL,
                `eventType` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL DEFAULT 0,
                `lastAttemptEpochMillis` INTEGER,
                `syncedAtEpochMillis` INTEGER
            )
            """.trimIndent()
        )
    }
}

/** Risoluzione crash schema mismatch (§ bug fix): forza l'aggiornamento del numero di versione per riallineare gli identity hash. */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

/** Risoluzione crash schema mismatch (§ bug fix): forza l'aggiornamento del numero di versione per riallineare gli identity hash. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {}
}

/** Risoluzione crash schema mismatch (§ bug fix): aggiunge i campi mancanti su scale_calibrations per allineare lo schema. */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scale_calibrations ADD COLUMN plateShapeType TEXT")
        db.execSQL("ALTER TABLE scale_calibrations ADD COLUMN plateBottomDiameterCm REAL")
        db.execSQL("ALTER TABLE scale_calibrations ADD COLUMN clientId TEXT")
    }
}

/** v4.2.0: conteggio proteine, Indice Glicemico e Carico Glicemico post-prandiale. */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE normalized_food_entries ADD COLUMN proteinePer100g REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE normalized_food_entries ADD COLUMN indiceGlicemico INTEGER")
        db.execSQL("ALTER TABLE analysis_history ADD COLUMN totalProteineG REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE analysis_history ADD COLUMN glycemicLoad REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE favorite_shortcuts ADD COLUMN comboProteinePer100g REAL")
        db.execSQL("ALTER TABLE favorite_shortcuts ADD COLUMN comboIndiceGlicemico INTEGER")
    }
}
