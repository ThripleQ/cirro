package com.thripleq.nume.ui.screens

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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.ui.components.BannerCoverSize
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.CoverExpandShell
import com.thripleq.nume.ui.components.LocalShellHeroAlpha
import com.thripleq.nume.ui.components.LocalShellSettled
import com.thripleq.nume.ui.components.NumeArtwork
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeShape

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
    CoverExpandShell(
        fromRect = capsuleRect,
        coverUrl = target.coverUrl,
        title = target.title(),
        onDismiss = onDismiss,
        // hero 带卡片同一份数据行：否则展开时 hero 盖掉卡片，数量消失、结尾再冒出（闪）。
        meta = target.meta,
        watermarkIcon = target.icon,
    ) { onCoverReady ->
        when (target) {
            is ProfilePanel.Tracks -> {
                val src = if (uid != null && target.source == "liked") uid else target.id
                TrackListScreen(
                    source = target.source,
                    id = src,
                    title = target.title,
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    showTopBar = false,
                    // 壳内容：返回键交回 ExpandableShell 处理（本屏不再注册 BackHandler），
                    // 否则本屏后注册的 BackHandler 会抢在壳之前触发 onBack、跳过壳的收起动画。
                    backHandlerEnabled = false,
                    // 面板走「内容固定终态排版 + 壳裁剪露出」，封面内缩用常量，
                    // 使列表 measure 在展开动画期间被跳过（封面形变交给 hero）。
                    coverInsetFollowsShell = false,
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
        }
    }
}

private fun ProfilePanel.title(): String = when (this) {
    is ProfilePanel.Tracks -> title
    is ProfilePanel.Playlists -> title
}

/**
 * 官方容器变换版的面板内容：外壳/关闭键/scrim 由 [com.thripleq.nume.ui.components.ShellPanel]
 * 负责；封面 morph 由内容 banner 挂 [shellSharedCover] 完成。与自研 [ProfilePanelLegacy] 并存，
 * 由 com.thripleq.nume.ui.theme.Motion.SharedShellEnabled 切换。
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
                    showTopBar = false,
                    previewCoverUrl = target.coverUrl,
                    watermarkIcon = target.icon,
                    bottomPadding = bottomPadding,
                    coverSharedModifier = Modifier.shellSharedCover(shared, avScope, target.shellKey),
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
                coverSharedModifier = Modifier.shellSharedCover(shared, avScope, target.shellKey),
            )
        }
    }
}

/** 歌单网格面板内容：首个 banner 封面 + 全屏懒加载网格，点格子进歌单曲目列表。
 *  banner 位于 16dp 内缩、状态栏下 4dp（[CoverExpandShell] 的 hero 终点契约）。 */
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
                    .clip(NumeShape.Card)
                    // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                    .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f },
            ) {
                BigCoverVisual(
                    coverUrl = coverUrl,
                    name = title,
                    modifier = Modifier.fillMaxSize(),
                    // 恒显示数量（含 0）：与卡片/hero 同文案，末尾交接不出现数据消失。
                    meta = "${playlists.size} 个歌单",
                    scrimTop = NumeFade.BANNER_SCRIM_TOP,
                    scrimAlpha = NumeFade.BANNER_SCRIM,
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
            .numeEntrySurface(inset = 0.dp, vertical = 0.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        NumeArtwork(
            url = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            size = null,
            shape = NumeShape.CardSmall,
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
