package com.ugocalc.app.domain.inference.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Le sei strategie di stima del peso (piano V2 §6, "Tassonomia degli alimenti per strategia di
 * stima"). La categoria di un alimento serve a un solo scopo: scegliere COME calcolarne il peso in
 * M3 (Motore fisico). Non descrive la geometria né la composizione, ma il metodo di calcolo.
 *
 * L'assegnazione avviene in M2 (Router di strategia) tramite una tabella locale nome→strategia:
 * aggiungere o correggere una strategia non richiede di toccare i prompt AI (§5 M2, §14).
 */
@Serializable
enum class EstimationStrategy {
    /** Frutta intera, polpette, biscotti, fette: numero di pezzi × peso unitario di riferimento. */
    @SerialName("contabile") CONTABILE,

    /** Pasta, riso, insalate, contorni: superficie coperta × altezza × riempimento × densità. */
    @SerialName("distesa") DISTESA_SUL_PIATTO,

    /** Latte, minestre, yogurt, succhi: capacità × frazione di riempimento × densità. */
    @SerialName("contenitore") A_CONTENITORE,

    /** Carne, pesce, torte, formaggio, pane a fetta: forma geometrica × dimensioni × densità. */
    @SerialName("blocco") BLOCCO_MISURABILE,

    /** Lasagne, ripieni, sughi coprenti, panini farciti: peso geometrico totale × ripartizione da ricetta. */
    @SerialName("composto") COMPOSTO_OCCLUSO,

    /** Olio, spolverate, salse leggere: valore fisso di riferimento, dichiarato incerto. */
    @SerialName("trascurabile") TRASCURABILE,
}
