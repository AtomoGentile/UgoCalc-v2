package com.ugocalc.app.data.remote.ai.dto

import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.OnlineResearchReliability
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia di regressione per lo schema JSON snake_case dei prompt AI (§SerialName): un errore qui
 * ha causato in passato "JSON malformato" su tutti e 3 i passaggi principali, individuato solo
 * quando un provider reale ha iniziato a rispondere per la prima volta.
 */
class AiDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `VisionAnalysisDto decodes the snake_case schema from the vision prompt`() {
        val raw = """
            {
              "alimenti": [
                {"nome": "pasta", "peso_stimato_g": 150.0, "commento": "porzione media", "confidenza": "alto"}
              ],
              "confidenza_globale": "medio",
              "fattori_incertezza": ["scarsa illuminazione"],
              "avvertenze": ""
            }
        """.trimIndent()

        val domain = json.decodeFromString<VisionAnalysisDto>(raw).toDomain()

        assertEquals(1, domain.alimenti.size)
        assertEquals("pasta", domain.alimenti[0].nome)
        assertEquals(150.0, domain.alimenti[0].pesoStimatoG, 0.001)
        assertEquals(Confidence.ALTO, domain.alimenti[0].confidenza)
        assertEquals(Confidence.MEDIO, domain.confidenzaGlobale)
        assertEquals(listOf("scarsa illuminazione"), domain.fattoriIncertezza)
    }

    @Test
    fun `NormalizationDto decodes cho_per_100g, grassi_per_100g, optional protein and GI`() {
        val raw = """
            {
              "alimenti": [
                {"nome": "Pasta di semola, secca, cruda", "cho_per_100g": 75.0, "grassi_per_100g": 1.5},
                {"nome": "Petto di pollo", "cho_per_100g": 0.0, "grassi_per_100g": 1.5, "proteine_per_100g": 30.0, "indice_glicemico": 0},
                {"nome": "Alimento con proteine null", "cho_per_100g": 10.0, "grassi_per_100g": 2.0, "proteine_per_100g": null, "indice_glicemico": null}
              ],
              "righe_scartate": [{"contenuto_originale": "???", "motivo": "dati mancanti"}],
              "note_conversione": ""
            }
        """.trimIndent()

        val domain = json.decodeFromString<NormalizationDto>(raw).toDomain()

        assertEquals(75.0, domain.alimenti[0].choPer100g, 0.001)
        assertEquals(1.5, domain.alimenti[0].grassiPer100g, 0.001)
        assertEquals(0.0, domain.alimenti[0].proteinePer100g, 0.001)
        assertNull(domain.alimenti[0].indiceGlicemico)
        assertEquals(30.0, domain.alimenti[1].proteinePer100g, 0.001)
        assertEquals(0, domain.alimenti[1].indiceGlicemico)
        assertEquals(0.0, domain.alimenti[2].proteinePer100g, 0.001)
        assertEquals(1, domain.righeScartate.size)
    }

    @Test
    fun `MatchingDto decodes nessuna_corrispondenza correctly`() {
        val raw = """
            {"abbinamenti": [{"nome_riconosciuto": "boh", "voce_tabella_abbinata": "", "nessuna_corrispondenza": true, "confidenza_abbinamento": "basso"}]}
        """.trimIndent()

        val domain = json.decodeFromString<MatchingDto>(raw).toDomain()

        assertTrue(domain.abbinamenti[0].nessunaCorrispondenza)
        assertEquals(Confidence.BASSO, domain.abbinamenti[0].confidenzaAbbinamento)
    }

    @Test
    fun `OnlineFoodResearchDto decodes affidabilita and nullable macro values when data is missing`() {
        val raw = """
            {"affidabilita": "errore_dati", "alimento": "seitan", "cho_per_100g": null, "grassi_per_100g": null, "fonte_nome": "", "fonte_url": "", "motivo_errore": "nessuna fonte trovata"}
        """.trimIndent()

        val domain = json.decodeFromString<OnlineFoodResearchDto>(raw).toDomain()

        assertEquals(OnlineResearchReliability.ERRORE_DATI, domain.affidabilita)
        assertNull(domain.choPer100g)
        assertFalse(domain.haDatiUtilizzabili)
    }

    @Test
    fun `OnlineFoodResearchDto with a sicura result has usable data`() {
        val raw = """
            {"affidabilita": "sicura", "alimento": "riso", "cho_per_100g": 80.0, "grassi_per_100g": 0.6, "fonte_nome": "CREA", "fonte_url": "https://crea.gov.it", "motivo_errore": ""}
        """.trimIndent()

        val domain = json.decodeFromString<OnlineFoodResearchDto>(raw).toDomain()

        assertEquals(OnlineResearchReliability.SICURA, domain.affidabilita)
        assertTrue(domain.haDatiUtilizzabili)
        assertNull(domain.proteinePer100g)
    }

    @Test
    fun `OnlineFoodResearchDto decodes optional proteine_per_100g and indice_glicemico`() {
        val raw = """
            {"affidabilita": "sicura", "alimento": "pasta integrale", "cho_per_100g": 66.2, "grassi_per_100g": 2.5, "proteine_per_100g": 13.4, "indice_glicemico": 42, "fonte_nome": "CREA", "fonte_url": "https://crea.gov.it", "motivo_errore": ""}
        """.trimIndent()

        val domain = json.decodeFromString<OnlineFoodResearchDto>(raw).toDomain()

        assertTrue(domain.haDatiUtilizzabili)
        assertEquals(13.4, domain.proteinePer100g!!, 0.001)
        assertEquals(42, domain.indiceGlicemico)
    }
}
