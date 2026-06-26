package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActiveUser
import com.example.data.model.ShareToken
import com.example.data.repository.AuthRepository
import com.example.data.repository.ShareTokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shareTokenRepository: ShareTokenRepository
) : ViewModel() {

    init {
        authRepository.startPresenceTracking()
    }

    val userEmail: String
        get() = authRepository.getCurrentUserEmail()

    val currentUid: String
        get() = authRepository.getCurrentUid() ?: ""

    val activeUsers: StateFlow<List<ActiveUser>> = authRepository.getActiveUsersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeTunnels: StateFlow<List<ShareToken>> = shareTokenRepository.getActiveSharingTokens()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun logout() {
        authRepository.logout()
    }
}
