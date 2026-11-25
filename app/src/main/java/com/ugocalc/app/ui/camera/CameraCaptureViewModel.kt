package com.ugocalc.app.ui.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.ugocalc.app.domain.inference.m0.CameraMetadata
import com.ugocalc.app.domain.inference.m0.PhotoQuality
import com.ugocalc.app.domain.inference.m0.PhotoRejectionReason
import com.ugocalc.app.domain.inference.m0.QualityGate
import com.ugocalc.app.domain.inference.toAnalysisTotals
import com.ugocalc.app.domain.inference.toModelVisionSuccess
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.usecase.RunVisionForModelsUseCase
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.util.AnalysisNotificationHelper
import com.ugocalc.app.util.Camera2LiveMetadata
import com.ugocalc.app.util.CameraMetadataHelper
import com.ugocalc.app.util.GyroscopeMotionTracker
import com.ugocalc.app.util.OpticalGeometryCalculator
import com.ugocalc.app.util.computePhotoQuality
import com.ugocalc.app.util.extractCameraMetadata
import java.util.Locale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val CAPTURED_IMAGE_MIME_TYPE = "image/jpeg"

/** Limite ragionevole di scatti per sessione: più foto migliorano l'analisi ma allungano tempo di invio e costo per chiamata AI. */
internal const val MAX_PHOTOS = 5

/** Motivi leggibili mostrati quando [QualityGate] segnala problemi su tutte le foto della sessione. */
data class QualityWarningDetails(
    val reasons: List<String>,
)

private fun PhotoRejectionReason.toReadableItalian(): String = when (this) {
    PhotoRejectionReason.SFOCATA -> "Sfocata"
    PhotoRejectionReason.TROPPO_SCURA -> "Troppo scura"
    PhotoRejectionReason.TROPPO_CHIARA -> "Troppo chiara"
    PhotoRejectionReason.MOSSA_DAL_SENSORE -> "Mossa dal sensore"
    PhotoRejectionReason.MOSSA_ESPOSIZIONE -> "Esposizione lunga (mosso)"
    PhotoRejectionReason.DUPLICATA -> "Foto duplicata"
    PhotoRejectionReason.RUMOROSA_ISO -> "Rumore elevato (ISO alto)"
}

/**
 * Testo pronto per il prompt di M1 con angolazione REALMENTE misurata, roll, movimento giroscopico
 * e metriche immagine per questa foto, `null` se non disponibile (§13 punto 7, CameraX Pro Studio).
 */
private fun buildAngleDescription(
    photo: CapturedPhoto,
    motionRadPerSec: Double? = null,
    sharpness: Double? = null,
    brightness: Double? = null,
): String? {
    val parts = mutableListOf<String>()
    val angle = photo.measuredAngle
    if (angle != null) {
        val rollLine = photo.liveTelemetry?.rollDeg
            ?.takeIf { it.isFinite() }
            ?.let { String.format(Locale.US, ", roll %+.0f°", it) }
            .orEmpty()
        val rotationLine = if (angle.target.turnDeg > 0f) {
            String.format(Locale.US, ", rotazione misurata %.0f°", angle.turnDeg)
        } else ""
        parts += String.format(
            Locale.US,
            "inclinazione misurata %.0f°%s%s (target: %s)",
            angle.pitchDeg,
            rollLine,
            rotationLine,
            angle.target.label,
        )
    }
    if (motionRadPerSec != null && motionRadPerSec.isFinite()) {
        val stabilityTag = when {
            motionRadPerSec <= 0.35 -> "stabile"
            motionRadPerSec <= QualityGate.DEFAULT_MOTION_MAX_RAD_PER_SEC -> "leggero movimento"
            else -> "mosso"
        }
        parts += String.format(Locale.US, "giroscopio %.2f rad/s (%s)", motionRadPerSec, stabilityTag)
    }
    if (sharpness != null && sharpness.isFinite() && brightness != null && brightness.isFinite()) {
        parts += String.format(Locale.US, "nitidezza %.0f, luminosità %.0f/255", sharpness, brightness)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

/**
 * [capturedAtEpochMillis] registra l'istante esatto dello scatto in CameraX Pro Studio (o il
 * timestamp approssimato del file per la fotocamera di sistema, §16).
 * [measuredAngle] è l'angolazione REALMENTE letta dai sensori durante questo scatto (non solo il
 * target suggerito), `null` se non disponibile.
 * [isFromGallery] distingue le foto importate dalla galleria (per cui il buffer live del
 * giroscopio non ha alcun significato).
 * [liveTelemetry] trasporta i metadati Camera2 live (diottrie, AF, OIS, zoom, sensore fisico,
 * pitch/roll) per la fusione ottica con EXIF.
 */
data class CapturedPhoto(
    val bytes: ByteArray,
    val capturedAtEpochMillis: Long,
    val measuredAngle: MeasuredAngle? = null,
    val isFromGallery: Boolean = false,
    val liveTelemetry: Camera2LiveMetadata? = null,
    val captureSource: String = if (isFromGallery) {
        OpticalGeometryCalculator.CAPTURE_SOURCE_GALLERY
    } else {
        OpticalGeometryCalculator.CAPTURE_SOURCE_SYSTEM_CAMERA
    },
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CapturedPhoto

        if (!bytes.contentEquals(other.bytes)) return false
        if (capturedAtEpochMillis != other.capturedAtEpochMillis) return false
        if (measuredAngle != other.measuredAngle) return false
        if (isFromGallery != other.isFromGallery) return false
        if (liveTelemetry != other.liveTelemetry) return false
        if (captureSource != other.captureSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + capturedAtEpochMillis.hashCode()
        result = 31 * result + (measuredAngle?.hashCode() ?: 0)
        result = 31 * result + isFromGallery.hashCode()
        result = 31 * result + (liveTelemetry?.hashCode() ?: 0)
        result = 31 * result + captureSource.hashCode()
        return result
    }
}

data class V2AnalysisResultInternal(
    val outcome: ModelVisionOutcome.Success,
    val totals: AnalysisTotals,
    val stime: List<com.ugocalc.app.domain.inference.model.WeightEstimate>,
    val ambiguita: List<com.ugocalc.app.domain.inference.model.AmbiguityResolution>,
)

data class CameraUiState(
    val capturedImages: List<CapturedPhoto> = emptyList(),
    val isAnalyzing: Boolean = false,
    val isLoadingGallery: Boolean = false,
    val analysisDone: Boolean = false,
    val qualityWarning: QualityWarningDetails? = null,
    val galleryError: String? = null,
    val analysisError: String? = null,
    val angleOverlayEnabled: Boolean = false,
    val availableCalibrations: List<ScaleCalibrationEntity> = emptyList(),
    val selectedCalibrationIds: Set<Long> = emptySet(),
    val preMealGlucoseMgDl: Int? = null,
    val userPromptNote: String = "",
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
        viewModelScope.launch {
            sessionStore.session
                .map { it.preMealGlucoseMgDl }
                .distinctUntilChanged()
                .collect { mgDl ->
                    _uiState.update { it.copy(preMealGlucoseMgDl = mgDl) }
                }
        }
        viewModelScope.launch {
            sessionStore.session
                .map { it.userPromptNote }
                .distinctUntilChanged()
                .collect { note ->
                    _uiState.update { it.copy(userPromptNote = note) }
                }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.angleOverlayEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    _uiState.update { it.copy(angleOverlayEnabled = enabled) }
                }
        }
    }

    /** Preferisce i modelli esatti salvati in [AnalysisSessionStore] per evitare collisioni di ID tra provider (es. Gemini vs Vertex AI). */
    private fun resolveEffectiveModels(fallbackModels: List<AiModel>): List<AiModel> =
        sessionStore.session.value.selectedModels.ifEmpty { fallbackModels }

    /** Scatto avvenuto: si aggiunge alla galleria di foto di questa sessione (fino a [MAX_PHOTOS]). */
    fun onPhotoCaptured(
        imageBytes: ByteArray,
        capturedAtEpochMillis: Long,
        measuredAngle: MeasuredAngle? = null,
        liveTelemetry: Camera2LiveMetadata? = null,
        captureSource: String = OpticalGeometryCalculator.CAPTURE_SOURCE_SYSTEM_CAMERA,
    ) {
        _uiState.update { state ->
            if (state.capturedImages.size >= MAX_PHOTOS) return@update state
            state.copy(
                capturedImages = state.capturedImages + CapturedPhoto(
                    bytes = imageBytes,
                    capturedAtEpochMillis = capturedAtEpochMillis,
                    measuredAngle = measuredAngle,
                    isFromGallery = false,
                    liveTelemetry = liveTelemetry,
                    captureSource = captureSource,
                ),
                galleryError = null,
            )
        }
    }

    /** Aggiunge immagini già lette in byte dalla galleria rispettando il limite [MAX_PHOTOS]. */
    fun onGalleryImagesSelected(bytesList: List<ByteArray>) {
        if (bytesList.isEmpty()) return
        viewModelScope.launch {
            val validPhotos = withContext(Dispatchers.Default) {
                val now = System.currentTimeMillis()
                bytesList.mapNotNull { bytes ->
                    if (isDecodableImage(bytes)) {
                        CapturedPhoto(bytes = bytes, capturedAtEpochMillis = now, measuredAngle = null, isFromGallery = true)
                    } else {
                        null
                    }
                }
            }
            _uiState.update { state ->
                val remaining = (MAX_PHOTOS - state.capturedImages.size).coerceAtLeast(0)
                val toAdd = validPhotos.take(remaining)
                val skippedCount = bytesList.size - validPhotos.size
                val errorMsg = if (skippedCount > 0) {
                    "Alcune immagini selezionate non sono leggibili e sono state ignorate."
                } else null
                state.copy(
                    capturedImages = state.capturedImages + toAdd,
                    galleryError = errorMsg,
                )
            }
        }
    }

    /** Legge e valida gli URI scelti dal picker di sistema su [Dispatchers.IO]/[Dispatchers.Default] fino a [MAX_PHOTOS]. */
    fun onGalleryUrisSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remainingSlots = (MAX_PHOTOS - _uiState.value.capturedImages.size).coerceAtLeast(0)
        if (remainingSlots == 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGallery = true, galleryError = null) }
            val result = withContext(Dispatchers.IO) {
                val loaded = mutableListOf<CapturedPhoto>()
                var failedCount = 0
                val now = System.currentTimeMillis()
                for (uri in uris.take(remainingSlots)) {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes != null && isDecodableImage(bytes)) {
                        loaded += CapturedPhoto(
                            bytes = bytes,
                            capturedAtEpochMillis = now,
                            measuredAngle = null,
                            isFromGallery = true,
                        )
                    } else {
                        failedCount++
                    }
                }
                loaded to failedCount
            }
            val (loadedPhotos, failedCount) = result
            _uiState.update { state ->
                val remaining = (MAX_PHOTOS - state.capturedImages.size).coerceAtLeast(0)
                val toAdd = loadedPhotos.take(remaining)
                val error = when {
                    failedCount > 0 && toAdd.isEmpty() -> "Impossibile leggere le immagini selezionate dalla galleria."
                    failedCount > 0 -> "Alcune immagini non erano leggibili e sono state ignorate."
                    else -> null
                }
                state.copy(
                    capturedImages = state.capturedImages + toAdd,
                    isLoadingGallery = false,
                    galleryError = error,
                )
            }
        }
    }

    private fun isDecodableImage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    fun dismissGalleryError() {
        _uiState.update { it.copy(galleryError = null) }
    }

    fun dismissAnalysisError() {
        _uiState.update { it.copy(analysisError = null) }
    }

    fun setAngleOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAngleOverlayEnabled(enabled)
        }
    }

    /** L'utente scarta una foto già scattata dalla galleria della sessione corrente. */
    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(capturedImages = state.capturedImages.filterIndexed { i, _ -> i != index })
        }
    }

    /** L'utente chiude l'avviso di foto scadenti per rifare o sostituire gli scatti. */
    fun dismissQualityWarning() {
        _uiState.update { it.copy(qualityWarning = null) }
    }

    fun onAnalysisDoneConsumed() {
        _uiState.update { it.copy(analysisDone = false) }
    }

    fun toggleCalibrationSelection(id: Long) {
        val current = _uiState.value.selectedCalibrationIds
        val next = if (id in current) current - id else current + id
        sessionStore.setSelectedCalibrationIds(next)
    }

    fun setPreMealGlucoseMgDl(mgDl: Int?) {
        sessionStore.setPreMealGlucoseMgDl(mgDl)
    }

    fun setUserPromptNote(note: String) {
        sessionStore.setUserPromptNote(note)
    }

    /** Tutte le foto confermate → controllo qualità su [Dispatchers.Default] → Passaggio 1 (§2.4 punto 3). */
    fun confirmPhotos(models: List<AiModel>) {
        if (_uiState.value.isAnalyzing) return
        val capturedPhotos = _uiState.value.capturedImages
        if (capturedPhotos.isEmpty()) return
        val effectiveModels = resolveEffectiveModels(models)

        viewModelScope.launch {
            val assessment = withContext(Dispatchers.Default) {
                val qualityPhotos = buildQualityPhotos(capturedPhotos)
                QualityGate().assess(qualityPhotos)
            }
            if (assessment.tutteScadenti) {
                val reasons = assessment.scartate
                    .mapNotNull { it.motivo?.toReadableItalian() }
                    .distinct()
                    .ifEmpty { listOf("Qualità immagine non ottimale") }
                _uiState.update { it.copy(qualityWarning = QualityWarningDetails(reasons)) }
                return@launch
            }
            runAnalysisGuarded(capturedPhotos, effectiveModels)
        }
    }

    /** L'utente sceglie esplicitamente "Analizza comunque" dopo l'avviso qualità. */
    fun confirmPhotosIgnoringQuality(models: List<AiModel>) {
        if (_uiState.value.isAnalyzing) return
        val capturedPhotos = _uiState.value.capturedImages
        _uiState.update { it.copy(qualityWarning = null) }
        if (capturedPhotos.isEmpty()) return
        val effectiveModels = resolveEffectiveModels(models)

        viewModelScope.launch {
            runAnalysisGuarded(capturedPhotos, effectiveModels)
        }
    }

    private suspend fun runAnalysisGuarded(capturedPhotos: List<CapturedPhoto>, models: List<AiModel>) {
        try {
            confirmPhotosAfterQualityCheck(capturedPhotos, models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CameraCaptureVM", "Analysis failed unexpectedly", e)
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    analysisError = e.message ?: "Errore imprevisto durante l'analisi.",
                )
            }
        }
    }

    private suspend fun buildQualityPhotos(capturedPhotos: List<CapturedPhoto>): List<PhotoQuality> =
        withContext(Dispatchers.Default) {
            capturedPhotos.mapIndexed { index, photo ->
                val motion = if (photo.isFromGallery) {
                    null
                } else {
                    gyroscopeTracker.peakMotionNear(photo.capturedAtEpochMillis)
                        ?: photo.measuredAngle?.motionRadPerSec
                }
                val cameraMetadata = runCatching {
                    val exifMeta = runCatching { extractCameraMetadata(photo.bytes) }.getOrDefault(CameraMetadata())
                    CameraMetadataHelper.mergeWithLiveTelemetry(
                        exifMetadata = exifMeta,
                        context = context,
                        live = photo.liveTelemetry,
                        captureSource = photo.captureSource,
                    )
                }.getOrNull()

                val baseQuality = runCatching {
                    computePhotoQuality("foto_$index", photo.bytes, motion, cameraMetadata, angleDescription = null)
                }.getOrElse {
                    PhotoQuality(
                        id = "foto_$index",
                        sharpness = QualityGate.DEFAULT_SHARPNESS_MIN,
                        brightness = 128.0,
                        hash = index.toLong(),
                        motionRadPerSec = motion,
                        cameraMetadata = cameraMetadata,
                        angleDescription = null,
                    )
                }
                val enrichedAngleDesc = buildAngleDescription(
                    photo = photo,
                    motionRadPerSec = motion,
                    sharpness = baseQuality.sharpness,
                    brightness = baseQuality.brightness,
                )
                baseQuality.copy(angleDescription = enrichedAngleDesc)
            }
        }

    private suspend fun confirmPhotosAfterQualityCheck(capturedPhotos: List<CapturedPhoto>, models: List<AiModel>) {
        val images = capturedPhotos.map { it.bytes }
        sessionStore.setCapturedImages(images, CAPTURED_IMAGE_MIME_TYPE)
        _uiState.update { it.copy(isAnalyzing = true, analysisError = null) }
        val settings = settingsRepository.settings.first()
        val session = sessionStore.session.value
        val sessionId = session.sessionId
        val userPromptNote = session.userPromptNote.takeIf { it.isNotBlank() }

        // Calcola una sola volta le metriche ottiche/qualità e popola la pillola telemetrica di sessione
        val qualityPhotos = buildQualityPhotos(capturedPhotos)
        sessionStore.setPhotoTelemetrySummary(OpticalGeometryCalculator.formatCompactSessionSummary(qualityPhotos))
        val photoTelemetryBlock = OpticalGeometryCalculator.formatTelemetryPromptBlock(qualityPhotos)

        // Il benchmark pesato decide se la V2 ha già superato la V1 sul CHO (§13 punto 1, §20).
        val v2VisibleEnabled = benchmarkRepository.evaluatePromotion(forceV2 = settings.v2VisibleModeEnabled).promoteV2

        if (v2VisibleEnabled && models.isNotEmpty()) {
            val v2Results = coroutineScope {
                models.map { model ->
                    async { runV2Visible(capturedPhotos, qualityPhotos, model, userPromptNote) }
                }.awaitAll().filterNotNull()
            }
            if (v2Results.isNotEmpty()) {
                sessionStore.setV2Results(
                    v2Results.map {
                        AnalysisSessionStore.V2VisibleResult(it.outcome, it.totals, it.stime, it.ambiguita)
                    }
                )
                _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
                notificationHelper.notifyAnalysisCompletedIfBackgrounded()

                if (settings.v2ShadowModeEnabled || settings.globalBiasEnabled) {
                    viewModelScope.launch {
                        val outcomes = runVisionForModels(
                            models = models,
                            images = images,
                            imageMimeType = CAPTURED_IMAGE_MIME_TYPE,
                            sessionId = sessionId,
                            photoTelemetryBlock = photoTelemetryBlock,
                            userPromptNote = userPromptNote,
                        )
                        recordV1EstimateForBenchmarkIfEnabled(outcomes, models)
                    }
                }
                return
            }
        }

        // La V1 resta sempre la rete di sicurezza (§13 punto 1), ora arricchita con telemetria ottica CameraX/EXIF e note utente.
        val outcomes = runVisionForModels(
            models = models,
            images = images,
            imageMimeType = CAPTURED_IMAGE_MIME_TYPE,
            sessionId = sessionId,
            photoTelemetryBlock = photoTelemetryBlock,
            userPromptNote = userPromptNote,
        )
        sessionStore.setVisionOutcomes(outcomes)
        _uiState.update { it.copy(isAnalyzing = false, analysisDone = true) }
        notificationHelper.notifyAnalysisCompletedIfBackgrounded()
        recordV1EstimateForBenchmarkIfEnabled(outcomes, models)

        if (!v2VisibleEnabled) {
            runV2ShadowAnalysisIfEnabled(capturedPhotos, qualityPhotos, models, userPromptNote)
        }
    }

    private suspend fun runV2Visible(
        capturedPhotos: List<CapturedPhoto>,
        qualityPhotos: List<PhotoQuality>,
        model: AiModel,
        userPromptNote: String? = null,
    ): V2AnalysisResultInternal? {
        val sessionId = sessionStore.session.value.sessionId
        val sessionMode = sessionStore.session.value.sessionMode
        return runCatching {
            val photos = qualityPhotos.zip(capturedPhotos) { quality, raw -> SessionPhoto(quality, raw.bytes) }
            val selectedIds = sessionStore.session.value.selectedCalibrationIds
            val calibrations = if (selectedIds.isEmpty()) null else {
                scaleCalibrationRepository.observeAll(sessionMode).first().filter { it.id in selectedIds }
            }
            val outcome = v2PipelineFactory.create(sessionId, userPromptNote).run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model, calibrations, sessionId)
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
                ambiguita = outcome.verifica.ambiguitaRisolta,
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e("CameraCaptureVM", "V2 exception", e)
        }.getOrNull()
    }

    private suspend fun recordV1EstimateForBenchmarkIfEnabled(outcomes: List<ModelVisionOutcome>, models: List<AiModel>) {
        val settings = settingsRepository.settings.first()
        if (!settings.v2ShadowModeEnabled && !settings.globalBiasEnabled) return
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

    private fun runV2ShadowAnalysisIfEnabled(
        capturedPhotos: List<CapturedPhoto>,
        qualityPhotos: List<PhotoQuality>,
        models: List<AiModel>,
        userPromptNote: String? = null,
    ) {
        val sessionId = sessionStore.session.value.sessionId
        shadowScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.v2ShadowModeEnabled) return@launch
            val sessionMode = sessionStore.session.value.sessionMode
            val shadowModelIds = if (settings.shadowModelIds.isNotEmpty()) {
                settings.shadowModelIds
            } else {
                setOf(models.firstOrNull()?.uid ?: return@launch)
            }
            val shadowModels = models.filter { it.uid in shadowModelIds || it.id in shadowModelIds }
            if (shadowModels.isEmpty()) return@launch
            for (model in shadowModels) {
                val outcome = runCatching {
                    val photos = qualityPhotos.zip(capturedPhotos) { quality, raw -> SessionPhoto(quality, raw.bytes) }
                    val selectedIds = sessionStore.session.value.selectedCalibrationIds
                    val calibrations = if (selectedIds.isEmpty()) null else {
                        scaleCalibrationRepository.observeAll(sessionMode).first().filter { it.id in selectedIds }
                    }
                    v2PipelineFactory.create(sessionId, userPromptNote).run(photos, CAPTURED_IMAGE_MIME_TYPE, sessionMode, model, calibrations, sessionId)
                }
                when {
                    outcome.isFailure -> {
                        val err = outcome.exceptionOrNull()
                        if (err is CancellationException) throw err
                        val msg = err?.message ?: "errore sconosciuto"
                        Log.e("V2Shadow", "Pipeline exception for ${model.displayName}: $msg", err)
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
                                ambiguita = verificato.verifica.ambiguitaRisolta,
                            ),
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
