package com.ugocalc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.auth.AuthResult
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: AuthUser) : AuthState()
    data object LocalMode : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @Named("aws_base_url") awsBaseUrl: String,
) : ViewModel() {

    val isAwsConfigured: Boolean = awsBaseUrl.isNotBlank()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    val isLoggedInFlow = authRepository.isLoggedInFlow
    val currentUserFlow = authRepository.currentUserFlow

    fun signUp(email: String, password: String) = authenticate { authRepository.signUp(email, password) }

    fun signIn(email: String, password: String) = authenticate { authRepository.signIn(email, password) }

    private fun authenticate(call: suspend () -> AuthResult) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _authState.value = when (val result = call()) {
                is AuthResult.Success -> AuthState.Success(result.user)
                is AuthResult.Error -> AuthState.Error(result.message)
            }
        }
    }
    fun signOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.signOut()
            _authState.value = AuthState.Idle
        }
    }

    fun continueInLocalMode() {
        viewModelScope.launch {
            authRepository.enableLocalMode()
            _authState.value = AuthState.LocalMode
        }
    }

    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }
}
