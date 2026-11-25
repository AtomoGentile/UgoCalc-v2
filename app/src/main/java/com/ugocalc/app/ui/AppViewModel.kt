package com.ugocalc.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.data.remote.github.ApkDownloadState
import com.ugocalc.app.data.remote.github.ApkInstallLaunchResult
import com.ugocalc.app.data.remote.github.ApkUpdateDownloader
import com.ugocalc.app.data.repository.AppUpdateCheckResult
import com.ugocalc.app.data.repository.AppUpdateRepository
import com.ugocalc.app.data.repository.AppUpdateStatus
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelCatalog
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.domain.model.Changelog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val apkUpdateDownloader: ApkUpdateDownloader,
    private val tableRepository: TableRepository,
    private val sessionStore: AnalysisSessionStore,
    @Named("no_auth") private val okHttpClient: OkHttpClient,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /**
     * Popup "Novità" (§ richiesta utente): confronta `versionCode` (incrementato ad ogni commit),
     * non `versionName` (fisso a "1.0.0", vedi [[ugocalc-workflow]]). `0` significa "mai avviata
     * prima": in quel caso non mostriamo nulla qui, se ne occupa l'onboarding / disclaimer di primo avvio.
     * Richiede anche [Changelog.ENTRIES] non vuoto: finché non esce il primo aggiornamento dopo
     * la 1.0.0 non c'è nulla da mostrare (evita anche un crash su `ENTRIES.first()`).
     */
    val showWhatsNew: StateFlow<Boolean> = settings
        .map { it.lastSeenVersionCode != 0 && it.lastSeenVersionCode < BuildConfig.VERSION_CODE && Changelog.ENTRIES.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Bug fix: [settings] parte con i valori di default di [UserSettings] finché DataStore non
     * emette il valore vero (asincrono), quindi per un istante `hasSeenDataDisclaimer = false` per
     * qualsiasi utente — anche chi l'aveva già chiuso — causando un lampeggio del disclaimer/popup
     * Novità ad ogni avvio. [isReady] resta `false` finché non arriva la prima emissione reale e
     * il seed iniziale della tabella CREA non è completato.
     */
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _appUpdateStatus = MutableStateFlow<AppUpdateStatus?>(null)
    val appUpdateStatus: StateFlow<AppUpdateStatus?> = _appUpdateStatus

    val apkDownloadState: StateFlow<ApkDownloadState> = apkUpdateDownloader.state

    init {
        viewModelScope.launch {
            val first = settingsRepository.settings.first()
            if (first.lastSeenVersionCode == 0) {
                settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    tableRepository.ensureStarterTableSeeded(settingsRepository)
                }
            }
            _isReady.value = true
        }
        viewModelScope.launch {
            when (val result = appUpdateRepository.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> _appUpdateStatus.value = result.status
                else -> Unit
            }
        }
        // Auto-sync silenzioso delle capacità reali `thinking` da Google AI Studio per chi aveva già
        // salvato la chiave Gemini in versioni precedenti (così gli effort reali sono subito accurati).
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncGeminiThinkingMetadataIfMissing() }
        }
    }

    private fun syncGeminiThinkingMetadataIfMissing() {
        val geminiKey = settingsRepository.getApiKey(AiProviderType.GEMINI)?.trim().orEmpty()
        if (geminiKey.isBlank()) return
        if (settingsRepository.getGeminiThinkingCapabilities().isNotEmpty()) return

        val allIds = mutableListOf<String>()
        val thinkingCaps = linkedMapOf<String, Boolean>()
        val excludedTokens = listOf("embedding", "imagen", "veo", "tts", "aqa", "bison")
        var pageToken: String? = null
        var pages = 0
        do {
            val url = buildString {
                append("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", geminiKey)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val arr = root.optJSONArray("models") ?: return
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val methods = obj.optJSONArray("supportedGenerationMethods")
                    val supportsGenerate = methods != null && (0 until methods.length()).any {
                        methods.getString(it) == "generateContent"
                    }
                    if (!supportsGenerate) continue
                    val name = obj.optString("name").removePrefix("models/").trim()
                    val lower = name.lowercase()
                    if (lower.startsWith("gemini-") && excludedTokens.none { it in lower }) {
                        allIds.add(name)
                        if (obj.has("thinking") && !obj.isNull("thinking")) {
                            thinkingCaps[name] = obj.optBoolean("thinking")
                        }
                    }
                }
                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
            }
            pages++
        } while (!pageToken.isNullOrBlank() && pages < 5)

        val distinctIds = allIds.distinct()
        if (distinctIds.isNotEmpty()) {
            settingsRepository.setGeminiAvailableModels(distinctIds)
            settingsRepository.setGeminiThinkingCapabilities(thinkingCaps)
            AiModelCatalog.setGeminiModels(distinctIds, thinkingCaps)
        }
    }

    fun startApkDownload(status: AppUpdateStatus) {
        apkUpdateDownloader.startDownload(
            version = status.latestVersion,
            apkUrl = status.apkUrl,
            expectedSizeBytes = status.apkSizeBytes,
        )
    }

    fun cancelApkDownload() {
        apkUpdateDownloader.cancelDownload()
    }

    fun launchApkInstall(context: Context, apkFile: File): ApkInstallLaunchResult =
        apkUpdateDownloader.launchInstall(context, apkFile)

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
            settingsRepository.setHasSeenDataDisclaimer(true)
        }
    }

    fun dismissDataDisclaimer() {
        viewModelScope.launch { settingsRepository.setHasSeenDataDisclaimer(true) }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch { settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE) }
    }

    fun dismissAppUpdate() {
        val status = _appUpdateStatus.value ?: return
        if (!status.mandatory) _appUpdateStatus.value = null
    }

    /**
     * Risolve i modelli per la schermata fotocamera evitando collisioni di `id` tra provider
     * diversi (es. `gemini-2.5-flash` presente sia su [AiProviderType.GEMINI] che su
     * [AiProviderType.VERTEX_AI]). Usa direttamente la sessione attiva se popolata; in fallback
     * preferisce i provider effettivamente configurati.
     */
    fun resolveCameraModels(modelIds: List<String>): List<AiModel> {
        val fromSession = sessionStore.session.value.selectedModels
        if (fromSession.isNotEmpty()) {
            return fromSession
        }
        val idSet = modelIds.toSet()
        val exactUidMatches = AiModelCatalog.ALL.filter { it.uid in idSet }
        val remainingIds = idSet - exactUidMatches.map { it.uid }.toSet()
        val legacyMatches = if (remainingIds.isEmpty()) {
            emptyList()
        } else {
            val candidates = AiModelCatalog.ALL.filter { it.id in remainingIds }
            val configured = candidates.filter { isProviderConfigured(it.provider) }
            if (configured.isNotEmpty()) configured else candidates
        }
        return (exactUidMatches + legacyMatches).distinctBy { it.uid }
    }

    private fun isProviderConfigured(provider: AiProviderType): Boolean = when (provider) {
        AiProviderType.BEDROCK_CLAUDE,
        AiProviderType.BEDROCK_GPT -> settingsRepository.hasBedrockCredentials()
        AiProviderType.BEDROCK_IAM_CLAUDE,
        AiProviderType.BEDROCK_IAM_GPT,
        AiProviderType.BEDROCK_IAM_AMAZON -> settingsRepository.hasBedrockIamCredentials()
        AiProviderType.VERTEX_AI -> settingsRepository.hasVertexAiCredentials()
        else -> settingsRepository.getApiKey(provider) != null
    }
}
