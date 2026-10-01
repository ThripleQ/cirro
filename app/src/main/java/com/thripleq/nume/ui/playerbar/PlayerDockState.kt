package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.Motion
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow

/** 播放页「打开程度」：0 = 完全收起在迷你条胶囊，2 = 盖满全屏。
 *  两段式：p∈[0,1] 胶囊原位展开成悬浮卡（dock 保持可见）；
 *  p∈[1,2] 卡片放大盖满全屏（dock 淡出）。 */

/** 第一档（胶囊→卡片）的分裂点：progress ∈ [0,SPLIT] 是「扩展」，[SPLIT,1] 是「分裂」。 */
internal const val SPLIT = 0.5f

/** 卡片档吸附进度：分裂完成后壳顶继续线性升高到该进度才停 —— 卡片占屏约 2/3、
 *  高度足够装下封面+滑块+控制整组内容（此前卡片只有半屏高，内容溢出被裁、比例失调）。
 *  全屏固定 2；卡片→全屏的形变（贴边/收角/变色/dock淡出）全部在 [HALF_ANCHOR_P, 2] 内插值，
 *  壳顶行程不变（一行程直达全屏），但这一段按 [FULL_DRAG_RESISTANCE] 带阻力、不跟手；
 *  收起→卡片段仍严格跟手。 */
internal const val HALF_ANCHOR_P = 1.66f

/** 分裂段内部再分两拍：前一半「挤腰」（交界圆角涨大、两侧内收成腰），后一半「断开」（缝打开）。 */
internal const val SPLIT_SQUEEZE = 0.5f

/**
 * 卡片→全屏段的拖动阻力指数（>1）：越大于 1，起步越沉、视觉进度越落后于手指。
 *
 * 1 = 完全跟手；1.4 时拖到该段中点，视觉只走到约 38%（落后约四成）。
 * 这一段（卡片往全屏拉）不需要跟手，所以把「阻力」全放在这里；收起→卡片段仍严格跟手。
 */
internal const val FULL_DRAG_RESISTANCE = 1.4f

/** 挤腰峰值圆角（dp）：分裂前拍交界处圆角从 0 涨到它，形成内收的腰。 */
internal val WAIST_CORNER_DP = 36f

/**
 * spring 动画参数：**集中到 [Motion]**，壳与 dock 同源，不再各写一套阻尼/刚度。
 *
 * 厚实化（本轮）：吸附与展开改用 [Motion.SheetSettleHeavy]（中等刚度 + 略低阻尼），
 * 壳"有力地咬住档位、落地微微一顿"；收起仍用 [Motion.SheetSettle] 保持利落。
 */
internal val SPRING_CLOSE = Motion.SheetSettle
/** 手势吸附 + 展开到卡片：厚实版（中等刚度、不软；落定有克制的一顿）。 */
internal val SPRING_SETTLE = Motion.SheetSettleHeavy
/** 点击整页展开：低阻尼带一点弹性过冲 + 中低刚度，既有生长过程可见、又跟手不闷。 */
internal val SPRING_FULL = Motion.SheetExpand

/** 分裂段进度 [0,1] 拆成两拍：挤腰 ([0,SPLIT_SQUEEZE]) 与 断开 ([SPLIT_SQUEEZE,1])。 */
internal fun splitSqueezeT(splitT: Float): Float = (splitT / SPLIT_SQUEEZE).coerceIn(0f, 1f)
internal fun splitBreakT(splitT: Float): Float =
    ((splitT - SPLIT_SQUEEZE) / (1f - SPLIT_SQUEEZE)).coerceIn(0f, 1f)

/** 挤腰用的圆形过渡：0 起涨、到 1、收 0（让腰先挤出来再松开，模拟细胞缢裂）。 */
internal fun splitWaistCurve(x: Float): Float {
    val eased = x * x * (3f - 2f * x)
    return if (x < 0.5f) eased else 1f - eased
}

/** 播放页「档位」：三档——收起(dock 胶囊) / 卡片 / 全屏。 */
enum class PlayerSheet { Closed, Half, Full }

/**
 * 播放页状态：用官方 [AnchoredDraggableState] 管理「收起/卡片/全屏」三档之间的拖动与吸附。
 *
 * 锚点像素 = 胶囊展开进度（像素），几何是「胶囊 → 悬浮卡 → 全屏」两段 lerp：
 * - [PlayerSheet.Closed] = 0          → 壳收在迷你条胶囊原位
 * - [PlayerSheet.Half]   = HALF_ANCHOR_P*travelPx → 壳展开成悬浮卡（progress == HALF_ANCHOR_P，高卡装得下全部内容）
 * - [PlayerSheet.Full]   = 2*travelPx → 壳盖满全屏（progress == 2）
 * [progress] = offset / travelPx ∈ [0,2]，在 draw 阶段读，不触发重组。
 *
 * - 手势由 `Modifier.anchoredDraggable(state)` 驱动（内部处理 slop 仲裁/松手吸附/甩动）。
 * - 组合与否只由 [open] 显式布尔决定，由 offset/settledValue 观察驱动。
 */
class PlayerDockState internal constructor(
    val sheetState: AnchoredDraggableState<PlayerSheet>,
    private val scope: CoroutineScope,
) {
    /** 全屏播放面是否在组合中：进入会话即 true，完全收起（尾帧落地）后才复位。
     *  内部由 [AnchoredDraggableState] 的 offset/settledValue 观察驱动，外部只读。 */
    var open by mutableStateOf(false)
        internal set

    /** 手势行程（px）：从迷你条胶囊到「全屏」的展开总距（卡片是它的一半）。 */
    var travelPx by mutableFloatStateOf(1f)

    /**
     * 定时关闭到点时刻（epoch ms）；0 = 未开启。
     *
     * 放在常驻的 [PlayerDockState] 里、由 [PlayerDock] 里的 effect 驱动倒数——而不是放在只在
     * 播放页组合时才存在的芯片里。否则把播放页收起成迷你条会一并取消那个 effect，计时停摆、
     * 到点也不会暂停（旧实现的状态就挂在 [PlayerPageContent] 的 `SleepTimerChip` 里）。
     */
    var sleepEndAt by mutableLongStateOf(0L)

    /** 三档锚点是否已注册（[PlayerDock] 布局后置 true）。[open] 前等它，避免 animateTo 未注册锚点。 */
    var anchorsReady by mutableStateOf(false)
        internal set

    /**
     * 胶囊展开进度 0..2（[0,1]=胶囊→卡片，[1,2]=卡片→全屏）。
     *
     * 两段手感不同：
     * - 收起→卡片（[0,[HALF_ANCHOR_P]]）**严格跟手**：手指移多少壳移多少。
     * - 卡片→全屏（[[HALF_ANCHOR_P],2]）**带阻力**：这一段不需要跟手，视觉进度按
     *   [FULL_DRAG_RESISTANCE] 落后于手指——起步最沉、越拖越顺，读起来像拖着有分量的东西往全屏拽。
     *
     * 两段的端点（0 / [HALF_ANCHOR_P] / 2）映射保持不动，所以档位几何与吸附目标不受影响。
     * 只应在 draw 阶段（graphicsLayer）读，勿在组合读。
     */
    val progress: Float
        get() {
            val raw = (sheetState.offset / travelPx).coerceIn(0f, 2f)
            if (raw <= HALF_ANCHOR_P) return raw
            val x = (raw - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)
            return HALF_ANCHOR_P + (2f - HALF_ANCHOR_P) * x.pow(FULL_DRAG_RESISTANCE)
        }

    /**
     * 迷你条胶囊的窗口坐标 Rect（**遗留字段：当前无任何读取方**）。
     *
     * 由 [PlayerBar] 在 padding 之前实测上报。`1613459`（commit 名 capsule-origin geometry）曾以它
     * 作展开几何起点（`lerp(capsuleRect → card → full)`）；`973a453` 改「气泡/分裂」模型后，几何改由
     * dock 顶推导（见 [PlayerPage] 的 `shellRect`：`dockTopPx = fullHeightPx - dockHeightPx`），
     * 对它的**读取随之删除**，只剩声明与上报。
     *
     * 保留仅为日后若回归「从真实胶囊矩形起跳」时取数；若确定不再回归，可连同 [PlayerBar] 的
     * 上报一并删除。
     */
    @Deprecated("capsule-origin 几何的遗留：当前展开几何为 dock-origin，本字段已无读取方")
    var capsuleRect by mutableStateOf<Rect?>(null)
        internal set

    private var animJob: Job? = null

    /** 点击迷你条/列表项/我的：整页动画弹出。
     *  [toFull] = false 时两段式先弹到卡片（Half），可继续上滑看全屏；
     *  true 时直接盖满全屏（列表项点歌/点击迷你条用）。 */
    fun open(toFull: Boolean = false) {
        animJob?.cancel()
        animJob = scope.launch {
            val target = if (toFull) PlayerSheet.Full else PlayerSheet.Half
            if (!open) {
                open = true
                // 等一帧，等壳（PlayerPage）组合就位，从当前 offset 续跑展开，
                // 而不是先 snap 到 0 再展开——第一帧就是迷你条本身。
                withFrameNanos { }
            }
            // 锚点由 [PlayerDock] 布局后经 updateAnchors 注册，首个 LaunchedEffect 未必已跑过；
            // animateTo 到尚未注册的锚点会抛异常（崩溃）。等锚点就绪再跑。
            snapshotFlow { anchorsReady }.first { it }
            sheetState.animateTo(target, if (toFull) SPRING_FULL else SPRING_SETTLE)
        }
    }

    /** 收起箭头/返回键：无论当前在哪个档位，都缩回迷你条。 */
    fun close() {
        animJob?.cancel()
        animJob = scope.launch { runClose() }
    }

    private suspend fun runClose() {
        sheetState.animateTo(PlayerSheet.Closed, SPRING_CLOSE)
        // animateTo 返回后再等两帧，确保尾帧真正落地才卸载，避免收起闪最后一帧。
        withFrameNanos {}
        withFrameNanos {}
        open = false
    }
}

/** 在 [PlayerDock] 外部持有同一状态（NumeApp 需要列表项/我的点歌弹开播放页）。 */
@Composable
fun rememberPlayerDockState(): PlayerDockState {
    val scope = rememberCoroutineScope()
    // 官方 AnchoredDraggableState：三锚点（收起/半高/全屏），锚点像素值（offset）在
    // PlayerDock 布局后由 updateAnchors 填充（依赖 dock 高/屏高）。初始只有一个锚点。
    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = PlayerSheet.Closed,
            anchors = DraggableAnchors {
                PlayerSheet.Closed at 0f
            },
            // 换档阈值 0.4 → 0.5：要拖过一半行程才咬住下一档，档位"咬得牢"、
            // 不被小拖动带跑；快速甩动仍由 velocityThreshold(800) 判定，不影响甩到全屏。
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { 800f },
            // 松手吸附：厚实版（中等刚度有力地咬住档位、落定微微一顿），不是软绵绵飘停。
            snapAnimationSpec = SPRING_SETTLE,
            // 甩动衰减：摩擦 0.7 → 1.35 —— 快速滑动时壳要"很快咬住"档位才读着实；
            // 摩擦太低会让它一路飘过去，这正是"快速滑动时轻飘飘"的来源。
            decayAnimationSpec = exponentialDecay(frictionMultiplier = 1.35f),
            confirmValueChange = { true },
        )
    }
    return remember { PlayerDockState(sheetState, scope) }
}

internal const val SWIPE_THRESHOLD_DP = 56
