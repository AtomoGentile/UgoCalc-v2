package com.ugocalc.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ugocalc.app.domain.model.OfficialFileType

@Entity(tableName = "official_tables")
data class OfficialTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val fileType: OfficialFileType,
    val storedFilePath: String,
    val uploadedAtEpochMillis: Long,
    val priorityOrder: Int,
    val rawExtractedText: String,
    val normalizationStatus: NormalizationStatus,
    val noteConversione: String = "",
    val discardedRowsJson: String = "[]",
    val normalizationErrorMessage: String? = null,
    /**
     * Quanti chunk di testo (vedi `DocumentTextExtractor.chunkText`) sono già stati normalizzati e
     * salvati in `normalized_food_entries`. Permette di riprendere un import interrotto (es. rate
     * limit del provider AI su una tabella molto grande) dal punto in cui si era fermato, invece di
     * rifare da capo tutte le chiamate già andate a buon fine.
     */
    val normalizedChunkCount: Int = 0,
)
