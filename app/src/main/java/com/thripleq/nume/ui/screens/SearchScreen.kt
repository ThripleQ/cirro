package com.thripleq.nume.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.SearchAlbum
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SharedKeys
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.search.SearchTab
import com.thripleq.nume.ui.search.SearchUiState
import com.thripleq.nume.ui.search.SearchViewModel
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 搜索 tab：落地页给「语种 / 风格 / 场景」标签，提交后按
 * 单曲 / 歌单 / 播客 / 专辑 / 歌手 五类分页展示（对齐 kanade）。
 */
@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String, origin: Rect) -> Unit,
    onOpenArtist: (id: String, name: String, avatarUrl: String, origin: Rect) -> Unit,
    onOpenRadio: (id: String, name: String, origin: Rect) -> Unit,
    islandHeight: Float = 0f,
    // 共享元素试验：歌手头像在「搜索结果行 ↔ 歌手页头部」间做官方 sharedElement。
    // 为空则退化为普通图片（不影响其他调用方）。
    shared: SharedTransitionScope? = null,
    avScope: AnimatedVisibilityScope? = null,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    // 结果态下返回键先退回落地页，而不是退出 app。
    BackHandler(enabled = state.active != null) { vm.onBack() }

    val bottomPadding = (islandHeight + 16f).dp

    Column(Modifier.fillMaxSize()) {
        SearchTopBar(
            query = state.query,
            inResults = state.active != null,
            onQueryChange = vm::onQueryChange,
            onSubmit = vm::onSubmit,
            onClear = vm::onClear,
            onBack = vm::onBack,
        )
        when {
            state.active == null -> LandingContent(bottomPadding, onTag = vm::onTagSearch)
            else -> ResultsContent(
                state = state,
                bottomPadding = bottomPadding,
                onTab = vm::onTabSelect,
                onLoadMore = vm::onLoadMore,
                onPlayTrack = vm::onPlayTrack,
                onOpenTracks = onOpenTracks,
                onOpenArtist = onOpenArtist,
                onOpenRadio = onOpenRadio,
                shared = shared,
                avScope = avScope,
            )
        }
    }
}

/* ── 顶部搜索框 ─────────────────────────────────────────── */

@Composable
private fun SearchTopBar(
    query: String,
    inResults: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inResults) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = scheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(NumeShape.Capsule)
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.NumPadEnter)
                        ) {
                            keyboard?.hide()
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "单曲、歌单、专辑以及更多内容",
                                color = scheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onClear()
                            keyboard?.hide()
                        },
                )
            }
        }
    }
}

/* ── 落地页：分类标签 ───────────────────────────────────── */

private val TAG_SECTIONS: List<Pair<String, List<String>>> = listOf(
    "语种" to listOf("华语", "欧美", "日语", "韩语", "粤语"),
    "风格" to listOf(
        "流行", "摇滚", "民谣", "电子", "说唱", "轻音乐", "爵士", "乡村",
        "R&B/Soul", "古典", "英伦", "金属", "朋克", "蓝调", "雷鬼", "世界音乐",
        "拉丁", "另类/独立", "New Age", "古风", "Bossa Nova", "后摇", "舞曲", "音乐剧",
    ),
    "场景" to listOf("清晨", "夜晚", "学习", "工作", "午休", "通勤", "运动", "旅行", "派对", "咖啡"),
)

@Composable
private fun LandingContent(bottomPadding: androidx.compose.ui.unit.Dp, onTag: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        TAG_SECTIONS.forEach { (title, tags) ->
            item(key = "h_$title") { TagHeader(title) }
            itemsIndexed(
                items = tags.chunked(2),
                key = { index, _ -> "$title-$index" },
            ) { index, pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index == 0) 0.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { tag ->
                        TagChip(tag, Modifier.weight(1f)) { onTag(tag) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TagHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun TagChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(NumeShape.CardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ── 结果页：分类页签 + 列表 ─────────────────────────────── */

private val TAB_LABELS = listOf(
    SearchTab.SONGS to "单曲",
    SearchTab.PLAYLISTS to "歌单",
    SearchTab.RADIOS to "播客",
    SearchTab.ALBUMS to "专辑",
    SearchTab.ARTISTS to "歌手",
)

@Composable
private fun ResultsContent(
    state: SearchUiState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onTab: (SearchTab) -> Unit,
    onLoadMore: () -> Unit,
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
        LaunchedEffect(state.tab, state.active) { listState.scrollToItem(0) }

        val empty = !state.loading && isTabEmpty(state)
        when {
            state.loading -> SearchSkeleton()
            empty -> CenteredBox {
                Text(
                    text = if (state.error) "搜索失败，请稍后重试" else "没有找到相关内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                LoadMoreWatcher(listState, onLoadMore)
                ResultList(
                    state = state,
                    listState = listState,
                    bottomPadding = bottomPadding,
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
    bottomPadding: androidx.compose.ui.unit.Dp,
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
                SongRow(track) { onPlayTrack(index) }
            }

            SearchTab.PLAYLISTS -> itemsIndexed(
                state.playlists,
                key = { _, p -> "p_${p.id}" },
                contentType = { _, _ -> "playlist" },
            ) { _, p ->
                MediaRow(
                    coverUrl = p.coverUrl,
                    title = p.name,
                    subtitle = mediaSubtitle(
                        "${p.trackCount}首",
                        p.creator,
                        p.playCount,
                    ),
                    circle = false,
                ) { rect -> onOpenTracks("playlist", p.id, p.name, rect) }
            }

            SearchTab.RADIOS -> itemsIndexed(
                state.radios,
                key = { _, r -> "r_${r.id}" },
                contentType = { _, _ -> "radio" },
            ) { _, r ->
                MediaRow(
                    coverUrl = r.coverUrl,
                    title = r.name,
                    subtitle = mediaSubtitle("${r.programCount}个声音", r.djName, r.playCount),
                    circle = false,
                ) { rect -> onOpenRadio(r.id, r.name, rect) }
            }

            SearchTab.ALBUMS -> itemsIndexed(
                state.albums,
                key = { _, a -> "a_${a.id}" },
                contentType = { _, _ -> "album" },
            ) { _, a ->
                MediaRow(
                    coverUrl = a.coverUrl,
                    title = a.name,
                    subtitle = albumSubtitle(a),
                    circle = false,
                ) { rect -> onOpenTracks("album", a.id, a.name, rect) }
            }

            SearchTab.ARTISTS -> itemsIndexed(
                state.artists,
                key = { _, a -> "ar_${a.id}" },
                contentType = { _, _ -> "artist" },
            ) { _, a ->
                val coverModifier = if (shared != null && avScope != null) {
                    with(shared) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = SharedKeys.artistAvatar(a.id)),
                            animatedVisibilityScope = avScope,
                        )
                    }
                } else {
                    Modifier
                }
                MediaRow(
                    coverUrl = a.avatarUrl,
                    title = a.name,
                    subtitle = null,
                    circle = true,
                    coverModifier = coverModifier,
                ) { rect -> onOpenArtist(a.id, a.name, a.avatarUrl.orEmpty(), rect) }
            }
        }

        if (state.loadingMore) {
            item(key = "loading_more") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp)) }
            }
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

/* ── 行组件 ─────────────────────────────────────────── */

@Composable
private fun SongRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(track.artworkUrl, track.name, 52.dp, circle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(track.artist, track.albumName)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { /* 三点菜单：暂无功能 */ }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaRow(
    coverUrl: String?,
    title: String,
    subtitle: String?,
    circle: Boolean,
    coverModifier: Modifier = Modifier,
    onClick: (Rect) -> Unit,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rect = it.boundsInWindow() }
            .clickable { onClick(rect) }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(coverUrl, title, 52.dp, circle, modifier = coverModifier)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    circle: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = if (circle) CircleShape else NumeShape.Chip
    val context = LocalContext.current
    val model = remember(url) {
        url?.let { ImageRequest.Builder(context).data(it).size(120).build() }
    }
    Box(
        modifier
            .size(size)
            .clip(shape),
    ) {
        if (model != null) {
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 搜索结果骨架：分类页签下方重复结果行（封面 + 标题/副标题）。四类页签行高一致，通用。 */
@Composable
private fun SearchSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(top = 4.dp),
    ) {
        repeat(9) { SearchSkeletonRow() }
    }
}

@Composable
private fun SearchSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(52.dp), NumeShape.Chip)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
        }
    }
}

/* ── 小工具 ─────────────────────────────────────────── */

private fun isTabEmpty(s: SearchUiState): Boolean = when (s.tab) {
    SearchTab.SONGS -> s.songs.isEmpty()
    SearchTab.PLAYLISTS -> s.playlists.isEmpty()
    SearchTab.RADIOS -> s.radios.isEmpty()
    SearchTab.ALBUMS -> s.albums.isEmpty()
    SearchTab.ARTISTS -> s.artists.isEmpty()
}

private fun mediaSubtitle(count: String, creator: String, playCount: Long): String {
    val by = creator.takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""
    val play = if (playCount > 0) " · 播放${formatPlay(playCount)}" else ""
    return count + by + play
}

private fun albumSubtitle(a: SearchAlbum): String {
    val date = if (a.publishTime > 0) {
        SimpleDateFormat("yyyy.M.d", Locale.getDefault()).format(Date(a.publishTime))
    } else {
        ""
    }
    return listOf(a.artist, date).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun formatPlay(n: Long): String = when {
    n >= 100_000_000 -> trim1(n / 100_000_000.0) + "亿"
    n >= 10_000 -> trim1(n / 10_000.0) + "万"
    else -> n.toString()
}

private fun trim1(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
