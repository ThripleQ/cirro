package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
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
 * 探索页四个横滑列表的滚动状态（播放歌单 / 榜单 / 每日推荐 / 最近播放）。
 *
 * 必须 hoist 到 [HomeScreen]：开合面板走 [androidx.compose.animation.AnimatedContent]，
 * 关闭面板时网格会重新组合，写在 [HomeContent] 内的 `rememberLazyListState()` 会随组合树
 * 销毁而回到 0——与纵向 [listState] 同样的坑。
 */
internal data class HomeRowStates(
    val playlists: LazyListState,
    val charts: LazyListState,
    val daily: LazyGridState,
    val recent: LazyGridState,
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

        // 每日推荐歌曲（小封面单曲行，每页 4 首左右翻页）
        val daily = data.dailySongs
        if (daily != null) {
            item(key = "h_daily") { NumeSectionHeader("每日推荐歌曲") }
            if (daily.isNotEmpty()) {
                item(key = "daily_pager") {
                    SongRowGrid(tracks = daily, onPlay = onPlay, state = rowStates.daily)
                }
            } else {
                item(key = "login_daily") { LoginPrompt(onWebLogin) }
            }
        }

        // 推荐歌单（大封面横滑卡片）
        val playlists = data.playlists
        if (playlists.isNullOrEmpty().not()) {
            item(key = "h_pl") { NumeSectionHeader("推荐歌单") }
            item(key = "row_pl") {
                CarouselRow(
                    items = playlists,
                    keyPrefix = "playlist",
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
                    state = rowStates.playlists,
                    shared = shared,
                    avScope = avScope,
                ) { p, rect ->
                    onExpand(ExpandTarget("playlist", p.id, p.name, p.coverUrl, rect))
                }
            }
        }

        // 排行榜（大封面横滑卡片）
        val charts = data.charts
        if (charts.isNullOrEmpty().not()) {
            item(key = "h_chart") { NumeSectionHeader("排行榜") }
            item(key = "row_chart") {
                CarouselRow(
                    items = charts,
                    keyPrefix = "chart",
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
                    state = rowStates.charts,
                    shared = shared,
                    avScope = avScope,
                ) { c, rect ->
                    onExpand(ExpandTarget("chart", c.id, c.name, c.coverUrl, rect))
                }
            }
        }

        // 最近播放（小封面单曲行，每页 4 首左右翻页）
        val recent = data.recentSongs
        if (recent != null) {
            item(key = "h_recent") { NumeSectionHeader("最近播放") }
            if (recent.isNotEmpty()) {
                item(key = "recent_pager") {
                    SongRowGrid(tracks = recent, onPlay = onPlay, state = rowStates.recent)
                }
            } else {
                item(key = "login_recent") { LoginPrompt(onWebLogin) }
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

/** 大封面横滑卡片行（歌单 / 榜单）。 */
@Composable
private fun <T> CarouselRow(
    items: List<T>,
    keyPrefix: String,
    keyOf: (T) -> Any,
    coverOf: (T) -> String?,
    nameOf: (T) -> String,
    state: LazyListState,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    onClick: (T, Rect) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { keyOf(it) }) { item ->
            BigCoverCard(
                coverUrl = coverOf(item),
                name = nameOf(item),
                shared = shared,
                avScope = avScope,
                sharedKey = "shell:$keyPrefix:${keyOf(item)}",
            ) { rect -> onClick(item, rect) }
        }
    }
}

internal val BigCoverSize = 116.dp

@Composable
private fun BigCoverCard(
    coverUrl: String?,
    name: String,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    sharedKey: Any,
    onClick: (Rect) -> Unit,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .size(BigCoverSize)
            .onGloballyPositioned { coords ->
                rect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            }
            // 容器变换（sharedBounds）：与目标 banner 封面挂同一个 sharedKey，框架把这张
            // 封面从卡片位置/尺寸 morph 到 banner，内容按 scaleToBounds 缩放（不逐帧重排）。
            .then(
                if (shared != null && avScope != null) {
                    Modifier.shellSharedCover(shared, avScope, sharedKey)
                } else {
                    Modifier
                },
            )
            .clip(NumeShape.Card)
            .clickable { rect?.let(onClick) },
    ) {
        BigCoverVisual(coverUrl, name, Modifier.fillMaxSize())
    }
}

/** 单曲区块行高（封面 52dp + 上下 8dp）。 */
private val TrackRowHeight = 68.dp

/**
 * 单曲区块：横向滚动的多行网格（最多 4 行），每列占屏 ~47.5%——YT Music / InnerTune 的
 * 「Quick picks」式布局：一屏内把整组歌摊开、横向滑查看更多，比整页翻页更易扫读。
 * 布局参照 InnerTune（z-huang/InnerTune，Material 3 YT Music 客户端）的 HomeScreen Quick Picks。
 */
@Composable
private fun SongRowGrid(
    tracks: List<Track>,
    onPlay: (List<Track>, Int) -> Unit,
    state: LazyGridState,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth = maxWidth * 0.475f
        val rows = minOf(4, tracks.size).coerceAtLeast(1)
        LazyHorizontalGrid(
            state = state,
            rows = GridCells.Fixed(rows),
            modifier = Modifier
                .fillMaxWidth()
                .height(TrackRowHeight * rows),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
            ) { index, track ->
                NumeMediaRow(
                    title = track.name,
                    subtitle = track.artist.ifBlank { null },
                    coverUrl = track.artworkUrl,
                    onClick = { onPlay(tracks, index) },
                    modifier = Modifier.width(itemWidth),
                )
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
