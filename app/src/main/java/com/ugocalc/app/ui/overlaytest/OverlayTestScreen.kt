package com.ugocalc.app.ui.overlaytest

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private const val TAG = "OverlayTest"

/**
 * Schermata di test isolata (non collegata alla livella vera, §13 punto 7): verifica se è
 * possibile disegnare un overlay statico sopra la fotocamera di sistema e se il produttore del
 * device lo blocca. Nessuna logica sensori: solo permesso overlay + service + intent camera.
 */
@Composable
fun OverlayTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Pronto.") }

    fun startOverlayService() {
        Log.d(TAG, "startOverlayService: avvio OverlayTestService")
        context.startService(Intent(context, OverlayTestService::class.java))
    }

    fun stopOverlayService() {
        Log.d(TAG, "stopOverlayService: stop OverlayTestService")
        context.stopService(Intent(context, OverlayTestService::class.java))
    }

    // Rimozione di sicurezza se il Composable viene distrutto in modo inatteso (es. processo
    // ricreato): non deve restare un service/overlay "appiccicato" sopra altre app.
    DisposableEffect(Unit) {
        onDispose { stopOverlayService() }
    }

    var pendingCapturePath by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        Log.d(TAG, "takePictureLauncher callback: success=$success")
        stopOverlayService()
        pendingCapturePath?.let { File(it).delete() }
        pendingCapturePath = null
        statusText = if (success) "Foto scattata, overlay fermato." else "Annullato, overlay fermato."
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = OverlayPermissionHelper.hasOverlayPermission(context)
        Log.d(TAG, "overlayPermissionLauncher: tornati dalle impostazioni, granted=$granted")
        statusText = if (granted) "Permesso overlay concesso." else "Permesso overlay NON concesso."
    }

    fun launchSystemCameraWithOverlay() {
        startOverlayService()
        val photosDir = File(context.cacheDir, "overlay_test").apply { mkdirs() }
        val file = File(photosDir, "overlay_test_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCapturePath = file.absolutePath
        statusText = "Overlay avviato, apertura fotocamera di sistema..."
        takePictureLauncher.launch(uri)
    }

    fun onStartTestClicked() {
        if (OverlayPermissionHelper.hasOverlayPermission(context)) {
            launchSystemCameraWithOverlay()
        } else {
            statusText = "Permesso overlay mancante, apertura impostazioni..."
            overlayPermissionLauncher.launch(OverlayPermissionHelper.buildRequestOverlayPermissionIntent(context))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
    ) {
        Text("Test overlay su fotocamera di sistema", style = MaterialTheme.typography.titleMedium)
        Text(
            "Verifica se il produttore blocca un rettangolo rosso disegnato sopra l'app fotocamera. " +
                "Nessuna relazione con la livella vera: solo un esperimento isolato.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Text(statusText, style = MaterialTheme.typography.bodyMedium)

        Button(onClick = ::onStartTestClicked, modifier = Modifier.padding(top = 24.dp)) {
            Text("Avvia test overlay")
        }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Torna indietro")
        }
    }
}
