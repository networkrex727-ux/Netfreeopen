package com.example.webrtc

import android.content.Context
import com.example.data.repository.SignalingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.*

enum class WebRTCConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

class WebRTCManager(
    private val context: Context,
    private val token: String,
    private val isHost: Boolean,
    private val signalingRepository: SignalingRepository,
    private val scope: CoroutineScope,
    private val onDataChannelReady: (DataChannel) -> Unit,
    private val onConnectionStateChanged: (WebRTCConnectionState) -> Unit
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        initializeWebRTC()
    }

    private fun initializeWebRTC() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
        } catch (e: Exception) {
            e.printStackTrace()
            onConnectionStateChanged(WebRTCConnectionState.FAILED)
        }
    }

    fun start() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    onConnectionStateChanged(WebRTCConnectionState.CONNECTED)
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                    onConnectionStateChanged(WebRTCConnectionState.DISCONNECTED)
                } else if (state == PeerConnection.IceConnectionState.FAILED) {
                    onConnectionStateChanged(WebRTCConnectionState.FAILED)
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}

            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    try {
                        signalingRepository.sendIceCandidate(
                            token = token,
                            isHost = isHost,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            sdp = candidate.sdp
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}

            override fun onAddStream(p0: MediaStream) {}

            override fun onRemoveStream(p0: MediaStream) {}

            override fun onDataChannel(dc: DataChannel) {
                if (!isHost) {
                    dataChannel = dc
                    onDataChannelReady(dc)
                }
            }

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(p0: RtpReceiver, p1: Array<out MediaStream>) {}
        })

        if (isHost) {
            setupHostFlow()
        } else {
            setupGuestFlow()
        }

        scope.launch {
            signalingRepository.listenForIceCandidates(token, listenToHost = !isHost).collect { candidateModel ->
                val candidate = IceCandidate(
                    candidateModel.sdpMid,
                    candidateModel.sdpMLineIndex,
                    candidateModel.sdp
                )
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    private fun setupHostFlow() {
        onConnectionStateChanged(WebRTCConnectionState.CONNECTING)

        val init = DataChannel.Init().apply {
            ordered = true
        }
        dataChannel = peerConnection?.createDataChannel("bandwidth-proxy", init)?.also { dc ->
            onDataChannelReady(dc)
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        scope.launch {
                            try {
                                signalingRepository.sendOffer(token, sdp.description)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())

        scope.launch {
            signalingRepository.listenForAnswer(token).collect { answerModel ->
                if (answerModel != null) {
                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        answerModel.sdp
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, sessionDescription)
                }
            }
        }
    }

    private fun setupGuestFlow() {
        onConnectionStateChanged(WebRTCConnectionState.CONNECTING)

        scope.launch {
            signalingRepository.listenForOffer(token).collect { offerModel ->
                if (offerModel != null) {
                    val offerDescription = SessionDescription(
                        SessionDescription.Type.OFFER,
                        offerModel.sdp
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            peerConnection?.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(answerSdp: SessionDescription) {
                                    peerConnection?.setLocalDescription(object : SdpObserver {
                                        override fun onCreateSuccess(p0: SessionDescription?) {}
                                        override fun onSetSuccess() {
                                            scope.launch {
                                                try {
                                                    signalingRepository.sendAnswer(token, answerSdp.description)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        override fun onCreateFailure(p0: String?) {}
                                        override fun onSetFailure(p0: String?) {}
                                    }, answerSdp)
                                }

                                override fun onSetSuccess() {}
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, MediaConstraints())
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, offerDescription)
                }
            }
        }
    }

    fun disconnect() {
        try {
            dataChannel?.close()
            dataChannel = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        onConnectionStateChanged(WebRTCConnectionState.IDLE)
    }
}
