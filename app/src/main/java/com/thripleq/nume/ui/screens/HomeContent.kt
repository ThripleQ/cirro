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
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 探索页三个横滑列表的滚动状态（精选推荐 / 雷达歌单 / 场景音乐）。
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
                        KanadeCardModel(it.key, it.coverUrl, it.title, it.badge, it.source, it.id)
                    },
                    cardWidth = FeaturedCardSize,
                    state = rowStates.featured,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    onExpand(ExpandTarget(c.source, c.id, c.title, c.coverUrl, rect))
                }
            }
        }

        // 猜你喜欢的好歌：竖列表（封面 + 歌名 + 歌手 - 专辑），点了直接播。
        // kanade 的标题是「猜你喜欢的「风格」好歌」，风格词来自它的 styleList 接口
        // （OpenAPI 专属）；我们没有，故标题不带风格限定。
        val daily = data.dailySongs
        if (daily != null) {
            item(key = "h_guess") { NumeSectionHeader("猜你喜欢的好歌") }
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

        // 雷达歌单：横滑卡（无左上角徽标 —— kanade 里「私人雷达」等字样是封面图自带的，
        // 我们用底部名称条替代；名称条两种卡都有）。数据取 RECOMMEND_RESOURCE 里
        // 名字带「雷达」的条目；当前账号实测只有「私人雷达」一条，有几个显示几个。
        val radar = data.radar.orEmpty().filter { it.name.contains("雷达") }
        if (radar.isNotEmpty()) {
            item(key = "h_radar") { NumeSectionHeader("雷达歌单") }
            item(key = "row_radar") {
                KanadeCardRow(
                    cards = radar.map { KanadeCardModel(it.id, it.coverUrl, it.name, null, "playlist", it.id) },
                    cardWidth = FeaturedCardSize,
                    state = rowStates.radar,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    onExpand(ExpandTarget("playlist", c.id, c.title, c.coverUrl, rect))
                }
            }
        }

        // 场景音乐：横滑小卡（110dp 窄版，同样只有底部名称条）。
        // kanade 的场景音乐走 OpenAPI 的 scene/radio 接口（情绪/场景标签歌单）；
        // 我们用大众化推荐歌单（RECOMMEND_PLAYLISTS）顶位，取前 6 张。
        val scene = data.playlists.orEmpty().take(SCENE_CARD_COUNT)
        if (scene.isNotEmpty()) {
            item(key = "h_scene") { NumeSectionHeader("场景音乐") }
            item(key = "row_scene") {
                KanadeCardRow(
                    cards = scene.map { KanadeCardModel(it.id, it.coverUrl, it.name, null, "playlist", it.id) },
                    cardWidth = SceneCardSize,
                    state = rowStates.scene,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    onExpand(ExpandTarget("playlist", c.id, c.title, c.coverUrl, rect))
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
 * - **雷达歌单**卡：同宽但**无徽标** —— kanade 里「私人雷达」等字样是封面图自带的（dump
 *   对应位置读不到文本节点），我们补一条底部名称条，两种卡其余完全一致；
 * - **场景音乐**卡：110dp（330px）窄版，同样只有名称条。
 */
internal data class KanadeCardModel(
    val key: Any,
    val coverUrl: String?,
    val title: String,
    /** 左上角白底类型徽标；null = 不画（雷达 / 场景卡）。 */
    val badge: String?,
    val source: String,
    val id: String,
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

/** 横滑卡片行：精选推荐（带徽标）与雷达 / 场景（无徽标）共用。 */
@Composable
private fun KanadeCardRow(
    cards: List<KanadeCardModel>,
    cardWidth: Dp,
    state: LazyListState,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    onClick: (KanadeCardModel, Rect) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        items(cards, key = { it.key }) { card ->
            KanadeCard(card, cardWidth, shared, avScope, onClick)
        }
    }
}

@Composable
private fun KanadeCard(
    card: KanadeCardModel,
    cardWidth: Dp,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
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
                    if (shared != null && avScope != null) {
                        Modifier.shellSharedCover(shared, avScope, "shell:${card.source}:${card.id}")
                    } else {
                        Modifier
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
