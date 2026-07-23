package com.ugocalc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.ugocalc.app.data.auth.AuthResult
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: AuthUser) : AuthState()
    data class NeedsConfirmation(val email: String) : AuthState()
    data object ConfirmationComplete : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    val isLoggedInFlow = authRepository.isLoggedInFlow
    val currentUserFlow = authRepository.currentUserFlow

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUp(email, password)
            _authState.value = when (result) {
                is AuthResult.Success -> AuthState.Success(result.user)
                is AuthResult.NeedsConfirmation -> AuthState.NeedsConfirmation(result.email)
                is AuthResult.Error -> AuthState.Error(result.message)
            }
        }
    }

    fun confirmSignUp(email: String, code: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.confirmSignUp(email, code)
            _authState.value = when (result) {
                is AuthResult.Success -> AuthState.ConfirmationComplete
                is AuthResult.NeedsConfirmation -> AuthState.NeedsConfirmation(result.email)
                is AuthResult.Error -> AuthState.Error(result.message)
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(activity)
            _authState.value = when (result) {
                is AuthResult.Success -> AuthState.Success(result.user)
                is AuthResult.NeedsConfirmation -> AuthState.NeedsConfirmation(result.email)
                is AuthResult.Error -> AuthState.Error(result.message)
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signIn(email, password)
            _authState.value = when (result) {
                is AuthResult.Success -> AuthState.Success(result.user)
                is AuthResult.NeedsConfirmation -> AuthState.NeedsConfirmation(result.email)
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

    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }
}
