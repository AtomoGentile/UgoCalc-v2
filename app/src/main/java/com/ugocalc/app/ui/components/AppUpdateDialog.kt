package com.ugocalc.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ugocalc.app.data.repository.AppUpdateStatus

/**
 * Dialog aggiornamento app (Prompt 4): mostra changelog e pulsante download APK.
 * Se [status.mandatory] = true, non è dismissibile — l'utente deve aggiornare prima di continuare.
 */
@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!status.mandatory) onDismiss() },
        title = {
            Text(
                if (status.mandatory) "Aggiornamento obbligatorio" else "Aggiornamento disponibile",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Versione ${status.latestVersion} disponibile.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (status.changelog.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Novità:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (status.mandatory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Questa versione non è più supportata. Aggiorna l'app per continuare.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Button(
                    onClick = {
                        val url = status.apkUrl.ifBlank { null }
                        if (url != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (status.apkUrl.isNotBlank()) "Scarica e installa" else "Vai al sito")
                }
            }
        },
        dismissButton = if (!status.mandatory) {
            { TextButton(onClick = onDismiss) { Text("Più tardi") } }
        } else null,
    )
}
