package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.m2.StrategyRouter
import com.ugocalc.app.domain.inference.model.ScaleAnchor
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.domain.model.Confidence
import kotlin.math.abs

/**
 * Ponte tra le ancore di scala percepite da M1 ([ScaleAnchor], nello [com.ugocalc.app.domain.inference.model.SceneGraph])
 * e lo [ScaleContext] richiesto da M3 per le formule "distesa sul piatto" (§5, §8).
 *
 * Convenzione (vedi [com.ugocalc.app.domain.inference.m1.PerceptionPrompt]): quando la scena
 * contiene un alimento a strategia "distesa", M1 riporta comunque il diametro del PIATTO in
 * `dimensione_reale_cm` dell'ancora scelta, anche quando l'ancora di partenza è un riferimento
 * indiretto (corporeo/universale) — è l'unico modo per cui `frazione_piatto` diventi un'area reale
 * a valle. Per questo il risolutore non filtra per `descrizione` per decidere il diametro, ma si
 * fida del `dimensione_reale_cm` dell'ancora di priorità più alta disponibile; se sono fornite le
 * [ScaleCalibrationEntity] dell'utente, arricchisce inoltre lo [ScaleContext] con la geometria
 * (piatto conico/cilindrico, diametro del fondo) e la capacità calibrata dei contenitori.
 *
 * Priorità (§8, stesso ordine di [com.ugocalc.app.domain.inference.model.ScaleAnchorType]): stoviglia
 * calibrata > riferimento corporeo > oggetto universale > stima generica. A parità di tipo vince la
 * confidenza più alta. Se nessuna ancora porta una dimensione utilizzabile, si ripiega sul diametro
 * di default con qualità bassa (§9): mai falsa precisione.
 */
object ScaleResolver {

    fun resolve(
        ancore: List<ScaleAnchor>,
        calibrations: List<ScaleCalibrationEntity> = emptyList(),
    ): ScaleContext {
        val capacitiesByName = calibrations
            .filter { it.capacityMl != null && it.capacityMl > 0.0 && it.name.isNotBlank() }
            .associate { StrategyRouter.normalize(it.name) to it.capacityMl!! }

        val migliore = ancore
            .filter { it.dimensioneRealeCm != null }
            .minWithOrNull(compareBy({ it.tipo.ordinal }, { confidencePriority(it.confidenza) }))

        if (migliore == null) {
            val fallbackCap = ancore
                .firstNotNullOfOrNull { matchCalibration(it, calibrations)?.capacityMl?.takeIf { ml -> ml > 0.0 } }
            return ScaleContext(
                calibratedCapacityMl = fallbackCap,
                calibratedCapacitiesByName = capacitiesByName,
            )
        }

        val matched = matchCalibration(migliore, calibrations)
        val shapeType = matched?.plateShapeType?.takeIf { it.isNotBlank() } ?: "cilindro"

        return ScaleContext(
            plateDiameterCm = migliore.dimensioneRealeCm!!,
            plateDiameterIsDefault = false,
            quality = migliore.confidenza,
            plateShapeType = shapeType,
            plateBottomDiameterCm = matched?.plateBottomDiameterCm,
            plateHeightCm = matched?.heightCm,
            calibratedCapacityMl = matched?.capacityMl?.takeIf { it > 0.0 },
            calibratedCapacitiesByName = capacitiesByName,
        )
    }

    private fun matchCalibration(
        anchor: ScaleAnchor,
        calibrations: List<ScaleCalibrationEntity>,
    ): ScaleCalibrationEntity? {
        if (calibrations.isEmpty()) return null
        val descNorm = StrategyRouter.normalize(anchor.descrizione)

        if (descNorm.isNotEmpty()) {
            // 1. Corrispondenza esatta sul nome (preferendo stesso tipo di ancora)
            val exactMatches = calibrations.filter {
                val calNorm = StrategyRouter.normalize(it.name)
                calNorm.isNotEmpty() && calNorm == descNorm
            }
            exactMatches.firstOrNull { it.type == anchor.tipo.name }?.let { return it }
            exactMatches.firstOrNull()?.let { return it }

            // 2. Nome calibrazione contenuto nella descrizione (solo stesso tipo, preferendo il più lungo)
            calibrations
                .filter {
                    val calNorm = StrategyRouter.normalize(it.name)
                    it.type == anchor.tipo.name && calNorm.isNotEmpty() && descNorm.contains(calNorm)
                }
                .maxByOrNull { StrategyRouter.normalize(it.name).length }
                ?.let { return it }
        }

        // 3. Fallback per tipo STOVIGLIA_CALIBRATA: abbina per dimensione compatibile o unica stoviglia
        if (anchor.tipo == ScaleAnchorType.STOVIGLIA_CALIBRATA) {
            val dishware = calibrations.filter { it.type == ScaleAnchorType.STOVIGLIA_CALIBRATA.name }
            if (dishware.isEmpty()) return null
            val anchorCm = anchor.dimensioneRealeCm
            if (anchorCm != null) {
                dishware.firstOrNull { abs(it.valueCm - anchorCm) <= 0.5 }?.let { return it }
            }
            if (dishware.size == 1) return dishware.first()
            return dishware.firstOrNull { it.plateShapeType != null || it.plateBottomDiameterCm != null }
                ?: dishware.first()
        }

        return null
    }

    /** Ordina ALTO prima di MEDIO prima di BASSO (valore più basso = priorità più alta), per il tie-break a parità di tipo. */
    private fun confidencePriority(c: Confidence): Int = when (c) {
        Confidence.ALTO -> 0
        Confidence.MEDIO -> 1
        Confidence.BASSO -> 2
    }
}
