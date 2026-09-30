package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.ArtistAvatarSize
import com.thripleq.nume.ui.components.NumeEmptyState
import com.thripleq.nume.ui.components.NumeErrorState
import com.thripleq.nume.ui.components.NumeLoadMoreFailed
import com.thripleq.nume.ui.components.NumeLoadMoreIndicator
import com.thripleq.nume.ui.components.NumeMediaRow
import com.thripleq.nume.ui.components.SharedKeys
import com.thripleq.nume.ui.search.SearchTab
import com.thripleq.nume.ui.search.SearchUiState
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeShape

/* ── 结果页：分类页签 + 列表 ─────────────────────────────── */

private val TAB_LABELS = listOf(
    SearchTab.SONGS to "单曲",
    SearchTab.PLAYLISTS to "歌单",
    SearchTab.RADIOS to "播客",
    SearchTab.ALBUMS to "专辑",
    SearchTab.ARTISTS to "歌手",
)

@Composable
internal fun ResultsContent(
    state: SearchUiState,
    bottomPadding: Dp,
    onTab: (SearchTab) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onOpenTracks: (String, String, String, Rect) -> Unit,
    onOpenArtist: (String, String, String, Rect) -> Unit,
    onOpenRadio: (String, String, Rect) -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    Column(Modifier.fillMaxSize()) {
        TabStrip(state.tab, onTab)
        val listState = rememberLazyListState()
        // 只在「新查询 / 切页签」时回到顶部。
        //
        // 不能写成 `LaunchedEffect(state.tab, state.active) { scrollToItem(0) }`：从歌手页返回时
        // Search 目的地的组合被重建，LaunchedEffect 会**重新启动**（即便 key 没变），于是列表被
        // 滚回顶部——就是「点开歌手主页返回后列表回到开头」。用可保存的 key 记住上次已经复位过的
        // 状态：返回时恢复的 key 相同 → 不再复位；新查询/切页签 key 变了 → 才复位。
        var lastResetKey by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(state.tab, state.active) {
            val key = "${state.active}\u0000${state.tab}"
            if (key != lastResetKey) {
                lastResetKey = key
                listState.scrollToItem(0)
            }
        }

        val empty = !state.loading && isTabEmpty(state)
        when {
            state.loading -> SearchSkeleton()
            // 错误必须给出路：错误态可点重试（与播客/评论/歌手/曲目页同一交互语言）。
            empty -> if (state.error) {
                NumeErrorState(text = "搜索失败，点此重试", onRetry = onRetry)
            } else {
                NumeEmptyState("没有找到相关内容")
            }
            else -> {
                LoadMoreWatcher(listState, onLoadMore)
                ResultList(
                    state = state,
                    listState = listState,
                    bottomPadding = bottomPadding,
                    onLoadMore = onLoadMore,
                    onPlayTrack = onPlayTrack,
                    onOpenTracks = onOpenTracks,
                    onOpenArtist = onOpenArtist,
                    onOpenRadio = onOpenRadio,
                    shared = shared,
                    avScope = avScope,
                )
            }
        }
    }
}

@Composable
private fun TabStrip(selected: SearchTab, onSelect: (SearchTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TAB_LABELS.forEach { (tab, label) ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .height(38.dp)
                    .clip(NumeShape.Chip)
                    .then(
                        if (active) Modifier.border(1.5.dp, scheme.primary, NumeShape.Chip)
                        else Modifier,
                    )
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultList(
    state: SearchUiState,
    listState: LazyListState,
    bottomPadding: Dp,
    onLoadMore: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onOpenTracks: (String, String, String, Rect) -> Unit,
    onOpenArtist: (String, String, String, Rect) -> Unit,
    onOpenRadio: (String, String, Rect) -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
    ) {
        when (state.tab) {
            SearchTab.SONGS -> itemsIndexed(
                state.songs,
                key = { _, t -> "s_${t.id}" },
                contentType = { _, _ -> "song" },
            ) { index, track ->
                val sub = listOf(track.artist, track.albumName)
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                NumeMediaRow(
                    title = track.name,
                    subtitle = sub.ifBlank { null },
                    coverUrl = track.artworkUrl,
                    onClick = { onPlayTrack(index) },
                    trailing = {
                        IconButton(onClick = { /* 三点菜单：暂无功能 */ }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }

            SearchTab.PLAYLISTS -> itemsIndexed(
                state.playlists,
                key = { _, p -> "p_${p.id}" },
                contentType = { _, _ -> "playlist" },
            ) { _, p ->
                var rect by remember { mutableStateOf(Rect.Zero) }
                NumeMediaRow(
                    title = p.name,
                    subtitle = mediaSubtitle(
                        "${p.trackCount}首",
                        p.creator,
                        p.playCount,
                    ),
                    coverUrl = p.coverUrl,
                    onClick = { onOpenTracks("playlist", p.id, p.name, rect) },
                    modifier = Modifier.onGloballyPositioned { rect = it.boundsInWindow() },
                )
            }

            SearchTab.RADIOS -> itemsIndexed(
                state.radios,
                key = { _, r -> "r_${r.id}" },
                contentType = { _, _ -> "radio" },
            ) { _, r ->
                var rect by remember { mutableStateOf(Rect.Zero) }
                NumeMediaRow(
                    title = r.name,
                    subtitle = mediaSubtitle("${r.programCount}个声音", r.djName, r.playCount),
                    coverUrl = r.coverUrl,
                    onClick = { onOpenRadio(r.id, r.name, rect) },
                    modifier = Modifier.onGloballyPositioned { rect = it.boundsInWindow() },
                )
            }

            SearchTab.ALBUMS -> itemsIndexed(
                state.albums,
                key = { _, a -> "a_${a.id}" },
                contentType = { _, _ -> "album" },
            ) { _, a ->
                var rect by remember { mutableStateOf(Rect.Zero) }
                NumeMediaRow(
                    title = a.name,
                    subtitle = albumSubtitle(a),
                    coverUrl = a.coverUrl,
                    onClick = { onOpenTracks("album", a.id, a.name, rect) },
                    modifier = Modifier.onGloballyPositioned { rect = it.boundsInWindow() },
                )
            }

            SearchTab.ARTISTS -> itemsIndexed(
                state.artists,
                key = { _, a -> "ar_${a.id}" },
                contentType = { _, _ -> "artist" },
            ) { _, a ->
                var rect by remember { mutableStateOf(Rect.Zero) }
                val coverModifier = if (shared != null && avScope != null) {
                    with(shared) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = SharedKeys.artistAvatar(a.id)),
                            animatedVisibilityScope = avScope,
                            // 共享元素位移用 Emphasized 族（与壳动画同族——"同一个物体在动"），
                            // 默认弹簧尾巴过长、跨页拖沓。见 Motion.sharedBoundsSpec。
                            boundsTransform = { _, _ -> Motion.sharedBoundsSpec() },
                        )
                    }
                } else {
                    Modifier
                }
                NumeMediaRow(
                    title = a.name,
                    coverUrl = a.avatarUrl,
                    coverShape = CircleShape,
                    // 与歌手页头像同尺寸请求 → 共享 morph 命中同一张缓存图，不中途重解码。
                    coverRequestSize = ArtistAvatarSize,
                    coverModifier = coverModifier,
                    onClick = { onOpenArtist(a.id, a.name, a.avatarUrl.orEmpty(), rect) },
                    modifier = Modifier.onGloballyPositioned { rect = it.boundsInWindow() },
                )
            }
        }

        if (state.loadingMore) {
            item(key = "loading_more") { NumeLoadMoreIndicator() }
        } else if (state.loadMoreFailed) {
            item(key = "load_more_failed") { NumeLoadMoreFailed(onRetry = onLoadMore) }
        }
    }
}

@Composable
private fun LoadMoreWatcher(listState: LazyListState, onLoadMore: () -> Unit) {
    val shouldLoad by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }
}
