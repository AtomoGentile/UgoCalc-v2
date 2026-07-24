package com.ugocalc.app.ui.camera

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.data.repository.BenchmarkRepository
import com.ugocalc.app.data.repository.ScaleCalibrationRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.V2TelemetryRepository
import com.ugocalc.app.di.ShadowAnalysisScope
import com.ugocalc.app.domain.inference.PipelineOutcome
import com.ugocalc.app.domain.inference.SessionPhoto
import com.ugocalc.app.domain.inference.V2AnalysisPipelineFactory
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.toAnalysisTotals
import com.ugocalc.app.domain.inference.toModelVisionSuccess
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.RunVisionForModelsUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import com.ugocalc.app.util.CameraMetadataHelper
import com.ugocalc.app.util.GyroscopeMotionTracker
import com.ugocalc.app.util.computePhotoQuality
import com.ugocalc.app.util.extractCameraMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CAPTURED_IMAGE_MIME_TYPE = "image/jpeg"

/** Mostrato quando [QualityGate] scarta tutte le foto della sessione (§5): meglio un tono diretto che spinga a rifare lo scatto piuttosto che un errore tecnico. */
const val PHOTO_QUALITY_WARNING = "Ma sei capace di fare una foto? La guardo e non capisco un ca...\nRifalla"

/** Testo pronto per il prompt di M1 con l'angolazione REALMENTE misurata per questa foto, `null` se non disponibile (§13 punto 7). */
private fun buildAngleDescription(photo: CapturedPhoto): String? {
    val angle = photo.measuredAngle ?: return null
    val rotationLine = if (angle.target.turnDeg > 0f) ", rotazione misurata ${"%.0f".format(angle.turnDeg)}°" else ""
    return "inclinazione misurata ${"%.0f".format(angle.pitchDeg)}°$rotationLine (target: ${angle.target.label})"
}

/**
 * [capturedAtEpochMillis] approssima l'istante dello scatto (§16): la fotocamera di sistema non
 * offre alcun callback nell'istante esatto, si usa il timestamp del file scritto su disco.
 * [measuredAngle] è l'angolazione REALMENTE letta dai sensori durante questo scatto (non solo il
 * target suggerito), `null` se il permesso overlay non era concesso o se non è arrivato nessun
 * campione sensore prima dello scatto — in quel caso M1 riceve solo il protocollo generico, senza
 * il valore misurato per questa foto specifica.
 */
data class CapturedPhoto(val bytes: ByteArray, val capturedAtEpochMillis: Long, val measuredAngle: MeasuredAngle? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CapturedPhoto

        if (!bytes.contentEquals(other.bytes)) return false
        if (capturedAtEpochMillis != other.capturedAtEpochMillis) return false
        if (measuredAngle != other.measuredAngle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + capturedAtEpochMillis.hashCode()
        result = 31 * result + (measuredAngle?.hashCode() ?: 0)
        return result
    }
}

data class V2AnalysisResultInternal(
    val outcome: ModelVisionOutcome.Success,
    val totals: AnalysisTotals,
    val stime: List<com.ugocalc.app.domain.inference.model.WeightEstimate>,
    val ambiguita: List<com.ugocalc.app.domain.inference.model.AmbiguityResolution>
)

data class CameraUiState(
    val capturedImages: List<CapturedPhoto> = emptyList(),
    val isAnalyzing: Boolean = false,
    val analysisDone: Boolean = false,
    val qualityWarning: String? = null,
    val availableCalibrations: List<ScaleCalibrationEntity> = emptyList(),
    val selectedCalibrationIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CameraCaptureViewModel @Inject constructor(
    private val runVisionForModels: RunVisionForModelsUseCase,
    private val sessionStore: AnalysisSessionStore,
    private val notificationHelper: AnalysisNotificationHelper,
    private val settingsRepository: SettingsRepository,
    private val v2PipelineFactory: V2AnalysisPipelineFactory,
    private val benchmarkRepository: BenchmarkRepository,
    private val telemetryRepository: V2TelemetryRepository,
    private val scaleCalibrationRepository: ScaleCalibrationRepository,
    private val gyroscopeTracker: GyroscopeMotionTracker,
    @param:ApplicationContext private val context: Context,
    @param:ShadowAnalysisScope private val shadowScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    init {
        viewModelScope.launch {
            sessionStore.session
                .map { it.sessionMode }
                .distinctUntilChanged()
                .flatMapLatest { mode -> scaleCalibrationRepository.observeAll(mode) }
                .collect { calibs ->
                    _uiState.update { it.copy(availableCalibrations = calibs) }
                }
        }
        viewModelScope.launch {
            sessionStore.session
                .map { it.selectedCalibrationIds }
                .distinctUntilChanged()
                .collect { ids ->
                    _uiState.update { it.copy(selectedCalibrationIds = ids) }
                }
        }
    }

    /** Scatto avvenuto: si aggiunge alla galleria di foto di questa sessione (più scatti per un'analisi più efficace). */
    fun onPhotoCaptured(imageBytes: ByteArray, capturedAtEpochMillis: Long, measuredAngle: MeasuredAngle? = null) {
        _uiState.update { it.copy(capturedImages = it.capturedImages + CapturedPhoto(imageBytes, capturedAtEpochMillis, measuredAngle)) }
    }

    /** L'utente scarta una foto già scattata dalla galleria della sessione corrente. */
    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(capturedImages = state.capturedImages.filterIndexed { i, _ -> i != index })
        }
    }

    /** L'utente chiude l'avviso di foto scadenti e torna alla galleria per rifare gli scatti. */
    fun dismissQualityWarning() {
        _uiState.update { it.copy(qualityWarning = null) }
    }

    fun toggleCalibrationSelection(id: Long) {
        val current = _uiState.value.selectedCalibrationIds
        val next = if (id in current) current - id else current + id
        sessionStore.setSelectedCalibrationIds(next)
    }

    /** Tutte le foto confermate → invio al Passaggio 1 per ciascun modello selezionato (§2.4 punto 3). */
    fun confirmPhotos(models: List<AiModel>) {
        val capturedPhotos = _uiState.value.capturedImages
        if (capturedPhotos.isEmpty()) return
        viewModelScope.launch {
            // Controllo qualità prima di spendere una sola chiamata AI (§5): se M0 scarterebbe tutte
            // le foto, meglio dirlo subito e far rifare lo scatto piuttosto che produrre una stima
            // inaffidabile con V1 (che non applica questo gate).
            val qualityPhotos = capturedPhotos.mapIndexed { index, photo ->
                val motion = gyroscopeTracker.peakMotionNear(photo.capturedAtEpochMillis)
                val cameraMetadata = CameraMetadataHelper.enrichWithPhysicalSize(
                    extractCameraMetadata(photo.bytes),
                    context
                )
                computePhotoQuality("foto_$index", photo.bytes, motion, cameraMetadata, buildAngleDescription(photo))
            }
            if (QualityGate().assess(qualityPhotos).tutteScadenti) {
                _uiState.update { it.copy(qualityWarning = PHOTO_QUALITY_WARNING) }
                return@launch
            }
            confirmPhotosAfterQualityCheck(capturedPhotos, models)
        }
    }

    private suspend fun confirmPhotosAfterQualityCheck(capturedPhotos: List<CapturedPhoto>, models: List<AiModel>) {
        val images = capturedPhotos.map { it.bytes }
        sessionStore.setCapturedImages(images, CAPTURED_IMAGE_MIME_TYPE)
        _uiState.update { it.copy(isAnalyzing = true) }
        val settings = settingsRepository.settings.first()
        val model = models.firstOrNull()
        // Non più uno switch manuale (§13 punto 1, §20): il benchmark pesato decide se la V2 ha
        // già superato la V1 sul CHO. DEBUG: l'utente può forzare l'uso della V2 dalle impostazioni bias.
            val v2VisibleEnabled = (benchmarkRepository.evaluatePromotion(forceV2 = settings.v2VisibleModeEnabled).promoteV2) && model != null
            val v2Result = if (v2VisibleEnabled) runV2Visible(capturedPhotos, model) else null
        if (v2Result != null) {
            sessionStore.setV2Result(v2Result.outcome, v2Result.totals, v2Result.stime, v2Result.ambiguita)
            _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
            notificationHelper.notifyAnalysisCompletedIfBackgrounded()
            return
        }
        // Nessuna V2 visibile richiesta, oppure V2 fallita (foto scadenti, fallback dichiarato,
        // schema non supportato, eccezione): la V1 resta sempre la rete di sicurezza (§13 punto 1).
        val outcomes = runVisionForModels(models, images, CAPTURED_IMAGE_MIME_TYPE)
        sessionStore.setVisionOutcomes(outcomes)
        _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
        notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        recordV1EstimateForBenchmarkIfEnabled(outcomes, models)
        // La modalità ombra ha senso solo quando la V2 non è già stata provata come motore
        // primario in questa stessa analisi: farla girare due volte raddoppierebbe le chiamate
        // AI senza alcun beneficio (§13 punto 1).
        if (!v2VisibleEnabled) {
            runV2ShadowAnalysisIfEnabled(capturedPhotos, models)
        }
    }

    /**
     * Motore V2 come flusso visibile (piano V2 §13 punto 1): esegue la pipeline M0→M8 e, se
     * completata con successo, restituisce l'esito già pronto per [AnalysisSessionStore.setV2Result].
     * Qualunque esito diverso da [PipelineOutcome.Verificato] (foto scadenti, fallback dichiarato,
     * schema non supportato) o un'eccezione (rete, quota, parsing) fa tornare `null`: il chiamante
     * ricade sulla V1 nella stessa sessione, mai un errore mostrato senza alternativa.
     */
    private suspend fun runV2Visible(
        capturedPhotos: List<CapturedPhoto>, 
        model: AiModel
    ): V2AnalysisResultInternal? {
        val sessionId = sessionStore.session.value.sessionId
        val sessionMode = sessionStore.session.value.sessionMode
        return runCatching {
            val photos = capturedPhotos.mapIndexed { index, photo ->
                val motion = gyroscopeTracker.peakMotionNear(photo.capturedAtEpochMillis)
                val cameraMetadata = CameraMetadataHelper.enrichWithPhysicalSize(
                    extractCameraMetadata(photo.bytes),
                    context
                )
                SessionPhoto(computePhotoQuality("foto_$index", photo.bytes, motion, cameraMetadata, buildAngleDescription(photo)), photo.bytes)
            }
            val selectedIds = sessionStore.session.value.selectedCalibrationIds
            val calibrations = if (selectedIds.isEmpty()) null else {
                scaleCalibrationRepository.observeAll(sessionMode).first().filter { it.id in selectedIds }
            }
            val outcome = v2PipelineFactory.create(sessionId).run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model, calibrations)
            if (outcome !is PipelineOutcome.Verificato) {
                Log.w("CameraCaptureVM", "V2 failed or requested fallback: $outcome")
                return@runCatching null
            }
            if (outcome.telemetryId >= 0) {
                benchmarkRepository.linkV2Record(sessionId, outcome.telemetryId)
            }
            V2AnalysisResultInternal(
                outcome = outcome.toModelVisionSuccess(model),
                totals = outcome.toAnalysisTotals(),
                stime = outcome.stime,
                ambiguita = outcome.verifica.ambiguitaRisolta
            )
        }.onFailure { e ->
            Log.e("CameraCaptureVM", "V2 exception", e)
        }.getOrNull()
    }

    /**
     * Salva subito i pesi grezzi per alimento stimati da V1, per il benchmark V1/V2 (§10, §13 punto
     * 5): senza questo, un pasto per cui l'utente non preme "Salva" in cronologia sparirebbe anche
     * dal confronto. Usa il modello di riferimento (il primo con successo), stesso criterio della V2
     * shadow, così i due motori restano confrontabili sullo stesso modello quando possibile.
     */
    private suspend fun recordV1EstimateForBenchmarkIfEnabled(outcomes: List<ModelVisionOutcome>, models: List<AiModel>) {
        if (!settingsRepository.settings.first().v2ShadowModeEnabled) return
        val success = outcomes.filterIsInstance<ModelVisionOutcome.Success>().firstOrNull() ?: return
        val session = sessionStore.session.value
        benchmarkRepository.recordV1Estimate(
            sessionId = session.sessionId,
            capturedAtEpochMillis = System.currentTimeMillis(),
            sessionMode = session.sessionMode,
            aiModel = models.firstOrNull() ?: success.model,
            pesiPerAlimento = success.result.alimenti.associate { it.nome to it.pesoStimatoG },
        )
    }

    /**
     * Motore V2 in ombra (piano V2 §13 punto 1, §10 Fase 2): gira in aggiunta a V1, mai al posto —
     * l'utente continua a vedere solo l'esito V1. Su [shadowScope], non su [viewModelScope]: questo
     * ViewModel viene distrutto appena la UI naviga verso i risultati V1, ben prima che le due
     * chiamate AI di V2 (M1 + M6) abbiano finito. Avvolto per intero in [runCatching]: un fallimento
     * qui (rete, quota, parsing) non deve mai emergere in UI né interrompere il flusso V1.
     *
     * Il session ID della sessione corrente viene passato alla pipeline (§13 punto 5): collega il
     * record di telemetria M8 al pasto tracciato nel benchmark, dove [recordV1EstimateForBenchmarkIfEnabled]
     * ha già salvato la stima V1 sulle stesse foto.
     */
    private fun runV2ShadowAnalysisIfEnabled(capturedPhotos: List<CapturedPhoto>, models: List<AiModel>) {
        val sessionId = sessionStore.session.value.sessionId
        shadowScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.v2ShadowModeEnabled) return@launch
            val sessionMode = sessionStore.session.value.sessionMode
            val shadowModelIds = if (settings.shadowModelIds.isNotEmpty()) settings.shadowModelIds else setOf(models.firstOrNull()?.id ?: return@launch)
            val shadowModels = models.filter { it.id in shadowModelIds }
            if (shadowModels.isEmpty()) return@launch
            for (model in shadowModels) {
                val outcome = runCatching {
                    val photos = capturedPhotos.mapIndexed { index, photo ->
                        val motion = gyroscopeTracker.peakMotionNear(photo.capturedAtEpochMillis)
                        val cameraMetadata = CameraMetadataHelper.enrichWithPhysicalSize(
                            extractCameraMetadata(photo.bytes),
                            context
                        )
                        SessionPhoto(computePhotoQuality("foto_$index", photo.bytes, motion, cameraMetadata, buildAngleDescription(photo)), photo.bytes)
                    }
                    val selectedIds = sessionStore.session.value.selectedCalibrationIds
                    val calibrations = if (selectedIds.isEmpty()) null else {
                        scaleCalibrationRepository.observeAll(sessionMode).first().filter { it.id in selectedIds }
                    }
                    v2PipelineFactory.create(sessionId).run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model, calibrations)
                }
                when {
                    outcome.isFailure -> {
                        val msg = outcome.exceptionOrNull()?.message ?: "errore sconosciuto"
                        Log.e("V2Shadow", "Pipeline exception for ${model.displayName}: $msg", outcome.exceptionOrNull())
                        telemetryRepository.recordFailure("Eccezione: $msg", model, sessionMode, sessionId)
                        sessionStore.setShadowV2Result(model, AnalysisSessionStore.PipelineOutcomeForShadow.Failure("Eccezione: $msg"))
                    }
                    outcome.getOrNull() is PipelineOutcome.Verificato -> {
                        val verificato = outcome.getOrNull() as PipelineOutcome.Verificato
                        if (verificato.telemetryId >= 0) benchmarkRepository.linkV2Record(sessionId, verificato.telemetryId)
                        sessionStore.setShadowV2Result(
                            model,
                            AnalysisSessionStore.PipelineOutcomeForShadow.Success(
                                visionOutcome = verificato.toModelVisionSuccess(model),
                                totals = verificato.toAnalysisTotals(),
                                stime = verificato.stime,
                                ambiguita = verificato.verifica.ambiguitaRisolta
                            )
                        )
                    }
                    outcome.getOrNull() is PipelineOutcome.NessunaFotoValida -> {
                        telemetryRepository.recordFailure("NessunaFotoValida", model, sessionMode, sessionId)
                        sessionStore.setShadowV2Result(model, AnalysisSessionStore.PipelineOutcomeForShadow.Failure("NessunaFotoValida"))
                    }
                    outcome.getOrNull() is PipelineOutcome.FallbackAV1 -> {
                        val motivo = (outcome.getOrNull() as PipelineOutcome.FallbackAV1).motivo
                        telemetryRepository.recordFailure("FallbackAV1: $motivo", model, sessionMode, sessionId)
                        sessionStore.setShadowV2Result(model, AnalysisSessionStore.PipelineOutcomeForShadow.Failure("FallbackAV1: $motivo"))
                    }
                    outcome.getOrNull() is PipelineOutcome.SchemaNonSupportato -> {
                        val s = outcome.getOrNull() as PipelineOutcome.SchemaNonSupportato
                        telemetryRepository.recordFailure("SchemaNonSupportato: trovata v${s.trovata} attesa v${s.attesa}", model, sessionMode, sessionId)
                        sessionStore.setShadowV2Result(model, AnalysisSessionStore.PipelineOutcomeForShadow.Failure("SchemaNonSupportato"))
                    }
                }
            }
        }
    }
}
