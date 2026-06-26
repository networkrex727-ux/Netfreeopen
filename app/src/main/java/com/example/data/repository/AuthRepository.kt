package com.example.data.repository

import android.content.Context
import com.example.data.model.ActiveUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val rtdb: FirebaseDatabase
) {
    private val prefs = context.getSharedPreferences("netshare_auth_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserEmail(): String {
        return auth.currentUser?.email ?: ""
    }

    fun getCurrentUser(): FirebaseUser? {
        return try {
            auth.currentUser
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUid(): String? {
        return auth.currentUser?.uid
    }

    fun startPresenceTracking() {
        val uid = getCurrentUid() ?: return
        val email = getCurrentUserEmail()
        val presenceRef = rtdb.getReference("active_users").child(uid)
        val now = System.currentTimeMillis()
        val userData = mapOf(
            "uid" to uid,
            "email" to email,
            "status" to "online",
            "lastActive" to now,
            "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
            "role" to "idle"
        )
        presenceRef.setValue(userData)
        presenceRef.child("status").onDisconnect().setValue("offline")
        presenceRef.child("lastActive").onDisconnect().setValue(System.currentTimeMillis())
        presenceRef.child("role").onDisconnect().setValue("idle")
    }

    suspend fun login(email: String, pass: String): String {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        val uid = result.user!!.uid
        prefs.edit().clear().apply() // clear local session

        // Start real-time presence tracking right away
        startPresenceTracking()

        // Save user profile & session details to Firestore & Realtime DB asynchronously
        scope.launch {
            try {
                val userData = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "lastLogin" to System.currentTimeMillis()
                )
                firestore.collection("users").document(uid).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                
                val sessionId = java.util.UUID.randomUUID().toString()
                val sessionData = mapOf(
                    "sessionId" to sessionId,
                    "uid" to uid,
                    "email" to email,
                    "loginTime" to System.currentTimeMillis(),
                    "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                    "status" to "active"
                )
                firestore.collection("user_sessions").document(sessionId).set(sessionData).await()
                
                // Store sessionId in shared preferences to track logout
                prefs.edit().putString("current_session_id", sessionId).apply()
            } catch (fsEx: Exception) {
                fsEx.printStackTrace()
            }
        }
        
        return uid
    }

    suspend fun register(email: String, pass: String): String {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        val uid = result.user!!.uid
        prefs.edit().clear().apply() // clear local session

        // Start real-time presence tracking right away
        startPresenceTracking()

        // Save user profile & session details to Firestore & Realtime DB asynchronously
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val userData = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "createdAt" to now,
                    "lastLogin" to now
                )
                firestore.collection("users").document(uid).set(userData).await()
                
                val sessionId = java.util.UUID.randomUUID().toString()
                val sessionData = mapOf(
                    "sessionId" to sessionId,
                    "uid" to uid,
                    "email" to email,
                    "loginTime" to now,
                    "deviceInfo" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                    "status" to "active"
                )
                firestore.collection("user_sessions").document(sessionId).set(sessionData).await()
                
                // Store sessionId in shared preferences to track logout
                prefs.edit().putString("current_session_id", sessionId).apply()
            } catch (fsEx: Exception) {
                fsEx.printStackTrace()
            }
        }
        
        return uid
    }

    fun getActiveUsersFlow(): Flow<List<ActiveUser>> = callbackFlow {
        val ref = rtdb.getReference("active_users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ActiveUser>()
                for (child in snapshot.children) {
                    val user = child.getValue(ActiveUser::class.java)
                    if (user != null) {
                        list.add(user)
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun logout() {
        val uid = getCurrentUid()
        if (uid != null) {
            rtdb.getReference("active_users").child(uid).child("status").setValue("logged_out")
            rtdb.getReference("active_users").child(uid).child("role").setValue("idle")
        }
        val currentSessionId = prefs.getString("current_session_id", null)
        if (currentSessionId != null) {
            scope.launch {
                try {
                    firestore.collection("user_sessions").document(currentSessionId)
                        .update("status", "logged_out").await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.edit().clear().apply()
    }
}
