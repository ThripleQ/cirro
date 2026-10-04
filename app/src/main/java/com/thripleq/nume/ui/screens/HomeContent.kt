package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.NumeMediaRow
import com.thripleq.nume.ui.components.NumePageTitleBar
import com.thripleq.nume.ui.components.NumeSectionHeader
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.home.HomeUiState
import com.thripleq.nume.ui.home.HomeViewModel
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 探索页横滑列表的滚动状态（精选推荐 / 雷达歌单 / 场景音乐）。
 *
 * 必须 hoist 到 [HomeScreen]：开合面板走 [androidx.compose.animation.AnimatedContent]，
 * 关闭面板时网格会重新组合，写在 [HomeContent] 内的 `rememberLazyListState()` 会随组合树
 * 销毁而回到 0——与纵向 [listState] 同样的坑。
 */
internal data class HomeRowStates(
    val featured: LazyListState,
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
    // 顶层铺 `surfaceContainer` 当「标题条」底色，内容是一张 `surface` 圆角纸：纸的顶角
    // 圆角把底下的容器色露出来 —— 就是状态栏那条容器色 + 下方圆角内容的关系（用户参照）。
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        NumePageTitleBar("探索") {
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
                .clip(RoundedCornerShape(topStart = HomeSheetRadius, topEnd = HomeSheetRadius))
                .background(MaterialTheme.colorScheme.surface),
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
            item(key = "h_featured") { NumeSectionHeader("精选推荐") }
            item(key = "row_featured") {
                KanadeCardRow(
                    cards = featured.map {
                        // 徽标 = 卡名，底部条 = 说明（kanade 实测两者的分工就是这样）。
                        KanadeCardModel(
                            key = it.key,
                            coverUrl = it.coverUrl,
                            title = it.caption,
                            badge = it.label,
                            source = it.source,
                            id = it.id,
                            playKind = it.playKind,
                        )
                    },
                    cardWidth = FeaturedCardSize,
                    state = rowStates.featured,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    if (c.playKind != null) {
                        onPlayFeatured(c.playKind)
                    } else {
                        onExpand(ExpandTarget(c.source, c.id, c.title, c.coverUrl, rect))
                    }
                }
            }
        }

        // 猜你喜欢的好歌：竖列表（封面 + 歌名 + 歌手 - 专辑），点了直接播。
        // kanade 的标题是「猜你喜欢的「风格」好歌」，风格词来自它的 styleList 接口。
        // 2026-10-04 补齐：风格词改从 `/api/tag/list/get` 拿，歌曲优先用同体系的
        // `/api/style-tag/home/song`（匿名也常能拿到），取不到才回落每日推荐。
        val daily = data.guessSongs
        if (daily != null) {
            item(key = "h_guess") { NumeSectionHeader(data.guessTitle) }
            if (daily.isNotEmpty()) {
                itemsIndexed(
                    daily.take(GUESS_ROW_COUNT),
                    key = { i, t -> "guess_${t.id}_$i" },
                ) { index, track ->
                    NumeMediaRow(
                        title = track.name,
                        subtitle = guessSubtitle(track),
                        coverUrl = track.artworkUrl,
                        onClick = { onPlay(daily, index) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            } else {
                item(key = "login_daily") { LoginPrompt(onWebLogin) }
            }
        }

        // 雷达歌单：横滑大卡（146dp 方形封面）。**无徽标、无底部名称条** ——
        // 官方雷达封面是程序生成的海报，图里自带「私人雷达」「新歌雷达」这类大字
        // 标题（实测下载封面即带标题 + 网易云角标），kanade 卡面就是这个样子，
        // 再叠一条名称条反而与它不像。
        //
        // 数据源见 HomeRepository.radarPlaylists：首页 block 流里的
        // HOMEPAGE_BLOCK_MGC_PLAYLIST block。**它曾经被误判为"拿不到"而整区删除**
        // （搜 radar 字样的独立端点全 404、api-enhanced 里也没有 radar module）——
        // 因为它从来不是独立端点，服务端把雷达卡塞在通用 block 流里。
        val radarCards = data.radarCards
        if (radarCards.isNotEmpty()) {
            item(key = "h_radar") { NumeSectionHeader("雷达歌单") }
            item(key = "row_radar") {
                KanadeCardRow(
                    cards = radarCards.map {
                        KanadeCardModel(it.id, it.coverUrl, it.name, null, HomeViewModel.RADAR_WIRE, it.id)
                    },
                    cardWidth = FeaturedCardSize,
                    state = rowStates.radar,
                    shared = shared,
                    avScope = avScope,
                    showTitleBar = false,
                ) { c, rect ->
                    // wire 必须原样带上（同下方场景区）：目标端共享键是
                    // `shell:<source>:<id>`，写成 "playlist" 就与源端
                    // `shell:radar:<id>` 对不上，morph 直接不触发。
                    onExpand(ExpandTarget(HomeViewModel.RADAR_WIRE, c.id, c.title, c.coverUrl, rect))
                }
            }
        }

        // 场景音乐：横滑小卡（110dp 窄版，只有底部名称条 —— kanade 实测卡面就是
        // 「封面 + 底部标签名」，日语弦歌 / 伤感 / 浪漫时光 这类词）。
        //
        // kanade 这一区是 sceneTags（它那套 OpenAPI 的场景标签）。我们拿不到，
        // 但官方歌单分类（/weapi/playlist/catalogue）里 category 2 = 场景、
        // 3 = 情感，标签名与它高度重合（清晨/夜晚/学习/伤感/治愈/放松…），
        // 所以用标签卡；封面取该标签下第一张热门歌单（分类表里的标签本身没图）。
        val scene = data.sceneCards.take(SCENE_CARD_COUNT)
        if (scene.isNotEmpty()) {
            item(key = "h_scene") { NumeSectionHeader("场景音乐") }
            item(key = "row_scene") {
                KanadeCardRow(
                    // source "scene" = 区块专属 wire，保证整页共享键唯一
                    // （见 HomeViewModel.FEATURED_PLAYLIST_WIRE 注释）。
                    // **ExpandTarget 必须原样带上这个 wire**：目标端 banner 的
                    // 共享键是 `shell:<source>:<id>`，这里再写 "playlist" 就会
                    // 与源端 `shell:scene:<id>` 对不上，morph 直接不触发。
                    cards = scene.map {
                        KanadeCardModel(it.playlistId, it.coverUrl, it.tag, null, "scene", it.playlistId)
                    },
                    cardWidth = SceneCardSize,
                    state = rowStates.scene,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    onExpand(ExpandTarget("scene", c.id, c.title, c.coverUrl, rect))
                }
            }
        }
    }
    }
}

/** 内容圆角纸的顶角半径（状态栏容器色会从两角露出）。 */
internal val HomeSheetRadius = 28.dp

/**
 * 钉在顶部的「探索」大标题条由 [NumePageTitleBar] 担当（与搜索页 / 我的页共用一份实现）。
 * 它本身透明，铺在 [HomeContent] 顶层的 `surfaceContainer` 之上；下方 [LazyColumn] 那张
 * `surface` 圆角纸的顶角会把容器色露出来。标题固定，内容在圆角纸里滚。
 */

/**
 * kanade 式卡片（布局实测 2026-10-04，uiautomator 坐标 ÷ 3 = dp）：
 *
 * - **精选推荐**卡：方形封面 146dp（440px）+ 左上角白底圆角**类型徽标** + 底部浅灰名称条；
 * - **雷达歌单**卡：同宽同高的**纯封面**（[showTitleBar] = false）—— 官方雷达封面是
 *   程序生成的海报，图里自带「私人雷达」「新歌雷达」这类大字标题（下载封面实测确认），
 *   在 kanade 的 dump 里对应位置读不到文本节点，正因为那不是文本而是图；
 * - **场景音乐**卡：110dp（330px）窄版，只有底部名称条（这里的标签名确实是文本）。
 */
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
)

/** 精选推荐 / 雷达歌单卡宽（kanade 实测 440px ÷ 3）。 */
internal val FeaturedCardSize = 146.dp

/** 场景音乐卡宽（kanade 实测 330px ÷ 3）。 */
internal val SceneCardSize = 110.dp

/** 猜你喜欢好歌展示行数（kanade 实测 3 行）。 */
private const val GUESS_ROW_COUNT = 3

/** 场景音乐展示张数（kanade 实测 3 张，我们多给几张可横滑）。 */
private const val SCENE_CARD_COUNT = 6

/** 卡片底部名称条高度（kanade 卡高约 1/4）。 */
private val CardStripHeight = 32.dp

/** 卡片封面（上半部）圆角：与 [NumeShape.Card] 同半径，只用于共享元素内层的 clip。 */
private val CardCoverRadius = 16.dp

/** 横滑卡片行：精选推荐（带徽标 + 名称条）与雷达（纯封面）/ 场景（窄版 + 名称条）共用。 */
@Composable
private fun KanadeCardRow(
    cards: List<KanadeCardModel>,
    cardWidth: Dp,
    state: LazyListState,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    /** false = 纯封面卡（雷达歌单）：卡名在封面图上，不再画名称条。 */
    showTitleBar: Boolean = true,
    onClick: (KanadeCardModel, Rect) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        items(cards, key = { it.key }) { card ->
            KanadeCard(card, cardWidth, shared, avScope, showTitleBar, onClick)
        }
    }
}

@Composable
private fun KanadeCard(
    card: KanadeCardModel,
    cardWidth: Dp,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    showTitleBar: Boolean,
    onClick: (KanadeCardModel, Rect) -> Unit,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    Column(
        Modifier
            .width(cardWidth)
            .clip(NumeShape.Card)
            .clickable { rect?.let { onClick(card, it) } },
    ) {
        // 共享元素挂**封面**而非整卡：morph 的目标是面板 banner 封面（同为方形），
        // 名称条不该跟着飞。rect 也量封面 —— 与旧 BigCoverCard 的起点几何同口径。
        Box(
            Modifier
                .fillMaxWidth()
                .height(cardWidth)
                .onGloballyPositioned { coords ->
                    rect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                }
                .then(
                    // playKind 非空的卡（漫游 / 艺人）点了直接播、不展开面板 —— 没有
                    // 对侧目标却挂共享元素，等于给转场注册一个孤儿 bounds，不挂。
                    if (shared != null && avScope != null && card.playKind == null) {
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
                        NumeShape.Card
                    },
                ),
        ) {
            BigCoverVisual(card.coverUrl, card.title, Modifier.fillMaxSize())
            card.badge?.let { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(NumeShape.Chip)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (showTitleBar) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CardStripHeight)
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

@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(NumeShape.Card)
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
