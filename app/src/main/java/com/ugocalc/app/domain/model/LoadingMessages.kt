package com.ugocalc.app.domain.model

/**
 * Messaggi di caricamento informali mostrati durante l'attesa delle chiamate AI (§2.4 punti 3 e 5).
 * Scelti a rotazione casuale (vedi `randomMessage()`), nello stesso tono leggero dell'esempio
 * "È andato tutto in mona" già presente nel documento (§2.4 punto 4).
 */
object LoadingMessages {
    val ALL = listOf(
        "Sto facendo una magia...",
        "Lasciami cucinare...",
        "Sto annusando il piatto...",
        "Conto le calorie a mente...",
        "Sto consultando il mio amico nutrizionista...",
        "Affetto, peso, ragiono...",
        "Sto facendo i calcoli, non disturbare lo chef...",
        "Un attimo, sto assaggiando con gli occhi...",
        "Sto chiedendo lumi all'intelligenza artificiale...",
        "Quasi fatto, manca solo il pizzico di sale...",
    )

    fun randomMessage(): String = ALL.random()
}
