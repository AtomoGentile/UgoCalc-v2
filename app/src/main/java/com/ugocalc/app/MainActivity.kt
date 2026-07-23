package com.ugocalc.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.AppViewModel
import com.ugocalc.app.ui.components.AppUpdateDialog
import com.ugocalc.app.ui.components.DataDisclaimerDialog
import com.ugocalc.app.ui.components.SplashScreen
import com.ugocalc.app.ui.components.WhatsNewDialog
import com.ugocalc.app.ui.navigation.Routes
import com.ugocalc.app.ui.navigation.UgoCalcNavHost
import com.ugocalc.app.ui.theme.AppFontFamily
import com.ugocalc.app.ui.theme.UgoCalcTheme
import com.ugocalc.app.util.isCameraPermissionGranted
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    private val appViewModel: AppViewModel by viewModels()

    // Token estratto dal deep link ugocalc://invite?token=XXX — condiviso con la Compose UI
    private var pendingInviteToken by mutableStateOf<String?>(null)

    private val startupPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Controlla se l'app è stata aperta tramite il deep link ugocalc://invite
        pendingInviteToken = intent?.data
            ?.takeIf { it.scheme == "ugocalc" && it.host == "invite" }
            ?.getQueryParameter("token")
        requestStartupPermissions()
        setContent {
            val isReady by appViewModel.isReady.collectAsState()
            val settings by appViewModel.settings.collectAsState()
            val showWhatsNew by appViewModel.showWhatsNew.collectAsState()
            val appUpdateStatus by appViewModel.appUpdateStatus.collectAsState()

            val isLoggedIn by authRepository.isLoggedInFlow.collectAsState(initial = null)

            val isInitializing = !isReady || isLoggedIn == null

            if (isInitializing) {
                SplashScreen(isLoading = true)
            } else {
                // Icona Casa/Ristorante in Homepage (§ richiesta utente): sceglie quale delle due liste
                // di colori personalizzati in Accessibilità applicare a tutta l'app.
                val isRistorante = settings.activeSessionMode == SessionMode.RISTORANTE
                val appFontFamily by remember(settings.fontFamilyName) {
                    derivedStateOf { settings.fontFamilyName?.let { runCatching { AppFontFamily.valueOf(it) }.getOrNull() } }
                }
                UgoCalcTheme(
                    appTheme = settings.theme,
                    isRistorante = isRistorante,
                    textScale = settings.textScale,
                    primaryColor = (if (isRistorante) settings.primaryColorRistoranteArgb else settings.primaryColorArgb)?.let { Color(it) },
                    secondaryColor = (if (isRistorante) settings.secondaryColorRistoranteArgb else settings.secondaryColorArgb)?.let { Color(it) },
                    tertiaryColor = (if (isRistorante) settings.tertiaryColorRistoranteArgb else settings.tertiaryColorArgb)?.let { Color(it) },
                    backgroundColor = (if (isRistorante) settings.backgroundColorRistoranteArgb else settings.backgroundColorArgb)?.let { Color(it) },
                    errorColor = (if (isRistorante) settings.errorColorRistoranteArgb else settings.errorColorArgb)?.let { Color(it) },
                    fontFamily = appFontFamily?.fontFamily,
                    fontWeight = appFontFamily?.fontWeight,
                    fontColor = settings.fontColorArgb?.let { Color(it) },
                ) {
                    val navController = rememberNavController()
                    UgoCalcNavHost(
                        navController = navController,
                        startAuthenticated = isLoggedIn ?: false,
                        pendingInviteToken = pendingInviteToken,
                        onInviteTokenConsumed = { pendingInviteToken = null },
                    )

                    appUpdateStatus?.let { status ->
                        AppUpdateDialog(status = status, onDismiss = { appViewModel.dismissAppUpdate() })
                    }
                    if (appUpdateStatus?.mandatory != true) {
                        if (!settings.hasSeenDataDisclaimer) {
                            DataDisclaimerDialog(onDismiss = { appViewModel.dismissDataDisclaimer() })
                        } else if (showWhatsNew) {
                            WhatsNewDialog(onDismiss = { appViewModel.dismissWhatsNew() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteToken = intent.data
            ?.takeIf { it.scheme == "ugocalc" && it.host == "invite" }
            ?.getQueryParameter("token")
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
