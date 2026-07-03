package com.ugocalc.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

private fun isCameraGranted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

private fun isNotificationGranted(context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
} else true

/** Impostazioni > Permessi (§2.2): accesso a fotocamera e notifiche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // I permessi possono cambiare fuori dal ciclo di vita di Compose (dialog di sistema o schermata
    // "Impostazioni app" raggiunta col pulsante sotto): senza rileggerli a ON_RESUME, lo stato mostrato
    // resta congelato a quello calcolato alla prima composizione anche se nel frattempo sono stati concessi.
    var cameraGranted by remember { mutableStateOf(isCameraGranted(context)) }
    var notificationGranted by remember { mutableStateOf(isNotificationGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = isCameraGranted(context)
                notificationGranted = isNotificationGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = isCameraGranted(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = isNotificationGranted(context)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Permessi") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ListItem(
                headlineContent = { Text("Fotocamera") },
                supportingContent = { Text(if (cameraGranted) "Concesso" else "Non concesso") },
                trailingContent = {
                    if (!cameraGranted) {
                        Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Concedi") }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Notifiche") },
                supportingContent = { Text("Notifica \"Analisi terminata\" quando l'app è in background") },
                trailingContent = {
                    Switch(
                        checked = state.userSettings.notificationsEnabled && notificationGranted,
                        onCheckedChange = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (enabled && !notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                },
            )
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Apri impostazioni di sistema dell'app") }
        }
    }
}
