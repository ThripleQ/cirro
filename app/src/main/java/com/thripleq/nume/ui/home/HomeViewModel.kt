package com.thripleq.nume.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.HomeRepository
import com.thripleq.nume.core.repo.PlaylistCard
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState

    /**
     * 逐块回填态：四个内容字段用 `null` 表示「这块还没拉到」，非空表示已就绪
     * （其中 daily/recent 的 `emptyList()` 表示就绪但确实没有，用于展示登录引导）。
     * 各块异步并行、一就绪即整体发布，让首屏先显示先到的歌单/榜单，而不是等
     * 最慢的一块决定整页。
     */
    data class Ready(
        val loggedIn: Boolean,
        val playlists: List<PlaylistCard>?,
        val charts: List<Chart>?,
        val dailySongs: List<Track>?,
        val recentSongs: List<Track>?,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val chartRepo: ChartRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            // 并行发起四块请求（登录态仅决定是否拉 daily/recent）。
            // loggedIn 是网络调用（account 接口）：不能串行挡在四个内容块前面——
            // account 慢（风控抖动时数秒）会让整个首页干等。它只是 daily/recent 的
            // 前置条件，与 playlists/charts 无依赖，async 并行。
            val loggedInAsync = async { homeRepo.loggedIn() }
            val playlists = async { homeRepo.recommendPlaylists() }
            val charts = async { chartRepo.charts() }
            // account 慢只拖后 daily/recent 两块，playlists/charts 完全不受影响。
            // Deferred.await() 幂等，两个块各 await 一次不产生重复请求。
            val daily = async { if (loggedInAsync.await()) homeRepo.dailySongs() else emptyList() }
            val recent = async { if (loggedInAsync.await()) homeRepo.recentSongs() else emptyList() }

            // 逐块回填：每一块一就绪就整体发布，最慢的那块不再拖慢整页首屏。
            // 四块全部聚齐后统一判空，全部为空才落到 Error。
            // Ready.loggedIn 目前无 UI 消费者：先用占位值即刻发布首块，收尾时
            // 再补真值——那时 daily/recent 已经等到过 account，await 瞬间返回。
            var ready = HomeUiState.Ready(false, null, null, null, null)

            val pl = playlists.await()
            ready = ready.copy(playlists = pl)
            _uiState.value = ready

            val ch = charts.await()
            ready = ready.copy(charts = ch)
            _uiState.value = ready

            val da = daily.await()
            ready = ready.copy(dailySongs = da)
            _uiState.value = ready

            val re = recent.await()
            ready = ready.copy(recentSongs = re, loggedIn = loggedInAsync.await())

            val allEmpty = ready.playlists.isNullOrEmpty() &&
                ready.charts.isNullOrEmpty() &&
                ready.dailySongs.isNullOrEmpty() &&
                ready.recentSongs.isNullOrEmpty()
            _uiState.value = if (allEmpty) HomeUiState.Error else ready
        }
    }

    /** 小封面单曲行：点了直接播 —— **不弹播放页**（2026-10-03 用户：「点一首歌进行播放不要
     *  进入播放页，就单纯开始播放就行」）。展开播放页的入口只有底部迷你条本身。 */
    fun onPlayTrack(tracks: List<Track>, index: Int) {
        viewModelScope.launch {
            playback.play(context, tracks, index)
        }
    }
}
