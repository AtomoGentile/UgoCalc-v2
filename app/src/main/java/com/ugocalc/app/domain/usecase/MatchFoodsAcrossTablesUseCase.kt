package com.ugocalc.app.domain.usecase

import com.ugocalc.app.data.local.db.entity.NormalizationStatus
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.ai.dto.MatchingInputFoodDto
import com.ugocalc.app.data.remote.ai.dto.MatchingInputTableEntryDto
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.mapEntryToDomain
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.MatchingResult
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Passaggio 3 (§5) con matching ibrido in 4 fasi:
 * 1. **Match locale istantaneo**: risolve subito senza chiamate AI le corrispondenze esatte
 *    normalizzate o con forte sovrapposizione lessicale (>= 0.88), rispettando la priorità
 *    tra le tabelle.
 * 2. **Match AI filtrato per candidati**: per gli alimenti residui invia al modello solo i top 60
 *    candidati per tabella (o l'intera tabella se ha <= 80 righe) così le tabelle grandi non
 *    superano mai il contesto né generano JSON troncati.
 * 3. **Riconciliazione tollerante**: riallinea `nomeRiconosciuto` e `voceTabellaAbbinata`
 *    ignorando accenti/maiuscole/punteggiatura e con fallback fuzzy (>= 0.80).
 * 4. **Fallback locale resiliente**: se una chiamata AI fallisce ([AiCallException]), recupera il
 *    miglior match fuzzy locale fra tutte le tabelle (>= 0.65: `MEDIO` se >= 0.78, altrimenti `BASSO`).
 */
class MatchFoodsAcrossTablesUseCase internal constructor(
    private val loadDoneTablesWithEntries: suspend () -> List<List<NormalizedFoodEntry>>,
    private val runAiMatching: suspend (AiModel, String, String, String?) -> MatchingResult,
    private val json: Json,
) {
    @Inject
    constructor(
        tableRepository: TableRepository,
        aiOrchestrator: AiOrchestrator,
        json: Json,
    ) : this(
        loadDoneTablesWithEntries = {
            tableRepository.getAllTablesByPriority()
                .filter { it.normalizationStatus == NormalizationStatus.DONE }
                .map { table -> tableRepository.getEntriesForTable(table.id).map(::mapEntryToDomain) }
                .filter { it.isNotEmpty() }
        },
        runAiMatching = { model, listaJson, tabellaJson, sessionId ->
            aiOrchestrator.runMatching(model, listaJson, tabellaJson, sessionId)
        },
        json = json,
    )

    suspend operator fun invoke(
        recognizedFoods: List<RecognizedFood>,
        referenceModel: AiModel,
        sessionId: String? = null,
    ): List<CalculatedFoodLine> {
        if (recognizedFoods.isEmpty()) return emptyList()

        val tables = loadDoneTablesWithEntries().filter { it.isNotEmpty() }
        if (tables.isEmpty()) {
            return recognizedFoods.map { food ->
                CalculatedFoodLine(
                    recognizedFood = food,
                    match = noMatchPlaceholder(food),
                    tableEntry = null,
                )
            }
        }

        val resolved = mutableMapOf<Int, Pair<FoodMatch, NormalizedFoodEntry>>()
        val pendingIndices = recognizedFoods.indices.toMutableList()

        // ── Fase 1: Match locale istantaneo in ordine di priorità tabelle ──────
        val stillPendingAfterLocal = mutableListOf<Int>()
        for (idx in pendingIndices) {
            val food = recognizedFoods[idx]
            val localMatch = findInstantLocalMatch(food.nome, tables)
            if (localMatch != null) {
                resolved[idx] = FoodMatch(
                    nomeRiconosciuto = food.nome,
                    voceTabellaAbbinata = localMatch.nome,
                    nessunaCorrispondenza = false,
                    confidenzaAbbinamento = Confidence.ALTO,
                ) to localMatch
            } else {
                stillPendingAfterLocal += idx
            }
        }
        pendingIndices.clear()
        pendingIndices += stillPendingAfterLocal

        // ── Fase 2 & 3: Match AI con filtro candidati + riconciliazione tollerante ─
        var anyAiCallFailed = false
        for (tableEntries in tables) {
            if (pendingIndices.isEmpty()) break

            val pendingFoods = pendingIndices.map { recognizedFoods[it] }
            val candidates = selectCandidatesForTable(pendingFoods, tableEntries)

            val listaAlimentiJson = json.encodeToString(
                pendingFoods.map { MatchingInputFoodDto(it.nome, it.pesoStimatoG) }
            )
            val tabellaJson = json.encodeToString(
                candidates.map { MatchingInputTableEntryDto(it.nome, it.choPer100g, it.grassiPer100g) }
            )

            val matchingResult = try {
                runAiMatching(referenceModel, listaAlimentiJson, tabellaJson, sessionId)
            } catch (e: AiCallException) {
                anyAiCallFailed = true
                continue
            }

            val nextPending = mutableListOf<Int>()
            for (idx in pendingIndices) {
                val food = recognizedFoods[idx]
                val aiMatch = findBestMatchingResult(food.nome, matchingResult.abbinamenti)
                if (aiMatch == null || aiMatch.nessunaCorrispondenza) {
                    nextPending += idx
                    continue
                }
                val entry = findBestTableEntry(aiMatch.voceTabellaAbbinata, tableEntries)
                if (entry == null) {
                    nextPending += idx
                } else {
                    resolved[idx] = aiMatch.copy(
                        nomeRiconosciuto = food.nome,
                        voceTabellaAbbinata = entry.nome,
                    ) to entry
                }
            }
            pendingIndices.clear()
            pendingIndices += nextPending
        }

        // ── Fase 4: Fallback fuzzy locale se almeno una chiamata AI è fallita ──
        if (anyAiCallFailed && pendingIndices.isNotEmpty()) {
            val remaining = mutableListOf<Int>()
            for (idx in pendingIndices) {
                val food = recognizedFoods[idx]
                val bestFallback = findBestFallbackMatch(food.nome, tables)
                if (bestFallback != null) {
                    val (entry, score) = bestFallback
                    val confidence = if (score >= FALLBACK_MEDIUM_THRESHOLD) Confidence.MEDIO else Confidence.BASSO
                    resolved[idx] = FoodMatch(
                        nomeRiconosciuto = food.nome,
                        voceTabellaAbbinata = entry.nome,
                        nessunaCorrispondenza = false,
                        confidenzaAbbinamento = confidence,
                    ) to entry
                } else {
                    remaining += idx
                }
            }
            pendingIndices.clear()
            pendingIndices += remaining
        }

        return recognizedFoods.mapIndexed { idx, food ->
            val resolution = resolved[idx]
            CalculatedFoodLine(
                recognizedFood = food,
                match = resolution?.first ?: noMatchPlaceholder(food),
                tableEntry = resolution?.second,
            )
        }
    }

    private fun findInstantLocalMatch(
        foodName: String,
        tables: List<List<NormalizedFoodEntry>>,
    ): NormalizedFoodEntry? {
        for (tableEntries in tables) {
            var bestEntry: NormalizedFoodEntry? = null
            var bestScore = 0.0
            for (entry in tableEntries) {
                val score = similarity(foodName, entry.nome)
                if (score >= INSTANT_LOCAL_THRESHOLD && score > bestScore) {
                    bestScore = score
                    bestEntry = entry
                    if (score >= 1.0) break
                }
            }
            if (bestEntry != null) return bestEntry
        }
        return null
    }

    private fun findBestMatchingResult(
        foodName: String,
        matches: List<FoodMatch>,
    ): FoodMatch? {
        val rawTarget = normalizeRaw(foodName)
        matches.firstOrNull { normalizeRaw(it.nomeRiconosciuto) == rawTarget }?.let { return it }
        val stemTarget = normalizeStemmed(foodName)
        matches.firstOrNull { normalizeStemmed(it.nomeRiconosciuto) == stemTarget }?.let { return it }

        var best: FoodMatch? = null
        var bestScore = 0.0
        for (candidate in matches) {
            val score = similarity(foodName, candidate.nomeRiconosciuto)
            if (score >= RECONCILIATION_THRESHOLD && score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun findBestTableEntry(
        matchedTableName: String,
        tableEntries: List<NormalizedFoodEntry>,
    ): NormalizedFoodEntry? {
        if (matchedTableName.isBlank()) return null
        val rawTarget = normalizeRaw(matchedTableName)
        tableEntries.firstOrNull { normalizeRaw(it.nome) == rawTarget }?.let { return it }
        val stemTarget = normalizeStemmed(matchedTableName)
        tableEntries.firstOrNull { normalizeStemmed(it.nome) == stemTarget }?.let { return it }

        var best: NormalizedFoodEntry? = null
        var bestScore = 0.0
        for (entry in tableEntries) {
            val score = similarity(matchedTableName, entry.nome)
            if (score >= RECONCILIATION_THRESHOLD && score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        return best
    }

    private fun findBestFallbackMatch(
        foodName: String,
        tables: List<List<NormalizedFoodEntry>>,
    ): Pair<NormalizedFoodEntry, Double>? {
        var bestEntry: NormalizedFoodEntry? = null
        var bestScore = 0.0
        for (tableEntries in tables) {
            for (entry in tableEntries) {
                val score = similarity(foodName, entry.nome)
                if (score >= FALLBACK_MIN_THRESHOLD && score > bestScore) {
                    bestScore = score
                    bestEntry = entry
                }
            }
        }
        return bestEntry?.let { it to bestScore }
    }

    private fun noMatchPlaceholder(food: RecognizedFood) = FoodMatch(
        nomeRiconosciuto = food.nome,
        voceTabellaAbbinata = "",
        nessunaCorrispondenza = true,
        confidenzaAbbinamento = Confidence.BASSO,
    )

    internal companion object {
        const val INSTANT_LOCAL_THRESHOLD = 0.88
        const val FULL_TABLE_THRESHOLD = 80
        const val MAX_AI_CANDIDATES = 60
        const val RECONCILIATION_THRESHOLD = 0.80
        const val FALLBACK_MEDIUM_THRESHOLD = 0.78
        const val FALLBACK_MIN_THRESHOLD = 0.65

        private val COMBINING_DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
        private val ITALIAN_STOPWORDS = setOf(
            "di", "da", "del", "dell", "della", "delle", "dello", "degli", "dei",
            "al", "all", "alla", "alle", "allo", "agli", "ai",
            "con", "e", "ed", "o", "od", "in", "a", "per", "su", "sul", "sulla",
            "tipo", "il", "lo", "la", "i", "gli", "le", "un", "una", "uno",
        )

        /**
         * Normalizzazione base: NFD strip di accenti/diacritici, minuscolo, rimozione punteggiatura
         * e compressione spazi.
         */
        internal fun normalizeRaw(value: String): String {
            val nfd = Normalizer.normalize(value, Normalizer.Form.NFD)
            val stripped = COMBINING_DIACRITICS_REGEX.replace(nfd, "")
            return NON_ALNUM_REGEX.replace(stripped.lowercase(Locale.ROOT), " ").trim()
        }

        /**
         * Normalizzazione con gestione leggera del plurale/genere italiano per token.
         */
        internal fun normalizeStemmed(value: String): String {
            val raw = normalizeRaw(value)
            if (raw.isEmpty()) return ""
            return raw.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { stemItalianToken(it) }
        }

        internal fun stemItalianToken(token: String): String {
            if (token.length < 4 || token.any { it.isDigit() }) return token
            if (token == "uova" || token == "uovo") return "uov"
            return when {
                token.length >= 6 && token.endsWith("cchio") -> token.dropLast(3)
                token.length >= 6 && token.endsWith("cchi") -> token.dropLast(2)
                token.length >= 6 && (token.endsWith("chia") || token.endsWith("chie")) -> token.dropLast(2)
                token.length >= 5 && token.endsWith("che") -> token.dropLast(3) + "ca"
                token.length >= 5 && token.endsWith("ghe") -> token.dropLast(3) + "ga"
                token.length >= 5 && token.endsWith("chi") -> token.dropLast(2)
                token.length >= 5 && token.endsWith("ghi") -> token.dropLast(2)
                token.length >= 5 && (token.endsWith("cia") || token.endsWith("cie")) -> token.dropLast(2)
                token.length >= 5 && (token.endsWith("gia") || token.endsWith("gie")) -> token.dropLast(2)
                token.endsWith("ca") || token.endsWith("ga") -> token // mantiene distinto es. "pesca" da "pesce"
                token.endsWith("ce") || token.endsWith("ci") -> token.dropLast(1)
                token.endsWith("ge") || token.endsWith("gi") -> token.dropLast(1)
                token.endsWith("a") || token.endsWith("e") || token.endsWith("i") || token.endsWith("o") -> token.dropLast(1)
                else -> token
            }
        }

        internal fun contentTokens(value: String): List<String> {
            val rawTokens = normalizeRaw(value).split(' ').filter { it.isNotEmpty() }
            if (rawTokens.isEmpty()) return emptyList()
            val filtered = rawTokens.filter { it !in ITALIAN_STOPWORDS }.map { stemItalianToken(it) }
            return if (filtered.isNotEmpty()) filtered else rawTokens.map { stemItalianToken(it) }
        }

        /**
         * Calcola una similarità lessicale in `[0.0, 1.0]` combinando:
         * - uguaglianza esatta normalizzata/stemmed (`1.0`);
         * - equivalenza esatta dei token di contenuto (`0.95`);
         * - Dice / F1 sui token di contenuto (con tolleranza per piccoli typo su parole >= 5 lettere);
         * - contenimento di sottosequenza di token pesato sul rapporto di lunghezza/token;
         * - similarità di Levenshtein su stringhe sufficientemente lunghe (>= 6 caratteri).
         */
        internal fun similarity(a: String, b: String): Double {
            val rawA = normalizeRaw(a)
            val rawB = normalizeRaw(b)
            if (rawA.isEmpty() || rawB.isEmpty()) return 0.0
            if (rawA == rawB) return 1.0

            val stemA = normalizeStemmed(a)
            val stemB = normalizeStemmed(b)
            if (stemA == stemB) return 1.0

            val tokensA = contentTokens(a).toSet()
            val tokensB = contentTokens(b).toSet()
            if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
            if (tokensA == tokensB) return 0.95

            var matchedWeight = 0.0
            for (ta in tokensA) {
                if (ta in tokensB) {
                    matchedWeight += 1.0
                } else if (ta.length >= 5) {
                    val bestTokenSim = tokensB
                        .filter { it.length >= 5 }
                        .maxOfOrNull { levenshteinSimilarity(ta, it) }
                        ?: 0.0
                    if (bestTokenSim >= 0.85) {
                        matchedWeight += bestTokenSim
                    }
                }
            }
            val dice = (2.0 * matchedWeight) / (tokensA.size + tokensB.size)

            val containment = computeContainmentScore(stemA, stemB, tokensA, tokensB)

            val minLen = min(stemA.length, stemB.length)
            val levScore = if (minLen >= 6) {
                levenshteinSimilarity(stemA, stemB)
            } else {
                0.0
            }

            return max(dice, max(containment, levScore)).coerceIn(0.0, 1.0)
        }

        private fun computeContainmentScore(
            stemA: String,
            stemB: String,
            tokensA: Set<String>,
            tokensB: Set<String>,
        ): Double {
            val smallerTokens = if (tokensA.size <= tokensB.size) tokensA else tokensB
            val largerTokens = if (tokensA.size <= tokensB.size) tokensB else tokensA
            val allContained = smallerTokens.all { it in largerTokens }
            if (!allContained) return 0.0

            val lenRatio = min(stemA.length, stemB.length).toDouble() / max(stemA.length, stemB.length).toDouble()
            val tokenRatio = smallerTokens.size.toDouble() / largerTokens.size.toDouble()
            return if (smallerTokens.size >= 2 || lenRatio >= 0.78) {
                0.5 + 0.5 * ((lenRatio + tokenRatio) / 2.0)
            } else {
                0.4 + 0.4 * lenRatio
            }
        }

        /**
         * Seleziona al massimo [MAX_AI_CANDIDATES] (60) candidati per tabella se la tabella ha più
         * di [FULL_TABLE_THRESHOLD] (80) righe, ordinandoli per miglior similarità lessicale verso
         * almeno uno degli alimenti ancora in sospeso.
         */
        internal fun selectCandidatesForTable(
            pendingFoods: List<RecognizedFood>,
            tableEntries: List<NormalizedFoodEntry>,
        ): List<NormalizedFoodEntry> {
            if (tableEntries.size <= FULL_TABLE_THRESHOLD) return tableEntries
            return tableEntries
                .mapIndexed { index, entry ->
                    val bestScore = pendingFoods.maxOfOrNull { similarity(it.nome, entry.nome) } ?: 0.0
                    Triple(entry, bestScore, index)
                }
                .sortedWith(compareByDescending<Triple<NormalizedFoodEntry, Double, Int>> { it.second }.thenBy { it.third })
                .take(MAX_AI_CANDIDATES)
                .map { it.first }
        }

        internal fun levenshteinSimilarity(s1: String, s2: String): Double {
            val maxLen = max(s1.length, s2.length)
            if (maxLen == 0) return 1.0
            val dist = levenshteinDistance(s1, s2)
            return (1.0 - dist.toDouble() / maxLen.toDouble()).coerceIn(0.0, 1.0)
        }

        private fun levenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length
            var prev = IntArray(n + 1) { it }
            var curr = IntArray(n + 1)
            for (i in 1..m) {
                curr[0] = i
                val c1 = s1[i - 1]
                for (j in 1..n) {
                    val cost = if (c1 == s2[j - 1]) 0 else 1
                    curr[j] = min(
                        min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost,
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[n]
        }
    }
}
