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
