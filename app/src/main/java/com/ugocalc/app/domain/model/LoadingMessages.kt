package com.ugocalc.app.domain.model

/**
 * Messaggi caldi, accoglienti e professionali mostrati a rotazione durante l'attesa delle analisi AI.
 */
object LoadingMessages {
    val ALL = listOf(
        "Analizzo gli ingredienti presenti nel piatto...",
        "Stimo porzioni e proporzioni visive...",
        "Consulto i riferimenti nutrizionali...",
        "Calcolo carboidrati e grassi con cura...",
        "Verifico i dettagli dell'inquadratura...",
        "Confronto i valori con le tue tabelle...",
        "Preparo il riepilogo nutrizionale del pasto...",
        "Quasi pronto, completo gli ultimi calcoli...",
    )

    fun randomMessage(): String = ALL.random()
}
