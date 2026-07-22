package com.ugocalc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.remote.aws.ShareClient
import com.ugocalc.app.data.remote.aws.ShareResult
import com.ugocalc.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val currentUser: AuthUser? = null,
    val globalBiasEnabled: Boolean = false,
    val inviteEmail: String = "",
    val inviteResult: InviteResult? = null,
    val isLoading: Boolean = false,
)

sealed class InviteResult {
    data class Success(val invitedEmail: String) : InviteResult()
    data class Error(val message: String) : InviteResult()
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shareClient: ShareClient,
) : ViewModel() {

    val currentUserFlow = authRepository.currentUserFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    fun setGlobalBiasEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(globalBiasEnabled = enabled)
        // Persisted server-side: POST /account with globalBiasEnabled flag (future network call)
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

    fun dismissInviteResult() {
        _uiState.value = _uiState.value.copy(inviteResult = null)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
