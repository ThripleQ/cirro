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
     * （daily 的 `emptyList()` 表示就绪但确实没有/未登录，用于展示登录引导）。
     * 各块异步并行、一就绪即整体发布，让首屏先显示先到的区块，而不是等
     * 最慢的一块决定整页。
     */
    data class Ready(
        val loggedIn: Boolean,
        val playlists: List<PlaylistCard>?,
        val charts: List<Chart>?,
        val dailySongs: List<Track>?,
        val radar: List<PlaylistCard>?,
    ) : HomeUiState {

        /**
         * 「精选推荐」横滑功能卡（布局抄 kanade 主页，2026-10-04）。
         *
         * kanade 的这个区块是**客户端侧的功能卡枚举**（14 种：热歌榜/每日推荐/私人漫游/
         * 私人雷达/相似艺人/华语流行日推…），卡片文案固定、不来自接口。我们没有那套
         * OpenAPI，所以按同一视觉规格（图 + 左上角类型标签 + 底部名称条）从**已有数据**
         * 组装等价物，凑满 6 张，缺哪块就用推荐歌单调剂：
         *
         * | 卡 | 数据 | 徽标 |
         * |---|---|---|
         * | 热歌榜 / 飙升榜 / 新歌榜 | TOPLIST_DETAIL 按名取 | 榜单 |
         * | 每日推荐 | RECOMMEND_SONGS（登录才有） | 每日推荐 |
         * | 私人雷达 | RECOMMEND_RESOURCE 首条 | 雷达 |
         * | 补位 | RECOMMEND_PLAYLISTS | 歌单 |
         */
        val featured: List<FeaturedCard> by lazy {
            buildList {
                charts?.firstOrNull { it.name == "热歌榜" }?.let {
                    add(FeaturedCard("hot", it.name, "榜单", it.coverUrl, "chart", it.id))
                }
                if (!dailySongs.isNullOrEmpty()) {
                    add(
                        FeaturedCard(
                            "daily", "每日推荐", "每日推荐",
                            dailySongs.first().artworkUrl, "daily", "",
                        ),
                    )
                }
                radar?.firstOrNull()?.let {
                    add(FeaturedCard("radar", it.name, "雷达", it.coverUrl, "playlist", it.id))
                }
                charts?.firstOrNull { it.name == "飙升榜" }?.let {
                    add(FeaturedCard("rise", it.name, "榜单", it.coverUrl, "chart", it.id))
                }
                charts?.firstOrNull { it.name == "新歌榜" }?.let {
                    add(FeaturedCard("new", it.name, "榜单", it.coverUrl, "chart", it.id))
                }
                playlists.orEmpty().forEachIndexed { i, p ->
                    if (size < 6) add(FeaturedCard("pl$i", p.name, "歌单", p.coverUrl, "playlist", p.id))
                }
            }
        }
    }
}

/** 「精选推荐」一张功能卡：封面 + 左上角类型徽标 + 底部名称条。 */
data class FeaturedCard(
    val key: String,
    val title: String,
    val badge: String,
    val coverUrl: String?,
    val source: String,
    val id: String,
)

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

            // 并行发起五块请求（登录态仅决定是否拉 daily）。
            // loggedIn 是网络调用（account 接口）：不能串行挡在内容块前面——
            // account 慢（风控抖动时数秒）会让整个首页干等。它只是 daily 的
            // 前置条件，与其余四块无依赖，async 并行。
            val loggedInAsync = async { homeRepo.loggedIn() }
            val playlists = async { homeRepo.recommendPlaylists() }
            val charts = async { chartRepo.charts() }
            val radar = async { homeRepo.radarPlaylists() }
            // account 慢只拖后 daily 一块，其余四块完全不受影响。
            val daily = async { if (loggedInAsync.await()) homeRepo.dailySongs() else emptyList() }

            // 逐块回填：每一块一就绪就整体发布，最慢的那块不再拖慢整页首屏。
            // 全部聚齐后统一判空，全部为空才落到 Error。
            var ready = HomeUiState.Ready(false, null, null, null, null)

            val pl = playlists.await()
            ready = ready.copy(playlists = pl)
            _uiState.value = ready

            val ch = charts.await()
            ready = ready.copy(charts = ch)
            _uiState.value = ready

            val rd = radar.await()
            ready = ready.copy(radar = rd)
            _uiState.value = ready

            val da = daily.await()
            ready = ready.copy(dailySongs = da, loggedIn = loggedInAsync.await())

            val allEmpty = ready.playlists.isNullOrEmpty() &&
                ready.charts.isNullOrEmpty() &&
                ready.radar.isNullOrEmpty() &&
                ready.dailySongs.isNullOrEmpty()
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
