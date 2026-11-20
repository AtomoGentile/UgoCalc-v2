package com.ugocalc.app.data.repository

import android.content.Context
import android.net.Uri
import com.ugocalc.app.data.local.data.StarterFoodDatabase
import com.ugocalc.app.data.local.db.dao.NormalizedFoodEntryDao
import com.ugocalc.app.data.local.db.dao.OfficialTableDao
import com.ugocalc.app.data.local.db.dao.SearchFrequencyDao
import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.local.db.entity.NormalizedFoodEntryEntity
import com.ugocalc.app.data.local.db.entity.OfficialTableEntity
import com.ugocalc.app.data.local.db.entity.SearchFrequencyEntity
import com.ugocalc.app.data.export.XlsxExporter
import com.ugocalc.app.data.parsing.DocumentExtractionException
import com.ugocalc.app.data.parsing.DocumentTextExtractor
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.DiscardedRow
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OfficialFileType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestisce le tabelle ufficiali (§2.2, §4): import file, estrazione testo, normalizzazione AI
 * (Passaggio 2, eseguito una sola volta per file), editing manuale tipo Excel e priorità tra
 * tabelle multiple usata dal Passaggio 3 (§5).
 */
@Singleton
class TableRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tableDao: OfficialTableDao,
    private val entryDao: NormalizedFoodEntryDao,
    private val searchFrequencyDao: SearchFrequencyDao,
    private val extractor: DocumentTextExtractor,
    private val aiOrchestrator: AiOrchestrator,
    private val xlsxExporter: XlsxExporter,
    private val json: Json,
) {

    /**
     * Avanzamento della normalizzazione per tabella (tableId -> "chunk in corso di totale"), solo
     * in memoria: un PDF con molte righe viene diviso in più chiamate AI sequenziali (vedi
     * `DocumentTextExtractor.chunkText`) e ciascuna può richiedere fino a un minuto o più con i
     * modelli gratuiti — senza questo indicatore la UI resta su "in corso..." per minuti senza dare
     * alcun segnale di reale avanzamento.
     */
    private val _normalizationProgress = MutableStateFlow<Map<Long, String>>(emptyMap())
    val normalizationProgress: StateFlow<Map<Long, String>> = _normalizationProgress.asStateFlow()

    private val starterSeedMutex = Mutex()

    /**
     * Popola una tantum la tabella nutrizionale italiana base (CREA) se l'utente non ha ancora
     * alcuna tabella nel database. Se l'utente possiede già tabelle importate, imposta comunque il
     * flag `hasSeededStarterTable = true` senza sovrascrivere né aggiungere tabelle indesiderate.
     */
    suspend fun ensureStarterTableSeeded(settingsRepository: SettingsRepository) = starterSeedMutex.withLock {
        val currentSettings = settingsRepository.settings.first()
        if (currentSettings.hasSeededStarterTable) return@withLock
        val existingTables = tableDao.getAllOrderedByPriority()
        if (existingTables.isEmpty()) {
            insertStarterTable(priorityOrder = 0)
        }
        settingsRepository.setHasSeededStarterTable(true)
    }

    /**
     * Ripristina o reintegra la tabella base italiana (CREA): se assente la crea e la popola con
     * tutte le voci; se già presente ma con voci mancanti, aggiunge quelle mancanti senza rimuovere
     * eventuali personalizzazioni dell'utente. Restituisce l'ID della tabella.
     */
    suspend fun restoreStarterTable(): Long = starterSeedMutex.withLock {
        val existing = tableDao.getAllOrderedByPriority()
            .firstOrNull { it.displayName == StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME }
        if (existing == null) {
            val nextPriority = (tableDao.maxPriorityOrder() ?: -1) + 1
            return@withLock insertStarterTable(priorityOrder = nextPriority)
        }
        val currentEntries = entryDao.getForTable(existing.id)
        val existingNames = currentEntries.map { it.nome.trim().lowercase() }.toSet()
        val missing = StarterFoodDatabase.ENTRIES.filter { it.nome.trim().lowercase() !in existingNames }
        if (missing.isNotEmpty()) {
            val startOrder = (entryDao.maxRowOrder(existing.id) ?: -1) + 1
            entryDao.insertAll(
                missing.mapIndexed { index, entry ->
                    NormalizedFoodEntryEntity(
                        tableId = existing.id,
                        nome = entry.nome,
                        choPer100g = entry.choPer100g,
                        grassiPer100g = entry.grassiPer100g,
                        rowOrder = startOrder + index,
                    )
                }
            )
        }
        existing.id
    }

    private suspend fun insertStarterTable(priorityOrder: Int): Long {
        val tableId = tableDao.insert(
            OfficialTableEntity(
                displayName = StarterFoodDatabase.STARTER_TABLE_DISPLAY_NAME,
                fileType = OfficialFileType.MANUAL_ENTRY,
                storedFilePath = "",
                uploadedAtEpochMillis = System.currentTimeMillis(),
                priorityOrder = priorityOrder,
                rawExtractedText = "",
                normalizationStatus = NormalizationStatus.DONE,
            )
        )
        entryDao.insertAll(
            StarterFoodDatabase.ENTRIES.mapIndexed { index, entry ->
                NormalizedFoodEntryEntity(
                    tableId = tableId,
                    nome = entry.nome,
                    choPer100g = entry.choPer100g,
                    grassiPer100g = entry.grassiPer100g,
                    rowOrder = index,
                )
            }
        )
        return tableId
    }

    /**
     * Restituisce fino a [limit] voci sfogliabili dalle tabelle normalizzate, in ordine di priorità,
     * così la ricerca mostra subito alimenti consultabili anche a campo di ricerca vuoto.
     */
    suspend fun getBrowseEntries(limit: Int = 50): List<FoodSearchResult> {
        if (limit <= 0) return emptyList()
        val tables = tableDao.getAllOrderedByPriority()
            .filter { it.normalizationStatus == NormalizationStatus.DONE }
        if (tables.isEmpty()) return emptyList()
        val results = ArrayList<FoodSearchResult>(limit)
        for (table in tables) {
            if (results.size >= limit) break
            val entries = entryDao.getForTable(table.id)
            for (entry in entries) {
                if (results.size >= limit) break
                results += FoodSearchResult(
                    entryId = entry.id,
                    entry = mapEntryToDomain(entry),
                    tableDisplayName = table.displayName,
                )
            }
        }
        return results
    }

    fun observeTables(): Flow<List<OfficialTableEntity>> = tableDao.observeAll()

    fun observeTableCount(): Flow<Int> = tableDao.observeCount()

    fun observeEntriesForTable(tableId: Long): Flow<List<NormalizedFoodEntryEntity>> =
        entryDao.observeForTable(tableId)

    suspend fun getAllTablesByPriority(): List<OfficialTableEntity> = tableDao.getAllOrderedByPriority()

    suspend fun getEntriesForTable(tableId: Long): List<NormalizedFoodEntryEntity> = entryDao.getForTable(tableId)

    /**
     * Ricerca manuale tra tutte le tabelle dell'utente (composizione manuale del pasto, correzione
     * di un abbinamento AI): restituisce anche il nome della tabella di provenienza, utile per
     * distinguere in UI voci omonime importate da fonti diverse.
     */
    suspend fun searchEntries(query: String): List<FoodSearchResult> {
        if (query.isBlank()) return emptyList()
        val matches = entryDao.searchByName(query.trim())
        if (matches.isEmpty()) return emptyList()
        val tableNames = tableDao.getAllOrderedByPriority().associateBy({ it.id }, { it.displayName })
        return matches.map { FoodSearchResult(it.id, mapEntryToDomain(it), tableNames[it.tableId] ?: "?") }
    }

    /**
     * "Più ricercate" (§ richiesta utente): alimenti scelti più spesso dalla ricerca manuale, con
     * le voci fissate sempre in cima. Chiamare [recordSearchSelection] ogni volta che l'utente
     * sceglie un risultato, così questa lista resta aggiornata.
     */
    suspend fun getMostSearched(limit: Int = 10): List<FoodSearchResult> {
        val frequencies = searchFrequencyDao.getMostSearched(limit)
        if (frequencies.isEmpty()) return emptyList()
        val tableNames = tableDao.getAllOrderedByPriority().associateBy({ it.id }, { it.displayName })
        return frequencies.mapNotNull { freq ->
            val entry = entryDao.getById(freq.entryId) ?: return@mapNotNull null
            FoodSearchResult(entry.id, mapEntryToDomain(entry), tableNames[entry.tableId] ?: "?", pinned = freq.pinned)
        }
    }

    suspend fun recordSearchSelection(entryId: Long) {
        val existing = searchFrequencyDao.getByEntryId(entryId)
        searchFrequencyDao.upsert(
            SearchFrequencyEntity(
                entryId = entryId,
                searchCount = (existing?.searchCount ?: 0) + 1,
                lastSearchedAtEpochMillis = System.currentTimeMillis(),
                pinned = existing?.pinned ?: false,
            )
        )
    }

    /** "Possibilità di fissare una voce" (§ richiesta utente) tra le più ricercate. */
    suspend fun toggleSearchPin(entryId: Long) {
        val existing = searchFrequencyDao.getByEntryId(entryId) ?: SearchFrequencyEntity(entryId = entryId)
        searchFrequencyDao.upsert(existing.copy(pinned = !existing.pinned))
    }

    /** "Cancella ultime ricerche" (§ richiesta utente): tiene le voci fissate con lo spillo. */
    suspend fun clearMostSearched() = searchFrequencyDao.clearUnpinned()

    fun decodeDiscardedRows(table: OfficialTableEntity): List<DiscardedRow> =
        try {
            json.decodeFromString<List<DiscardedRow>>(table.discardedRowsJson)
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Copia il file in storage interno (§2.2: "I file vengono salvati in un database dedicato
     * dell'app per prevenire la perdita di dati"), crea la riga in tabella con priorità più bassa
     * (in fondo alla lista) e avvia subito la normalizzazione (Passaggio 2).
     */
    suspend fun importAndNormalize(uris: List<Uri>, displayName: String, fileType: OfficialFileType, model: AiModel): Long {
        // Copia tutti i file; usa il primo come file "principale" (storedFilePath).
        val storedFiles = uris.map { copyToInternalStorage(it, fileType) }
        val nextPriority = (tableDao.maxPriorityOrder() ?: -1) + 1

        var entity = OfficialTableEntity(
            displayName = displayName,
            fileType = fileType,
            storedFilePath = storedFiles.first().absolutePath,
            uploadedAtEpochMillis = System.currentTimeMillis(),
            priorityOrder = nextPriority,
            rawExtractedText = "",
            normalizationStatus = NormalizationStatus.PENDING,
        )
        val tableId = tableDao.insert(entity)
        entity = entity.copy(id = tableId)

        runNormalization(entity, storedFiles.map { Uri.fromFile(it) }, model)
        return tableId
    }

    suspend fun importAndNormalize(uri: Uri, displayName: String, fileType: OfficialFileType, model: AiModel): Long =
        importAndNormalize(listOf(uri), displayName, fileType, model)

    /**
     * Import "già pronto" (§ richiesta utente: niente AI, il file è già nel formato giusto): un
     * Excel con colonne nome/CHO/grassi nella prima riga viene letto e inserito direttamente,
     * senza passare dal Passaggio 2 — evita del tutto i limiti di quota/disponibilità dei
     * provider AI, utile per tabelle molto grandi che l'utente prepara o converte da sé.
     */
    suspend fun importPreFormatted(uri: Uri, displayName: String): Long {
        val storedFile = copyToInternalStorage(uri, OfficialFileType.XLSX)
        val result = extractor.extractPreformattedTable(Uri.fromFile(storedFile))
        val nextPriority = (tableDao.maxPriorityOrder() ?: -1) + 1

        val entity = OfficialTableEntity(
            displayName = displayName,
            fileType = OfficialFileType.XLSX,
            storedFilePath = storedFile.absolutePath,
            uploadedAtEpochMillis = System.currentTimeMillis(),
            priorityOrder = nextPriority,
            rawExtractedText = "",
            normalizationStatus = NormalizationStatus.DONE,
            discardedRowsJson = json.encodeToString(result.righeScartate),
        )
        val tableId = tableDao.insert(entity)
        entryDao.insertAll(
            result.alimenti.mapIndexed { index, e ->
                NormalizedFoodEntryEntity(tableId = tableId, nome = e.nome, choPer100g = e.choPer100g, grassiPer100g = e.grassiPer100g, rowOrder = index)
            }
        )
        exportTableAsXlsx(tableId)
        return tableId
    }

    /** Permette di ri-eseguire il Passaggio 2 (es. dopo un fallimento, o cambiando modello AI). */
    suspend fun retryNormalization(tableId: Long, model: AiModel) {
        val table = tableDao.getById(tableId) ?: return
        runNormalization(table, listOf(Uri.fromFile(File(table.storedFilePath))), model)
    }

    /**
     * Normalizza a chunk (vedi `DocumentTextExtractor.chunkText`), salvando il risultato di ogni
     * chunk subito nel database invece che tutto insieme alla fine: su una tabella molto grande
     * (centinaia/migliaia di righe, quindi decine di chiamate AI sequenziali) un fallimento a metà
     * strada — es. rate limit del provider gratuito — non fa perdere il lavoro già fatto. Un retry
     * riprende da `normalizedChunkCount` invece di rifare da capo i chunk già andati a buon fine.
     */
    private suspend fun runNormalization(table: OfficialTableEntity, fileUris: List<Uri>, model: AiModel) {
        var currentTable = table.copy(normalizationStatus = NormalizationStatus.PROCESSING, normalizationErrorMessage = null)
        tableDao.update(currentTable)
        try {
            val useOcr = table.fileType in listOf(OfficialFileType.JPG, OfficialFileType.PNG)
            val extractedText = fileUris.map { uri ->
                if (useOcr) extractor.extractWithOcr(uri, table.fileType) else extractor.extract(uri, table.fileType)
            }.joinToString("\n\n")
            if (extractedText.isBlank()) {
                throw DocumentExtractionException("Il file non contiene testo leggibile. Se è un PDF scansionato, riprova con OCR.")
            }
            val chunks = DocumentTextExtractor.chunkText(extractedText)

            val startChunkIndex = DocumentTextExtractor.resolveStartChunkIndex(table.normalizedChunkCount, chunks.size)

            if (startChunkIndex == 0) {
                entryDao.deleteAllForTable(table.id)
            }
            val discardedSoFar = (if (startChunkIndex == 0) emptyList() else decodeDiscardedRows(table)).toMutableList()
            val notesSoFar = (if (startChunkIndex == 0 || table.noteConversione.isBlank()) emptyList() else listOf(table.noteConversione)).toMutableList()

            for (index in startChunkIndex until chunks.size) {
                if (chunks.size > 1) {
                    _normalizationProgress.update { it + (table.id to "${index + 1} di ${chunks.size}") }
                }
                val result = aiOrchestrator.runTableNormalization(model, chunks[index])

                if (result.alimenti.isEmpty() && result.righeScartate.isEmpty()) {
                    throw Exception("Il modello ha ritornato un risultato vuoto per il chunk ${index + 1}. Verifica che il file contenga dati tabulari validi.")
                }

                val nextRowOrder = (entryDao.maxRowOrder(table.id) ?: -1) + 1
                entryDao.insertAll(
                    result.alimenti.mapIndexed { i, e ->
                        NormalizedFoodEntryEntity(
                            tableId = table.id,
                            nome = e.nome,
                            choPer100g = e.choPer100g,
                            grassiPer100g = e.grassiPer100g,
                            rowOrder = nextRowOrder + i,
                        )
                    }
                )
                discardedSoFar += result.righeScartate
                if (result.noteConversione.isNotBlank()) notesSoFar += result.noteConversione

                // Persistito dopo OGNI chunk: se il prossimo fallisce, questo progresso non si perde.
                currentTable = currentTable.copy(
                    rawExtractedText = extractedText,
                    normalizedChunkCount = index + 1,
                    noteConversione = notesSoFar.joinToString("\n"),
                    discardedRowsJson = json.encodeToString(discardedSoFar),
                )
                tableDao.update(currentTable)
            }

            tableDao.update(currentTable.copy(normalizationStatus = NormalizationStatus.DONE, normalizationErrorMessage = null))
            exportTableAsXlsx(table.id)
        } catch (e: DocumentExtractionException) {
            failNormalization(table.id, currentTable, e.message)
        } catch (e: AiCallException) {
            failNormalization(table.id, currentTable, e.message)
        } catch (e: Exception) {
            failNormalization(table.id, currentTable, "Errore inatteso: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            _normalizationProgress.update { it - table.id }
        }
    }

    /** Rilegge lo stato più recente dalla tabella (già aggiornato chunk per chunk) prima di segnare l'errore, per non sovrascrivere il progresso salvato con il [table] iniziale, ormai superato. */
    private suspend fun failNormalization(tableId: Long, fallback: OfficialTableEntity, errorMessage: String?) {
        val latest = tableDao.getById(tableId) ?: fallback
        tableDao.update(latest.copy(normalizationStatus = NormalizationStatus.FAILED, normalizationErrorMessage = errorMessage))
    }

    suspend fun deleteTable(table: OfficialTableEntity) {
        File(table.storedFilePath).delete()
        tableDao.delete(table)
        reindexPriorities()
    }

    /** UI editor tipo Excel (requisito utente): riordino priorità tra tabelle multiple (§2.2). */
    suspend fun reorderTables(idsInNewPriorityOrder: List<Long>) {
        tableDao.reorder(idsInNewPriorityOrder)
    }

    private suspend fun reindexPriorities() {
        val ordered = tableDao.getAllOrderedByPriority()
        tableDao.reorder(ordered.map { it.id })
    }

    // --- Editing manuale della tabella normalizzata (§2.2 "Visualizzazione e modifica") ---

    suspend fun upsertEntry(entry: NormalizedFoodEntryEntity) {
        if (entry.id == 0L) entryDao.insert(entry) else entryDao.update(entry)
    }

    suspend fun deleteEntry(entry: NormalizedFoodEntryEntity) {
        entryDao.delete(entry)
    }

    /**
     * Sovrascrive tutte le righe normalizzate di una tabella con il contenuto corrente dell'editor
     * (requisito utente: pulsante "Salva" quando righe/dati vengono aggiunti/modificati/eliminati).
     * Più semplice ed esplicito di un diff riga-per-riga: l'editor tipo Excel tiene comunque l'intero
     * set di righe in memoria mentre l'utente modifica.
     */
    suspend fun replaceEntries(tableId: Long, entries: List<NormalizedFoodEntryEntity>) {
        entryDao.deleteAllForTable(tableId)
        entryDao.insertAll(entries.mapIndexed { index, e -> e.copy(id = 0, tableId = tableId, rowOrder = index) })
    }

    suspend fun addBlankRow(tableId: Long): Long {
        val nextOrder = (entryDao.maxRowOrder(tableId) ?: -1) + 1
        return entryDao.insert(
            NormalizedFoodEntryEntity(
                tableId = tableId,
                nome = "",
                choPer100g = 0.0,
                grassiPer100g = 0.0,
                rowOrder = nextOrder,
                isManuallyEdited = true,
            )
        )
    }

    // --- Alimenti trovati con la ricerca online (§Pezzo E) ---

    /**
     * Tabella sintetica separata dalle tabelle importate dall'utente (§ AskUserQuestion: "nuova
     * tabella 'Alimenti trovati online'"), con priorità più bassa così il Matching AI prova prima
     * le fonti caricate manualmente dall'utente. `storedFilePath` vuoto e `normalizationStatus =
     * DONE` perché non c'è nessun file da normalizzare: le righe vengono inserite direttamente.
     */
    suspend fun getOrCreateOnlineResearchTable(): OfficialTableEntity {
        return getOrCreateSpecialTable("Alimenti trovati online", OfficialFileType.ONLINE_RESEARCH)
    }

    /**
     * Tabella per gli alimenti inseriti manualmente dall'utente (§ richiesta utente): creata alla
     * prima necessità, permette di salvare voci con CHO/grassi personalizzati che non esistono
     * nelle tabelle ufficiali importate.
     */
    suspend fun getOrCreateManualEntriesTable(): OfficialTableEntity {
        return getOrCreateSpecialTable("Alimenti inseriti manualmente", OfficialFileType.MANUAL_ENTRY)
    }

    private suspend fun getOrCreateSpecialTable(displayName: String, fileType: OfficialFileType): OfficialTableEntity {
        val existing = tableDao.getAllOrderedByPriority()
            .firstOrNull { it.fileType == fileType && it.displayName == displayName }
        if (existing != null) return existing
        val nextPriority = (tableDao.maxPriorityOrder() ?: -1) + 1
        val entity = OfficialTableEntity(
            displayName = displayName,
            fileType = fileType,
            storedFilePath = "",
            uploadedAtEpochMillis = System.currentTimeMillis(),
            priorityOrder = nextPriority,
            rawExtractedText = "",
            normalizationStatus = NormalizationStatus.DONE,
        )
        val id = tableDao.insert(entity)
        return entity.copy(id = id)
    }

    /** "Aggiungi al database" (§Pezzo E): persiste un alimento trovato con la ricerca online. */
    suspend fun addOnlineResearchEntry(nome: String, choPer100g: Double, grassiPer100g: Double): NormalizedFoodEntry {
        val table = getOrCreateOnlineResearchTable()
        return addEntryToTable(table.id, nome, choPer100g, grassiPer100g).second
    }

    /** Crea una nuova voce manuale nel database (§ richiesta utente). */
    suspend fun addManualEntry(nome: String, choPer100g: Double, grassiPer100g: Double): NormalizedFoodEntry {
        return addManualEntryWithResult(nome, choPer100g, grassiPer100g).entry
    }

    /**
     * Crea una nuova voce manuale nel database e restituisce un [FoodSearchResult] contenente
     * l'`entryId` reale generato da Room (utile nei picker in modalità selezione multipla).
     */
    suspend fun addManualEntryWithResult(nome: String, choPer100g: Double, grassiPer100g: Double): FoodSearchResult {
        val table = getOrCreateManualEntriesTable()
        val (entryId, entry) = addEntryToTable(table.id, nome.trim(), choPer100g, grassiPer100g)
        return FoodSearchResult(
            entryId = entryId,
            entry = entry,
            tableDisplayName = table.displayName,
        )
    }

    private suspend fun addEntryToTable(tableId: Long, nome: String, choPer100g: Double, grassiPer100g: Double): Pair<Long, NormalizedFoodEntry> {
        val nextOrder = (entryDao.maxRowOrder(tableId) ?: -1) + 1
        val entity = NormalizedFoodEntryEntity(
            tableId = tableId,
            nome = nome,
            choPer100g = choPer100g,
            grassiPer100g = grassiPer100g,
            rowOrder = nextOrder,
            isManuallyEdited = true,
        )
        val id = entryDao.insert(entity)
        return id to mapEntryToDomain(entity)
    }

    private fun copyToInternalStorage(uri: Uri, fileType: OfficialFileType): File {
        val dir = File(context.filesDir, "official_tables").apply { mkdirs() }
        val extension = fileType.name.lowercase()
        val destination = File(dir, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw DocumentExtractionException("Impossibile leggere il file selezionato")
        return destination
    }

    /** Esporta una tabella normalizzata come file XLSX scaricabile. */
    suspend fun exportTableAsXlsx(tableId: Long): File {
        val entries = entryDao.getForTable(tableId)
        val normalized = entries.map { mapEntryToDomain(it) }
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "tabella_$tableId.xlsx")
        return xlsxExporter.exportToFile(normalized, exportFile)
    }
}

fun mapEntryToDomain(entity: NormalizedFoodEntryEntity): NormalizedFoodEntry =
    NormalizedFoodEntry(entity.nome, entity.choPer100g, entity.grassiPer100g)

/** Un risultato della ricerca manuale tra tabelle (Pezzo A): l'alimento più la tabella da cui viene. */
data class FoodSearchResult(
    val entryId: Long,
    val entry: NormalizedFoodEntry,
    val tableDisplayName: String,
    val pinned: Boolean = false,
)
