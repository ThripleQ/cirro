package com.thripleq.nume.ui.screens

import com.thripleq.nume.ui.theme.NumeShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.CoverExpandShell
import com.thripleq.nume.ui.components.LocalShellSettled
import com.thripleq.nume.ui.components.ShellPanel
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.theme.Motion
import com.valentinilk.shimmer.shimmer
import com.thripleq.nume.ui.home.HomeUiState
import com.thripleq.nume.ui.home.HomeViewModel

/** 探索 tab：每日推荐歌曲 / 推荐歌单 / 排行榜 / 最近播放。
 *  大封面 = 歌单/榜单（横滑卡片，点开走胶囊伸展壳进全屏列表）；
 *  小封面 = 单曲（内联行，点了直接播，无展开动效）。 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onWebLogin: () -> Unit,
    islandHeight: Float = 0f,
    onShellOpenChange: (Boolean) -> Unit = {},
    shared: SharedTransitionScope? = null,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    var expand by remember { mutableStateOf<ExpandTarget?>(null) }
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }
    // 滚动位置 hoist：容器变换用 AnimatedContent 在「网格 ↔ 面板」间切换，关闭面板时
    // 网格会重新组合——不 hoist 就会跳回顶部。
    val listState = rememberLazyListState()
    val bottomPad = islandClearance + 16.dp

    // 展开壳打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = expand != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }

    // 稳定的回调：开/关壳只改 expand，若 lambda 每次重组都新建会把整页列表（HomeContent）
    // 一起重组，产生尖峰帧。用 remember 固定后 expand 变化不会再重组底下列表。
    val onPlay = remember(vm) { vm::onPlayTrack }
    val onRefresh = remember(vm) { { vm.load() } }
    val onExpand = remember { { t: ExpandTarget -> expand = t } }
    val onDismiss = remember { { expand = null } }

    if (Motion.SharedShellEnabled && shared != null) {
        // 官方容器变换：源卡片（CarouselRow 里的 BigCoverCard）与全屏面板挂同一个
        // key 的 sharedBounds，由框架把「同一个表面」从卡片 morph 到全屏。
        with(shared) {
            AnimatedContent(
                targetState = expand,
                transitionSpec = {
                    // 网格必须像歌手页搜索列表那样**真实淡出**（90ms），绝不能是 ExitTransition.None：
                    // None 会让退出内容在首帧就被判定完成、立刻移除，共享元素的「起点」随之消失
                    // → 框架匹配不到两端、封面直接闪现在 banner（就是「某些情况跳过动画」）。
                    // 网格底色与面板同为 surface，短暂的空档不可见。封面 morph 由 sharedBounds
                    // 主导（350ms），面板只跑 260ms 铺底。
                    if (targetState != null) {
                        fadeIn(tween(Motion.ShellPanelInMs, easing = Motion.EmphasizedDecelerate)) togetherWith
                            fadeOut(tween(Motion.NavExitMs, easing = Motion.Standard))
                    } else {
                        // 收起：网格快速铺底（接住飞回的封面），面板随后淡出。
                        fadeIn(tween(Motion.NavExitMs, easing = Motion.Standard)) togetherWith
                            fadeOut(tween(Motion.ShellPanelOutMs, easing = Motion.Emphasized))
                    }
                },
                label = "homeShell",
            ) { target ->
                val scope = this
                if (target == null) {
                    HomeBodyUi(
                        state = state,
                        listState = listState,
                        bottomPadding = bottomPad,
                        onPlay = onPlay,
                        onExpand = onExpand,
                        onWebLogin = onWebLogin,
                        onRefresh = onRefresh,
                        shared = shared,
                        avScope = scope,
                    )
                } else {
                    ShellPanel(onDismiss = onDismiss) {
                        HomePanelContent(
                            target = target,
                            bottomPadding = bottomPad,
                            onOpenPlayer = onOpenPlayer,
                            onDismiss = onDismiss,
                            shared = shared,
                            avScope = scope,
                        )
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            HomeBodyUi(
                state = state,
                listState = listState,
                bottomPadding = bottomPad,
                onPlay = onPlay,
                onExpand = onExpand,
                onWebLogin = onWebLogin,
                onRefresh = onRefresh,
                shared = null,
                avScope = null,
            )
            expand?.let { target ->
                HomeExpandShell(
                    target = target,
                    bottomPadding = bottomPad,
                    onOpenPlayer = onOpenPlayer,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private data class ExpandTarget(
    val source: String,
    val id: String,
    val title: String,
    val coverUrl: String?,
    val rect: Rect,
) {
    /** 容器变换共享键：源卡片与目标面板必须一致。 */
    fun shellKey(): Any = "shell:$source:$id"
}

/** 探索页主体（骨架/错误/内容）：官方容器变换与自研壳两条路径共用。 */
@Composable
private fun HomeBodyUi(
    state: HomeUiState,
    listState: LazyListState,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onExpand: (ExpandTarget) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    when (state) {
        HomeUiState.Loading -> HomeSkeleton(bottomPadding = bottomPadding)
        HomeUiState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRefresh) { Text("重试") }
            }
        }
        is HomeUiState.Ready -> HomeContent(
            data = state,
            bottomPadding = bottomPadding,
            onPlay = onPlay,
            onExpand = onExpand,
            onWebLogin = onWebLogin,
            onRefresh = onRefresh,
            listState = listState,
            shared = shared,
            avScope = avScope,
        )
    }
}

/** 官方容器变换版的面板内容：直接渲染曲目列表（面板外壳/关闭键/scrim 由 [ShellPanel] 负责）。
 *  封面的 morph 由 [TrackListScreen] 的 banner 封面挂 [shellSharedElement] 完成（同歌手头像）。 */
@Composable
private fun HomePanelContent(
    target: ExpandTarget,
    bottomPadding: Dp,
    onOpenPlayer: () -> Unit,
    onDismiss: () -> Unit,
    shared: SharedTransitionScope,
    avScope: AnimatedVisibilityScope,
) {
    // 共享元素版没有「壳进度」概念，但 TrackListScreen 仍靠 LocalShellSettled 把「切到真列表」
    // 推迟到入场动画结束之后。这里把它接上**面板自己的 AnimatedVisibilityScope**：
    // 面板入场转场跑完（含封面的 sharedBounds morph）前保持骨架。
    //
    // 这一步是「两个大封面叠着」的根治：共享 morph 期间挂共享 modifier 的是**骨架封面**
    // （面板里第一个存在的封面宿主）；若此时数据已到、列表封面也在正常绘制，且它不挂
    // 共享 modifier，就会同时出现「overlay 里飞的封面」+「banner 位置自己画的封面」两份。
    // 压到 morph 结束后再切列表（骨架封面的占位几何与 banner 封面一致），交接瞬间无感，
    // 也顺带避开动画期间首次组合整张列表的开销。
    val settled = remember(avScope) {
        derivedStateOf {
            val t = avScope.transition
            t.currentState == EnterExitState.Visible && !t.isRunning
        }
    }
    CompositionLocalProvider(LocalShellSettled provides settled) {
        TrackListScreen(
            source = target.source,
            id = target.id,
            title = target.title,
            onBack = onDismiss,
            onOpenPlayer = onOpenPlayer,
            showTopBar = false,
            previewCoverUrl = target.coverUrl,
            bottomPadding = bottomPadding,
            coverSharedModifier = Modifier.shellSharedCover(shared, avScope, target.shellKey()),
        )
    }
}

@Composable
private fun HomeContent(
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
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurface)
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

private val BigCoverSize = 116.dp

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

/** 大封面卡 → 全屏列表：复用通用 [CoverExpandShell]；内容为曲目列表（banner 头作 hero 终点）。
 *  契约见 [CoverExpandShell]——封面左右 16dp 内缩、状态栏下 4dp。 */
@Composable
private fun HomeExpandShell(
    target: ExpandTarget,
    bottomPadding: Dp,
    onOpenPlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    CoverExpandShell(
        fromRect = target.rect,
        coverUrl = target.coverUrl,
        title = target.title,
        onDismiss = onDismiss,
    ) { onCoverReady ->
        TrackListScreen(
            source = target.source,
            id = target.id,
            title = target.title,
            onBack = onDismiss,
            onOpenPlayer = onOpenPlayer,
            showTopBar = false,
            // 壳内容：返回键交回 ExpandableShell 处理（本屏不再注册 BackHandler），
            // 否则本屏后注册的 BackHandler 会抢在壳之前触发 onBack、跳过壳的收起动画。
            backHandlerEnabled = false,
            // 封面内缩用常量（不随壳每帧重排 banner/LazyColumn）——封面形变交给 hero。
            coverInsetFollowsShell = false,
            onCoverReady = onCoverReady,
            previewCoverUrl = target.coverUrl,
            bottomPadding = bottomPadding,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/* ── 加载骨架 ─────────────────────────────────────────── */

/** 探索页骨架：与 [HomeContent] 同构——顶栏 + 区块标题 + 横滑大封面卡 + 单曲行。 */
@Composable
private fun HomeSkeleton(bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(bottom = bottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(widthFraction = 0.24f, height = 28.dp, shape = NumeShape.Chip)
            Spacer(Modifier.weight(1f))
            SkeletonBox(Modifier.size(28.dp), CircleShape)
        }

        SkeletonSectionHeader()
        repeat(4) { SkeletonTrackRow(artSize = 52.dp) }

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        repeat(4) { SkeletonTrackRow(artSize = 52.dp) }
    }
}

@Composable
private fun SkeletonSectionHeader() {
    SkeletonLine(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        widthFraction = 0.3f,
        height = 22.dp,
        shape = NumeShape.Chip,
    )
}

@Composable
private fun SkeletonCarousel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { SkeletonBox(Modifier.size(BigCoverSize), NumeShape.Card) }
    }
}

@Composable
private fun SkeletonTrackRow(artSize: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(artSize), NumeShape.Chip)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.55f, height = 14.dp)
            SkeletonLine(widthFraction = 0.3f, height = 12.dp)
        }
    }
}
