package com.example.data.repository

import com.example.data.model.ShareToken
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class JoinResult {
    object NotFound : JoinResult()
    object Expired : JoinResult()
    object AlreadyUsed : JoinResult()
    object LimitReached : JoinResult()
    data class Success(val hostUid: String, val bwLimitMB: Long) : JoinResult()
}

@Singleton
class ShareTokenRepository @Inject constructor(
    private val rtdb: FirebaseDatabase,
    private val authRepository: AuthRepository
) {
    private val tokensRef = rtdb.getReference("share_tokens")

    suspend fun createShareToken(bandwidthMB: Long): String {
        val hostUid = authRepository.getCurrentUid() ?: throw Exception("User not logged in")
        val hostEmail = authRepository.getCurrentUserEmail()
        var token = generateToken()
        
        var attempts = 0
        while (attempts < 3) {
            val snapshot = tokensRef.child(token).get().await()
            if (!snapshot.exists()) break
            token = generateToken()
            attempts++
        }

        val now = System.currentTimeMillis()
        val tokenData = ShareToken(
            token = token,
            hostUid = hostUid,
            hostEmail = hostEmail,
            guestUid = null,
            createdAt = now,
            expiresAt = now + (10 * 60 * 1000), // 10 minutes expiry
            bandwidthLimit = bandwidthMB,
            bandwidthUsed = 0L,
            status = "waiting"
        )

        tokensRef.child(token).setValue(tokenData).await()
        
        // Update user's current role to "hosting" in Realtime Database
        updateUserRole("hosting")
        
        return token
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun validateAndJoin(token: String): JoinResult {
        val guestUid = authRepository.getCurrentUid() ?: throw Exception("User not logged in")
        val snapshot = tokensRef.child(token).get().await()

        if (!snapshot.exists()) {
            return JoinResult.NotFound
        }

        val shareToken = snapshot.getValue(ShareToken::class.java) ?: return JoinResult.NotFound

        if (System.currentTimeMillis() > shareToken.expiresAt) {
            if (shareToken.status == "waiting") {
                tokensRef.child(token).child("status").setValue("expired").await()
            }
            return JoinResult.Expired
        }

        if (shareToken.status != "waiting" || shareToken.guestUid != null) {
            return JoinResult.AlreadyUsed
        }

        if (shareToken.bandwidthUsed >= shareToken.bandwidthLimit) {
            return JoinResult.LimitReached
        }

        tokensRef.child(token).child("guestUid").setValue(guestUid).await()
        tokensRef.child(token).child("status").setValue("connected").await()

        // Update user's current role to "using" in Realtime Database
        updateUserRole("using")

        return JoinResult.Success(shareToken.hostUid, shareToken.bandwidthLimit)
    }

    suspend fun updateBandwidthUsed(token: String, usedBytes: Long) {
        val usedMB = usedBytes / (1024 * 1024)
        if (usedMB > 0) {
            try {
                val childRef = tokensRef.child(token).child("bandwidthUsed")
                val currentSnapshot = childRef.get().await()
                val currentVal = currentSnapshot.getValue(Long::class.java) ?: 0L
                childRef.setValue(currentVal + usedMB)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun updateTokenStatus(token: String, status: String) {
        try {
            tokensRef.child(token).child("status").setValue(status).await()
            if (status == "completed" || status == "expired") {
                updateUserRole("idle")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUserRole(role: String) {
        val uid = authRepository.getCurrentUid() ?: return
        rtdb.getReference("active_users").child(uid).child("role").setValue(role)
    }

    fun getActiveSharingTokens(): Flow<List<ShareToken>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ShareToken>()
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val token = child.getValue(ShareToken::class.java)
                    if (token != null && token.status == "waiting" && token.guestUid == null && now < token.expiresAt) {
                        list.add(token)
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        tokensRef.addValueEventListener(listener)
        awaitClose { tokensRef.removeEventListener(listener) }
    }

    fun listenToToken(token: String): Flow<ShareToken?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val item = snapshot.getValue(ShareToken::class.java)
                    trySend(item)
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        tokensRef.child(token).addValueEventListener(listener)
        awaitClose { tokensRef.child(token).removeEventListener(listener) }
    }
}
