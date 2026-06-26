package com.example.ui.host

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ShareTokenRepository
import com.example.util.QRCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HostState {
    IDLE,
    GENERATING,
    TOKEN_GENERATED,
    WAITING_FOR_GUEST,
    CONNECTED,
    EXPIRED,
    ERROR
}

@HiltViewModel
class HostViewModel @Inject constructor(
    private val shareTokenRepository: ShareTokenRepository
) : ViewModel() {

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    val qrCode: StateFlow<Bitmap?> = _qrCode.asStateFlow()

    private val _state = MutableStateFlow(HostState.IDLE)
    val state: StateFlow<HostState> = _state.asStateFlow()

    private val _selectedBandwidthMB = MutableStateFlow(1024L) // default 1 GB
    val selectedBandwidthMB: StateFlow<Long> = _selectedBandwidthMB.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _timeLeftSeconds = MutableStateFlow(600)
    val timeLeftSeconds: StateFlow<Int> = _timeLeftSeconds.asStateFlow()

    private var tokenListenJob: Job? = null
    private var countdownJob: Job? = null

    fun selectBandwidth(mb: Long) {
        _selectedBandwidthMB.value = mb
    }

    fun generateToken() {
        viewModelScope.launch {
            _state.value = HostState.GENERATING
            _errorMessage.value = null
            _timeLeftSeconds.value = 600
            try {
                val generatedToken = shareTokenRepository.createShareToken(_selectedBandwidthMB.value)
                _token.value = generatedToken
                
                val bitmap = QRCodeUtil.generateQRCode(generatedToken, 400)
                _qrCode.value = bitmap
                
                _state.value = HostState.TOKEN_GENERATED
                
                startWaitingForGuest(generatedToken)
                startCountdown(generatedToken)
            } catch (e: Exception) {
                _state.value = HostState.ERROR
                _errorMessage.value = e.message ?: "Failed to generate token"
            }
        }
    }

    private fun startCountdown(generatedToken: String) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_timeLeftSeconds.value > 0) {
                kotlinx.coroutines.delay(1000)
                _timeLeftSeconds.value -= 1
                if (_state.value == HostState.CONNECTED) {
                    break
                }
            }
            if (_timeLeftSeconds.value == 0 && _state.value == HostState.WAITING_FOR_GUEST) {
                _state.value = HostState.EXPIRED
                shareTokenRepository.updateTokenStatus(generatedToken, "expired")
                tokenListenJob?.cancel()
            }
        }
    }

    private fun startWaitingForGuest(generatedToken: String) {
        _state.value = HostState.WAITING_FOR_GUEST
        tokenListenJob?.cancel()
        tokenListenJob = viewModelScope.launch {
            shareTokenRepository.listenToToken(generatedToken).collect { shareToken ->
                if (shareToken != null) {
                    if (shareToken.status == "connected" && shareToken.guestUid != null) {
                        _state.value = HostState.CONNECTED
                        countdownJob?.cancel()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tokenListenJob?.cancel()
        countdownJob?.cancel()
    }
}
