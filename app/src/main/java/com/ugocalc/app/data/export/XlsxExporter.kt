package com.ugocalc.app.data.export

import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genera file XLSX con i dati normalizzati (nome, CHO/100g, grassi/100g).
 * Usato dopo la normalizzazione AI per esportare/salvare i risultati.
 */
@Singleton
class XlsxExporter @Inject constructor() {

    suspend fun exportToFile(entries: List<NormalizedFoodEntry>, outputFile: File): File = withContext(Dispatchers.IO) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Alimenti")

            // Intestazione
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Nome alimento")
            headerRow.createCell(1).setCellValue("CHO per 100g")
            headerRow.createCell(2).setCellValue("Grassi per 100g")

            // Dati
            entries.forEachIndexed { index, entry ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(entry.nome)
                row.createCell(1).setCellValue(entry.choPer100g)
                row.createCell(2).setCellValue(entry.grassiPer100g)
            }

            // Larghezze fisse (autoSizeColumn non funziona su Android - dipende da java.awt)
            sheet.setColumnWidth(0, 7000)  // Nome: ~50 caratteri
            sheet.setColumnWidth(1, 4000)  // CHO: numerico
            sheet.setColumnWidth(2, 4000)  // Grassi: numerico

            outputFile.outputStream().use { workbook.write(it) }
        }
        return@withContext outputFile
    }
}
