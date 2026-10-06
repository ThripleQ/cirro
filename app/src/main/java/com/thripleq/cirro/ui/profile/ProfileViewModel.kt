package com.thripleq.cirro.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.repo.Account
import com.thripleq.cirro.core.repo.ProfileData
import com.thripleq.cirro.core.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * 「我的」页状态。
 *
 * 不持有播放能力：页内的可播内容都走全屏面板（`TrackListScreen` 自己的 VM 负责取数与起播），
 * 所以这里没有 `PlaybackLauncher` —— 2026-10-05 已购改成"壳里点一下 → 全屏曲目列表"后，
 * 原先那条「内联铺歌、点了直接播」的 `playPurchased(index)` 连它的依赖一起删掉了。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val gateway: NetEaseGateway,
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
     * 「已购 → 单曲」现在是**全屏曲目列表**（面板里的 `TrackListScreen`，source = "purchased"），
     * 播放交给它自己的 VM —— 本页不再有「内联铺歌、点了直接播」那条路，所以原来为它准备的
     * `playPurchased(index)`（队列 = 完整已购）随之删掉：留着就是一个没有调用方的死接口。
     */
}
