package com.ugocalc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.AppViewModel
import com.ugocalc.app.ui.components.AppUpdateDialog
import com.ugocalc.app.ui.components.DataDisclaimerDialog
import com.ugocalc.app.ui.components.SplashScreen
import com.ugocalc.app.ui.components.WhatsNewDialog
import com.ugocalc.app.ui.components.isDarkSurfaceTheme
import com.ugocalc.app.ui.navigation.Routes
import com.ugocalc.app.ui.navigation.UgoCalcNavHost
import com.ugocalc.app.ui.theme.AppFontFamily
import com.ugocalc.app.ui.theme.UgoCalcTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject @Named("aws_base_url") lateinit var awsBaseUrl: String
    private val appViewModel: AppViewModel by viewModels()

    // Token estratto dal deep link ugocalc://invite?token=XXX — condiviso con la Compose UI
    private var pendingInviteToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Controlla se l'app è stata aperta tramite il deep link ugocalc://invite
        pendingInviteToken = intent?.data
            ?.takeIf { it.scheme == "ugocalc" && it.host == "invite" }
            ?.getQueryParameter("token")

        setContent {
            val isReady by appViewModel.isReady.collectAsState()
            val settings by appViewModel.settings.collectAsState()
            val showWhatsNew by appViewModel.showWhatsNew.collectAsState()
            val appUpdateStatus by appViewModel.appUpdateStatus.collectAsState()

            val isLoggedIn by authRepository.isLoggedInFlow.collectAsState(initial = null)
            val awsEnabled = awsBaseUrl.isNotBlank()

            val isInitializing = !isReady || isLoggedIn == null
            val view = LocalView.current

            if (isInitializing) {
                val systemDark = isSystemInDarkTheme()
                if (!view.isInEditMode) {
                    SideEffect {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.isAppearanceLightStatusBars = !systemDark
                        insetsController.isAppearanceLightNavigationBars = !systemDark
                    }
                }
                SplashScreen(isLoading = true)
            } else {
                // Modalità Casa/Ristorante: seleziona la palette personalizzata e i colori del tema.
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
                    val useDark = isDarkSurfaceTheme()
                    if (!view.isInEditMode) {
                        SideEffect {
                            val insetsController = WindowCompat.getInsetsController(window, view)
                            insetsController.isAppearanceLightStatusBars = !useDark
                            insetsController.isAppearanceLightNavigationBars = !useDark
                        }
                    }

                    // Local-first startup: blocca su AUTH solo se un deep link di invito richiede Cognito;
                    // altrimenti onboarding al primo avvio oppure direttamente HOME.
                    val startDestination = remember {
                        when {
                            pendingInviteToken != null && awsEnabled && isLoggedIn != true -> Routes.AUTH
                            !settings.hasCompletedOnboarding -> Routes.ONBOARDING
                            else -> Routes.HOME
                        }
                    }

                    val navController = rememberNavController()
                    UgoCalcNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        isCognitoAuthenticated = isLoggedIn == true,
                        pendingInviteToken = pendingInviteToken,
                        onInviteTokenConsumed = { pendingInviteToken = null },
                        onCompleteOnboarding = appViewModel::completeOnboarding,
                        resolveCameraModels = appViewModel::resolveCameraModels,
                    )

                    appUpdateStatus?.let { status ->
                        AppUpdateDialog(status = status, onDismiss = { appViewModel.dismissAppUpdate() })
                    }
                    if (appUpdateStatus?.mandatory != true) {
                        if (settings.hasCompletedOnboarding && !settings.hasSeenDataDisclaimer) {
                            DataDisclaimerDialog(onDismiss = { appViewModel.dismissDataDisclaimer() })
                        } else if (settings.hasCompletedOnboarding && showWhatsNew) {
                            WhatsNewDialog(onDismiss = { appViewModel.dismissWhatsNew() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteToken = intent.data
            ?.takeIf { it.scheme == "ugocalc" && it.host == "invite" }
            ?.getQueryParameter("token")
    }
}
