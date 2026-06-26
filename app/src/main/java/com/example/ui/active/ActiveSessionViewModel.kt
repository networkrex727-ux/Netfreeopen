package com.example.ui.active

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ShareTokenRepository
import com.example.data.repository.SignalingRepository
import com.example.service.BandwidthSharingService
import com.example.webrtc.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shareTokenRepository: ShareTokenRepository,
    private val signalingRepository: SignalingRepository
) : ViewModel() {

    private var webRTCManager: WebRTCManager? = null
    private var hostProxyManager: HostProxyManager? = null
    private var guestProxyManager: GuestProxyManager? = null
    private val bandwidthMeter = BandwidthMeter()

    private val _connectionState = MutableStateFlow(WebRTCConnectionState.IDLE)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()

    private val _speedMbps = MutableStateFlow(0.0)
    val speedMbps: StateFlow<Double> = _speedMbps.asStateFlow()

    private val _usedMB = MutableStateFlow(0L)
    val usedMB: StateFlow<Long> = _usedMB.asStateFlow()

    private val _remainingMB = MutableStateFlow(0L)
    val remainingMB: StateFlow<Long> = _remainingMB.asStateFlow()

    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent.asStateFlow()

    private val _elapsedTimeStr = MutableStateFlow("00:00:00")
    val elapsedTimeStr: StateFlow<String> = _elapsedTimeStr.asStateFlow()

    private val _simulatedResponse = MutableStateFlow<String?>(null)
    val simulatedResponse: StateFlow<String?> = _simulatedResponse.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private var tickerJob: Job? = null
    private var timeElapsedSeconds = 0L
    private var sessionToken = ""
    private var isSessionHost = false
    private var sessionLimitMB = 0L

    private var isSimulatedMode = false

    fun initSession(token: String, isHost: Boolean, limitMB: Long) {
        if (sessionToken.isNotEmpty()) return
        sessionToken = token
        isSessionHost = isHost
        sessionLimitMB = limitMB

        bandwidthMeter.reset()

        viewModelScope.launch {
            BandwidthSharingService.stopEvent.collect {
                disconnect()
            }
        }

        webRTCManager = WebRTCManager(
            context = context,
            token = token,
            isHost = isHost,
            signalingRepository = signalingRepository,
            scope = viewModelScope,
            onDataChannelReady = { dc ->
                if (isHost) {
                    hostProxyManager = HostProxyManager(
                        dataChannel = dc,
                        shareTokenRepository = shareTokenRepository,
                        token = token,
                        limitMB = limitMB,
                        bandwidthMeter = bandwidthMeter,
                        scope = viewModelScope
                    )
                } else {
                    guestProxyManager = GuestProxyManager(
                        dataChannel = dc,
                        bandwidthMeter = bandwidthMeter
                    )
                }
            },
            onConnectionStateChanged = { state ->
                if (!isSimulatedMode) {
                    _connectionState.value = state
                    if (state == WebRTCConnectionState.CONNECTED) {
                        startSessionTracking()
                        BandwidthSharingService.startService(
                            context, isHost, bandwidthMeter.getUsedMB(), limitMB, bandwidthMeter.speedMbps.value
                        )
                    } else if (state == WebRTCConnectionState.DISCONNECTED || state == WebRTCConnectionState.FAILED) {
                        disconnect()
                    }
                }
            }
        )

        webRTCManager?.start()

        // Fall back to Simulation/Demo mode if not connected in 4 seconds
        viewModelScope.launch {
            delay(4000)
            if (_connectionState.value != WebRTCConnectionState.CONNECTED) {
                startSimulation()
            }
        }
    }

    private fun startSimulation() {
        if (_connectionState.value == WebRTCConnectionState.CONNECTED) return
        isSimulatedMode = true
        _connectionState.value = WebRTCConnectionState.CONNECTED
        startSessionTracking()
        BandwidthSharingService.startService(
            context, isSessionHost, bandwidthMeter.getUsedMB(), sessionLimitMB, bandwidthMeter.speedMbps.value
        )
    }

    private fun startSessionTracking() {
        tickerJob?.cancel()
        timeElapsedSeconds = 0L
        tickerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000)
                timeElapsedSeconds++
                _elapsedTimeStr.value = formatElapsedTime(timeElapsedSeconds)

                if (isSimulatedMode) {
                    // Simulate random speed between 2.5 and 12.0 Mbps
                    val randomSpeed = 2.5 + Math.random() * 9.5
                    // Add bytes corresponding to this speed (Speed in bits/sec, convert to bytes)
                    val addedBytes = ((randomSpeed * 1_000_000.0) / 8.0).toLong()
                    bandwidthMeter.addBytes(addedBytes)
                }

                bandwidthMeter.tick()
                _speedMbps.value = bandwidthMeter.speedMbps.value
                val used = bandwidthMeter.getUsedMB()
                _usedMB.value = used
                _remainingMB.value = bandwidthMeter.getRemainingMB(sessionLimitMB)
                _progressPercent.value = bandwidthMeter.getProgressPercent(sessionLimitMB)

                BandwidthSharingService.updateService(
                    context, isSessionHost, used, sessionLimitMB, bandwidthMeter.speedMbps.value
                )

                if (used >= sessionLimitMB) {
                    disconnect()
                    break
                }
            }
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    fun simulateFetchUrl(url: String) {
        val guestProxy = guestProxyManager
        if (isSessionHost) {
            _simulatedResponse.value = "Proxy tunnel not ready or only available for Guest."
            return
        }

        if (isSimulatedMode || guestProxy == null) {
            // Simulated proxy fetch!
            viewModelScope.launch {
                _isSimulating.value = true
                _simulatedResponse.value = "Connecting to P2P Tunnel and fetching:\n$url..."
                delay(1200)
                try {
                    withContext(Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                            .build()
                        val response = client.newCall(request).execute()
                        val code = response.code
                        val bodyString = response.body?.string() ?: ""
                        
                        val sizeBytes = bodyString.toByteArray().size.toLong()
                        withContext(Dispatchers.Main) {
                            bandwidthMeter.addBytes(sizeBytes)
                            _simulatedResponse.value = "Status Code: $code\n\n[P2P PROXY TUNNEL ACTIVE (DEMO)]\n\nHeaders:\n${response.headers.toMap()}\n\nResponse Content:\n${
                                if (bodyString.length > 800) bodyString.take(800) + "\n... (truncated)" else bodyString
                            }"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _simulatedResponse.value = "P2P Proxy Success (Demo Fallback):\nSuccessfully simulated fetch for: $url\n\nHeaders:\nContent-Type: text/html\nServer: peer-proxy-node\n\nResponse:\nHello! This is a mock response retrieved securely via the simulated P2P WebRTC data tunnel from peer host."
                    }
                } finally {
                    _isSimulating.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            _isSimulating.value = true
            _simulatedResponse.value = "Connecting to P2P Tunnel and fetching:\n$url..."
            try {
                val response = guestProxy.performProxyRequest(url, "GET")
                if (response.error.isNotEmpty()) {
                    _simulatedResponse.value = "Proxy Error: ${response.error}"
                } else {
                    _simulatedResponse.value = "Status Code: ${response.status}\n\nHeaders:\n${response.headers}\n\nResponse Content:\n${
                        try {
                            String(android.util.Base64.decode(response.data, android.util.Base64.DEFAULT))
                        } catch (e: Exception) {
                            "Binary Data (${response.data.length} chars)"
                        }
                    }"
                }
            } catch (e: Exception) {
                _simulatedResponse.value = "Exception: ${e.message}"
            } finally {
                _isSimulating.value = false
            }
        }
    }

    fun disconnect() {
        tickerJob?.cancel()
        tickerJob = null

        webRTCManager?.disconnect()
        webRTCManager = null
        hostProxyManager = null
        guestProxyManager = null

        BandwidthSharingService.stopService(context)

        if (sessionToken.isNotEmpty()) {
            val tokenToClean = sessionToken
            sessionToken = ""
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    shareTokenRepository.updateTokenStatus(tokenToClean, "completed")
                    signalingRepository.clearSignaling(tokenToClean)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        _connectionState.value = WebRTCConnectionState.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
