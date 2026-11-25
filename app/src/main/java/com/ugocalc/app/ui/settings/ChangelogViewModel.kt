package com.ugocalc.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.github.ApkDownloadState
import com.ugocalc.app.data.remote.github.ApkInstallLaunchResult
import com.ugocalc.app.data.remote.github.ApkUpdateDownloader
import com.ugocalc.app.data.repository.AppUpdateInfo
import com.ugocalc.app.data.repository.UpdateCheckResult
import com.ugocalc.app.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class ChangelogUiState(
    val checkingForUpdate: Boolean = false,
    val updateResult: UpdateCheckResult? = null,
)

/** Impostazioni > Software e aggiornamenti: controllo aggiornamenti + download e installazione APK in-app. */
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val apkUpdateDownloader: ApkUpdateDownloader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangelogUiState())
    val uiState: StateFlow<ChangelogUiState> = _uiState

    val apkDownloadState: StateFlow<ApkDownloadState> = apkUpdateDownloader.state

    fun checkForUpdate() {
        _uiState.value = _uiState.value.copy(checkingForUpdate = true, updateResult = null)
        viewModelScope.launch {
            val result = updateRepository.checkForUpdate()
            _uiState.value = _uiState.value.copy(checkingForUpdate = false, updateResult = result)
        }
    }

    fun startApkDownload(info: AppUpdateInfo) {
        val url = info.apkDownloadUrl ?: return
        apkUpdateDownloader.startDownload(
            version = info.version,
            apkUrl = url,
            expectedSizeBytes = info.apkSizeBytes,
        )
    }

    fun cancelApkDownload() {
        apkUpdateDownloader.cancelDownload()
    }

    fun launchApkInstall(context: Context, apkFile: File): ApkInstallLaunchResult =
        apkUpdateDownloader.launchInstall(context, apkFile)
}
