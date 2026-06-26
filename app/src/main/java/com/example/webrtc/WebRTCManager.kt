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
    private val onConnectionStateChanged: (WebRTCConnectionState) -> Unit,
    private val onLogUpdate: (String) -> Unit = {}
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
            onLogUpdate("⚡ WebRTC PeerConnectionFactory initialization started...")
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
            )
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
            onLogUpdate("⚡ PeerConnectionFactory successfully initialized via native Google WebRTC library.")
        } catch (e: Exception) {
            e.printStackTrace()
            onLogUpdate("❌ Failed to initialize WebRTC factory: ${e.localizedMessage}")
            onConnectionStateChanged(WebRTCConnectionState.FAILED)
        }
    }

    fun start() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        onLogUpdate("🌐 Configured Google STUN traversal servers: stun:stun.l.google.com:19302")

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                onLogUpdate("📶 Signaling state changed to: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                onLogUpdate("❄️ ICE Connection state changed to: $state")
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    onLogUpdate("✅ Connection Established! Peer-to-peer data tunnel established successfully.")
                    onConnectionStateChanged(WebRTCConnectionState.CONNECTED)
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                    onLogUpdate("⚠️ Peer disconnected.")
                    onConnectionStateChanged(WebRTCConnectionState.DISCONNECTED)
                } else if (state == PeerConnection.IceConnectionState.FAILED) {
                    onLogUpdate("❌ Connection failed. Check network firewalls.")
                    onConnectionStateChanged(WebRTCConnectionState.FAILED)
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                onLogUpdate("🔍 ICE candidate gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                onLogUpdate("❄️ Discovered local ICE Candidate: ${candidate.sdpMid} (${candidate.sdpMLineIndex})")
                scope.launch {
                    try {
                        signalingRepository.sendIceCandidate(
                            token = token,
                            isHost = isHost,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            sdp = candidate.sdp
                        )
                        onLogUpdate("⬆️ Uploaded local ICE candidate to Firebase Realtime Database.")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onLogUpdate("⚠️ Failed to upload ICE candidate: ${e.localizedMessage}")
                    }
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) {}

            override fun onAddStream(p0: MediaStream) {}

            override fun onRemoveStream(p0: MediaStream) {}

            override fun onDataChannel(dc: DataChannel) {
                onLogUpdate("📂 Data channel received from Host!")
                if (!isHost) {
                    dataChannel = dc
                    onDataChannelReady(dc)
                }
            }

            override fun onRenegotiationNeeded() {
                onLogUpdate("🔄 WebRTC Renegotiation needed.")
            }

            override fun onAddTrack(p0: RtpReceiver, p1: Array<out MediaStream>) {}
        })

        if (isHost) {
            setupHostFlow()
        } else {
            setupGuestFlow()
        }

        scope.launch {
            signalingRepository.listenForIceCandidates(token, listenToHost = !isHost).collect { candidateModel ->
                onLogUpdate("❄️ Received remote ICE candidate from Firebase. Exchanging NAT info...")
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
        onLogUpdate("📡 Host starting proxy configuration flow...")
        onConnectionStateChanged(WebRTCConnectionState.CONNECTING)

        val init = DataChannel.Init().apply {
            ordered = true
        }
        onLogUpdate("📂 Creating local WebRTC data channel 'bandwidth-proxy'...")
        dataChannel = peerConnection?.createDataChannel("bandwidth-proxy", init)?.also { dc ->
            onDataChannelReady(dc)
            onLogUpdate("📂 Local DataChannel registered and listening.")
        }

        onLogUpdate("📂 Creating local SDP Offer...")
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                onLogUpdate("📂 SDP Offer created successfully. Applying locally...")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        onLogUpdate("📂 Applied local SDP Offer. Uploading to Firebase for Guest...")
                        scope.launch {
                            try {
                                signalingRepository.sendOffer(token, sdp.description)
                                onLogUpdate("📂 SDP Offer successfully published! Waiting for Guest to join and Answer...")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onLogUpdate("❌ Failed to publish SDP Offer: ${e.localizedMessage}")
                            }
                        }
                    }
                    override fun onCreateFailure(p0: String?) {
                        onLogUpdate("❌ Failed to set local description: $p0")
                    }
                    override fun onSetFailure(p0: String?) {
                        onLogUpdate("❌ Failed to apply local description: $p0")
                    }
                }, sdp)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                onLogUpdate("❌ Failed to create local offer: $p0")
            }
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())

        scope.launch {
            signalingRepository.listenForAnswer(token).collect { answerModel ->
                if (answerModel != null) {
                    onLogUpdate("👥 Guest Answer detected on Firebase! Applying Guest SDP Answer...")
                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        answerModel.sdp
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onLogUpdate("👥 Remote SDP Answer applied successfully. Resolving candidates...")
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            onLogUpdate("❌ Failed to apply remote SDP Answer: $p0")
                        }
                    }, sessionDescription)
                }
            }
        }
    }

    private fun setupGuestFlow() {
        onLogUpdate("📡 Guest starting proxy tunnel flow...")
        onConnectionStateChanged(WebRTCConnectionState.CONNECTING)

        scope.launch {
            onLogUpdate("⏳ Listening for Host's SDP Offer on Firebase Realtime Database...")
            signalingRepository.listenForOffer(token).collect { offerModel ->
                if (offerModel != null) {
                    onLogUpdate("📂 SDP Offer from Host detected! Applying Host's description...")
                    val offerDescription = SessionDescription(
                        SessionDescription.Type.OFFER,
                        offerModel.sdp
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            onLogUpdate("📂 Applied Host's SDP Offer. Generating Answer SDP...")
                            peerConnection?.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(answerSdp: SessionDescription) {
                                    onLogUpdate("📂 SDP Answer generated successfully. Applying locally...")
                                    peerConnection?.setLocalDescription(object : SdpObserver {
                                        override fun onCreateSuccess(p0: SessionDescription?) {}
                                        override fun onSetSuccess() {
                                            onLogUpdate("📂 Applied local description. Uploading SDP Answer to Firebase...")
                                            scope.launch {
                                                try {
                                                    signalingRepository.sendAnswer(token, answerSdp.description)
                                                    onLogUpdate("⬆️ SDP Answer successfully published! Establishing direct peer-to-peer data stream...")
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    onLogUpdate("❌ Failed to upload SDP Answer: ${e.localizedMessage}")
                                                }
                                            }
                                        }
                                        override fun onCreateFailure(p0: String?) {
                                            onLogUpdate("❌ Failed to set local Answer: $p0")
                                        }
                                        override fun onSetFailure(p0: String?) {
                                            onLogUpdate("❌ Failed to apply local Answer: $p0")
                                        }
                                    }, answerSdp)
                                }

                                override fun onSetSuccess() {}
                                override fun onCreateFailure(p0: String?) {
                                    onLogUpdate("❌ Failed to create Answer SDP: $p0")
                                }
                                override fun onSetFailure(p0: String?) {}
                            }, MediaConstraints())
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            onLogUpdate("❌ Failed to apply Host's SDP Offer: $p0")
                        }
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
