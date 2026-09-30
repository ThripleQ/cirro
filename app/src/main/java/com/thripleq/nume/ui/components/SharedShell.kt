package com.thripleq.nume.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeInk

/**
 * 官方 `SharedTransitionLayout` 版「胶囊壳展开」——**试验实现**，与自研 [ExpandableShell]
 * 并存，由 [Motion.SharedShellEnabled] 切换。
 *
 * ## 做法（只对封面做容器变换）
 * 源卡片封面与面板 banner 封面挂同一个 [key]，框架负责把「同一张封面」从卡片位置/尺寸
 * morph 到 banner 位置/尺寸；面板外壳（scrim + 全屏底）另由普通淡入呈现，不参与形变。
 *
 * ## 为什么用 `sharedBounds` 而不是 `sharedElement`
 * 封面**两端内容不同**：卡片只有图，banner 还有名字/元信息（且 banner 是全宽方形、
 * 卡片是 116dp 小方）。`sharedElement` 假设两端视觉内容一致，会把**目标内容每帧重排到
 * 插值尺寸**——banner 的文字会在展开途中逐帧换行/抖动，同时每帧 measure 掉帧。
 * `sharedBounds` 正是为「两端内容不同」设计的：容器 bounds 做插值，内容按 `resizeMode`
 * 默认 **scaleToBounds 缩放**（不重排），并用 [enter]/[exit] 交叉淡化。于是封面形体连续、
 * 内容（图 + 字）平滑互变，且零逐帧重排。
 *
 * 这与「整只面板挂 sharedBounds」是两回事——那会把整棵列表一起缩放/测量；这里只圈住**封面
 * 这一个方块**，面板与列表照常、不参与形变。
 */
@Composable
fun Modifier.shellSharedCover(
    shared: SharedTransitionScope,
    avScope: AnimatedVisibilityScope,
    key: Any,
): Modifier = with(shared) {
    this@shellSharedCover.sharedBounds(
        sharedContentState = rememberSharedContentState(key = key),
        animatedVisibilityScope = avScope,
        // 内容交叉：源淡出、目标淡入。bounds 的位移仍由 Motion.sharedBoundsSpec 主导。
        enter = fadeIn(tween(Motion.ShellCoverFadeMs, easing = Motion.EmphasizedDecelerate)),
        exit = fadeOut(tween(Motion.ShellCoverFadeMs, easing = Motion.Emphasized)),
        boundsTransform = { _, _ -> Motion.sharedBoundsSpec() },
    )
}

/**
 * 容器变换的目标外壳：普通全屏面板（scrim + 底色 + 关闭键）。封面 morph 由内容里的
 * banner 封面自己挂 [shellSharedCover] 完成，本组件不参与形变。
 */
@Composable
fun ShellPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    scrim: Boolean = true,
    content: @Composable () -> Unit,
) {
    BackHandler { onDismiss() }
    Box(modifier.fillMaxSize()) {
        if (scrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = NumeFade.SHELL_SCRIM)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(containerColor),
        ) {
            // 表面铺到屏幕顶（状态栏那段也属于面板底色），内容再让开状态栏——
            // 与自研壳一致：否则 banner 顶到 0、压在状态栏图标上。
            Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = NumeFade.CONTROL_SCRIM))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "收起",
                tint = NumeInk.OnImage,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
