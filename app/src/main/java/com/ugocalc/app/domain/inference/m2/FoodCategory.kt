package com.ugocalc.app.domain.inference.model

/**
 * Categoria alimentare intermedia usata per segmentare i fattori di correzione del bias (§8, §14 —
 * problema riportato: pasta e riso condividono la stessa [EstimationStrategy] `DISTESA_SUL_PIATTO`,
 * quindi un fattore calcolato su sole misurazioni di pasta finirebbe applicato anche al riso senza
 * un solo dato di riso). Più fine della strategia di stima (6 valori), più larga del nome esatto
 * dell'alimento (che richiederebbe troppe osservazioni per raggiungere la soglia minima su ogni
 * singola voce): un compromesso tra precisione e velocità di accumulo dati.
 */
enum class FoodCategory {
    PIATTI_COMPOSTI,
    BEVANDE_E_LIQUIDI,
    CONTABILE,
    BLOCCO_MISURABILE,
    PASTA,
    RISO_E_CEREALI,
    PATATE,
    VERDURE_E_LEGUMI,
    TRASCURABILE,
}
