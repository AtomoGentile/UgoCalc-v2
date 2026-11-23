package com.ugocalc.app.ui.finalresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.ai.AiOrchestrator
import com.ugocalc.app.data.remote.ai.client.AiCallException
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsClient
import com.ugocalc.app.data.remote.barcode.OpenFoodFactsException
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.GroundTruthLevel
import com.ugocalc.app.data.repository.HistoryRepository
import com.ugocalc.app.data.repository.PhysicalDataRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicProfile
import com.ugocalc.app.domain.glycemic.GlycemicSimulationResult
import com.ugocalc.app.domain.inference.m2.FoodCategoryRouter
import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.OnlineFoodResearchResult
import com.ugocalc.app.domain.model.OnlineResearchReliability
import com.ugocalc.app.domain.model.RecognizedFood
import com.ugocalc.app.domain.usecase.MatchFoodsAcrossTablesUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

/** Stato della ricerca online (§Pezzo E) per una singola riga del dettaglio, tenuto per indice di riga. */
sealed class OnlineResearchUiState {
    data object Loading : OnlineResearchUiState()
    data class Result(val result: OnlineFoodResearchResult) : OnlineResearchUiState()
    data class Error(val message: String) : OnlineResearchUiState()
}

data class FinalResultUiState(
    val totals: AnalysisTotals? = null,
    val foodTitle: String = "",
    val modelNames: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val hasSavedSnapshot: Boolean = false,
    val isFinishing: Boolean = false,
    val saved: Boolean = false,
    val onlineResearch: Map<Int, OnlineResearchUiState> = emptyMap(),
    /** Stellina accanto al titolo (§ richiesta utente): se attiva, il pasto è tra i preferiti. */
    val favoriteRequested: Boolean = false,
    /** Scorciatoia "Registra peso vero" a fine analisi (§13 punto 3). */
    val showGroundTruthCta: Boolean = false,
    /** Stessa scorciatoia per il benchmark V1/V2 (§13 punto 5). */
    val showBenchmarkGroundTruthCta: Boolean = false,
    val glycemicSimulation: GlycemicSimulationResult? = null,
    val targetPostPrandialMaxMgDl: Double = 140.0,
    val baselineOverrideMgDl: Int? = null,
)

@HiltViewModel
class FinalResultViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore,
    private val historyRepository: HistoryRepository,
    private val tableRepository: TableRepository,
    private val aiOrchestrator: AiOrchestrator,
    private val openFoodFactsClient: OpenFoodFactsClient,
    private val favoritesRepository: FavoritesRepository,
    private val v2TelemetryRepository: V2TelemetryRepository,
    private val benchmarkRepository: BenchmarkRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val physicalDataRepository: PhysicalDataRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinalResultUiState())
    val uiState: StateFlow<FinalResultUiState> = _uiState

    private val saveMutex = Mutex()
    private var autoSaveJob: Job? = null
    private var currentHistoryId: Long? = null
    private var lastFavoriteEntryId: Long? = null

    init {
        sessionStore.session
            .map { Triple(it.totals, it.selectedModels, it.totals?.lines) }
            .distinctUntilChanged()
            .onEach { (totals, selectedModels, _) ->
                if (totals == null) return@onEach
                val defaultTitle = totals.lines.maxByOrNull { it.recognizedFood.pesoStimatoG }?.recognizedFood?.nome ?: "Piatto"
                _uiState.update { state ->
                    state.copy(
                        totals = totals,
                        foodTitle = state.foodTitle.ifBlank { defaultTitle },
                        modelNames = selectedModels.map { it.displayName },
                    )
                }
                autoSave()
            }
            .launchIn(viewModelScope)

        combine(
            sessionStore.session.map { it.totals }.distinctUntilChanged(),
            settingsRepository.settings.map { it.toGlycemicProfile() }.distinctUntilChanged(),
            sessionStore.session.map { it.preMealGlucoseMgDl }.distinctUntilChanged(),
        ) { totals, profile, overrideMgDl ->
            val sim = simulateMealWithOverride(totals, profile, overrideMgDl)
            Triple(sim, profile.targetPostPrandialMaxMgDl, overrideMgDl)
        }
            .onEach { (simulation, targetMax, overrideMgDl) ->
                _uiState.update {
                    it.copy(
                        glycemicSimulation = simulation,
                        targetPostPrandialMaxMgDl = targetMax,
                        baselineOverrideMgDl = overrideMgDl,
                    )
                }
            }
            .launchIn(viewModelScope)

        val sessionId = sessionStore.session.value.sessionId
        viewModelScope.launch {
            val existingHistory = historyRepository.getBySessionId(sessionId)
            currentHistoryId = existingHistory?.id
            if (existingHistory != null) {
                val existingFav = favoritesRepository.observeFavorites().first()
                    .firstOrNull { it.historyEntryId == existingHistory.id }
                if (existingFav != null) {
                    lastFavoriteEntryId = existingFav.id
                    _uiState.update { it.copy(favoriteRequested = true) }
                }
            }
            val showGroundTruth = v2TelemetryRepository.hasPendingGroundTruth(sessionId)
            val showBenchmarkGroundTruth = benchmarkRepository.hasPendingGroundTruth(sessionId)
            _uiState.update {
                it.copy(
                    showGroundTruthCta = showGroundTruth,
                    showBenchmarkGroundTruthCta = showBenchmarkGroundTruth,
                )
            }
        }
    }

    private fun autoSave() {
        val state = _uiState.value
        val totals = state.totals ?: return
        val title = state.foodTitle
        val modelNames = state.modelNames
        val favoriteRequested = state.favoriteRequested
        val session = sessionStore.session.value

        _uiState.update { it.copy(isSaving = true) }
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(300)
            persistNow(
                title = title,
                totals = totals,
                modelNames = modelNames,
                favoriteRequested = favoriteRequested,
                sessionId = session.sessionId,
                sessionMode = session.sessionMode,
            )
        }
    }

    private suspend fun persistNow(
        title: String,
        totals: AnalysisTotals,
        modelNames: List<String>,
        favoriteRequested: Boolean,
        sessionId: String = sessionStore.session.value.sessionId,
        sessionMode: SessionMode = sessionStore.session.value.sessionMode,
    ) {
        saveMutex.withLock {
            if (currentHistoryId == null && sessionId.isNotBlank()) {
                currentHistoryId = historyRepository.getBySessionId(sessionId)?.id
            }
            val resolvedTitle = title.ifBlank { "Piatto" }
            val savedId = historyRepository.saveAnalysis(
                foodTitle = resolvedTitle,
                totals = totals,
                modelDisplayNames = modelNames,
                sessionId = sessionId,
                sessionMode = sessionMode,
                id = currentHistoryId ?: 0L,
            )
            currentHistoryId = savedId

            val currentFavorites = favoritesRepository.observeFavorites().first()
            if (favoriteRequested) {
                val existing = currentFavorites.firstOrNull {
                    (lastFavoriteEntryId != null && it.id == lastFavoriteEntryId) || it.historyEntryId == savedId
                }
                if (existing == null) {
                    val nextOrder = currentFavorites.size
                    lastFavoriteEntryId = favoritesRepository.addFavorite(savedId, resolvedTitle, nextOrder)
                } else {
                    lastFavoriteEntryId = existing.id
                }
            } else {
                val toRemove = currentFavorites.filter {
                    (lastFavoriteEntryId != null && it.id == lastFavoriteEntryId) || it.historyEntryId == savedId
                }
                toRemove.forEach { favoritesRepository.removeFavorite(it) }
                lastFavoriteEntryId = null
            }
        }
        _uiState.update { it.copy(isSaving = false, hasSavedSnapshot = true) }
    }

    fun setFoodTitle(title: String) {
        _uiState.update { it.copy(foodTitle = title) }
        autoSave()
    }

    /**
     * Correzione manuale di un abbinamento (§ ricerca tra i cibi del database).
     */
    fun applyManualMatch(lineIndex: Int, entry: NormalizedFoodEntry) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val original = totals.lines[lineIndex]
        val updatedLine = original.copy(
            match = FoodMatch(
                nomeRiconosciuto = original.recognizedFood.nome,
                voceTabellaAbbinata = entry.nome,
                nessunaCorrispondenza = false,
                confidenzaAbbinamento = Confidence.ALTO,
            ),
            tableEntry = entry,
        )
        val newLines = totals.lines.toMutableList().apply { this[lineIndex] = updatedLine }
        sessionStore.setTotals(AnalysisTotals(newLines))
        autoSave()
    }

    /**
     * L'utente corregge il nome e/o il peso dell'alimento. Se il nome cambia, avvia automaticamente
     * un ri-abbinamento locale su [tableRepository.searchEntries] applicando un eventuale match forte/esatto.
     */
    fun correctRecognizedFood(lineIndex: Int, newName: String, newGrams: Double?) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val original = totals.lines[lineIndex]

        val trimmedName = newName.trim()
        val nameChanged = trimmedName.isNotBlank() && trimmedName != original.recognizedFood.nome
        val weightChanged = newGrams != null && newGrams > 0.0 && newGrams != original.recognizedFood.pesoStimatoG
        if (!nameChanged && !weightChanged) return

        val updatedRecognizedFood = original.recognizedFood.copy(
            nome = if (nameChanged) trimmedName else original.recognizedFood.nome,
            pesoStimatoG = if (weightChanged) newGrams!! else original.recognizedFood.pesoStimatoG,
        )
        val updatedLine = if (nameChanged) {
            original.copy(recognizedFood = updatedRecognizedFood, match = null, tableEntry = null)
        } else {
            original.copy(recognizedFood = updatedRecognizedFood)
        }
        val newLines = totals.lines.toMutableList().apply { this[lineIndex] = updatedLine }
        sessionStore.setTotals(AnalysisTotals(newLines))
        autoSave()

        if (nameChanged) {
            viewModelScope.launch {
                val bestEntry = findStrongLocalMatch(trimmedName) ?: return@launch
                val latestLine = _uiState.value.totals?.lines?.getOrNull(lineIndex) ?: return@launch
                if (latestLine.recognizedFood.nome == trimmedName && latestLine.isExcluded) {
                    applyManualMatch(lineIndex, bestEntry)
                }
            }
        }

        if (weightChanged && newGrams != null) {
            val sessionId = sessionStore.session.value.sessionId
            viewModelScope.launch {
                learnFromWeightCorrection(
                    sessionId = sessionId,
                    originalFoodName = original.recognizedFood.nome,
                    correctedFoodName = trimmedName.ifBlank { original.recognizedFood.nome },
                    newGrams = newGrams,
                )
            }
        }
    }

    /** Regola la glicemia pre-pasto (mg/dL) per la sessione corrente (clamped 40..400). */
    fun adjustBaselineBy(delta: Int) {
        val currentBaseline = _uiState.value.baselineOverrideMgDl
            ?: _uiState.value.glycemicSimulation?.baselineMgDl?.toInt()
            ?: 90
        sessionStore.setPreMealGlucoseMgDl((currentBaseline + delta).coerceIn(40, 400))
    }

    /** Imposta direttamente la glicemia pre-pasto (mg/dL) per la sessione corrente (40..400). */
    fun setPreMealGlucose(mgDl: Int) {
        sessionStore.setPreMealGlucoseMgDl(mgDl.coerceIn(40, 400))
    }

    /**
     * Regola rapidamente il peso di una riga tramite stepper (+/- grammi), mantenendo almeno 5 g
     * e ricalcolando subito i totali e il salvataggio automatico.
     */
    fun adjustWeightBy(lineIndex: Int, deltaGrams: Float) {
        val totals = sessionStore.session.value.totals ?: _uiState.value.totals ?: return
        val newLines = adjustLineWeight(totals.lines, lineIndex, deltaGrams) ?: return
        val updatedTotals = AnalysisTotals(newLines)
        sessionStore.setTotals(updatedTotals)
        _uiState.update { it.copy(totals = updatedTotals) }
        autoSave()
    }

    private suspend fun findStrongLocalMatch(query: String): NormalizedFoodEntry? {
        var candidates = tableRepository.searchEntries(query)
        if (candidates.isEmpty() && !query.contains(' ') && query.length >= 4) {
            candidates = tableRepository.searchEntries(query.dropLast(1))
        }
        if (candidates.isEmpty()) return null
        val scored = candidates.map { candidate ->
            candidate.entry to MatchFoodsAcrossTablesUseCase.similarity(query, candidate.entry.nome)
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return best.first.takeIf { best.second >= AUTO_REMATCH_MIN_SIMILARITY }
    }

    private suspend fun learnFromWeightCorrection(
        sessionId: String,
        originalFoodName: String,
        correctedFoodName: String,
        newGrams: Double,
    ) {
        try {
            val v2Record = v2TelemetryRepository.getBySessionId(sessionId)
            if (v2Record != null && v2Record.failureReason == null) {
                val stime = runCatching {
                    json.decodeFromString<List<WeightEstimate>>(v2Record.weightEstimatesJson)
                }.getOrDefault(emptyList())

                val matchedName = stime.firstOrNull { it.nome == originalFoodName }?.nome
                    ?: stime.firstOrNull { it.nome == correctedFoodName }?.nome
                    ?: stime.firstOrNull {
                        StrategyRouter.normalize(it.nome) == StrategyRouter.normalize(originalFoodName)
                    }?.nome
                    ?: originalFoodName

                val existingGroundTruth = v2Record.groundTruthPesiJson?.let { raw ->
                    runCatching { json.decodeFromString<Map<String, Double>>(raw) }.getOrNull()
                }.orEmpty().toMutableMap()
                existingGroundTruth[matchedName] = newGrams

                v2TelemetryRepository.recordGroundTruth(v2Record.id, existingGroundTruth, GroundTruthLevel.BRONZE)
                benchmarkRepository.recordGroundTruth(sessionId, existingGroundTruth, GroundTruthLevel.BRONZE)

                if (stime.isNotEmpty()) {
                    physicalDataRepository.learnFromGroundTruth(stime, mapOf(matchedName to newGrams))
                    val consent = settingsRepository.settings.first().biasDataConsentGiven
                    stime.filter { it.nome == matchedName }.forEach { stima ->
                        val categoria = FoodCategoryRouter.categorize(stima.nome)
                        biasCorrectionRepository.recalculate(stima.strategia, v2Record.aiModelId, categoria, consent)
                    }
                }
            } else {
                benchmarkRepository.recordGroundTruth(sessionId, mapOf(originalFoodName to newGrams), GroundTruthLevel.BRONZE)
            }

            val showGroundTruth = v2TelemetryRepository.hasPendingGroundTruth(sessionId)
            val showBenchmarkGroundTruth = benchmarkRepository.hasPendingGroundTruth(sessionId)
            _uiState.update {
                it.copy(
                    showGroundTruthCta = showGroundTruth,
                    showBenchmarkGroundTruthCta = showBenchmarkGroundTruth,
                )
            }
        } catch (_: Exception) {
            // Apprendimento best-effort in background.
        }
    }

    /** Aggiunge un alimento scelto direttamente dall'utente tra quelli in tabella. */
    fun addManualFood(entry: NormalizedFoodEntry, grams: Double) {
        if (grams <= 0.0) return
        val newLine = CalculatedFoodLine(
            recognizedFood = RecognizedFood(nome = entry.nome, pesoStimatoG = grams, commento = "Inserito manualmente", confidenza = Confidence.ALTO),
            match = FoodMatch(nomeRiconosciuto = entry.nome, voceTabellaAbbinata = entry.nome, nessunaCorrispondenza = false, confidenzaAbbinamento = Confidence.ALTO),
            tableEntry = entry,
        )
        val newLines = (_uiState.value.totals?.lines ?: emptyList()) + newLine
        sessionStore.setTotals(AnalysisTotals(newLines))
        autoSave()
    }

    fun removeLine(lineIndex: Int) {
        val totals = _uiState.value.totals ?: return
        if (lineIndex !in totals.lines.indices) return
        val newLines = totals.lines.toMutableList().apply { removeAt(lineIndex) }
        sessionStore.setTotals(AnalysisTotals(newLines))
        autoSave()
    }

    /**
     * Ricerca online per una pietanza mancante (§Pezzo E): cerca tra tutti i modelli configurati
     * Gemini o Anthropic (non solo quelli selezionati nella sessione corrente); se nessuna chiave
     * web-search è configurata, usa automaticamente il database gratuito OpenFoodFacts.
     */
    fun searchOnline(lineIndex: Int) {
        val totals = _uiState.value.totals ?: return
        val line = totals.lines.getOrNull(lineIndex) ?: return

        _uiState.update { it.copy(onlineResearch = it.onlineResearch + (lineIndex to OnlineResearchUiState.Loading)) }
        val sessionId = sessionStore.session.value.sessionId
        val selected = sessionStore.session.value.selectedModels

        viewModelScope.launch {
            val aiSearchModel =
                selected.firstOrNull {
                    it.provider == AiProviderType.GEMINI && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
                }
                    ?: AiModelCatalog.ALL.firstOrNull {
                        it.provider == AiProviderType.GEMINI && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
                    }
                    ?: selected.firstOrNull {
                        it.provider == AiProviderType.ANTHROPIC && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
                    }
                    ?: AiModelCatalog.ALL.firstOrNull {
                        it.provider == AiProviderType.ANTHROPIC && !settingsRepository.getApiKey(it.provider).isNullOrBlank()
                    }

            val newState = try {
                if (aiSearchModel != null) {
                    OnlineResearchUiState.Result(
                        aiOrchestrator.runOnlineFoodResearch(aiSearchModel, line.recognizedFood.nome, sessionId)
                    )
                } else {
                    val entries = openFoodFactsClient.searchByName(line.recognizedFood.nome, limit = 5)
                    val best = entries.firstOrNull()
                    if (best != null) {
                        OnlineResearchUiState.Result(
                            OnlineFoodResearchResult(
                                affidabilita = OnlineResearchReliability.BUONA,
                                alimento = best.nome,
                                choPer100g = best.choPer100g,
                                grassiPer100g = best.grassiPer100g,
                                proteinePer100g = best.proteinePer100g,
                                indiceGlicemico = best.indiceGlicemico,
                                fonteNome = "OpenFoodFacts",
                                fonteUrl = "https://world.openfoodfacts.org",
                            )
                        )
                    } else {
                        OnlineResearchUiState.Result(
                            OnlineFoodResearchResult(
                                affidabilita = OnlineResearchReliability.ERRORE_DATI,
                                alimento = line.recognizedFood.nome,
                                motivoErrore = "Nessun risultato trovato su OpenFoodFacts per \"${line.recognizedFood.nome}\".",
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiCallException) {
                OnlineResearchUiState.Error(e.message ?: "Ricerca online non riuscita")
            } catch (e: OpenFoodFactsException) {
                OnlineResearchUiState.Error(e.message ?: "Impossibile contattare OpenFoodFacts")
            } catch (e: IOException) {
                OnlineResearchUiState.Error("Errore di rete durante la ricerca online.")
            } catch (e: Exception) {
                OnlineResearchUiState.Error(e.message ?: "Ricerca online non riuscita")
            }

            _uiState.update { it.copy(onlineResearch = it.onlineResearch + (lineIndex to newState)) }
        }
    }

    fun dismissOnlineResult(lineIndex: Int) {
        _uiState.update { it.copy(onlineResearch = it.onlineResearch - lineIndex) }
    }

    /** "Aggiungi al database": persiste il risultato trovato online e risolve subito la riga. */
    fun addOnlineResultToDatabase(lineIndex: Int) {
        val state = _uiState.value.onlineResearch[lineIndex] as? OnlineResearchUiState.Result ?: return
        val result = state.result
        val cho = result.choPer100g ?: return
        val grassi = result.grassiPer100g ?: return
        val proteine = result.proteinePer100g ?: 0.0
        viewModelScope.launch {
            val entry = tableRepository.addOnlineResearchEntry(
                nome = result.alimento,
                choPer100g = cho,
                grassiPer100g = grassi,
                proteinePer100g = proteine,
                indiceGlicemico = result.indiceGlicemico,
            )
            applyManualMatch(lineIndex, entry)
            dismissOnlineResult(lineIndex)
        }
    }

    /** Stellina accanto al titolo (§ richiesta utente): aggiunge o rimuove il preferito. */
    fun toggleFavoriteRequested() {
        _uiState.update { it.copy(favoriteRequested = !it.favoriteRequested) }
        autoSave()
    }

    /** Attende il commit su DB prima di resettare la sessione e segnalare il completamento. */
    fun finish() {
        val state = _uiState.value
        if (state.isFinishing || state.saved) return

        autoSaveJob?.cancel()
        val totals = state.totals
        val title = state.foodTitle
        val modelNames = state.modelNames
        val favoriteRequested = state.favoriteRequested
        val session = sessionStore.session.value

        _uiState.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            try {
                if (totals != null) {
                    persistNow(
                        title = title,
                        totals = totals,
                        modelNames = modelNames,
                        favoriteRequested = favoriteRequested,
                        sessionId = session.sessionId,
                        sessionMode = session.sessionMode,
                    )
                }
                sessionStore.reset()
                _uiState.update { it.copy(isFinishing = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isFinishing = false) }
            }
        }
    }

    companion object {
        private const val AUTO_REMATCH_MIN_SIMILARITY = 0.80
        internal const val MIN_ADJUSTED_GRAMS = 5f
    }
}

internal fun adjustLineWeight(
    lines: List<CalculatedFoodLine>,
    lineIndex: Int,
    deltaGrams: Float,
): List<CalculatedFoodLine>? {
    if (lineIndex !in lines.indices) return null
    val original = lines[lineIndex]
    val nextGrams = (original.recognizedFood.pesoStimatoG.toFloat() + deltaGrams)
        .coerceAtLeast(FinalResultViewModel.MIN_ADJUSTED_GRAMS)
        .toDouble()
    if (nextGrams == original.recognizedFood.pesoStimatoG) return null

    val updatedLine = original.copy(
        recognizedFood = original.recognizedFood.copy(pesoStimatoG = nextGrams),
    )
    return lines.toMutableList().apply { this[lineIndex] = updatedLine }
}

internal fun simulateMealWithOverride(
    totals: AnalysisTotals?,
    profile: GlycemicProfile,
    baselineOverrideMgDl: Int?,
): GlycemicSimulationResult? {
    val lines = totals?.lines ?: return null
    val effectiveProfile = if (baselineOverrideMgDl != null) {
        profile.copy(baselineGlucoseMgDl = baselineOverrideMgDl.toDouble())
    } else {
        profile
    }
    return GlycemicCurveCalculator.simulateMeal(lines, effectiveProfile)
}
