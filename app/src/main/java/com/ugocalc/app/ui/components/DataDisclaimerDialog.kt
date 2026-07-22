package com.ugocalc.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Disclaimer sull'utilizzo dei dati (§ richiesta utente), mostrato una sola volta al primo avvio:
 * cosa lascia il dispositivo, verso dove, e solo quando. Nessun testo legale formale — è un'app
 * personale non distribuita su store pubblici (vedi memoria progetto).
 */
@Composable
fun DataDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Benvenuto in UgoCalc") },
        text = {
            Column {
                Text(
                    "App personale per uso privato, non distribuita su store pubblici.\n\n" +
                        "Per riconoscere gli alimenti dalle foto e cercare i valori nutrizionali mancanti, " +
                        "UgoCalc invia i dati necessari (foto, tabelle, testo di ricerca) al servizio AI che " +
                        "scegli tu in Impostazioni > API — nulla parte finché non imposti almeno una chiave.\n\n" +
                        "Lo scanner codici a barre interroga il database pubblico OpenFoodFacts.\n\n" +
                        "Cronologia, preferiti e tabelle restano salvati solo sul telefono.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Ho capito") }
        },
    )
}
