package com.thripleq.nume.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.core.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object LoggedOut : ProfileUiState
    data class LoggedIn(val data: ProfileData) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val gateway: NetEaseGateway,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // VM 提升到 Activity 作用域（Profile 页与 WebLogin 页共用），跨 tab 常驻；
        // 若 repo 里有上次数据直接展示，不闪加载动画；随后后台静默刷新。
        val cached = repository.cachedProfile
        if (cached != null) _uiState.value = ProfileUiState.LoggedIn(cached)
        refresh()
    }

    /** Re-checks login state and (when logged in) reloads every block. */
    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            // 已有缓存/已登录数据时不置 Loading，避免切 tab 回来闪一下动画。
            if (_uiState.value !is ProfileUiState.LoggedIn) {
                _uiState.value = ProfileUiState.Loading
            }
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                _uiState.value = ProfileUiState.LoggedOut
                return@launch
            }
            val data = repository.loadProfile(account)
            _busy.value = false
            if (data != null) {
                _uiState.value = ProfileUiState.LoggedIn(data)
            } else if (_uiState.value !is ProfileUiState.LoggedIn) {
                _uiState.value = ProfileUiState.Error("加载失败")
            }
        }
    }

    /** Completes login from an in-app WebView session (official login page). */
    fun webLoginCookies(cookieStr: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            repository.invalidateAccount() // 登录态变了，丢弃账号短缓存
            gateway.importCookies(cookieStr)
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                onDone(false, "登录态无效，请重试")
                _uiState.value = ProfileUiState.LoggedOut
            } else {
                onDone(true, "")
                // 登录态变化，强制刷新并更新缓存。
                val data = repository.loadProfile(account)
                if (data != null) _uiState.value = ProfileUiState.LoggedIn(data)
            }
        }
    }

    /**
     * 「已购 → 单曲」内联列表的行点击：**从 [index] 起播，队列是完整已购**。
     *
     * 队列不能只给内联展开的那一截：内联列表有展示上限（[PurchasedInlineCap]），
     * 拿可见那段当队列，播到第 50 首就没了 —— 而用户以为自己播的是"我的已购"。
     */
    fun playPurchased(index: Int) {
        val songs = (_uiState.value as? ProfileUiState.LoggedIn)?.data?.purchasedSongs ?: return
        playback.play(context, songs, index)
    }
}
