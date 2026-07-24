package com.ugocalc.app.ui.bias

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.V2AnalysisRecordEntity
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.BiasCorrectionWithUsageStats
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.domain.model.AiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BiasEvolutionUiState(
    val loading: Boolean = false,
    val consentGiven: Boolean = false,
    val v2ShadowModeEnabled: Boolean = false,
    val v2VisibleModeEnabled: Boolean = false,
    /** Esito della decisione automatica (§13 punto 1, §20): il motore visibile in produzione, non più uno switch manuale. */
    val v2Promoted: Boolean = false,
    val corrections: List<BiasCorrectionWithUsageStats> = emptyList(),
    val recentV2Records: List<V2AnalysisRecordEntity> = emptyList(),
    val allV2Records: List<V2AnalysisRecordEntity> = emptyList(),
    val availableModels: List<AiModel> = emptyList(),
    val selectedModelId: String? = null,
    val recordsDisplayLimit: Int = 10,
)

/**
 * Menu laterale > BIAS ed evoluzione del comportamento (§ richiesta utente): il consenso al
 * trattamento dei dati per l'identificazione del bias (piano V2 §8) e la trasparenza su cosa il
 * sistema ha imparato finora vivono nella stessa schermata, perché sono la stessa cosa vista da due
 * lati — l'opt-in e il suo effetto osservabile.
 */
@HiltViewModel
class BiasEvolutionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val benchmarkRepository: BenchmarkRepository,
    private val telemetryRepository: V2TelemetryRepository,
) : ViewModel() {

    private val _selectedModelId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _recordsDisplayLimit = kotlinx.coroutines.flow.MutableStateFlow(10)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BiasEvolutionUiState> = combine(
        settingsRepository.settings,
        biasCorrectionRepository.getAllCorrectionsWithUsageStatsFlow(),
        telemetryRepository.getAllRecordsFlow(),
        _selectedModelId,
        _recordsDisplayLimit
    ) { settings, corrections, allV2, selectedModel, displayLimit ->
        val enabledModelIds = settings.shadowModelIds.takeIf { it.isNotEmpty() }
            ?: settings.preferredModelIds
        val availableModelIds = if (enabledModelIds.isNotEmpty()) {
            allV2.map { it.aiModelId }.distinct().sorted()
                .filter { it in enabledModelIds }
        } else {
            allV2.map { it.aiModelId }.distinct().sorted()
        }
        val effectiveSelectedModel = selectedModel ?: availableModelIds.firstOrNull()

        val filteredCorrections = if (effectiveSelectedModel != null) {
            corrections.filter { it.correction.aiModelId == effectiveSelectedModel }
        } else {
            corrections
        }

        val filteredRecords = if (effectiveSelectedModel != null) {
            allV2.filter { it.aiModelId == effectiveSelectedModel }
        } else {
            allV2
        }

        Triple(Pair(settings, filteredCorrections), filteredRecords, Triple(availableModelIds, effectiveSelectedModel, displayLimit))
    }.flatMapLatest { (settingsAndCorrections, filteredRecords, modelsInfo) ->
        flow {
            val (settings, corrections) = settingsAndCorrections
            val (availableModelIds, selectedModel, displayLimit) = modelsInfo
            // Convert model IDs to AiModel objects for display
            val availableModels = availableModelIds.mapNotNull { id ->
                com.ugocalc.app.domain.model.AiModelCatalog.ALL.find { it.id == id }
            }
            val promotion = benchmarkRepository.evaluatePromotion(forceV2 = settings.v2VisibleModeEnabled)

            emit(
                BiasEvolutionUiState(
                    loading = false,
                    consentGiven = settings.biasDataConsentGiven,
                    v2ShadowModeEnabled = settings.v2ShadowModeEnabled,
                    v2VisibleModeEnabled = settings.v2VisibleModeEnabled,
                    v2Promoted = promotion.promoteV2,
                    corrections = corrections,
                    recentV2Records = filteredRecords.take(displayLimit),
                    allV2Records = filteredRecords,
                    availableModels = availableModels,
                    selectedModelId = selectedModel,
                    recordsDisplayLimit = displayLimit
                )
            )
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BiasEvolutionUiState(loading = true)
    )

    fun setConsentGiven(given: Boolean) {
        viewModelScope.launch { settingsRepository.setBiasDataConsentGiven(given) }
    }

    fun setV2ShadowModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setV2ShadowModeEnabled(enabled) }
    }

    fun setV2VisibleModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setV2VisibleModeEnabled(enabled) }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            telemetryRepository.deleteRecord(recordId)
        }
    }

    fun selectModel(modelId: String?) {
        _selectedModelId.value = modelId
        _recordsDisplayLimit.value = 10
    }

    fun loadMoreRecords() {
        _recordsDisplayLimit.value += 10
    }
}
