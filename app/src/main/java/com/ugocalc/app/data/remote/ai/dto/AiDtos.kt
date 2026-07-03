package com.ugocalc.app.data.remote.ai.dto

import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.DiscardedRow
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.NormalizationResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.OnlineResearchReliability
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.model.VisionAnalysisResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisionAnalysisDto(
    val alimenti: List<RecognizedFood>,
    @SerialName("confidenza_globale") val confidenzaGlobale: Confidence,
    @SerialName("fattori_incertezza") val fattoriIncertezza: List<String> = emptyList(),
    val avvertenze: String = ""
)

fun VisionAnalysisDto.toDomain() = VisionAnalysisResult(
    alimenti = alimenti,
    confidenzaGlobale = confidenzaGlobale,
    fattoriIncertezza = fattoriIncertezza,
    avvertenze = avvertenze
)

@Serializable
data class NormalizationDto(
    val alimenti: List<NormalizedFoodEntry>,
    @SerialName("righe_scartate") val righeScartate: List<DiscardedRow> = emptyList(),
    @SerialName("note_conversione") val noteConversione: String = ""
)

fun NormalizationDto.toDomain() = NormalizationResult(
    alimenti = alimenti,
    righeScartate = righeScartate,
    noteConversione = noteConversione
)

@Serializable
data class MatchingDto(
    val abbinamenti: List<FoodMatch>
)

fun MatchingDto.toDomain() = MatchingResult(
    abbinamenti = abbinamenti
)

@Serializable
data class OnlineFoodResearchDto(
    val affidabilita: OnlineResearchReliability,
    val alimento: String,
    @SerialName("cho_per_100g") val choPer100g: Double? = null,
    @SerialName("grassi_per_100g") val grassiPer100g: Double? = null,
    @SerialName("fonte_nome") val fonteNome: String = "",
    @SerialName("fonte_url") val fonteUrl: String = "",
    @SerialName("motivo_errore") val motivoErrore: String = ""
)

fun OnlineFoodResearchDto.toDomain() = OnlineFoodResearchResult(
    affidabilita = affidabilita,
    alimento = alimento,
    choPer100g = choPer100g,
    grassiPer100g = grassiPer100g,
    fonteNome = fonteNome,
    fonteUrl = fonteUrl,
    motivoErrore = motivoErrore
)

@Serializable
data class MatchingInputFoodDto(
    val nome: String,
    val pesoStimatoG: Double?
)

@Serializable
data class MatchingInputTableEntryDto(
    val nome: String,
    val choPer100g: Double,
    val grassiPer100g: Double
)
