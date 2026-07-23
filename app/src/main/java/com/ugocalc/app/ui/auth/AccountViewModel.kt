package com.ugocalc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.auth.AuthResult
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.remote.aws.ApproveResult
import com.ugocalc.app.data.remote.aws.GenerateInviteResult
import com.ugocalc.app.data.remote.aws.InviteStatus
import com.ugocalc.app.data.remote.aws.InviteStatusResult
import com.ugocalc.app.data.remote.aws.ShareClient
import com.ugocalc.app.data.remote.aws.ShareResult
import com.ugocalc.app.data.repository.AuthRepository
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Richiesta di accesso in attesa di approvazione (lato owner)
data class PendingAccessRequest(val token: String, val guestHint: String = "")

sealed class GenerateLinkResult {
    data class Success(val deepLink: String) : GenerateLinkResult()
    data class Error(val message: String) : GenerateLinkResult()
}

data class AccountUiState(
    val currentUser: AuthUser? = null,
    val globalBiasEnabled: Boolean = false,
    val inviteEmail: String = "",
    val inviteResult: InviteResult? = null,
    val pendingWhatsAppEmail: String? = null,
    // nuovo flusso token
    val generateLinkResult: GenerateLinkResult? = null,
    val pendingLinkToShare: String? = null,   // deep link pronto per il share sheet
    val pendingApprovalToken: String? = null,  // token che l'owner deve approvare
    val guestArrived: Boolean = false,         // true quando il guest ha attivato il token
    val approvalResult: Boolean? = null,       // true=approvato, false=negato, null=nessuno
    val isLoading: Boolean = false,
    val newEmail: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val accountActionResult: AccountActionResult? = null,
)

sealed class AccountActionResult {
    data object EmailUpdated : AccountActionResult()
    data object PasswordUpdated : AccountActionResult()
    data object Deleted : AccountActionResult()
    data class Error(val message: String) : AccountActionResult()
}

sealed class InviteResult {
    data class Success(val invitedEmail: String) : InviteResult()
    data class Error(val message: String) : InviteResult()
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shareClient: ShareClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val currentUserFlow = authRepository.currentUserFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState = _uiState.asStateFlow()

    private var ownerPollingJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(globalBiasEnabled = settings.globalBiasEnabled)
            }
        }
    }

    fun setGlobalBiasEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(globalBiasEnabled = enabled)
        viewModelScope.launch {
            settingsRepository.setGlobalBiasEnabled(enabled)
        }
    }

    fun setInviteEmail(email: String) {
        _uiState.value = _uiState.value.copy(inviteEmail = email, inviteResult = null)
    }

    fun sendInvite() {
        val email = _uiState.value.inviteEmail.trim()
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, inviteResult = null)
            val result = shareClient.invite(email)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                inviteResult = when (result) {
                    is ShareResult.Success -> InviteResult.Success(email)
                    is ShareResult.Error -> InviteResult.Error(result.message)
                },
                inviteEmail = if (result is ShareResult.Success) "" else email,
            )
        }
    }

    fun sendInviteViaWhatsApp() {
        val email = _uiState.value.inviteEmail.trim()
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, inviteResult = null)
            val result = shareClient.invite(email)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                inviteResult = when (result) {
                    is ShareResult.Success -> InviteResult.Success(email)
                    is ShareResult.Error -> InviteResult.Error(result.message)
                },
                inviteEmail = if (result is ShareResult.Success) "" else email,
                pendingWhatsAppEmail = if (result is ShareResult.Success) email else null,
            )
        }
    }

    fun clearPendingWhatsAppEmail() {
        _uiState.value = _uiState.value.copy(pendingWhatsAppEmail = null)
    }

    // ─── Nuovo flusso token ──────────────────────────────────────────────────

    fun generateInviteLink() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, generateLinkResult = null)
            when (val result = shareClient.generateInvite()) {
                is GenerateInviteResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        generateLinkResult = GenerateLinkResult.Success(result.deepLink),
                        pendingLinkToShare = result.deepLink,
                        pendingApprovalToken = result.token,
                        guestArrived = false,
                    )
                    startOwnerPolling(result.token)
                }
                is GenerateInviteResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    generateLinkResult = GenerateLinkResult.Error(result.message),
                )
            }
        }
    }

    private fun startOwnerPolling(token: String) {
        ownerPollingJob?.cancel()
        ownerPollingJob = viewModelScope.launch {
            repeat(120) {
                delay(5_000)
                when (val result = shareClient.pollStatus(token)) {
                    is InviteStatusResult.Success -> {
                        if (result.status == InviteStatus.WAITING_APPROVAL) {
                            _uiState.value = _uiState.value.copy(guestArrived = true)
                            return@launch
                        }
                    }
                    is InviteStatusResult.Error -> { /* ignora errori transitori */ }
                }
            }
        }
    }

    fun cancelOwnerPolling() {
        ownerPollingJob?.cancel()
        ownerPollingJob = null
        _uiState.value = _uiState.value.copy(pendingApprovalToken = null, guestArrived = false)
    }

    override fun onCleared() {
        super.onCleared()
        ownerPollingJob?.cancel()
    }

    fun clearPendingLinkToShare() {
        _uiState.value = _uiState.value.copy(pendingLinkToShare = null)
    }

    fun approveRequest(approved: Boolean) {
        val token = _uiState.value.pendingApprovalToken ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (shareClient.approve(token, approved)) {
                is ApproveResult.Ok -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    approvalResult = approved,
                    pendingApprovalToken = if (approved) null else _uiState.value.pendingApprovalToken,
                )
                is ApproveResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun dismissApprovalResult() {
        _uiState.value = _uiState.value.copy(approvalResult = null, pendingApprovalToken = null)
    }

    fun dismissGenerateLinkResult() {
        _uiState.value = _uiState.value.copy(generateLinkResult = null)
    }

    fun dismissInviteResult() {
        _uiState.value = _uiState.value.copy(inviteResult = null)
    }

    fun setNewEmail(value: String) { _uiState.value = _uiState.value.copy(newEmail = value) }
    fun setOldPassword(value: String) { _uiState.value = _uiState.value.copy(oldPassword = value) }
    fun setNewPassword(value: String) { _uiState.value = _uiState.value.copy(newPassword = value) }
    fun setConfirmNewPassword(value: String) { _uiState.value = _uiState.value.copy(confirmNewPassword = value) }
    fun dismissAccountActionResult() { _uiState.value = _uiState.value.copy(accountActionResult = null) }

    fun updateEmail() {
        val email = _uiState.value.newEmail.trim()
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.updateEmail(email)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                newEmail = "",
                accountActionResult = when (result) {
                    is AuthResult.Success -> AccountActionResult.EmailUpdated
                    else -> AccountActionResult.Error((result as? AuthResult.Error)?.message ?: "Errore")
                },
            )
        }
    }

    fun updatePassword() {
        val s = _uiState.value
        if (s.newPassword != s.confirmNewPassword) {
            _uiState.value = s.copy(accountActionResult = AccountActionResult.Error("Le password non coincidono"))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.updatePassword(s.oldPassword, s.newPassword)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                oldPassword = "",
                newPassword = "",
                confirmNewPassword = "",
                accountActionResult = when (result) {
                    is AuthResult.Success -> AccountActionResult.PasswordUpdated
                    else -> AccountActionResult.Error((result as? AuthResult.Error)?.message ?: "Errore")
                },
            )
        }
    }

    fun deleteUser(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.deleteUser()
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result is AuthResult.Success) {
                onDeleted()
            } else {
                _uiState.value = _uiState.value.copy(
                    accountActionResult = AccountActionResult.Error(
                        (result as? AuthResult.Error)?.message ?: "Eliminazione fallita"
                    )
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
