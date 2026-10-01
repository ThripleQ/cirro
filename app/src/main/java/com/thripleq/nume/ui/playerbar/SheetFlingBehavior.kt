package com.thripleq.nume.ui.playerbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import kotlin.math.abs

/** 判定「这是一次甩动」的最小速度（px/s）；低于它按当前位置就近落档。 */
private const val MIN_FLING_VELOCITY = 400f

private val SHEET_ORDER = listOf(PlayerSheet.Closed, PlayerSheet.Half, PlayerSheet.Full)

/**
 * 松手落档行为：**按「落在哪一档」分派动画 spec**，并让这段动画真正生效。
 *
 * ## 为什么要自己实现，不用官方的默认
 * `Modifier.anchoredDraggable` 在没显式传 `flingBehavior` 时会生成一个默认行为，内部用
 * `NoOpDecayAnimationSpec`（**抖落惯性整段被关掉**）+ 一段 `FastOutSlowIn`（**只有减速、
 * 没有加速**）一次性吸附——所以用户松手后壳只"贴上去"，永远读不出「先加速冲出去、再减速
 * 收稳」。也因此，只在 [AnchoredDraggableState] 里设弹簧/阈值参数是**读不到的**：新版本里
 * 那些参数已被移除，且默认 fling 用的是写死的行为。这里把这些参数拿回来自行接管。
 *
 * ## 分派
 * - **落在全屏 / 从全屏回落**（跨越「卡片↔全屏」这一段）→ 走 [fullSpecFor]：中心对称的
 *   缓入缓出，起步沉、中段最快、末端稳稳收住——这就是用户要的「先加速、后减速」。
 * - **落到卡片档 / 收起档**→ 走 [cardSpec] / [closedSpec]：保持原手感（干净不弹的弹簧），不乱动。
 *
 * 落档完成后 [AnchoredDraggableState] 会按最终 offset 自行结算 `settledValue`，所以这里只需把
 * offset 跑到目标锚点上。
 */
internal class SheetFlingBehavior(
    private val state: AnchoredDraggableState<PlayerSheet>,
    private val cardSpec: AnimationSpec<Float>,
    private val fullSpecFor: (remain: Float) -> AnimationSpec<Float>,
    private val closedSpec: AnimationSpec<Float>,
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val from = state.offset
        val target = pickTarget(from, initialVelocity)
        val targetOffset = state.anchors.positionOf(target)
        val spec = settleSpec(target, from, targetOffset)

        // 从当前 offset 续跑（不是从 0 起跳）。tween 会忽略 initialVelocity——这正是要的：
        // 落档是一段既定动画，从松手点衔接到目标锚点。
        var last = from
        var consumed = 0f
        AnimationState(initialValue = from, initialVelocity = initialVelocity)
            .animateTo(targetValue = targetOffset, animationSpec = spec) {
                consumed += scrollBy(value - last)
                last = value
            }
        return consumed
    }

    /** 选 spec：落到全屏档给「卡片↔全屏」的缓入缓出（时长随剩余行程缩放），其余档位保持弹簧原手感。 */
    private fun settleSpec(target: PlayerSheet, from: Float, targetOffset: Float): AnimationSpec<Float> {
        val anchors = state.anchors
        if (!anchors.hasPositionFor(PlayerSheet.Half) || !anchors.hasPositionFor(PlayerSheet.Full)) {
            return cardSpec
        }
        val full = anchors.positionOf(PlayerSheet.Full)
        val half = anchors.positionOf(PlayerSheet.Half)
        val segment = full - half
        if (segment <= 0f) return cardSpec
        return when (target) {
            PlayerSheet.Full -> fullSpecFor(((targetOffset - from) / segment).coerceIn(0f, 1f))
            PlayerSheet.Half -> cardSpec
            PlayerSheet.Closed -> closedSpec
        }
    }

    private fun pickTarget(from: Float, velocity: Float): PlayerSheet {
        val anchors = state.anchors
        if (anchors.size == 0) return PlayerSheet.Closed
        if (abs(velocity) > MIN_FLING_VELOCITY) {
            val forward = velocity > 0f
            val ahead = SHEET_ORDER
                .filter { anchors.hasPositionFor(it) }
                .map { it to anchors.positionOf(it) }
                .filter { if (forward) it.second > from else it.second < from }
            val next = if (forward) ahead.minByOrNull { it.second } else ahead.maxByOrNull { it.second }
            if (next != null) return next.first
        }
        return anchors.closestAnchor(from) ?: PlayerSheet.Closed
    }
}