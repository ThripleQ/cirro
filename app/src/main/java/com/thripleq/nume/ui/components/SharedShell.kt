package com.thripleq.nume.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
 * 源卡片封面的**可见性守卫**：记住滚动视口（裁切边界）在窗口坐标里的矩形。
 *
 * ## 为什么需要它
 * `sharedBounds` 的内容是在 **overlay 里绘制**的，**不受滚动容器裁切**。于是当源卡片
 * 被列表边缘切掉一部分 —— 最典型的是往上滚、卡片顶进顶部那张圆角纸底下（探索页的
 * 「探索」标题条 + 我的页的「我的」标题条都是这个结构）—— 点击后 overlay 里飞的那份
 * 封面会画在**纸的上方**：看上去像封面从标题栏里钻出来；关闭时又一头扎进纸背后。
 * 这不是「起点算错了」（起点窗口坐标本来就是对的），是 overlay 天生不裁。
 *
 * 所以挂共享元素之前先问一句：**这张封面此刻在视口里完整吗**？不完整就不挂 ——
 * 目标端 banner 找不到 counterpart，框架把它当普通元素淡入/淡出：面板照常开合，
 * 只是不飞。宁可少一段动画，也不要一段从遮挡处飞出来的错动画。
 *
 * ## 用法
 * 1. 滚动容器（承载裁切的那个）挂 [viewportModifier]；
 * 2. 卡片在 `onGloballyPositioned` 里用 [isFullyVisible] 判断，结果存进布尔 state
 *    （只在「完整 ↔ 被切」翻转时写，滚动途中不重组），据此决定挂不挂 [shellSharedCover]。
 *
 * 视口本身是**普通字段**而非 state：滚动时列表自身的 bounds 不变，变的只有子项位置，
 * 所以记录视口不会因为滚动而触发重组。
 *
 * 注：只约束**源**。目标（面板 banner）永远完整可见，不需要问。
 */
@Stable
class SharedSourceGuard {
    private var viewport: Rect? = null

    /** 绑到真正承载裁切的滚动容器上（探索页的 LazyColumn / 我的页那张圆角纸）。 */
    fun viewportModifier(): Modifier =
        Modifier.onGloballyPositioned { viewport = it.boundsInWindow() }

    /**
     * [r]（源封面在窗口坐标里的矩形）是否完整落在视口内。
     *
     * 拿不到视口时按**可见**处理：首帧视口还没量到就误判成遮挡，会出现「第一次点
     * 没有动画、第二次才有」的偶发。宁可先给动画。
     */
    fun isFullyVisible(r: Rect): Boolean {
        val vp = viewport ?: return true
        return r.left >= vp.left - SLACK && r.top >= vp.top - SLACK &&
            r.right <= vp.right + SLACK && r.bottom <= vp.bottom + SLACK
    }

    private companion object {
        /** 亚像素容差：正好贴着视口边的卡片不该因为浮点误差被判成被切。 */
        const val SLACK = 0.5f
    }
}

/** @see SharedSourceGuard */
@Composable
fun rememberSharedSourceGuard(): SharedSourceGuard = remember { SharedSourceGuard() }

/**
 * 容器变换的目标外壳：普通全屏面板（scrim + 底色 + 关闭键）。封面 morph 由内容里的
 * banner 封面自己挂 [shellSharedCover] 完成，本组件不参与形变。
 *
 * 关闭键在**右上角**（2026-10-03 从左上挪过来，与 [com.thripleq.nume.ui.components.ExpandableShell]
 * 一致：内容标题在左上，键在左上就得逼标题让出一档内缩）。
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
                    .background(NumeInk.Scrim.copy(alpha = NumeFade.SHELL_SCRIM)),
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
        NumeCloseButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                // 上提一档与内容标题条中线对齐（详见 [CloseButtonRaise]）——与自研壳一致，
                // 两个壳的键不许长得不一样。
                .offset(y = -CloseButtonRaise)
                .padding(12.dp),
        )
    }
}
