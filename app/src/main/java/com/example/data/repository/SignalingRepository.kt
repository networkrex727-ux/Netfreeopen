package com.example.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class IceCandidateModel(
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val sdp: String = ""
)

data class SdpModel(
    val sdp: String = "",
    val type: String = ""
)

@Singleton
class SignalingRepository @Inject constructor(
    private val rtdb: FirebaseDatabase
) {
    private val signalingRef = rtdb.getReference("signaling")

    suspend fun sendOffer(token: String, sdp: String) {
        signalingRef.child(token).child("offer").setValue(
            mapOf("sdp" to sdp, "type" to "offer")
        ).await()
    }

    suspend fun sendAnswer(token: String, sdp: String) {
        signalingRef.child(token).child("answer").setValue(
            mapOf("sdp" to sdp, "type" to "answer")
        ).await()
    }

    suspend fun sendIceCandidate(
        token: String,
        isHost: Boolean,
        sdpMid: String?,
        sdpMLineIndex: Int,
        sdp: String
    ) {
        val side = if (isHost) "host" else "guest"
        signalingRef.child(token).child("ice_candidates").child(side).push().setValue(
            mapOf(
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex,
                "sdp" to sdp
            )
        ).await()
    }

    fun listenForOffer(token: String): Flow<SdpModel?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                    val type = snapshot.child("type").getValue(String::class.java) ?: ""
                    trySend(SdpModel(sdp, type))
                } else {
                    trySend(null)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        signalingRef.child(token).child("offer").addValueEventListener(listener)
        awaitClose { signalingRef.child(token).child("offer").removeEventListener(listener) }
    }

    fun listenForAnswer(token: String): Flow<SdpModel?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                    val type = snapshot.child("type").getValue(String::class.java) ?: ""
                    trySend(SdpModel(sdp, type))
                } else {
                    trySend(null)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        signalingRef.child(token).child("answer").addValueEventListener(listener)
        awaitClose { signalingRef.child(token).child("answer").removeEventListener(listener) }
    }

    fun listenForIceCandidates(token: String, listenToHost: Boolean): Flow<IceCandidateModel> = callbackFlow {
        val side = if (listenToHost) "host" else "guest"
        val listener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                trySend(IceCandidateModel(sdpMid, sdpMLineIndex, sdp))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        signalingRef.child(token).child("ice_candidates").child(side).addChildEventListener(listener)
        awaitClose {
            signalingRef.child(token).child("ice_candidates").child(side).removeEventListener(listener)
        }
    }

    suspend fun clearSignaling(token: String) {
        signalingRef.child(token).removeValue().await()
    }
}
