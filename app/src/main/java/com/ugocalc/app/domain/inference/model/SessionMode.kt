package com.ugocalc.app.domain.inference.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modalità di sessione (piano V2 §8, §14): scelta esplicita dell'utente all'avvio di ogni analisi,
 * **mai dedotta automaticamente dalla foto**.
 *
 * La modalità viene salvata su ogni analisi (M8) e determina due cose:
 * - l'ordine delle ancore di scala tentate da M1 (in Casa le stoviglie calibrate hanno priorità;
 *   fuori casa si parte dai riferimenti corporei/universali — vedi [ScaleAnchorType]);
 * - quale insieme di fattori di correzione appresi applicare, tenuti separati tra Casa e Ristorante
 *   perché i bias sistematici non coincidono necessariamente.
 */
@Serializable
enum class SessionMode {
    @SerialName("casa") CASA,
    @SerialName("ristorante") RISTORANTE,
}
