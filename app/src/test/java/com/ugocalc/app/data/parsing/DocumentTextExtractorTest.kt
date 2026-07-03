package com.ugocalc.app.data.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextExtractorTest {

    @Test
    fun `short text stays in a single chunk`() {
        val text = "riga1\nriga2\nriga3"
        val chunks = DocumentTextExtractor.chunkText(text, maxChars = 1000)
        assertEquals(1, chunks.size)
    }

    @Test
    fun `text longer than maxChars is split into multiple chunks within a safe margin`() {
        val lines = (1..500).map { "alimento $it;10;5" }
        val text = lines.joinToString("\n")
        val chunks = DocumentTextExtractor.chunkText(text, maxChars = 200)

        assertTrue("expected more than one chunk for ${text.length} chars", chunks.size > 1)
        chunks.forEach { chunk -> assertTrue("chunk too long: ${chunk.length}", chunk.length <= 200 + 50) }
    }

    @Test
    fun `chunking preserves every line without loss or duplication`() {
        val lines = (1..100).map { "riga numero $it" }
        val text = lines.joinToString("\n")
        val chunks = DocumentTextExtractor.chunkText(text, maxChars = 150)

        val reassembledLines = chunks.joinToString("") { it }.lines().filter { it.isNotBlank() }
        assertEquals(lines, reassembledLines)
    }

    @Test
    fun `resolveStartChunkIndex resumes from stored progress when still in range`() {
        assertEquals(3, DocumentTextExtractor.resolveStartChunkIndex(storedChunkCount = 3, totalChunks = 10))
    }

    @Test
    fun `resolveStartChunkIndex restarts from zero when stored progress is out of range`() {
        // Caso reale che ha causato un bug (0.12.3): MAX_CHARS_PER_CHUNK raddoppiato tra un
        // tentativo e l'altro, il totale di chunk ricalcolato scende sotto il progresso salvato.
        assertEquals(0, DocumentTextExtractor.resolveStartChunkIndex(storedChunkCount = 15, totalChunks = 8))
    }

    @Test
    fun `resolveStartChunkIndex restarts from zero when stored progress equals the new total`() {
        assertEquals(0, DocumentTextExtractor.resolveStartChunkIndex(storedChunkCount = 8, totalChunks = 8))
    }
}
