package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.home.HomeUiState
import com.thripleq.nume.ui.theme.NumeShape

@Composable
internal fun HomeContent(
    data: HomeUiState.Ready,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onExpand: (ExpandTarget) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "topbar") { HomeTopBar(onRefresh) }

        // 逐块渲染：null = 这块还没就绪，整个区块（含标题）不显示，避免未就绪
        // 时先闪出空标题/登录引导；就绪后再按是否有内容决定渲染。

        // 每日推荐歌曲（小封面单曲行，每页 4 首左右翻页）
        val daily = data.dailySongs
        if (daily != null) {
            item(key = "h_daily") { SectionHeader("每日推荐歌曲") }
            if (daily.isNotEmpty()) {
                item(key = "daily_pager") { PagedTrackSection(tracks = daily, onPlay = onPlay) }
            } else {
                item(key = "login_daily") { LoginPrompt(onWebLogin) }
            }
        }

        // 推荐歌单（大封面横滑卡片）
        val playlists = data.playlists
        if (playlists.isNullOrEmpty().not()) {
            item(key = "h_pl") { SectionHeader("推荐歌单") }
            item(key = "row_pl") {
                CarouselRow(
                    items = playlists,
                    keyPrefix = "playlist",
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
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
            item(key = "h_chart") { SectionHeader("排行榜") }
            item(key = "row_chart") {
                CarouselRow(
                    items = charts,
                    keyPrefix = "chart",
                    keyOf = { it.id },
                    coverOf = { it.coverUrl },
                    nameOf = { it.name },
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
            item(key = "h_recent") { SectionHeader("最近播放") }
            if (recent.isNotEmpty()) {
                item(key = "recent_pager") { PagedTrackSection(tracks = recent, onPlay = onPlay) }
            } else {
                item(key = "login_recent") { LoginPrompt(onWebLogin) }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "探索",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** 大封面横滑卡片行（歌单 / 榜单）。 */
@Composable
private fun <T> CarouselRow(
    items: List<T>,
    keyPrefix: String,
    keyOf: (T) -> Any,
    coverOf: (T) -> String?,
    nameOf: (T) -> String,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    onClick: (T, Rect) -> Unit,
) {
    LazyRow(
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

/** 小封面单曲行：点了直接播（无展开动效）。 */
@Composable
private fun SmallTrackRow(track: Track, onClick: () -> Unit) {
    val context = LocalContext.current
    val model = remember(track.artworkUrl) {
        track.artworkUrl?.let { ImageRequest.Builder(context).data(it).size(96).build() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .numeEntrySurface()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(NumeShape.Chip),
        ) {
            if (model != null) {
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 每页固定 [TRACKS_PER_PAGE] 首的整页翻页列表：页面吸附，左右滑动切页；多页时显示页码点。
 *  首屏即满页，Pager 高度由第一页确定，后续不满的尾页顶对齐，翻页时高度不跳动。 */
private const val TRACKS_PER_PAGE = 4

@Composable
private fun PagedTrackSection(
    tracks: List<Track>,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val pageCount = (tracks.size + TRACKS_PER_PAGE - 1) / TRACKS_PER_PAGE
    val pagerState = rememberPagerState(pageCount = { pageCount })
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val start = page * TRACKS_PER_PAGE
            val end = (start + TRACKS_PER_PAGE).coerceAtMost(tracks.size)
            Column(Modifier.fillMaxWidth()) {
                for (i in start until end) {
                    val track = tracks[i]
                    SmallTrackRow(track) { onPlay(tracks, i) }
                }
            }
        }
        if (pageCount > 1) PagerDots(pageCount = pageCount, current = pagerState.currentPage)
    }
}

/** 页码点：选中主色放大，其余淡色。尺寸/颜色带 150ms 过渡 —— 翻页硬切会显得廉价。 */
@Composable
private fun PagerDots(pageCount: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val selected = i == current
            // 尺寸走 graphicsLayer 缩放（选中 1.16x），不逐帧重组布局尺寸。
            // 动画值 0=未选中 1=选中，用 tween 而非 spring：页码点是状态指示，不是交互反馈。
            val sel = remember { Animatable(if (selected) 1f else 0f) }
            LaunchedEffect(selected) {
                sel.animateTo(if (selected) 1f else 0f, tween(150, easing = FastOutSlowInEasing))
            }
            val dotColor = MaterialTheme.colorScheme.run { lerp(outlineVariant, primary, sel.value) }
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .graphicsLayer {
                        val s = 1f + 0.16f * sel.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(dotColor),
            )
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
