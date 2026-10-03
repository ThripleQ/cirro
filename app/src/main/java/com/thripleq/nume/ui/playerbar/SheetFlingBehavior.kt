package com.thripleq.nume.ui.playerbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import com.thripleq.nume.ui.theme.Motion
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
 * - **落在全屏 / 从全屏回落**（跨越「卡片↔全屏」这一段）→ 走 [SettleWithVelocity]：
 *   **继承松手速度**的单调减速（越滑越慢、稳稳停住），松手慢时退化成中心对称的缓入缓出。
 * - **落到卡片档 / 收起档**→ 走 [cardSpec] / [closedSpec]：保持原手感（干净不弹的弹簧），不乱动。
 *
 * 落档完成后 [AnchoredDraggableState] 会按最终 offset 自行结算 `settledValue`，所以这里只需把
 * offset 跑到目标锚点上。
 */
internal class SheetFlingBehavior(
    private val state: AnchoredDraggableState<PlayerSheet>,
    private val cardSpec: AnimationSpec<Float>,
    private val closedSpec: AnimationSpec<Float>,
    /** 落位开始/结束通知跟随器（对应 [PlayerDockState.settling]）。 */
    private val onSettling: (Boolean) -> Unit = {},
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val from = state.offset
        val target = pickTarget(from, initialVelocity)
        val targetOffset = state.anchors.positionOf(target)
        val spec = settleSpec(target, from, targetOffset)

        // 落位期间通知跟随器改用极小的时间常数（[Motion.DragFollowReleaseMs]）让壳贴住这条
        // 轨迹 —— 否则滞后会一路累积、等轨迹跑完再慢慢挪完残余（真机实测 ~900ms 的尾巴）。
        // 见 `PlayerDockState.step`。
        onSettling(true)
        try {
            // 从当前 offset 续跑（不是从 0 起跳）。**曲线形状自己负责速度继承** ——
            // 松手速度经 `AnimationState(initialValue, initialVelocity)` 传给 spec
            // （见 [SettleWithVelocity]），它据此同时决定「时长」与「形状」。
            // **不要换回 tween**：tween 忽略初速度，会让松手瞬间出现速度断崖。
            //
            // **不要在这里做「冲过锚点再收回」**（试过，已删）：`AnchoredDraggableState.offset` 被钳在
            // 锚点范围内，超出 Full 的正 delta 全被吞掉、而负 delta 照常生效，于是那套两段式实际变成
            // 「先到顶 → 再往下缩一段 → 停在一个没到顶的位置」：既抖一下，终点还差约 10px。
            // 想要「重物停下来」的回弹，只能走**不经过 offset** 的通道（内容档位），不能动 offset。
            var last = from
            var consumed = 0f
            AnimationState(initialValue = from, initialVelocity = initialVelocity)
                .animateTo(targetValue = targetOffset, animationSpec = spec) {
                    consumed += scrollBy(value - last)
                    last = value
                }
            return consumed
        } finally {
            onSettling(false)
        }
    }

    /**
     * 选 spec：落到全屏档给「继承松手速度的落位曲线」（见 [SettleWithVelocity]），
     * 其余档位保持弹簧原手感。
     */
    private fun settleSpec(
        target: PlayerSheet,
        from: Float,
        targetOffset: Float,
    ): AnimationSpec<Float> {
        val anchors = state.anchors
        if (!anchors.hasPositionFor(PlayerSheet.Half) || !anchors.hasPositionFor(PlayerSheet.Full)) {
            return cardSpec
        }
        val full = anchors.positionOf(PlayerSheet.Full)
        val half = anchors.positionOf(PlayerSheet.Half)
        val segment = full - half
        if (segment <= 0f) return cardSpec
        return when (target) {
            PlayerSheet.Full -> {
                // 时长基准（松手慢时走它）：仍按「剩余行程占整段的比例」缩放，保留原有的
                // 「离目标越远跑越久、从容读出加减速」。松手快时 [SettleWithVelocity] 内部会
                // 改用物理匀减速时长（更短），这里给的只是上限。
                val remain = ((targetOffset - from) / segment).coerceIn(0f, 1f)
                val baseMs = Motion.SheetFullMinMs +
                    (Motion.SheetFullMaxMs - Motion.SheetFullMinMs) * remain
                SettleWithVelocity(baseMs)
            }
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