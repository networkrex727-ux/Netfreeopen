package com.example.ui.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.JoinResult
import com.example.data.repository.ShareTokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.data.model.ShareToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GuestState {
    IDLE,
    VALIDATING,
    SUCCESS,
    ERROR
}

@HiltViewModel
class GuestViewModel @Inject constructor(
    private val shareTokenRepository: ShareTokenRepository
) : ViewModel() {

    private val _tokenInput = MutableStateFlow("")
    val tokenInput: StateFlow<String> = _tokenInput.asStateFlow()

    private val _state = MutableStateFlow(GuestState.IDLE)
    val state: StateFlow<GuestState> = _state.asStateFlow()

    val activeSessions = shareTokenRepository.getActiveSharingTokens()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun connectToSession(token: String) {
        _tokenInput.value = token.uppercase()
        connect()
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _joinSuccessDetails = MutableStateFlow<Pair<String, Long>?>(null)
    val joinSuccessDetails: StateFlow<Pair<String, Long>?> = _joinSuccessDetails.asStateFlow()

    fun onTokenChanged(token: String) {
        if (token.length <= 6) {
            _tokenInput.value = token.uppercase()
        }
    }

    fun onQRScanned(result: String) {
        val sanitized = result.trim().uppercase()
        if (sanitized.length == 6) {
            _tokenInput.value = sanitized
            connect()
        } else {
            _errorMessage.value = "Invalid QR Code format"
        }
    }

    fun clearState() {
        _state.value = GuestState.IDLE
        _joinSuccessDetails.value = null
        _errorMessage.value = null
    }

    fun connect() {
        val token = _tokenInput.value.trim()
        if (token.length != 6) {
            _errorMessage.value = "Please enter a valid 6-digit token"
            return
        }

        viewModelScope.launch {
            _state.value = GuestState.VALIDATING
            _errorMessage.value = null
            try {
                when (val result = shareTokenRepository.validateAndJoin(token)) {
                    is JoinResult.NotFound -> {
                        _state.value = GuestState.ERROR
                        _errorMessage.value = "Token not found"
                    }
                    is JoinResult.Expired -> {
                        _state.value = GuestState.ERROR
                        _errorMessage.value = "Token expired. Please ask Host to generate a new one."
                    }
                    is JoinResult.AlreadyUsed -> {
                        _state.value = GuestState.ERROR
                        _errorMessage.value = "This token is already in use by another device."
                    }
                    is JoinResult.LimitReached -> {
                        _state.value = GuestState.ERROR
                        _errorMessage.value = "Session bandwidth limit has already been reached."
                    }
                    is JoinResult.Success -> {
                        _joinSuccessDetails.value = Pair(result.hostUid, result.bwLimitMB)
                        _state.value = GuestState.SUCCESS
                    }
                }
            } catch (e: Exception) {
                _state.value = GuestState.ERROR
                _errorMessage.value = e.message ?: "Connection validation failed"
            }
        }
    }
}
