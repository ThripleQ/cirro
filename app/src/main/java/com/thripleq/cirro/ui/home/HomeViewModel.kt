package com.thripleq.cirro.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.playback.PlaybackLauncher
import com.thripleq.cirro.core.model.Chart
import com.thripleq.cirro.core.repo.ChartRepository
import com.thripleq.cirro.core.repo.HomeRepository
import com.thripleq.cirro.core.model.PlaylistCard
import com.thripleq.cirro.core.model.StyleTag
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.core.util.RefreshGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
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
        /** 雷达 block 的服务端栏目标题（未登录「网易云音乐的雷达歌单」/ 登录「<昵称>的雷达歌单」）。 */
        val radarTitle: String? = null,
        /**
         * 「精选推荐」两张**点了直接播**的功能卡的卡面（私人漫游 / 相似艺人）。
         *
         * 2026-10-05 改：以前这两张卡借「猜你喜欢」的歌曲封面（`guess[1]` / `guess[2]`）当
         * 卡面 —— 那是**东拼西凑**：卡面内容和卡片语义毫无关系（一张日语歌封面配「私人漫游」
         * 四个字），而且猜你喜欢那批数据一旦拿不到，两张卡的卡面就一起变白。
         * 现在各取**自己的内容源**：
         * - 私人漫游 → `/api/v1/radio/get` 出的第一首歌封面（就是点开后会播的那批歌之一）
         * - 相似艺人 → 种子歌手在 `/api/discovery/simiArtist` 里的第一个相似歌手头像
         *
         * 两者都是**卡片自己被点开后会给出的内容**，卡面因此与内容自洽。
         *
         * ## 卡面拿不到时**不借别人的图**（2026-10-05 晚，用户：「用 shimmer」）
         *
         * 原先这两笔各挂了**两级兜底**（先借猜你喜欢的歌曲封面 `guess[1]` / `guess[2]`，
         * 最后退到推荐歌单第一张 `playlists[0].coverUrl`）。问题是首页**逐块回填**：
         * [featured] 每发布一块就重算一次，而「推荐歌单」那块来得比 block 流早 ——
         * 于是两张卡同时落到**同一个** `playlists[0].coverUrl` 上，首屏闪一下
         * 「两张卡顶着同一张封面」（用户介意的那一瞬）；block 流到了之后又改成借
         * 猜你喜欢第 2、3 首的封面，与下方单曲行撞图，直到这两笔真正回来才各就各位。
         *
         * 现在 null 就是 null，两级兜底**已删、别再挂回来**：卡面还没落定
         * （[cardFacesSettled] = false）时由 UI 画微光占位（见 [FeaturedCard.coverPending]）。
         *
         * 落定后**仍为 null → 整张卡不出场**（用户 2026-10-05：「如果是不存在而不是没加载完，
         * 就不显示」）。这两者是不同的：还在路上要闪微光（否则卡片凭空出现，位置会跳），
         * 确实没有就是没有 —— 一张顶着空壳的「相似艺人」既没信息量，还得占着一格。
         * 少掉的位置由 [featured] 末尾的补位卡自然填上。
         */
        val radioCover: String? = null,
        val artistCover: String? = null,
        /**
         * 上面两笔卡面是否**已落定**（请求都回来了，不管有没有值）。
         *
         * false = 还在路上 → 卡面画 shimmer 微光；true = 有值就画图、没值那张卡就不出场。
         * **没有这一位就分不清「还没到」与「确实没有」**——前者该闪微光，后者不该
         * 一直闪（未登录时 `simiArtist` 返 301，相似艺人卡的卡面恒为 null）。
         */
        val cardFacesSettled: Boolean = false,
        /**
         * 「猜你喜欢的「XX」好歌」整块 —— 标题与歌曲都来自首页 block 流里的
         * `HOMEPAGE_BLOCK_STYLE_RCMD`（见 [HomeRepository.homePage]）。
         *
         * 以前这块是自己拼的：`/api/tag/list/get` 取风格词当标题、`style-tag/home/song`
         * 取几首歌。绕路不说，风格词还不是服务端真正给的那个，且任一环拿不到就整块
         * 变空 —— 连带「私人漫游 / 相似艺人」两张功能卡的封面（它们借这块的歌曲封面）
         * 一起空掉。现在直接吃 block 里的原数据。
         */
        val guessTitle: String? = null,
        /** 猜你喜欢的单曲，**按页分组**（每页 3 首，横滑翻页，见 [HomeContent]）。 */
        val guessPages: List<List<Track>> = emptyList(),
        /** 曲风标签总表（`/api/tag/list/get`），null = 还没拉到。 */
        val styles: List<StyleTag>? = null,
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
         * 「猜你喜欢」区块标题。**优先用服务端给的**（block 的 `uiElement.subTitle.title`，
         * 形如「猜你喜欢的「日文」好歌」—— 那个风格词是按你口味算的，我们拼不出来），
         * 服务端没给才用曲风表自己拼一句兜底。
         */
        val guessHeadline: String
            get() = guessTitle?.takeIf { it.isNotBlank() }
                ?: styles?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                    ?.let { "猜你喜欢的「$it」好歌" }
                ?: "猜你喜欢的好歌"

        /**
         * 「猜你喜欢」的全部单曲（拍平，供直接播放用）。
         * 返回 null 表示这块还没就绪，UI 不渲染标题。
         */
        val guessSongs: List<Track>?
            get() = when {
                guessPages.isNotEmpty() -> guessPages.flatten()
                dailySongs == null -> null
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
         * | 5 | 相似艺人 | 从你喜欢的艺人听起 | 种子歌 → 歌手 → **相似歌手**（`/api/discovery/simiArtist`）→ 他们的热门歌 |
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
            // 「私人漫游 / 相似艺人」不是歌单，没有歌单封面可用 —— 卡面走 [Ready.radioCover]
            // / [Ready.artistCover]（各自的**内容源**）。
            //
            // 这里**故意不挂兜底**：卡面没到就让它空着，由 UI 画微光（`coverPending`）。
            // 原先的「借猜你喜欢歌曲封面 / 退到 playlists[0]」在逐块回填下会让两张卡
            // 同时落到同一张图上（首屏闪一下同图）、还会与下方单曲行撞图（见 [Ready.radioCover]）。
            val pending = !cardFacesSettled
            // 两张功能卡的出场条件：**卡面在路上**（出场闪微光）或**卡面已到手**。
            // 落定后仍无图 = 这张卡的内容源确实拿不到（未登录 simiArtist 返 301 等）→ 不出场。
            val radioShown = pending || radioCover != null
            val artistShown = pending || artistCover != null
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
                if (radioShown) {
                    add(
                        FeaturedCard(
                            "radio", radioCover,
                            "私人漫游", "多种听歌模式随心播放", "radio", "",
                            playKind = HomeViewModel.PLAY_RADIO,
                            coverPending = pending,
                        ),
                    )
                }
                privateRadar?.let {
                    add(
                        FeaturedCard(
                            "radar_private", it.coverUrl,
                            "私人雷达", "从你喜欢的歌听起", "fradar", it.id,
                        ),
                    )
                }
                if (artistShown) {
                    add(
                        FeaturedCard(
                            "artist", artistCover,
                            "相似艺人", "从你喜欢的艺人听起", "artist", "",
                            playKind = HomeViewModel.PLAY_ARTIST,
                            coverPending = pending,
                        ),
                    )
                }
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
    /**
     * 卡面**还在路上**（[Ready.cardFacesSettled] = false）→ 卡面画微光占位而不是空封面。
     *
     * 只有「私人漫游 / 相似艺人」这两张（卡面各自去别的接口取）会为 true；其余卡的
     * coverUrl 是跟数据一块到的，恒为 false。
     */
    val coverPending: Boolean = false,
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

    /** 见 [onEnterVisible]：反复切回「探索」tab 不该反复打网络。 */
    private val enterGate = RefreshGate()

    /**
     * 最后一次**发布出去**的完整内容。
     *
     * 为什么不直接从 `_uiState` 里读：手动刷新会先把 `_uiState` 置成
     * [HomeUiState.Loading]（要显骨架屏），而那一刻恰恰是「这一块拉空了、要不要保留
     * 旧值」需要旧内容的时候 —— 从 `_uiState` 读只会读到 Loading。所以单独留一份引用，
     * 它**只被成功发布的内容覆盖**。见 [reload] 的起手值与 `keep` 的判据。
     */
    private var lastContent: HomeUiState.Ready? = null

    /** 正在跑的那趟取数；新的一趟开始时把它取消，见 [reload]。 */
    private var reloadJob: Job? = null

    init {
        // 首次取数由 init 自己发起，所以先把门关上 —— 页面组合时那次 onEnterVisible()
        // 会被冷却吃掉，不会跟着补一枪重复请求。
        enterGate.mark()
        load()
    }

    /** 手动刷新（探索页顶栏那颗刷新键）：显示加载态、无条件重取。 */
    fun load() = reload(quiet = false)

    /**
     * 页面重新可见（切回「探索」tab、从详情页返回）时调用。
     *
     * 冷却期内什么都不做；冷却之外**静默**重取 —— 页面上已经有内容，不该退回骨架屏。
     * 页面的 VM 挂在导航回退栈上不会重建，刷新若只挂在 `init` 上就这辈子只跑一次，
     * 见 [RefreshGate] 的类注释。
     */
    fun onEnterVisible() {
        if (!enterGate.allow()) return
        enterGate.mark()
        reload(quiet = true)
    }

    /**
     * [quiet] = 静默刷新：不置 Loading（不显骨架屏）。
     *
     * 另外，**任何一次刷新**（手动或静默）都以「页面上现有那份」为底、某一块拉空时
     * 保留旧值 —— 一次失败的请求不该让区块从页面上消失。只有**首屏那趟**没有旧值
     * 可保留：那时的「空」要如实反映，末了统一判空落到 Error。
     */
    private fun reload(quiet: Boolean) {
        // [quiet] 与「有没有旧内容」是两件事，别混：
        //   quiet      → 要不要显骨架屏（用户点了刷新键，该给个反馈）
        //   hasContent → 拉空时要不要保留旧值
        // 手动刷新同样是「刷新」而不是「清空重来」：点一下刷新就看到某个区块没了，
        // 比"数据晚一点到"严重得多。所以保留旧值的判据是 hasContent。
        val previous = lastContent
        val hasContent = previous != null
        val silent = quiet && hasContent
        // 上一趟还在飞就先取消。两趟并发回填会各持一份 `ready` 交替发布，后发布的那份
        // 可能恰好缺了某一块 —— 表现就是区块莫名消失、或闪回旧值。取消是安全的：
        // 已经发布出去的内容留在 [lastContent] 里，新的一趟以它为底。
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (!silent) _uiState.value = HomeUiState.Loading

            // 并行发起五块请求（登录态仅决定是否拉 daily）。
            // loggedIn 是网络调用（account 接口）：不能串行挡在内容块前面——
            // account 慢（风控抖动时数秒）会让整个首页干等。它只是 daily 的
            // 前置条件，与其余四块无依赖，async 并行。
            val loggedInAsync = async { homeRepo.loggedIn() }
            val playlists = async { homeRepo.recommendPlaylists() }
            val charts = async { chartRepo.charts() }
            // 首页 block 流：**一次请求喂两块** —— 雷达歌单 + 猜你喜欢的标题与单曲。
            val homePage = async { homeRepo.homePage() }
            val styles = async { homeRepo.styleList() }
            // 「私人漫游」卡的卡面：**不依赖任何别的数据**，所以和上面几块一起发，
            // 不要等到 block 流回来才发（原来它挂在 homePage.await() 之后，白等一整个
            // block 页的往返，那张卡的微光窗口也就跟着拉长——见 [Ready.cardFacesSettled]）。
            val radioCover = async { homeRepo.radioSongs("1").firstOrNull()?.artworkUrl }
            // 场景音乐：先拿场景/情感标签，再为每个标签各取一张热门歌单当封面
            // （分类表里标签没有封面）。标签之间无依赖 → 并行。
            //
            // **静默刷新（切回 tab）不打这一块**。它是全页唯一「1 次分类表 + 每标签
            // 各 1 次歌单」= 9 个请求的区块，也是唯一一次对同一端点并发 8 枪的地方；
            // 而场景标签与它们的热门歌单几乎不变 —— 每次回 tab 都重打 9 枪换不来任何
            // 新东西，只让它成为全页最容易因抖动/限流而整块消失的一块（空结果会让
            // UI 连「场景音乐」标题一起不画）。返回 null，由 `keep` 把上次那份留在
            // 页面上；手动点刷新键仍然重取。
            val scene = async {
                if (silent) {
                    null
                } else {
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
            }
            // account 慢只拖后 daily 一块，其余四块完全不受影响。
            val daily = async { if (loggedInAsync.await()) homeRepo.dailySongs() else emptyList() }

            // 逐块回填：每一块一就绪就整体发布，最慢的那块不再拖慢整页首屏。
            // 全部聚齐后统一判空，全部为空才落到 Error。
            // 有旧内容时以**它**为底（首屏那趟才从空壳起手）。
            var ready = previous ?: HomeUiState.Ready(false, null, null, null, null)

            /** 发布一块进度，并把这份记成「页面上现在有的内容」（见 [lastContent]）。 */
            fun publish() {
                lastContent = ready
                _uiState.value = ready
            }

            /**
             * 这一块拉空（含 null）就保留旧值 —— 一次失败的请求不该让区块从页面上消失。
             * **首屏那趟不进这个分支**（[hasContent] 为 false）：那时「空」是要如实反映的
             * （最后统一判空落到 Error），不该拿一个本来就没有的旧值去顶。
             */
            fun <T> keep(next: T?, old: T?, isEmpty: (T) -> Boolean): T? =
                if (hasContent && (next == null || isEmpty(next))) old else next

            val pl = playlists.await()
            ready = ready.copy(playlists = keep(pl, ready.playlists) { it.isEmpty() })
            publish()

            val ch = charts.await()
            ready = ready.copy(charts = keep(ch, ready.charts) { it.isEmpty() })
            publish()

            val hp = homePage.await()
            ready = ready.copy(
                radar = keep(hp.radar, ready.radar) { it.isEmpty() },
                radarTitle = keep(hp.radarTitle, ready.radarTitle) { it.isEmpty() },
                guessTitle = keep(hp.guessTitle, ready.guessTitle) { it.isEmpty() },
                guessPages = keep(hp.guessPages, ready.guessPages) { it.isEmpty() } ?: emptyList(),
            )
            publish()

            // 「相似艺人」的卡面：要种子歌手 id，而它来自 homePage，所以只能等在这里发
            // （「私人漫游」那笔在开头就已发出，见 radioCover）。
            // 它不参与上面的逐块发布，晚到不影响任何区块的首屏。
            val artistCover = async {
                hp.seedArtistId?.let { homeRepo.simiArtist(it).firstOrNull()?.picUrl }
            }

            // 曲风表：只为「XX日推」功能卡取风格词 / 该曲风下的歌单。
            val st = styles.await()
            ready = ready.copy(styles = keep(st, ready.styles) { it.isEmpty() })
            publish()
            val tag2 = st.getOrNull(1)?.id
            val stylePlaylists = async {
                if (tag2 != null) homeRepo.stylePlaylists(tag2, "6") else emptyList()
            }

            val da = daily.await()
            val online = loggedInAsync.await()
            ready = ready.copy(
                dailySongs = keep(da, ready.dailySongs) { it.isEmpty() },
                // 登录态是单向的上升：只有未登录 → 登录才有信息量。静默刷新时抖动返回的
                // false 不该把已经登录的页面退回登录引导。
                loggedIn = if (silent) ready.loggedIn || online else online,
            )
            publish()

            // 曲风歌单与场景音乐**各自发布**：原来这一块是攒到最后跟卡面一起发的，
            // 于是它的可见性被后面那两个 await（私人漫游 / 相似艺人的封面）拖住 ——
            // 场景音乐那条链路本身就要 1 次分类表 + 每标签各 1 次歌单，是首页最慢的一块，
            // 再让别人拖一道，表现就是「这一区迟迟不出现、甚至看着像没了」。
            ready = ready.copy(
                stylePlaylists = keep(stylePlaylists.await(), ready.stylePlaylists) { it.isEmpty() },
            )
            publish()

            ready = ready.copy(scene = keep(scene.await(), ready.scene) { it.isEmpty() })
            publish()

            // 卡面最后合入（不挡住上面任何一块的展示）。拿不到就为 null —— **不再借别人的图**
            // 兜底，由 UI 画中性空封面；这一笔同时把 cardFacesSettled 标上，微光到此为止
            // （否则未登录时 simiArtist 返 301、相似艺人卡的封面会永远闪，见 [Ready.cardFacesSettled]）。
            ready = ready.copy(
                radioCover = keep(radioCover.await(), ready.radioCover) { it.isEmpty() },
                artistCover = keep(artistCover.await(), ready.artistCover) { it.isEmpty() },
                cardFacesSettled = true,
            )

            val allEmpty = ready.playlists.isNullOrEmpty() &&
                ready.charts.isNullOrEmpty() &&
                ready.radar.isNullOrEmpty() &&
                ready.dailySongs.isNullOrEmpty() &&
                ready.guessPages.isEmpty() &&
                ready.stylePlaylists.isNullOrEmpty() &&
                ready.scene.isNullOrEmpty()
            // 判 Error 的判据是「首屏且全空」：页面上已经有旧内容时（刷新），
            // 没有理由因为这一趟全拉空就清掉它去显示错误页。
            if (allEmpty && !hasContent) {
                _uiState.value = HomeUiState.Error
            } else {
                publish()
            }
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
