package com.ugocalc.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.ugocalc.app.ui.AppViewModel
import com.ugocalc.app.ui.components.DataDisclaimerDialog
import com.ugocalc.app.ui.components.WhatsNewDialog
import com.ugocalc.app.ui.navigation.UgoCalcNavHost
import com.ugocalc.app.ui.theme.AppFontFamily
import com.ugocalc.app.ui.theme.UgoCalcTheme
import com.ugocalc.app.util.isCameraPermissionGranted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    private val startupPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStartupPermissions()
        setContent {
            val settings by appViewModel.settings.collectAsState()
            val showWhatsNew by appViewModel.showWhatsNew.collectAsState()
            val isReady by appViewModel.isReady.collectAsState()
            UgoCalcTheme(
                appTheme = settings.theme,
                textScale = settings.textScale,
                primaryColor = settings.primaryColorArgb?.let { Color(it) },
                secondaryColor = settings.secondaryColorArgb?.let { Color(it) },
                tertiaryColor = settings.tertiaryColorArgb?.let { Color(it) },
                backgroundColor = settings.backgroundColorArgb?.let { Color(it) },
                errorColor = settings.errorColorArgb?.let { Color(it) },
                fontFamily = settings.fontFamilyName
                    ?.let { name -> runCatching { AppFontFamily.valueOf(name) }.getOrNull() }
                    ?.fontFamily,
                fontWeight = settings.fontFamilyName
                    ?.let { name -> runCatching { AppFontFamily.valueOf(name) }.getOrNull() }
                    ?.fontWeight,
                fontColor = settings.fontColorArgb?.let { Color(it) },
            ) {
                val navController = rememberNavController()
                UgoCalcNavHost(navController = navController)
                // Bug fix: aspetta il primo valore vero di `settings` prima di valutare questi
                // dialog, altrimenti lampeggiano coi valori di default (vedi AppViewModel.isReady).
                if (isReady) {
                    if (!settings.hasSeenDataDisclaimer) {
                        DataDisclaimerDialog(onDismiss = { appViewModel.dismissDataDisclaimer() })
                    } else if (showWhatsNew) {
                        WhatsNewDialog(onDismiss = { appViewModel.dismissWhatsNew() })
                    }
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val permissions = mutableListOf<String>()
        if (!isCameraPermissionGranted(this)) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            startupPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
