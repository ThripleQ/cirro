package com.thripleq.cirro.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.ui.components.BigCoverVisual
import com.thripleq.cirro.ui.components.CirroMediaRow
import com.thripleq.cirro.ui.components.CirroPageTitleBar
import com.thripleq.cirro.ui.components.CirroSectionHeader
import com.thripleq.cirro.ui.components.SharedSourceGuard
import com.thripleq.cirro.ui.components.cirroEntrySurface
import com.thripleq.cirro.ui.components.rememberPayTags
import com.thripleq.cirro.ui.components.rememberSharedSourceGuard
import com.thripleq.cirro.ui.components.shellSharedCover
import com.thripleq.cirro.ui.home.HomeUiState
import com.thripleq.cirro.ui.home.HomeViewModel
import com.thripleq.cirro.ui.theme.CirroShape
import com.valentinilk.shimmer.shimmer

/**
 * 探索页横滑列表的滚动状态（精选推荐 / 猜你喜欢 / 雷达歌单 / 场景音乐）。
 *
 * 必须 hoist 到 [HomeScreen]：开合面板走 [androidx.compose.animation.AnimatedContent]，
 * 关闭面板时网格会重新组合，写在 [HomeContent] 内的 `rememberLazyListState()` 会随组合树
 * 销毁而回到 0——与纵向 [listState] 同样的坑。
 */
internal data class HomeRowStates(
    val featured: LazyListState,
    /** 猜你喜欢的横向分页（一页 3 行，左右翻页）。 */
    val guess: LazyListState,
    val radar: LazyListState,
    val scene: LazyListState,
)

@Composable
internal fun HomeContent(
    data: HomeUiState.Ready,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayFeatured: (String) -> Unit,
    onExpand: (ExpandTarget) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState,
    rowStates: HomeRowStates,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    // 源可见性守卫：卡片被这张圆角纸的顶边切掉时不许挂共享元素
    // （overlay 不裁，飞的那份会画在纸上方 —— 见 [SharedSourceGuard]）。
    val guard = rememberSharedSourceGuard()

    // 顶层铺 `surfaceContainer` 当「标题条」底色，内容是一张 `surface` 圆角纸：纸的顶角
    // 圆角把底下的容器色露出来 —— 就是状态栏那条容器色 + 下方圆角内容的关系（用户参照）。
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        CirroPageTitleBar("探索") {
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CirroShape.SheetTop)
                .background(MaterialTheme.colorScheme.surface)
                // 视口 = 这张纸的裁切边界（列表就在纸里，标题条在纸外）。
                .then(guard.viewportModifier()),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {

        // 逐块渲染：null = 这块还没就绪，整个区块（含标题）不显示，避免未就绪
        // 时先闪出空标题/登录引导；就绪后再按是否有内容决定渲染。
        // 区块与卡片样式照抄 kanade 主页（uiautomator 实测坐标，2026-10-04）。

        // 精选推荐：功能卡横滑（方形封面 + 左上角类型徽标 + 底部名称条）。
        // kanade 用客户端侧枚举定死 6 张卡；我们从已有数据组装等价物（见
        // HomeUiState.Ready.featured），逐块就绪后这张行会自动长齐。
        val featured = data.featured
        if (featured.isNotEmpty()) {
            item(key = "h_featured") { CirroSectionHeader("精选推荐") }
            item(key = "row_featured") {
                // remember(featured)：卡片模型只在数据本身变时重建。否则每次重组都会新建
                // 一整列 KanadeCardModel（12 个对象）—— 下游 KanadeCardRow 即便 key 相同，
                // 收到的也是新 List 实例，跳过重组直接失效。
                val cards = remember(featured) {
                    featured.map {
                        // 徽标 = 卡名，底部条 = 说明（kanade 实测两者的分工就是这样）。
                        KanadeCardModel(
                            key = it.key,
                            coverUrl = it.coverUrl,
                            title = it.caption,
                            badge = it.label,
                            source = it.source,
                            id = it.id,
                            playKind = it.playKind,
                            coverPending = it.coverPending,
                        )
                    }
                }
                KanadeCardRow(
                    cards = cards,
                    spec = FeaturedCardSpec,
                    state = rowStates.featured,
                    guard = guard,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect, morphable ->
                    if (c.playKind != null) {
                        onPlayFeatured(c.playKind)
                    } else {
                        onExpand(
                            ExpandTarget(c.source, c.id, c.title, c.coverUrl, rect, morph = morphable),
                        )
                    }
                }
            }
        }

        // 猜你喜欢：「猜你喜欢的「XX」好歌」—— **一屏 3 行、左右翻页**。
        //
        // 标题与分页都是首页 block 流 HOMEPAGE_BLOCK_STYLE_RCMD 的原生形态
        // （`showType = HOMEPAGE_SLIDE_SONGLIST_ALIGN`，实测 4 组 × 3 首）：
        // 标题里的「XX」由服务端按口味算（实测「日文」），所以这一栏看着
        // 「没有固定标题」；内容一页 3 首、横滑换页，正是用户说的
        // 「有一些单曲，能左右翻」。我们照原样渲染，而不是拍平成一条纵向长列表。
        //
        // 以前这里是纵向 3 行 + 自己拼的标题：分页丢了、风格词拼不准，歌曲还要靠
        // `style-tag/home/song` 另取，拿不到就整块空 —— 那两张借它封面的功能卡
        // （私人漫游 / 相似艺人）也跟着空。
        val guessPages = data.guessPages.ifEmpty {
            // 回落：block 没给内容（未登录 / 服务端不出卡）时，用每日推荐凑一页。
            // **必须挡掉空表**：`emptyList<Track>().take(n)` 是非 null 的空表，
            // `listOf(它)` 会造出「一个空页」—— 那一栏渲染成一块没有任何行的空白，
            // 而且只有一页、怎么滑都不动（看起来正好像「不能左右翻页」）。
            data.dailySongs?.takeIf { it.isNotEmpty() }
                ?.take(GUESS_ROW_COUNT)
                ?.let { listOf(it) }
                .orEmpty()
        }
        if (guessPages.isNotEmpty() || data.dailySongs != null) {
            item(key = "h_guess") { CirroSectionHeader(data.guessHeadline) }
            if (guessPages.isNotEmpty()) {
                item(key = "row_guess") {
                    GuessSongPager(guessPages, rowStates.guess, onPlay)
                }
            } else {
                item(key = "login_daily") { LoginPrompt(onWebLogin) }
            }
        }

        // 雷达歌单：横滑**110dp 方形纯封面**（与场景音乐同宽，见 [RadarCardSpec]）。
        // **无徽标、无底部名称条** —— 官方雷达封面是程序生成的海报，图里自带「私人雷达」
        // 「新歌雷达」这类大字标题（实测下载封面即带标题 + 网易云角标），kanade 卡面就是
        // 这个样子，再叠一条名称条反而与它不像；行高因此比场景行矮 11dp（少一条名称条），
        // 不是因为卡更小。
        //
        // 数据源见 HomeRepository.homePage：首页 block 流里的
        // HOMEPAGE_BLOCK_MGC_PLAYLIST block（与「猜你喜欢」同一条流、同一次请求）。**它曾经被误判为"拿不到"而整区删除**
        // （搜 radar 字样的独立端点全 404、api-enhanced 里也没有 radar module）——
        // 因为它从来不是独立端点，服务端把雷达卡塞在通用 block 流里。
        val radarCards = data.radarCards
        if (radarCards.isNotEmpty()) {
            // 标题用**服务端的原文**，不写死「雷达歌单」：同一个 block 在未登录时给
            // 「网易云音乐的雷达歌单」、登录后给「<昵称>的雷达歌单」（2026-10-05 两态实测），
            // 写死就丢掉了这个区别。
            item(key = "h_radar") { CirroSectionHeader(data.radarTitle ?: "雷达歌单") }
            item(key = "row_radar") {
                // 同 row_featured：卡片模型按数据 remember，别每次重组重建（见那里的注释）。
                val cards = remember(radarCards) {
                    radarCards.map {
                        KanadeCardModel(it.id, it.coverUrl, it.name, null, HomeViewModel.RADAR_WIRE, it.id)
                    }
                }
                KanadeCardRow(
                    cards = cards,
                    spec = RadarCardSpec,
                    state = rowStates.radar,
                    guard = guard,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect, morphable ->
                    // wire 必须原样带上（同下方场景区）：目标端共享键是
                    // `shell:<source>:<id>`，写成 "playlist" 就与源端
                    // `shell:radar:<id>` 对不上，morph 直接不触发。
                    onExpand(
                        ExpandTarget(
                            HomeViewModel.RADAR_WIRE, c.id, c.title, c.coverUrl, rect,
                            morph = morphable,
                        ),
                    )
                }
            }
        }

        // 场景音乐：横滑小卡（110×92dp 封面 + 29dp 名称条，见 [SceneCardSpec] ——
        // kanade 这一区的封面**不是方形**，比宽矮 18dp）。
        //
        // kanade 这一区是 sceneTags（它那套 OpenAPI 的场景标签）。我们拿不到，
        // 但官方歌单分类（/weapi/playlist/catalogue）里 category 2 = 场景、
        // 3 = 情感，标签名与它高度重合（清晨/夜晚/学习/伤感/治愈/放松…），
        // 所以用标签卡；封面取该标签下第一张热门歌单（分类表里的标签本身没图）。
        val scene = data.sceneCards.take(SCENE_CARD_COUNT)
        if (scene.isNotEmpty()) {
            item(key = "h_scene") { CirroSectionHeader("场景音乐") }
            item(key = "row_scene") {
                // 同 row_featured：卡片模型按数据 remember，别每次重组重建（见那里的注释）。
                val cards = remember(scene) {
                    scene.map {
                        KanadeCardModel(it.playlistId, it.coverUrl, it.tag, null, "scene", it.playlistId)
                    }
                }
                KanadeCardRow(
                    // source "scene" = 区块专属 wire，保证整页共享键唯一
                    // （见 HomeViewModel.FEATURED_PLAYLIST_WIRE 注释）。
                    // **ExpandTarget 必须原样带上这个 wire**：目标端 banner 的
                    // 共享键是 `shell:<source>:<id>`，这里再写 "playlist" 就会
                    // 与源端 `shell:scene:<id>` 对不上，morph 直接不触发。
                    cards = cards,
                    spec = SceneCardSpec,
                    state = rowStates.scene,
                    guard = guard,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect, morphable ->
                    onExpand(
                        ExpandTarget("scene", c.id, c.title, c.coverUrl, rect, morph = morphable),
                    )
                }
            }
        }
    }
    }
}

/** 内容圆角纸的顶角半径见 [com.thripleq.cirro.ui.theme.CirroShape.SheetTop]（站内一份，不再本地定义）。 */

/**
 * 钉在顶部的「探索」大标题条由 [CirroPageTitleBar] 担当（与搜索页 / 我的页共用一份实现）。
 * 它本身透明，铺在 [HomeContent] 顶层的 `surfaceContainer` 之上；下方 [LazyColumn] 那张
 * `surface` 圆角纸的顶角会把容器色露出来。标题固定，内容在圆角纸里滚。
 */

/** kanade 式卡片的数据（尺寸见 [KanadeCardSpec]，视觉规格按区块选）。 */
internal data class KanadeCardModel(
    val key: Any,
    val coverUrl: String?,
    val title: String,
    /** 左上角白底类型徽标；null = 不画（雷达 / 场景卡）。 */
    val badge: String?,
    val source: String,
    val id: String,
    /** 非空 = 点了直接播一批歌（漫游 / 艺人），不走列表壳。 */
    val playKind: String? = null,
    /**
     * 卡面**还在路上**（只有「私人漫游 / 相似艺人」会为 true，见
     * [com.thripleq.cirro.ui.home.HomeViewModel.FeaturedCard.coverPending]）：
     * 画微光占位，而不是退到空封面 —— 空封面看着像「这张卡没有图」，微光才读得出「在加载」。
     *
     * 与之配对的是**出场条件**：卡面落定后仍为 null 的卡根本不会进这个列表
     * （见 `HomeViewModel.Ready.radioCover`），所以 `coverPending == false` 且
     * `coverUrl == null` 对这两张卡不会发生。
     */
    val coverPending: Boolean = false,
)

/**
 * 一种卡片规格（三处横滑行各一份）。
 *
 * 数值全部来自 kanade 主页的 uiautomator bounds（1080px 屏 ÷ 3 = dp），**别再凭感觉改**：
 *
 * | 行 | 卡宽 | 封面高 | 名称条 | 卡总高 |
 * |---|---|---|---|---|
 * | 精选推荐 | 146dp（440px） | 146（方形） | 37 | 183 |
 * | 雷达歌单 | **110**（330px） | 110（方形，纯封面） | — | 110 |
 * | 场景音乐 | 110（330px） | **92**（275px，不是方形） | 29（88px） | 121 |
 *
 * 【踩过的坑】雷达卡一开始跟着精选推荐用了 146dp —— 但 kanade 的雷达行与场景行
 * 是同一档宽度（都是 330px），只有精选推荐是 440px。雷达行比场景行矮，是因为它
 * **没有名称条**（雷达名印在封面海报上），而不是因为它更小。两组数字一混就会
 * 得出「雷达卡 146 方 + 无条 = 110 高」这种自相矛盾的规格，看上去就是雷达区
 * 比场景区大一圈、两者对不上。
 */
internal data class KanadeCardSpec(
    val width: Dp,
    /** 封面高度：只有场景音乐不是方形。 */
    val coverHeight: Dp,
    /** 底部名称条高度；[showTitleBar] = false 时忽略。 */
    val stripHeight: Dp,
    /** false = 纯封面卡（雷达歌单）：卡名在封面图上，不再画名称条。 */
    val showTitleBar: Boolean,
)

/** 精选推荐：440×550px = 146.7×183.3dp（方形封面 146.7 + 名称条 36.7）。 */
internal val FeaturedCardSpec = KanadeCardSpec(146.dp, 146.dp, 37.dp, showTitleBar = true)

/** 雷达歌单：330×330px = 110dp 方形**纯封面**（名称条不画，雷达名是海报图的一部分）。 */
internal val RadarCardSpec = KanadeCardSpec(110.dp, 110.dp, 0.dp, showTitleBar = false)

/** 场景音乐：330×363px = 110×121dp（封面 110×91.7 + 名称条 29.3）。 */
internal val SceneCardSpec = KanadeCardSpec(110.dp, 92.dp, 29.dp, showTitleBar = true)

/** 猜你喜欢好歌展示行数（kanade 实测 3 行）。 */
private const val GUESS_ROW_COUNT = 3

/** 场景音乐展示张数（kanade 频道首屏 3 张，多给几张可横滑）。 */
private const val SCENE_CARD_COUNT = 6

/** 卡片封面（上半部）圆角：与 [CirroShape.Card] 同半径，只用于共享元素内层的 clip。 */
private val CardCoverRadius = 16.dp

/** 横滑卡片行：三处横滑行共用，视觉规格由 [spec] 决定。 */
@Composable
private fun KanadeCardRow(
    cards: List<KanadeCardModel>,
    spec: KanadeCardSpec,
    state: LazyListState,
    guard: SharedSourceGuard,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    /** morphable = 这张卡的封面此刻完整可见（决定这次开合要不要走封面飞行）。 */
    onClick: (KanadeCardModel, Rect, morphable: Boolean) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        items(cards, key = { it.key }) { card ->
            KanadeCard(card, spec, guard, shared, avScope, onClick)
        }
    }
}

@Composable
private fun KanadeCard(
    card: KanadeCardModel,
    spec: KanadeCardSpec,
    guard: SharedSourceGuard,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    onClick: (KanadeCardModel, Rect, morphable: Boolean) -> Unit,
) {
    val showTitleBar = spec.showTitleBar
    var rect by remember { mutableStateOf<Rect?>(null) }
    // 封面此刻是否**完整**落在滚动视口里（被纸的顶边切掉一段就是 false）。
    // 组合期要读它决定挂不挂共享元素，所以必须是 state；但只在布尔翻转时才写，
    // 滚动途中不重组（见 [SharedSourceGuard]）。
    var shareable by remember { mutableStateOf(true) }
    Column(
        Modifier
            .width(spec.width)
            .clip(CirroShape.Card)
            // 点击时把「当时可见吗」一起交出去：目标端据此决定要不要飞
            // （关闭面板时本卡会重新组合、state 归位，判断只有记在目标数据上才留得住）。
            .clickable { rect?.let { onClick(card, it, shareable) } },
    ) {
        // 共享元素挂**封面**而非整卡：morph 的目标是面板 banner 封面（同为方形），
        // 名称条不该跟着飞。rect 也量封面 —— 与旧 BigCoverCard 的起点几何同口径。
        Box(
            Modifier
                .fillMaxWidth()
                .height(spec.coverHeight)
                .onGloballyPositioned { coords ->
                    val r = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                    rect = r
                    val ok = guard.isFullyVisible(r)
                    if (ok != shareable) shareable = ok
                }
                .then(
                    // playKind 非空的卡（漫游 / 艺人）点了直接播、不展开面板 —— 没有
                    // 对侧目标却挂共享元素，等于给转场注册一个孤儿 bounds，不挂。
                    //
                    // shareable = false（封面被列表裁切）：同样不挂 —— overlay 里飞的
                    // 那一份不受裁切，会画在顶部圆角纸上方，看上去像封面从标题栏钻出来。
                    // 不挂则面板照常开合、封面只淡入，不会出现「从遮挡处飞出来」的错动画。
                    if (shared != null && avScope != null && card.playKind == null && shareable) {
                        Modifier.shellSharedCover(shared, avScope, "shell:${card.source}:${card.id}")
                    } else {
                        Modifier
                    },
                )
                // 圆角 clip 必须写在 sharedBounds **内层**（Modifier 链更靠后）：
                // 转场时在 overlay 里飞的是 sharedBounds 圈住的这一份内容，圆角若只由
                // 整卡 Column（外层）提供，那一份就是直角 —— 表现正是「动画中卡片圆角没了」。
                // 旧 BigCoverCard 的 `.then(shared).clip()` 顺序也是这个道理。
                // 有名称条时只取上两角（下两角由整卡 clip 收口）；纯封面卡没有名称条
                // 托底，四角都得自己圆 —— 否则 overlay 里飞的那份是「上圆下直」。
                .clip(
                    if (showTitleBar) {
                        RoundedCornerShape(topStart = CardCoverRadius, topEnd = CardCoverRadius)
                    } else {
                        CirroShape.Card
                    },
                ),
        ) {
            // 卡面还在路上 → 微光占位（**不借别人的封面**）。
            //
            // 2026-10-05 用户：「我刚开 app 能看到一瞬间某几个卡片的封面是一样」—— 根因就是
            // 首页逐块回填时，私人漫游 / 相似艺人两张卡各自的两级兜底会同时落到
            // `playlists[0].coverUrl` 上（详见 HomeViewModel.Ready.radioCover 注释）。
            // 那两级兜底已删；这里没图就闪微光。**「确实没有」的卡不会走到这里**——
            // 卡面落定后仍无图时那两张卡整张不出场（用户：「如果是不存在而不是没加载完，
            // 就不显示」），所以这里的分支只剩「在路上」与「有图」两种。徽标照画。
            if (card.coverPending) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .shimmer()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                BigCoverVisual(card.coverUrl, card.title, Modifier.fillMaxSize())
            }
            card.badge?.let { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CirroShape.Chip)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (showTitleBar) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(spec.stripHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** 「歌手 - 专辑」副标题（kanade 猜你喜欢行格式；无专辑时只留歌手）。 */
private fun guessSubtitle(track: Track): String =
    if (track.albumName.isBlank()) track.artist
    else "${track.artist} - ${track.albumName}"

/**
 * 「猜你喜欢」的横向分页列表：**一页 3 首单曲行，左右滑动换页**。
 *
 * 分页形状不是我们定的 —— 服务端 block（`HOMEPAGE_BLOCK_STYLE_RCMD`，其
 * `showType = HOMEPAGE_SLIDE_SONGLIST_ALIGN`）本来就是每个 creative 3 首，
 * kanade 主页实测也是「一屏 3 行、能左右翻」。所以这里按**页宽 = 容器宽**排，
 * 一页独占一屏，滑到头才换下一批（而不是把 12 首排成一条长横滑）。
 *
 * **必须带吸附**（[rememberSnapFlingBehavior]）：页宽恰好等于容器宽，没有 snap 时
 * 松手停在任意位置，一屏里同时露出两页的半截，并且第 2 页以后永远对不齐 ——
 * 看上去就「不像翻页、像一条滑不动的长列表」。加了 snap 才是 kanade 那种
 * 一下翻一整页。
 *
 * 点任意一首，播放队列是**全部**猜你喜欢单曲而不是只有本页那 3 首 —— 连听不该
 * 在三首后断掉；起点落在被点的那首。
 */
@Composable
private fun GuessSongPager(
    pages: List<List<Track>>,
    state: LazyListState,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val all = remember(pages) { pages.flatten() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        val fling = rememberSnapFlingBehavior(lazyListState = state)
        LazyRow(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(pages, key = { i, _ -> "guess_page_$i" }) { _, page ->
                Column(Modifier.width(pageWidth)) {
                    page.forEach { track ->
                        CirroMediaRow(
                            title = track.name,
                            subtitle = guessSubtitle(track),
                            coverUrl = track.artworkUrl,
                            payTags = rememberPayTags(track),
                            onClick = {
                                val i = all.indexOfFirst { it.id == track.id }
                                onPlay(all, if (i >= 0) i else 0)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CirroShape.Card)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onLogin)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "登录后解锁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "去登录 ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
