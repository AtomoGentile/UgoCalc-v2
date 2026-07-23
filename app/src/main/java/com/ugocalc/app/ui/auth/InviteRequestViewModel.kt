package com.ugocalc.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.remote.aws.InviteStatus
import com.ugocalc.app.data.remote.aws.InviteStatusResult
import com.ugocalc.app.data.remote.aws.RequestAccessResult
import com.ugocalc.app.data.remote.aws.ShareClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InviteRequestState {
    data object Idle : InviteRequestState()
    data object Requesting : InviteRequestState()
    data object WaitingApproval : InviteRequestState()
    data object Approved : InviteRequestState()
    data object Denied : InviteRequestState()
    data class Error(val message: String) : InviteRequestState()
}

@HiltViewModel
class InviteRequestViewModel @Inject constructor(
    private val shareClient: ShareClient,
) : ViewModel() {

    private val _state = MutableStateFlow<InviteRequestState>(InviteRequestState.Idle)
    val state = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun handleInviteToken(token: String) {
        if (_state.value != InviteRequestState.Idle) return
        viewModelScope.launch {
            _state.value = InviteRequestState.Requesting
            when (val result = shareClient.requestAccess(token)) {
                is RequestAccessResult.WaitingApproval -> {
                    _state.value = InviteRequestState.WaitingApproval
                    startPolling(token)
                }
                is RequestAccessResult.Error -> {
                    _state.value = InviteRequestState.Error(result.message)
                }
            }
        }
    }

    private fun startPolling(token: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(120) { // massimo 10 minuti (5s * 120)
                delay(5_000)
                when (val result = shareClient.pollStatus(token)) {
                    is InviteStatusResult.Success -> when (result.status) {
                        InviteStatus.APPROVED -> {
                            _state.value = InviteRequestState.Approved
                            return@launch
                        }
                        InviteStatus.DENIED -> {
                            _state.value = InviteRequestState.Denied
                            return@launch
                        }
                        else -> { /* continua a fare polling */ }
                    }
                    is InviteStatusResult.Error -> { /* ignora errori transitori */ }
                }
            }
            // timeout scaduto
            if (_state.value == InviteRequestState.WaitingApproval) {
                _state.value = InviteRequestState.Error("Timeout: nessuna risposta ricevuta")
            }
        }
    }

    fun reset() {
        pollingJob?.cancel()
        _state.value = InviteRequestState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
