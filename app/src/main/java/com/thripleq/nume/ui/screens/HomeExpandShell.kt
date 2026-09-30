package com.thripleq.nume.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.thripleq.nume.ui.components.CoverExpandShell

/** 大封面卡 → 全屏列表：复用通用 [CoverExpandShell]；内容为曲目列表（banner 头作 hero 终点）。
 *  契约见 [CoverExpandShell]——封面左右 16dp 内缩、状态栏下 4dp。 */
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
