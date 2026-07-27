package com.ugocalc.app.data.parsing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.ugocalc.app.domain.model.DiscardedRow
import com.ugocalc.app.domain.model.NormalizationResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OfficialFileType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class DocumentExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Estrae il contenuto testuale/tabellare grezzo dai file caricati dall'utente (§4: "L'app estrae
 * anzitutto il contenuto testuale/tabellare grezzo dal file caricato"), prima di passarlo al
 * Passaggio 2 (Normalizzazione). Per i fogli Excel preserva la struttura a colonne usando un
 * separatore TAB tra celle, così il modello AI può comunque distinguere le colonne anche se le
 * intestazioni sono in ordine non standard (vedi documento §4 "Nota sul formato").
 */
@Singleton
class DocumentTextExtractor @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun extract(uri: Uri, fileType: OfficialFileType): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (fileType) {
                    OfficialFileType.PDF -> extractPdf(input)
                    OfficialFileType.DOC -> extractDoc(input)
                    OfficialFileType.DOCX -> extractDocx(input)
                    OfficialFileType.XLSX -> extractXlsx(input)
                    OfficialFileType.JPG, OfficialFileType.PNG -> extractImageWithOcr(input)
                    // Casi speciali (§Pezzo E / § richiesta utente), mai passati da qui: non
                    // hanno un file fisico da cui estrarre testo via DocumentTextExtractor.
                    OfficialFileType.ONLINE_RESEARCH,
                    OfficialFileType.MANUAL_ENTRY -> throw DocumentExtractionException("Questa voce non ha un file sorgente da estrarre")
                }
            } ?: throw DocumentExtractionException("Impossibile aprire il file selezionato")
        } catch (e: DocumentExtractionException) {
            throw e
        } catch (e: IOException) {
            throw DocumentExtractionException("Errore di lettura del file", e)
        } catch (e: Exception) {
            throw DocumentExtractionException("Formato file non interpretabile", e)
        }
    }

    private fun extractPdf(input: java.io.InputStream): String {
        PDDocument.load(input).use { document ->
            val text = PDFTextStripper().getText(document)
            if (text.isBlank()) {
                throw DocumentExtractionException("Il PDF non contiene testo estraibile. È un PDF scansionato? Occorre convertirlo con OCR. Riprova con l'app — verrà processato automaticamente.")
            }
            return text
        }
    }

    private fun extractDoc(input: java.io.InputStream): String {
        HWPFDocument(input).use { doc ->
            return WordExtractor(doc).text
        }
    }

    private fun extractDocx(input: java.io.InputStream): String {
        XWPFDocument(input).use { doc ->
            val extractor = XWPFWordExtractor(doc)
            val paragraphText = extractor.text
            val tablesText = doc.tables.joinToString("\n\n") { table ->
                table.rows.joinToString("\n") { row ->
                    row.tableCells.joinToString("\t") { it.text }
                }
            }
            return listOf(paragraphText, tablesText).filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }

    private fun extractXlsx(input: java.io.InputStream): String {
        XSSFWorkbook(input).use { workbook ->
            return buildString {
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    appendLine("# Foglio: ${sheet.sheetName}")
                    for (row in sheet) {
                        val cells = row.map { cell -> cell.toString() }
                        appendLine(cells.joinToString("\t"))
                    }
                    appendLine()
                }
            }
        }
    }

    /**
     * Import "già pronto" (§ richiesta utente: niente AI, il file è già nel formato giusto): un
     * Excel con una riga di intestazione contenente colonne per nome/CHO/grassi viene letto
     * direttamente, saltando del tutto il Passaggio 2 — utile per tabelle che l'utente prepara o
     * converte da sé, aggirando i limiti di quota/disponibilità dei provider AI sulle tabelle
     * molto grandi.
     */
    suspend fun extractPreformattedTable(uri: Uri): NormalizationResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> parsePreformattedXlsx(input) }
                ?: throw DocumentExtractionException("Impossibile aprire il file selezionato")
        } catch (e: DocumentExtractionException) {
            throw e
        } catch (e: IOException) {
            throw DocumentExtractionException("Errore di lettura del file", e)
        } catch (e: Exception) {
            throw DocumentExtractionException("File non interpretabile come Excel valido", e)
        }
    }

    private fun parsePreformattedXlsx(input: java.io.InputStream): NormalizationResult {
        XSSFWorkbook(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(sheet.firstRowNum)
                ?: throw DocumentExtractionException("Il foglio è vuoto")

            val nameCol = findColumnIndex(headerRow) { it.contains("nome") || it.contains("aliment") }
            val choCol = findColumnIndex(headerRow) { it.contains("cho") || it.contains("carboidrat") }
            val fatCol = findColumnIndex(headerRow) { it.contains("grass") }
            if (nameCol < 0 || choCol < 0 || fatCol < 0) {
                throw DocumentExtractionException(
                    "Intestazioni non riconosciute nella prima riga: servono colonne per nome, CHO e grassi (es. \"nome\", \"cho_per_100g\", \"grassi_per_100g\")"
                )
            }

            val entries = mutableListOf<NormalizedFoodEntry>()
            val discarded = mutableListOf<DiscardedRow>()
            for (rowIndex in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val nome = cellText(row.getCell(nameCol)).trim()
                if (nome.isBlank()) continue
                val cho = cellNumber(row.getCell(choCol))
                val grassi = cellNumber(row.getCell(fatCol))
                if (cho == null || grassi == null) {
                    discarded += DiscardedRow(nome, "Valori CHO/grassi mancanti o non numerici")
                } else {
                    entries += NormalizedFoodEntry(nome, cho, grassi)
                }
            }
            return NormalizationResult(entries, discarded, "")
        }
    }

    private fun findColumnIndex(headerRow: Row, matches: (String) -> Boolean): Int {
        for (i in 0 until headerRow.lastCellNum.toInt()) {
            if (matches(cellText(headerRow.getCell(i)).lowercase())) return i
        }
        return -1
    }

    private fun cellText(cell: Cell?): String = when (cell?.cellType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> cell.numericCellValue.toString()
        else -> cell?.toString().orEmpty()
    }

    private fun cellNumber(cell: Cell?): Double? = when (cell?.cellType) {
        CellType.NUMERIC -> cell.numericCellValue
        CellType.STRING -> cell.stringCellValue.trim().replace(",", ".").toDoubleOrNull()
        else -> null
    }

    suspend fun extractWithOcr(uri: Uri, fileType: OfficialFileType): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (fileType) {
                    OfficialFileType.JPG, OfficialFileType.PNG -> extractImageWithOcr(input)
                    else -> throw DocumentExtractionException("OCR non supportato per questo tipo di file")
                }
            } ?: throw DocumentExtractionException("Impossibile aprire il file selezionato")
        } catch (e: DocumentExtractionException) {
            throw e
        } catch (e: IOException) {
            throw DocumentExtractionException("Errore di lettura del file", e)
        } catch (e: Exception) {
            throw DocumentExtractionException("Errore durante l'OCR: ${e.message}", e)
        }
    }

    private suspend fun extractImageWithOcr(input: java.io.InputStream): String = withContext(Dispatchers.Default) {
        try {
            val bitmap = BitmapFactory.decodeStream(input) ?: throw DocumentExtractionException("Impossibile decodificare l'immagine")
            recognizeTextFromBitmap(bitmap)
        } catch (e: Exception) {
            throw DocumentExtractionException("Errore durante l'OCR dell'immagine: ${e.message}", e)
        }
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap)
        try {
            val visionText = recognizer.process(image).await()
            if (visionText.text.isBlank()) {
                throw DocumentExtractionException("OCR non ha estratto alcun testo dall'immagine")
            }
            visionText.text
        } finally {
            recognizer.close()
        }
    }

    companion object {
        /**
         * Dimensione massima di testo per singola chiamata AI (§4 "Nota implementativa": può essere
         * necessario il chunking per estrazioni disordinate o voluminose). Su una tabella di
         * centinaia di pagine, un valore troppo piccolo moltiplica il numero di chiamate AI
         * sequenziali necessarie — e ogni chiamata in più è un'occasione in più per finire in un
         * limite di quota (es. 429 su GitHub Models) o in un sovraccarico temporaneo del provider.
         * Ancora ben al di sotto del contesto di tutti e 3 i provider (centinaia di migliaia di
         * token), ma il doppio del valore precedente per dimezzare il numero di chiamate su
         * documenti grandi. Se un chunk troppo grande producesse JSON troncato in output, andrebbe
         * abbassato di nuovo verso il valore originale.
         */
        const val MAX_CHARS_PER_CHUNK = 24_000

        fun chunkText(text: String, maxChars: Int = MAX_CHARS_PER_CHUNK): List<String> {
            if (text.length <= maxChars) return listOf(text)
            val lines = text.lines()
            val chunks = mutableListOf<String>()
            val current = StringBuilder()
            for (line in lines) {
                if (current.length + line.length + 1 > maxChars && current.isNotEmpty()) {
                    chunks += current.toString()
                    current.clear()
                }
                current.appendLine(line)
            }
            if (current.isNotEmpty()) chunks += current.toString()
            return chunks
        }

        /**
         * Punto di ripresa per la normalizzazione a chunk (usato da TableRepository.runNormalization):
         * se il numero di chunk già completati è maggiore o uguale al totale ricalcolato (es. perché
         * MAX_CHARS_PER_CHUNK è cambiato tra un tentativo e l'altro, o il file è stato sostituito),
         * riparte da zero invece di lasciare un ciclo che non esegue nulla (bug corretto in 0.12.3).
         */
        fun resolveStartChunkIndex(storedChunkCount: Int, totalChunks: Int): Int =
            if (storedChunkCount >= totalChunks) 0 else storedChunkCount
    }
}
