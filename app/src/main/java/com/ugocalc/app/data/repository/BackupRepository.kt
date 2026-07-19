package com.ugocalc.app.data.repository

import com.ugocalc.app.data.backup.BackupSummary
import com.ugocalc.app.data.backup.BiasCorrectionDto
import com.ugocalc.app.data.backup.FavoriteDto
import com.ugocalc.app.data.backup.FoodEntryDto
import com.ugocalc.app.data.backup.HistoryEntryDto
import com.ugocalc.app.data.backup.ImportValidation
import com.ugocalc.app.data.backup.ScaleCalibrationDto
import com.ugocalc.app.data.backup.TableDto
import com.ugocalc.app.data.backup.UgoCalcBackupArchive
import com.ugocalc.app.data.backup.V2AnalysisRecordDto
import com.ugocalc.app.data.local.db.dao.AnalysisHistoryDao
import com.ugocalc.app.data.local.db.dao.BiasCorrectionDao
import com.ugocalc.app.data.local.db.dao.FavoriteShortcutDao
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.ScaleCalibrationDao
import com.ugocalc.app.data.local.db.dao.V2AnalysisRecordDao
import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.data.local.db.entity.BiasCorrectionEntity
import com.ugocalc.app.data.local.db.entity.FavoriteShortcutEntity
import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.domain.model.OfficialFileType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import/export dei dati utente come archivio JSON unico e versionato (piano V2 §17): storico,
 * tabelle nutrizionali, fattori di correzione del bias (§8) e preferiti. Mai le chiavi API
 * (`SecureApiKeyStore`), per scelta esplicita di sicurezza — un backup che finisse nelle mani
 * sbagliate non deve poter esporre credenziali con costo reale.
 *
 * **Sostituzione totale, mai merge silenzioso** (§17): un import cancella i dati esistenti nelle
 * quattro categorie e li rimpiazza con quelli dell'archivio. Un merge automatico creerebbe stati
 * ambigui (es. due fattori di correzione per lo stesso segmento modalità/strategia/modello, §8,
 * con quale priorità?) difficili da diagnosticare a posteriori. Per questo l'import è in due fasi:
 * [previewImport] (sola lettura, valida lo schema e conta cosa cambierebbe) e [applyImport]
 * (esegue davvero la sostituzione) — la UI mostra sempre l'anteprima e chiede conferma esplicita
 * prima di invocare la seconda.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val historyDao: AnalysisHistoryDao,
    private val officialTableDao: OfficialTableDao,
    private val normalizedFoodEntryDao: NormalizedFoodEntryDao,
    private val biasCorrectionDao: BiasCorrectionDao,
    private val favoriteShortcutDao: FavoriteShortcutDao,
    private val v2AnalysisRecordDao: V2AnalysisRecordDao,
    private val scaleCalibrationDao: ScaleCalibrationDao,
    private val json: Json,
) {
    suspend fun exportAll(): String {
        val history = historyDao.getAllOnce()
        val historyIndexById = history.indices.associateBy({ history[it].id }) { it }

        val v2Records = v2AnalysisRecordDao.getAll().map {
            V2AnalysisRecordDto(
                analyzedAtEpochMillis = it.analyzedAtEpochMillis,
                sessionMode = it.sessionMode,
                aiModelId = it.aiModelId,
                schemaVersion = it.schemaVersion,
                sceneGraphJson = it.sceneGraphJson,
                weightEstimatesJson = it.weightEstimatesJson,
                verificationReportJson = it.verificationReportJson,
                nutritionReportJson = it.nutritionReportJson,
                arbitratoMotivoMancato = it.arbitratoMotivoMancato,
                groundTruthPesiJson = it.groundTruthPesiJson,
                groundTruthLivello = it.groundTruthLivello,
                sessionId = it.sessionId,
            )
        }

        val scaleCalibrations = scaleCalibrationDao.getAll().first().map {
            ScaleCalibrationDto(
                type = it.type,
                name = it.name,
                valueCm = it.valueCm,
                capacityMl = it.capacityMl,
            )
        }

        val tables = officialTableDao.getAllOrderedByPriority().map { table ->
            val entries = normalizedFoodEntryDao.getForTable(table.id).map {
                FoodEntryDto(
                    nome = it.nome,
                    choPer100g = it.choPer100g,
                    grassiPer100g = it.grassiPer100g,
                    rowOrder = it.rowOrder,
                    isManuallyEdited = it.isManuallyEdited,
                )
            }
            TableDto(
                displayName = table.displayName,
                fileType = table.fileType.name,
                uploadedAtEpochMillis = table.uploadedAtEpochMillis,
                priorityOrder = table.priorityOrder,
                rawExtractedText = table.rawExtractedText,
                normalizationStatus = table.normalizationStatus.name,
                noteConversione = table.noteConversione,
                discardedRowsJson = table.discardedRowsJson,
                normalizationErrorMessage = table.normalizationErrorMessage,
                normalizedChunkCount = table.normalizedChunkCount,
                entries = entries,
            )
        }

        val biasCorrections = biasCorrectionDao.getAll().map {
            BiasCorrectionDto(
                sessionMode = it.sessionMode,
                strategy = it.strategy,
                aiModelId = it.aiModelId,
                foodCategory = it.foodCategory,
                factor = it.factor,
                sampleCount = it.sampleCount,
                updatedAtEpochMillis = it.updatedAtEpochMillis,
            )
        }

        val favorites = favoriteShortcutDao.getAllOnce().map {
            FavoriteDto(
                historyEntryIndex = it.historyEntryId?.let { id -> historyIndexById[id] },
                label = it.label,
                sortOrder = it.sortOrder,
                comboGrams = it.comboGrams,
                comboChoPer100g = it.comboChoPer100g,
                comboGrassiPer100g = it.comboGrassiPer100g,
            )
        }

        val archive = UgoCalcBackupArchive(
            exportedAtEpochMillis = System.currentTimeMillis(),
            history = history.map {
                HistoryEntryDto(
                    foodTitle = it.foodTitle,
                    analyzedAtEpochMillis = it.analyzedAtEpochMillis,
                    totalChoG = it.totalChoG,
                    totalGrassiG = it.totalGrassiG,
                    modelDisplayNamesJson = it.modelDisplayNamesJson,
                    detailsJson = it.detailsJson,
                )
            },
            v2Records = v2Records,
            scaleCalibrations = scaleCalibrations,
            tables = tables,
            biasCorrections = biasCorrections,
            favorites = favorites,
        )
        return json.encodeToString(archive)
    }

    /** Valida l'archivio e conta cosa cambierebbe, senza scrivere nulla (§17 "anteprima del contenuto con conferma esplicita"). */
    fun previewImport(content: String): ImportValidation {
        val archive = try {
            json.decodeFromString<UgoCalcBackupArchive>(content)
        } catch (e: SerializationException) {
            return ImportValidation.Invalid(e.message ?: "JSON non valido")
        } catch (e: IllegalArgumentException) {
            return ImportValidation.Invalid(e.message ?: "contenuto non valido")
        }

        if (archive.schemaVersion > UgoCalcBackupArchive.SCHEMA_VERSION) {
            return ImportValidation.SchemaTooNew(found = archive.schemaVersion, supported = UgoCalcBackupArchive.SCHEMA_VERSION)
        }
        // schemaVersion < SCHEMA_VERSION: nessuna versione precedente esiste ancora oggi (§17 è la
        // prima), quindi non c'è alcuna migrazione da applicare finché non ne nasce una seconda.

        return ImportValidation.Ok(
            BackupSummary(
                historyCount = archive.history.size,
                v2RecordsCount = archive.v2Records.size,
                scaleCalibrationsCount = archive.scaleCalibrations.size,
                tablesCount = archive.tables.size,
                biasCorrectionsCount = archive.biasCorrections.size,
                favoritesCount = archive.favorites.size,
            ),
        )
    }

    /**
     * Esegue la sostituzione totale: da chiamare solo dopo che l'utente ha confermato l'anteprima
     * di [previewImport]. Non rivalida lo schema una seconda volta: se il chiamante non ha
     * rispettato il contratto (import senza preview), lascia propagare l'eccezione di
     * deserializzazione piuttosto che applicare parzialmente un archivio non validato.
     */
    suspend fun applyImport(content: String) {
        val archive = json.decodeFromString<UgoCalcBackupArchive>(content)

        historyDao.clearAll()
        val newHistoryIds = archive.history.map { entry ->
            historyDao.insert(
                AnalysisHistoryEntity(
                    foodTitle = entry.foodTitle,
                    analyzedAtEpochMillis = entry.analyzedAtEpochMillis,
                    totalChoG = entry.totalChoG,
                    totalGrassiG = entry.totalGrassiG,
                    modelDisplayNamesJson = entry.modelDisplayNamesJson,
                    detailsJson = entry.detailsJson,
                ),
            )
        }

        v2AnalysisRecordDao.deleteAll()
        archive.v2Records.forEach {
            v2AnalysisRecordDao.insert(
                V2AnalysisRecordEntity(
                    analyzedAtEpochMillis = it.analyzedAtEpochMillis,
                    sessionMode = it.sessionMode,
                    aiModelId = it.aiModelId,
                    schemaVersion = it.schemaVersion,
                    sceneGraphJson = it.sceneGraphJson,
                    weightEstimatesJson = it.weightEstimatesJson,
                    verificationReportJson = it.verificationReportJson,
                    nutritionReportJson = it.nutritionReportJson,
                    arbitratoMotivoMancato = it.arbitratoMotivoMancato,
                    groundTruthPesiJson = it.groundTruthPesiJson,
                    groundTruthLivello = it.groundTruthLivello,
                    sessionId = it.sessionId,
                ),
            )
        }

        scaleCalibrationDao.deleteAll()
        archive.scaleCalibrations.forEach {
            scaleCalibrationDao.insert(
                ScaleCalibrationEntity(
                    type = it.type,
                    name = it.name,
                    valueCm = it.valueCm,
                    capacityMl = it.capacityMl,
                ),
            )
        }

        officialTableDao.deleteAll() // le voci normalizzate seguono a cascata (ON DELETE CASCADE)
        archive.tables.forEach { tableDto ->
            val newTableId = officialTableDao.insert(
                OfficialTableEntity(
                    displayName = tableDto.displayName,
                    fileType = OfficialFileType.valueOf(tableDto.fileType),
                    storedFilePath = "",
                    uploadedAtEpochMillis = tableDto.uploadedAtEpochMillis,
                    priorityOrder = tableDto.priorityOrder,
                    rawExtractedText = tableDto.rawExtractedText,
                    normalizationStatus = NormalizationStatus.valueOf(tableDto.normalizationStatus),
                    noteConversione = tableDto.noteConversione,
                    discardedRowsJson = tableDto.discardedRowsJson,
                    normalizationErrorMessage = tableDto.normalizationErrorMessage,
                    normalizedChunkCount = tableDto.normalizedChunkCount,
                ),
            )
            normalizedFoodEntryDao.insertAll(
                tableDto.entries.map {
                    NormalizedFoodEntryEntity(
                        tableId = newTableId,
                        nome = it.nome,
                        choPer100g = it.choPer100g,
                        grassiPer100g = it.grassiPer100g,
                        rowOrder = it.rowOrder,
                        isManuallyEdited = it.isManuallyEdited,
                    )
                },
            )
        }

        biasCorrectionDao.deleteAll()
        archive.biasCorrections.forEach {
            biasCorrectionDao.upsert(
                BiasCorrectionEntity(
                    sessionMode = it.sessionMode,
                    strategy = it.strategy,
                    aiModelId = it.aiModelId,
                    foodCategory = it.foodCategory,
                    factor = it.factor,
                    sampleCount = it.sampleCount,
                    updatedAtEpochMillis = it.updatedAtEpochMillis,
                ),
            )
        }

        favoriteShortcutDao.deleteAll()
        archive.favorites.forEach { favoriteDto ->
            favoriteShortcutDao.insert(
                FavoriteShortcutEntity(
                    historyEntryId = favoriteDto.historyEntryIndex?.let { newHistoryIds.getOrNull(it) },
                    label = favoriteDto.label,
                    sortOrder = favoriteDto.sortOrder,
                    comboGrams = favoriteDto.comboGrams,
                    comboChoPer100g = favoriteDto.comboChoPer100g,
                    comboGrassiPer100g = favoriteDto.comboGrassiPer100g,
                ),
            )
        }
    }
}
