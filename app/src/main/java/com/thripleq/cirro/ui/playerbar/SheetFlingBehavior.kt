package com.thripleq.cirro.ui.playerbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import com.thripleq.cirro.ui.theme.Motion
import kotlin.math.abs

/** 判定「这是一次甩动」的最小速度（px/s）；低于它按当前位置就近落档。 */
private const val MIN_FLING_VELOCITY = 400f

/**
 * 慢速「向上拉出」的位移门槛：拖过卡片行程的这个比例即认卡片档（相对卡片锚点）。
 *
 * 用户 2026-10-05 的原话把这条规则该管什么说清楚了：「需要快速拉才会出现卡片，慢慢拉
 * 卡片不会出现 …… **拉到多高都没有用**」。原先低速一律按「最近锚点」判 —— 门槛是卡片
 * 锚点的一半（可滑行程的 42%、约屏高 1/3），而甩动（> [MIN_FLING_VELOCITY]）走
 * 「向前取下一档」直接给卡片：于是**只有甩才拉得开**。
 *
 * 现在取 0.25：慢拉滑过卡片行程 1/4（travelPx≈984px 时约 410px、屏高 1/6）松手即留卡片。
 * **真机实测（K40, 2026-10-05）**：慢速（~38px/s）上拉 478px 后松手 → 旧规则落 Closed
 * （弹回收起档，"卡片不出来"）；新规则落 Half（卡片留住）。即用户抱怨的"慢慢拉"确实卡在
 * 这条门槛上。
 * 只作用于**从收起档起手**（守卫见 `pickTarget`）+ **尚未越过卡片锚点**的情形：
 * - 甩动仍按原逻辑「向前取下一档」；
 * - 从卡片档往下收仍按最近锚点（门槛仍是行程一半）；
 * - 越过卡片锚点之后仍在「卡片↔全屏」之间按最近锚点。
 */
private const val CARD_COMMIT_FRAC = 0.25f

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
        // 从收起档向上起手：拖过卡片行程的 [CARD_COMMIT_FRAC] 就认卡片档（用户 2026-10-05：
        // 「上拖播放条拉出卡片有点难拉出来」）。只在**还没越过卡片锚点**时接管 ——
        // 越过之后该按最近锚点在「卡片↔全屏」之间判，guard 见常量注释。
        //
        // 【守卫为什么用 settledValue，而不是"松手速度在往上"】
        // 位置在收起档与卡片档之间是**双义**的：从收起档往上拉到一半、和从卡片档往下收到
        // 一半，是同一批 offset。要分开只能靠"这一拖从哪一档起手"，而 `settledValue` 正是它：
        // 它只在**落档结束时**被外层 `anchoredDrag` 收尾写入（条件是最终 offset 落在某锚点
        // 0.5px 内，见框架源码），拖动过程中不更新 —— 所以 fling 里读到的就是"起手那一档"。
        // 我们这条自定义 fling 精确把 offset 跑到 `anchors.positionOf(target)`，落点必然贴住
        // 锚点，结算成立（同文件开头也记着这一点）。
        // 我一度把它换成"松手速度没在往回拉"（`velocity >= -150`）：那会把"从卡片档往下收、
        // 收到一半停住再松手"误判成"又拉出卡片"（速度≈0 同样通过），语义比 settledValue 弱。
        if (state.settledValue == PlayerSheet.Closed &&
            anchors.hasPositionFor(PlayerSheet.Half)
        ) {
            val half = anchors.positionOf(PlayerSheet.Half)
            if (from >= half * CARD_COMMIT_FRAC && from <= half) return PlayerSheet.Half
        }
        return anchors.closestAnchor(from) ?: PlayerSheet.Closed
    }
}