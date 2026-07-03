package com.ugocalc.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ugocalc.app.domain.model.Changelog

/**
 * Popup "cosa è cambiato" (§ richiesta utente), mostrato in automatico quando l'app si riavvia
 * dopo un aggiornamento (confronto sul `versionCode`, vedi [[ugocalc-workflow]] — `versionName`
 * resta fisso a "1.0.0" durante la pre-release). Riusa la stessa voce che compare in Impostazioni
 * > Novità, così il contenuto non va scritto due volte.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val latest = Changelog.ENTRIES.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novità nella versione ${latest.version}") },
        text = {
            // Bug fix: senza scroll esplicito il contenuto lungo (§ Changelog "1.0" ha decine di
            // voci) veniva tagliato dall'AlertDialog senza alcun modo di raggiungere il fondo.
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                latest.highlights.forEach { highlight ->
                    Text("• $highlight", modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Ho capito") }
        },
    )
}
