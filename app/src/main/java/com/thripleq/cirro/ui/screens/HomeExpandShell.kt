package com.thripleq.cirro.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.thripleq.cirro.ui.components.CoverExpandShell

/** 大封面卡 → 全屏列表：复用通用 [CoverExpandShell]；内容为曲目列表（头部封面作 hero 终点）。
 *  契约见 [CoverExpandShell]——终点几何由 [TrackListMetrics] 声明，两边同源。 */
@Composable
internal fun HomeExpandShell(
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
        heroCoverInset = TrackListMetrics.SideInset,
        heroCoverTop = TrackListMetrics.HeroCoverTop,
        heroCoverSide = TrackListMetrics.CoverSize,
    ) { onCoverReady ->
        TrackListScreen(
            source = target.source,
            id = target.id,
            title = target.title,
            onBack = onDismiss,
            onOpenPlayer = onOpenPlayer,
            // 壳自带关闭键（与头部封面同位），本屏不再画返回键。
            showBackButton = false,
            // 壳内容：返回键交回 ExpandableShell 处理（本屏不再注册 BackHandler），
            // 否则本屏后注册的 BackHandler 会抢在壳之前触发 onBack、跳过壳的收起动画。
            backHandlerEnabled = false,
            onCoverReady = onCoverReady,
            previewCoverUrl = target.coverUrl,
            bottomPadding = bottomPadding,
        )
    }
}
