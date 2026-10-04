package com.thripleq.nume.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.HomeRepository
import com.thripleq.nume.core.repo.PlaylistCard
import com.thripleq.nume.core.repo.StyleTag
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        /**
         * 「雷达歌单」区的卡（首页 block 流里 HOMEPAGE_BLOCK_MGC_PLAYLIST 那个
         * block，6 张官方雷达歌单：私人雷达 / 新歌雷达 / 会员雷达 / 乐迷雷达…）。
         * 它**不是**「精选推荐」里那张私人雷达功能卡 —— 后者见 [featured] 的
         * `radar_private`，两者数据源不同、故意不重名。
         */
        val radar: List<PlaylistCard>?,
        /** 曲风标签总表（`/api/tag/list/get`），null = 还没拉到。 */
        val styles: List<StyleTag>? = null,
        /** 首个曲风下的歌曲（`/api/style-tag/home/song`），null = 还没拉到。 */
        val styleSongs: List<Track>? = null,
        /** 第二个曲风下的歌单（`/weapi/style-tag/home/playlist`）。 */
        val stylePlaylists: List<PlaylistCard>? = null,
        /**
         * 「场景音乐」卡：kanade 这一区是 **sceneTags**（清晨 / 夜晚 / 伤感 /
         * 治愈…的标签卡）。官方歌单分类里 category 2 = 场景、3 = 情感，标签名与
         * kanade 卡面重合，所以用它，封面取该标签下第一张热门歌单。
         * null = 还没拉到。
         */
        val scene: List<SceneCard>? = null,
    ) : HomeUiState {

        /**
         * 「猜你喜欢」区块标题：像 kanade 那样带上曲风词。风格词来自
         * [styles]，拉不到就退回不带限定的通用文案。
         */
        val guessTitle: String
            get() {
                val s = styles?.firstOrNull()?.name
                return if (s.isNullOrBlank()) "猜你喜欢的好歌" else "猜你喜欢的「$s」好歌"
            }

        /**
         * 「猜你喜欢」的歌曲：优先曲风歌（官方客户端同一套 style-tag 体系，
         * 匿名也常能拿到），拿不到再回落每日推荐（需登录）。
         * 返回 null 表示这块还没就绪，UI 不渲染标题。
         */
        val guessSongs: List<Track>?
            get() = when {
                styleSongs == null && dailySongs == null -> null
                !styleSongs.isNullOrEmpty() -> styleSongs
                else -> dailySongs
            }

        /**
         * 场景音乐卡：直接用 [scene]（场景 / 情感标签卡）。
         *
         * 以前这里是「曲风歌单 / 大众推荐歌单」，与 kanade 的 sceneTags 不是一回事，
         * 而且与「雷达歌单」区共用推荐池会撞同一条歌单（见 466b035）。换成标签卡后
         * 数据源彻底分开，不会再撞。
         */
        val sceneCards: List<SceneCard> get() = scene.orEmpty()

        /** 「雷达歌单」区的卡：直接来自 [radar]（首页 block 流里的雷达 block）。 */
        val radarCards: List<PlaylistCard> get() = radar.orEmpty()

        /**
         * 「精选推荐」横滑功能卡（布局抄 kanade 主页，2026-10-04）。
         *
         * kanade 的这一区是**客户端侧的功能卡枚举**，首屏 6 张，顺序与文案由 uiautomator
         * 实测钉死（徽标 = 卡名，底部条 = 一句说明）：
         *
         * | # | 徽标 | 底部说明 | 我们的数据 |
         * |---|---|---|---|
         * | 1 | 热歌榜 | 云音乐官方top排行榜 | TOPLIST 里名为「热歌榜」的那张 |
         * | 2 | 每日推荐 | 符合你口味的新鲜好歌 | RECOMMEND_SONGS（需登录） |
         * | 3 | 私人漫游 | 多种听歌模式随心播放 | `/api/v1/radio/get`，点了直接播 |
         * | 4 | 私人雷达 | 从你喜欢的歌听起 | RECOMMEND_PLAYLISTS 里名称带「雷达」的那条 |
         * | 5 | 相似艺人 | 从你喜欢的艺人听起 | 种子歌 → 歌手 → 热门歌（见 HomeRepository.artistRadio） |
         * | 6 | 「XX」日推 | 你喜欢的XX歌曲 | 曲风歌单（style-tag 体系，风格词随账号口味） |
         *
         * 我们没有 kanade 那套 OpenAPI（个人开发者需成年，暂不可用），所以按同一视觉
         * 规格从**已有数据**组装等价物；拿不到就少一张，末尾用推荐歌单补位凑 6 张。
         */
        val featured: List<FeaturedCard> by lazy {
            // 私人雷达卡：从当日推荐池里挑名字带「雷达」的那条（通常是
            // 「今天从《X》听起|私人雷达」）。**必须按名过滤**：推荐池绝大多数条目
            // 是普通歌单，firstOrNull() 会把一张普通歌单当成私人雷达。
            //
            // 数据源是 RECOMMEND_PLAYLISTS 而不是下面的 radar —— 2026-10-04 起
            // radar 改指「雷达歌单」区块（首页 block 流里那 6 张雷达卡），
            // 而这张功能卡要的是「从你喜欢的歌听起」那条私人雷达，
            // 两者混用会让功能卡与雷达区第一张卡撞成同一张封面。
            val privateRadar = playlists?.firstOrNull { it.name.contains("雷达") }
            // 补位去重：雷达区块与私人雷达卡已经展示过的歌单，补位卡不再重复取，
            // 避免同一条歌单在一屏里出现两次（各区块 wire 不同只保证共享元素
            // 键唯一，视觉上还是会重复）。
            val used = radar.orEmpty().map { it.id }.toSet() + listOfNotNull(privateRadar?.id)
            buildList {
                charts?.firstOrNull { it.name == "热歌榜" }?.let {
                    add(
                        FeaturedCard(
                            "hot", it.coverUrl, "热歌榜", "云音乐官方top排行榜",
                            "chart", it.id,
                        ),
                    )
                }
                if (!dailySongs.isNullOrEmpty()) {
                    add(
                        FeaturedCard(
                            "daily", dailySongs.first().artworkUrl,
                            "每日推荐", "符合你口味的新鲜好歌", "daily", "",
                        ),
                    )
                }
                add(
                    FeaturedCard(
                        "radio", guessSongs?.getOrNull(1)?.artworkUrl,
                        "私人漫游", "多种听歌模式随心播放", "radio", "",
                        playKind = HomeViewModel.PLAY_RADIO,
                    ),
                )
                privateRadar?.let {
                    add(
                        FeaturedCard(
                            "radar_private", it.coverUrl,
                            "私人雷达", "从你喜欢的歌听起", "fradar", it.id,
                        ),
                    )
                }
                add(
                    FeaturedCard(
                        "artist", guessSongs?.getOrNull(2)?.artworkUrl,
                        "相似艺人", "从你喜欢的艺人听起", "artist", "",
                        playKind = HomeViewModel.PLAY_ARTIST,
                    ),
                )
                stylePlaylists?.firstOrNull()?.let { p ->
                    val s = styles?.firstOrNull()?.name
                    add(
                        FeaturedCard(
                            "style_daily", p.coverUrl,
                            if (s.isNullOrBlank()) "曲风日推" else "${s}日推",
                            if (s.isNullOrBlank()) "你喜欢的歌曲" else "你喜欢的${s}歌曲",
                            HomeViewModel.FEATURED_PLAYLIST_WIRE, p.id,
                        ),
                    )
                }
                playlists.orEmpty().filter { it.id !in used }.forEachIndexed { i, p ->
                    if (size < 6) {
                        add(
                            FeaturedCard(
                                "pl$i", p.coverUrl, "歌单", p.name,
                                HomeViewModel.FEATURED_PLAYLIST_WIRE, p.id,
                            ),
                        )
                    }
                }
            }
        }

    }
}

/**
 * 「精选推荐」一张功能卡：方形封面 + 左上角白底徽标（卡名）+ 底部浅灰说明条。
 * 与 kanade 实测一致：徽标是**卡名**，说明条是**一句描述**，不是歌单名。
 */
/** 「场景音乐」一张标签卡：卡面是该标签下第一张热门歌单的封面，名称条是标签名。 */
data class SceneCard(
    val tag: String,
    val coverUrl: String?,
    val playlistId: String,
)

data class FeaturedCard(
    val key: String,
    val coverUrl: String?,
    /** 左上角白底圆角徽标。 */
    val label: String,
    /** 底部说明条。 */
    val caption: String,
    val source: String,
    val id: String,
    /** null = 点开列表壳；[HomeViewModel.PLAY_RADIO] / [PLAY_ARTIST] = 拉一批直接播。 */
    val playKind: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val chartRepo: ChartRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        /** [FeaturedCard.playKind]：点了拉 `/api/v1/radio/get` 直接播。 */
        const val PLAY_RADIO = "radio"

        /** [FeaturedCard.playKind]：点了播「种子歌所属艺人」的热门歌。 */
        const val PLAY_ARTIST = "artist"

        /** 场景音乐取几个场景 / 情感标签（kanade 实测首屏 3 张，多给几张可横滑）。 */
        const val SCENE_TAG_COUNT = 8

        /**
         * 歌单型功能卡的 wire：故意不叫 "playlist"。
         *
         * 共享元素 key 是 `shell:<source>:<id>`，而推荐池之间高度重叠 —— 同一条歌单
         * 会同时在「精选推荐」补位卡和下面某区块出现。各区块各用各的 wire
         * （功能卡 fpl / 雷达卡 fradar / 雷达区 radar / 场景区 scene，见各处注释），
         * 保证**整页范围内一个 key 只有一个源**；TrackListSource.from 对未知 wire
         * 一律回落 PLAYLIST，取数完全不受影响。
         */
        const val FEATURED_PLAYLIST_WIRE = "fpl"

        /**
         * 「雷达歌单」区的 wire。与 [FEATURED_PLAYLIST_WIRE] 同理：雷达卡也是歌单，
         * 若沿用 "playlist" 就可能与别处的同一条歌单撞共享元素键。
         * 源端 key = `shell:radar:<id>`，目标端由 `ExpandTarget("radar", …)` 原样拼出。
         */
        const val RADAR_WIRE = "radar"
    }

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
            val styles = async { homeRepo.styleList() }
            // 场景音乐：先拿场景/情感标签，再为每个标签各取一张热门歌单当封面
            // （分类表里标签没有封面）。标签之间无依赖 → 并行。
            val scene = async {
                val tags = homeRepo.sceneTags(SCENE_TAG_COUNT)
                coroutineScope {
                    tags.map { tag ->
                        async {
                            homeRepo.playlistsByCat(tag, "1").firstOrNull()
                                ?.let { SceneCard(tag, it.coverUrl, it.id) }
                        }
                    }.awaitAll().filterNotNull()
                }
            }
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

            // 曲风两段有依赖：先拿 tag 列表，才能按 tagId 拉歌曲/歌单；
            // 拉到 tag 之后这两块之间无依赖，并行发起。
            val st = styles.await()
            ready = ready.copy(styles = st)
            _uiState.value = ready
            val tag = st.firstOrNull()?.id
            val tag2 = st.getOrNull(1)?.id
            val styleSongs = async { if (tag != null) homeRepo.styleSongs(tag, "6") else emptyList() }
            val stylePlaylists = async {
                if (tag2 != null) homeRepo.stylePlaylists(tag2, "6") else emptyList()
            }

            val da = daily.await()
            ready = ready.copy(dailySongs = da, loggedIn = loggedInAsync.await())
            _uiState.value = ready

            ready = ready.copy(styleSongs = styleSongs.await())
            _uiState.value = ready
            ready = ready.copy(stylePlaylists = stylePlaylists.await())
            _uiState.value = ready
            ready = ready.copy(scene = scene.await())

            val allEmpty = ready.playlists.isNullOrEmpty() &&
                ready.charts.isNullOrEmpty() &&
                ready.radar.isNullOrEmpty() &&
                ready.dailySongs.isNullOrEmpty() &&
                ready.styleSongs.isNullOrEmpty() &&
                ready.stylePlaylists.isNullOrEmpty() &&
                ready.scene.isNullOrEmpty()
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

    /**
     * 「精选推荐」里点了直接播的卡（[FeaturedCard.playKind] 非空）。
     *
     * 这两类卡的共同点：内容不是一份可列的清单，而是**每次调用都不一样的一批歌**
     * （漫游是服务端随口味出，艺人是按种子歌的歌手现拉），所以不进列表壳 ——
     * 壳里那种「固定队列 + 播放全部」的语义对它们不成立。拉空就什么都不做，
     * 避免把空队列塞给播放器。
     */
    fun onPlayFeatured(kind: String) {
        viewModelScope.launch {
            val seed = (_uiState.value as? HomeUiState.Ready)?.guessSongs?.firstOrNull()?.id
            val tracks = when (kind) {
                PLAY_ARTIST -> homeRepo.artistRadio(seed).ifEmpty { homeRepo.radioSongs("10") }
                else -> homeRepo.radioSongs("10")
            }
            if (tracks.isNotEmpty()) playback.play(context, tracks, 0)
        }
    }
}
