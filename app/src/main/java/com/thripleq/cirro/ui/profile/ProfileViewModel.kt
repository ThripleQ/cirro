package com.thripleq.cirro.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.repo.LibraryStateStore
import com.thripleq.cirro.core.model.ProfileData
import com.thripleq.cirro.core.repo.ProfileRepository
import com.thripleq.cirro.core.util.RefreshGate
import com.thripleq.cirro.ui.components.OwnedTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val library: LibraryStateStore,
    private val gateway: NetEaseGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 见 [onEnterVisible]：反复切回「我的」tab 不该反复打网络。 */
    private val enterGate = RefreshGate()

    /**
     * 「我买了什么」的 UI 侧快照，供**所有**曲目列表画徽标用（见 `LocalOwnedTracks`）。
     *
     * 挂在 ProfileViewModel 上不是因为它属于「我的」页，而是**这个 VM 是 Activity 作用域的**
     * —— 它在 [com.thripleq.cirro.CirroApp] 根部就被创建，于是这份镜像跟着 App 启动一起加载，
     * 与用户在哪个 tab 无关（徽标出现在歌单/榜单/搜索/首页，随便从哪进都得是准的）。
     * [SharingStarted.Eagerly] 保证这两个 flow 一开始收集就不会因无人订阅而停。
     */
    val owned: StateFlow<OwnedTracks> =
        combine(library.ownedSongIds, library.ownedAlbumIds) { songs, albums ->
            OwnedTracks(songs, albums)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, OwnedTracks.None)

    init {
        // VM 提升到 Activity 作用域（Profile 页与 WebLogin 页共用），跨 tab 常驻；
        // 若 repo 里有上次数据直接展示，不闪加载动画；随后后台静默刷新。
        val cached = repository.cachedProfile
        if (cached != null) _uiState.value = ProfileUiState.LoggedIn(cached)
        // 首次刷新由 init 自己发起，先把门关上 —— 页面组合时那次 onEnterVisible()
        // 会被冷却吃掉，不会跟着补一枪重复请求。
        enterGate.mark()
        refresh()
    }

    /**
     * 页面重新可见（切回「我的」tab、从详情页返回）时调用。
     *
     * 冷却期内什么都不做；冷却之外先把「会在别处变」的几份缓存失效掉，再静默刷新：
     *
     * - **已购**：官方 App 里买歌/买专辑不会通知我们，原来只有登录态变化才失效；
     * - **红心曲目表**：同上，在别处点了红心，本进程这份列表就旧了；
     * - **收藏镜像**（红心 id 全集、已收藏专辑 id 全集）：只按 uid 记账，同一个账号
     *   一个进程内只拉一次 —— 在别处加了收藏就永远看不到。失效只清「已载入」记账，
     *   不动已载入的集合本身（否则红心会瞬间全灭，闪一下再亮）。
     */
    fun onEnterVisible() {
        if (!enterGate.allow()) return
        enterGate.mark()
        repository.invalidatePurchased()
        repository.invalidateLikedCache()
        library.markStale()
        refreshInternal(silent = true)
    }

    /** 手动刷新 / 错误态重试：显示忙碌态，无条件重取。 */
    fun refresh() = refreshInternal(silent = false)

    /** [silent] = 已有内容时的静默刷新：不置 Loading、不点 [busy]，避免切 tab 回来闪一下。 */
    private fun refreshInternal(silent: Boolean) {
        viewModelScope.launch {
            val quiet = silent && _uiState.value is ProfileUiState.LoggedIn
            if (!quiet) {
                _busy.value = true
                if (_uiState.value !is ProfileUiState.LoggedIn) {
                    _uiState.value = ProfileUiState.Loading
                }
            }
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                // 静默刷新时网络抖动也会让 account() 返回 null（它把 err!=0 也当未登录）。
                // 页面上已经是一份登录态数据，不该被一次抖动掀成「未登录」。
                if (!quiet) _uiState.value = ProfileUiState.LoggedOut
                return@launch
            }
            // 已购镜像（曲目徽标用的）与页面数据**并行**取：它服务于所有列表、不只是本页，
            // 没必要让「我的」页在这上面等两个来回。
            launch { library.ensureOwnedLoaded(account.uid) }
            val data = repository.loadProfile(account)
            _busy.value = false
            if (data != null) {
                _uiState.value = ProfileUiState.LoggedIn(data)
            } else if (!quiet && _uiState.value !is ProfileUiState.LoggedIn) {
                _uiState.value = ProfileUiState.Error("加载失败")
            }
        }
    }

    /** Completes login from an in-app WebView session (official login page). */
    fun webLoginCookies(cookieStr: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            repository.invalidateAccount() // 登录态变了，丢弃账号短缓存与集合副本
            library.invalidate()           // 收藏镜像整份作废，避免下个账号看到上个人的红心
            enterGate.reset()              // 换账号后的第一次可见必须真刷一次
            gateway.importCookies(cookieStr)
            val account = repository.account()
            _busy.value = false
            if (account == null) {
                onDone(false, "登录态无效，请重试")
                _uiState.value = ProfileUiState.LoggedOut
            } else {
                onDone(true, "")
                // 登录态变化，强制刷新并更新缓存。
                library.ensureOwnedLoaded(account.uid)
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
