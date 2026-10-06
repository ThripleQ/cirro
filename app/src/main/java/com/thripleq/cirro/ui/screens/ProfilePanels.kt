package com.thripleq.cirro.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.cirro.core.model.Album
import com.thripleq.cirro.core.model.PlaylistSummary
import com.thripleq.cirro.ui.components.BannerCoverSize
import com.thripleq.cirro.ui.components.BigCoverVisual
import com.thripleq.cirro.ui.components.CoverExpandShell
import com.thripleq.cirro.ui.components.LocalShellHeroAlpha
import com.thripleq.cirro.ui.components.LocalShellSettled
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.ShimmerImagePlaceholder
import com.thripleq.cirro.ui.components.cirroEntrySurface
import com.thripleq.cirro.ui.components.shellSharedCover
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 通用全屏面板（自研壳）：复用 [CoverExpandShell]，从大卡位置（[capsuleRect]）长成全屏，
 * hero 封面 morph 到内容里的 banner 封面（与探索页同一套观感与契约）。
 * 内容按 [target] 分派：曲目列表 → [TrackListScreen]；歌单网格 → [PlaylistGridPanel]。
 */
@Composable
internal fun ProfilePanelLegacy(
    target: ProfilePanel,
    uid: String?,
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    bottomPadding: Dp,
    capsuleRect: Rect?,
    onDismiss: () -> Unit,
) {
    // hero 终点几何随内容形态变：曲目列表是紧凑头部（98dp 方封面），歌单网格仍是满宽 banner。
    // 预测值必须与内容里的真实排版一致，否则 hero 停在别处（见 [CoverExpandShell] 契约）。
    val tracksPanel = target is ProfilePanel.Tracks
    CoverExpandShell(
        fromRect = capsuleRect,
        coverUrl = target.coverUrl,
        title = target.title(),
        onDismiss = onDismiss,
        // hero 带卡片同一份数据行：否则展开时 hero 盖掉卡片，数量消失、结尾再冒出（闪）。
        meta = target.meta,
        watermarkIcon = target.icon,
        // hero 左内缩两条路径同值（曲目列表 16dp = [TrackListMetrics.SideInset]；歌单网格的
        // banner 也是满宽 - 16dp）—— 2026-10-03 [SideInset] 从 20dp 收到 16dp 后不再分支，
        // 统一引它一处，免得以后又分叉。相异的只是顶端与边长。
        heroCoverInset = TrackListMetrics.SideInset,
        heroCoverTop = if (tracksPanel) TrackListMetrics.HeroCoverTop else 4.dp,
        heroCoverSide = TrackListMetrics.CoverSize.takeIf { tracksPanel },
    ) { onCoverReady ->
        when (target) {
            is ProfilePanel.Tracks -> {
                val src = if (uid != null && target.source == "liked") uid else target.id
                TrackListScreen(
                    source = target.source,
                    id = src,
                    title = target.title(),
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    // 壳自带关闭键（与头部封面同位），本屏不再画返回键。
                    showBackButton = false,
                    // 壳内容：返回键交回 ExpandableShell 处理（本屏不再注册 BackHandler），
                    // 否则本屏后注册的 BackHandler 会抢在壳之前触发 onBack、跳过壳的收起动画。
                    backHandlerEnabled = false,
                    onCoverReady = onCoverReady,
                    previewCoverUrl = target.coverUrl,
                    watermarkIcon = target.icon,
                    bottomPadding = bottomPadding,
                )
            }
            is ProfilePanel.Playlists -> PlaylistGridPanel(
                title = target.title(),
                playlists = target.playlists,
                coverUrl = target.coverUrl,
                watermarkIcon = target.icon,
                onCoverReady = onCoverReady,
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
            )
            is ProfilePanel.Albums -> AlbumGridPanel(
                title = target.title(),
                albums = target.albums,
                onCoverReady = onCoverReady,
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
            )
        }
    }
}

private fun ProfilePanel.title(): String = when (this) {
    is ProfilePanel.Tracks -> title
    is ProfilePanel.Playlists -> title
    is ProfilePanel.Albums -> title
}

/**
 * 官方容器变换版的面板内容：外壳/关闭键/scrim 由 [com.thripleq.cirro.ui.components.ShellPanel]
 * 负责；封面 morph 由内容 banner 挂 [shellSharedCover] 完成。与自研 [ProfilePanelLegacy] 并存，
 * 由 com.thripleq.cirro.ui.theme.Motion.SharedShellEnabled 切换。
 */
@Composable
internal fun ProfileSharedPanelContent(
    target: ProfilePanel,
    uid: String?,
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    bottomPadding: Dp,
    shared: SharedTransitionScope,
    avScope: AnimatedVisibilityScope,
    /** 这次开合要不要走封面 morph（点击时起点封面是否完整可见，见 [SharedSourceGuard]）。 */
    morph: Boolean,
    onDismiss: () -> Unit,
) {
    // 共享元素版没有「壳进度」，但 TrackListScreen 仍靠 LocalShellSettled 把「切到真列表」
    // 推迟到入场动画（含封面 sharedBounds morph）结束之后，避免双层封面 / 动画期首次组合整列表。
    val settled = remember(avScope) {
        derivedStateOf {
            val t = avScope.transition
            t.currentState == EnterExitState.Visible && !t.isRunning
        }
    }
    // morph = false（点它时起点封面被顶部圆角纸切着）→ **不挂**共享元素：挂上就有
    // counterpart，overlay 里那份不受裁切的封面会画在纸上方飞出来。不挂则匹配不到两端，
    // 面板与封面各自淡入淡出：少了飞行，但没有错动画。
    val coverShared = if (morph) Modifier.shellSharedCover(shared, avScope, target.shellKey) else Modifier
    CompositionLocalProvider(LocalShellSettled provides settled) {
        when (target) {
            is ProfilePanel.Tracks -> {
                val src = if (uid != null && target.source == "liked") uid else target.id
                TrackListScreen(
                    source = target.source,
                    id = src,
                    title = target.title,
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    showBackButton = false,
                    previewCoverUrl = target.coverUrl,
                    watermarkIcon = target.icon,
                    bottomPadding = bottomPadding,
                    coverSharedModifier = coverShared,
                )
            }
            is ProfilePanel.Playlists -> PlaylistGridPanel(
                title = target.title(),
                playlists = target.playlists,
                coverUrl = target.coverUrl,
                watermarkIcon = target.icon,
                onCoverReady = {},
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
                coverSharedModifier = coverShared,
            )
            // 专辑网格无 banner、不挂共享元素（起点是「已购」行卡的图标，见 ProfilePanel.Albums）。
            is ProfilePanel.Albums -> AlbumGridPanel(
                title = target.title(),
                albums = target.albums,
                onCoverReady = {},
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
            )
        }
    }
}

/**
 * 已购专辑网格（「已购 → 专辑」的目的地）：2 列正方形大卡，**卡面底部压名称条**
 * —— 复用 [BigCoverVisual] 的既有语言（底部 scrim + 专辑名 + 歌手），
 * 与探索页卡片一致；而不是 [PlaylistCell] 那种「图下一行字」。
 *
 * ## 为什么没有 banner
 *
 * 歌单/曲目面板的顶部 banner 是为了承载**共享元素的 morph 终点** + 交代「这是哪个集合」。
 * 专辑网格两者都不需要：入口是「已购」行卡的图标（一个图标 morph 成整屏网格没有意义，
 * 所以这条链不挂共享元素），而「这是什么」由第一行标题交代。于是 [onCoverReady] 直接放行
 * —— 不调它，自研壳的 hero 会一直顶着（它等的是「内容封面已就绪」）。
 */
@Composable
private fun AlbumGridPanel(
    title: String,
    albums: List<Album>,
    onCoverReady: () -> Unit,
    onOpenTracks: (source: String, id: String, name: String) -> Unit,
    bottomPadding: Dp,
) {
    LaunchedEffect(Unit) { onCoverReady() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "panelTitle") {
            // 与面板标题条同写法（headlineSmall + 行高居中裁剪 + 粗体，高度跟文字走）。
            // 右端让开右上角的关闭键：键是浮层、比标题高，长标题不许钻到它底下。
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 56.dp, bottom = 10.dp),
            )
        }
        if (albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无已购专辑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(albums, key = { it.id }) { a ->
                AlbumCard(album = a, onClick = { onOpenTracks("album", a.id, a.name) })
            }
        }
    }
}

/** 一张已购专辑卡：正方形封面 + 卡面底部名称条（同探索页卡片语言）。 */
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    BigCoverVisual(
        coverUrl = album.coverUrl,
        name = album.name,
        meta = album.artist.takeIf { it.isNotBlank() },
        // 用 LibraryMusic 而不是 Icons.Filled.Album：本文件同时 import 了数据类
        // `core.repo.Album`，两者同名会撞 import（Kotlin 不允许同名 import）。
        watermarkIcon = Icons.Filled.LibraryMusic,
        requestSize = BannerCoverSize,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CirroShape.Card)
            .clickable(onClick = onClick),
    )
}

/** 歌单网格面板内容：首个 banner 封面 + 全屏懒加载网格，点格子进歌单曲目列表。
 *  banner 满宽（左右 16dp 内缩、状态栏下 4dp）= [CoverExpandShell] 的默认 hero 终点契约。 */
@Composable
private fun PlaylistGridPanel(
    title: String,
    playlists: List<PlaylistSummary>,
    coverUrl: String?,
    watermarkIcon: ImageVector,
    onCoverReady: () -> Unit,
    onOpenTracks: (source: String, id: String, name: String) -> Unit,
    bottomPadding: Dp,
    /** 官方共享元素：附加到 banner 封面（与入口大卡同 key）；默认空即无共享元素。 */
    coverSharedModifier: Modifier = Modifier,
) {
    // LazyVerticalGrid 自带滚动，不再外包一层 verticalScroll + 全量 Column：
    // 歌单多时只组合可见格，避免每帧重排整棵树。
    // 展开动画期间 hero 正顶着封面：banner 与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "banner") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // 官方共享元素：与入口大卡封面同 key，面板一出现即可 morph。
                    .then(coverSharedModifier)
                    .clip(CirroShape.Card)
                    // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                    .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f },
            ) {
                BigCoverVisual(
                    coverUrl = coverUrl,
                    name = title,
                    modifier = Modifier.fillMaxSize(),
                    // 恒显示数量（含 0）：与卡片/hero 同文案，末尾交接不出现数据消失。
                    meta = "${playlists.size} 个歌单",
                    scrimTop = CirroFade.BANNER_SCRIM_TOP,
                    scrimAlpha = CirroFade.BANNER_SCRIM,
                    requestSize = BannerCoverSize,
                    onLoadSuccess = onCoverReady,
                    watermarkIcon = watermarkIcon,
                )
            }
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无歌单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(playlists, key = { it.id }) { p ->
                PlaylistCell(
                    playlist = p,
                    onClick = { onOpenTracks("playlist", p.id, p.name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlaylistCell(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .cirroEntrySurface(inset = 0.dp, vertical = 0.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CirroArtwork(
            url = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            size = null,
            shape = CirroShape.CardSmall,
            requestSize = 320,
            fallbackIcon = Icons.Filled.List,
            fallbackIconSize = 32.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${playlist.trackCount} 首",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
