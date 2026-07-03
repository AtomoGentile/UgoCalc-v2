package com.ugocalc.app.ui

import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AnalysisTotals
import com.ugocalc.app.domain.model.ModelVisionOutcome
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stato condiviso dell'analisi corrente, attraversa più schermate del flusso (§2.4): selezione
 * modello → scatto foto → risultati Visione → "Ricevi i CHO" → schermata finale → Salva.
 * Si usa uno store condiviso invece di passare byte[] dell'immagine o liste di risultati come
 * argomenti di navigazione, che su Android Navigation Compose non è pratico né sicuro per payload
 * grandi. Viene azzerato con [reset] quando l'utente torna in Homepage o avvia una nuova analisi.
 */
@Singleton
class AnalysisSessionStore @Inject constructor() {

    /** Un alimento con peso già noto (§ richiesta utente "preferiti portano alla sezione quantità"), pronto da mostrare nel passo Quantità di ManualFoodEntryScreen senza rifare la ricerca. */
    data class PendingManualRow(val entry: NormalizedFoodEntry, val grams: Double)

    data class Session(
        val selectedModels: List<AiModel> = emptyList(),
        val capturedImages: List<ByteArray> = emptyList(),
        val imageMimeType: String = "image/jpeg",
        val visionOutcomes: List<ModelVisionOutcome> = emptyList(),
        val referenceModelId: String? = null,
        val totals: AnalysisTotals? = null,
        /** Alimento trovato dallo scanner codici a barre (§ richiesta utente), in attesa che ManualFoodEntryScreen lo raccolga. */
        val pendingManualEntry: NormalizedFoodEntry? = null,
        /** Righe già quantificate, in attesa che ManualFoodEntryScreen le carichi direttamente nel passo Quantità (§ richiesta utente: preferiti che riaprono la modifica quantità). */
        val pendingManualRows: List<PendingManualRow>? = null,
    )

    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session

    fun setSelectedModels(models: List<AiModel>) = _session.update { it.copy(selectedModels = models) }

    /** [images] sono più scatti dello stesso piatto (§2.4 punto 2 esteso): tutti inviati insieme all'AI per un'analisi più accurata. */
    fun setCapturedImages(images: List<ByteArray>, mimeType: String) =
        _session.update { it.copy(capturedImages = images, imageMimeType = mimeType) }

    fun setVisionOutcomes(outcomes: List<ModelVisionOutcome>) =
        _session.update { it.copy(visionOutcomes = outcomes, referenceModelId = it.referenceModelId ?: outcomes.firstOrNull { o -> o is ModelVisionOutcome.Success }?.model?.id) }

    fun setReferenceModelId(modelId: String) = _session.update { it.copy(referenceModelId = modelId) }

    fun setTotals(totals: AnalysisTotals) = _session.update { it.copy(totals = totals) }

    fun reset() { _session.update { Session() } }

    fun setPendingManualEntry(entry: NormalizedFoodEntry) = _session.update { it.copy(pendingManualEntry = entry) }

    /** Consumo una tantum: la voce va aggiunta una sola volta, non ad ogni ricomposizione/riapertura dello schermo. */
    fun consumePendingManualEntry(): NormalizedFoodEntry? {
        val entry = _session.value.pendingManualEntry
        if (entry != null) _session.update { it.copy(pendingManualEntry = null) }
        return entry
    }

    fun setPendingManualRows(rows: List<PendingManualRow>) = _session.update { it.copy(pendingManualRows = rows) }

    /** Consumo una tantum, come [consumePendingManualEntry]. */
    fun consumePendingManualRows(): List<PendingManualRow>? {
        val rows = _session.value.pendingManualRows
        if (rows != null) _session.update { it.copy(pendingManualRows = null) }
        return rows
    }

    /** Riprova l'analisi (§2.4 punto 4) mantenendo gli stessi modelli selezionati, per ri-scattare le foto. */
    fun resetForRetry() = _session.update {
        it.copy(capturedImages = emptyList(), visionOutcomes = emptyList(), referenceModelId = null, totals = null)
    }
}
