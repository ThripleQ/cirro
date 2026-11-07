@file:OptIn(ExperimentalFoundationApi::class)

package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity

/**
 * 详情页列表「滑到底仍继续拉」时，让吸顶的「播放全部」那一行不被拉伸变形。
 *
 * ## 变形是哪来的
 *
 * 项目里没有任何自定义 overscroll：Android 12+ 的 Compose 默认给每个滚动容器挂一份
 * [OverscrollEffect]（系统 EdgeEffect 的 stretch）。手指到了边界还继续拉，**整张列表**
 * 被拉伸/压扁再弹回来 —— 这就是用户说的「吃力变形」。「播放全部」是 `LazyColumn` 里
 * `stickyHeader` 的吸顶行，和曲目行处在**同一个绘制层**，所以它也一起变形，四角圆角
 * 被拉成椭圆最扎眼。系统这套变换作用于整个容器，没法只把某一个 item 摘出去；
 * 想让这一行不变形，只有「这一次别让容器产生拉伸」这一条路。
 *
 * ## 只在到底时，顶部原样
 *
 * 平时完全透传给系统那一套，只在 `!canScrollForward && canScrollBackward`
 * （已经到底、且不是内容不足一屏）时，把滚动量**只交给 `performScroll` 真实消费**、
 * 不喂给 overscroll ⇒ 底部不再拉伸。到顶（`canScrollBackward == false`）和短列表
 * （两个都 false）都不拦，顶部那一下逐帧不变（2026-11-07 用户：「到顶不用管」）。
 *
 * 判据不看 delta 的正负号：**滚动照常发生**，只是不再生成视觉效果，所以往回滚也
 * 不会被误吃掉 —— 回滚第一帧 `canScrollForward` 一变 true 就恢复透传。
 *
 * ## 为什么包一层，而不是换成 `withoutVisualEffect()`
 *
 * `withoutVisualEffect()` 会把绘制用的 node 一起摘掉、且是全时段关视觉；这里把
 * [OverscrollEffect.node] 委托给系统实现（顶部还要它画），只改「要不要把滚动量喂进去」，
 * 也不在滚动中途切换 effect 实例 —— 切换会丢掉正在进行的那一帧拉伸。
 */
@Composable
internal fun rememberBottomGuardOverscrollEffect(listState: LazyListState): OverscrollEffect? {
    val platform = rememberOverscrollEffect()
    return remember(platform, listState) {
        platform?.let { BottomGuardOverscrollEffect(it, listState) }
    }
}

private class BottomGuardOverscrollEffect(
    private val delegate: OverscrollEffect,
    private val listState: LazyListState,
) : OverscrollEffect {

    /** 顶部那一下仍由系统这套 node 画。 */
    override val node: DelegatableNode get() = delegate.node

    override val isInProgress: Boolean get() = delegate.isInProgress

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset = if (atBottom) {
        // 滚动照常，只是这一份 delta 不进 EdgeEffect ⇒ 底部不拉伸。
        performScroll(delta)
    } else {
        delegate.applyToScroll(delta, source, performScroll)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (atBottom) {
            performFling(velocity)
        } else {
            delegate.applyToFling(velocity, performFling)
        }
    }

    /** 已到底 = 不能再向前、还能向后；到顶与短列表都返回 false，保持系统原样。 */
    private val atBottom: Boolean
        get() = !listState.canScrollForward && listState.canScrollBackward
}
