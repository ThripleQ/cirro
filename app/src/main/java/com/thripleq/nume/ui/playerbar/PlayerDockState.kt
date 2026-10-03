package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.Motion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
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

/** 播放页「打开程度」：0 = 完全收起在迷你条胶囊，2 = 盖满全屏。
 *  两段式：p∈[0,1] 胶囊原位展开成悬浮卡（dock 保持可见）；
 *  p∈[1,2] 卡片放大盖满全屏（dock 淡出）。
 *
 *  这是**拖动路径**的形状。点迷你条的展开走 [PlayerDockState.openByTap]，它共用同一个 0..2
 *  量纲但形态另有改派：跳过「分裂」那一拍（不挤腰/不断缝/不内收），「盖满全屏」铺开到后半段，
 *  内容档位随行程全程 0→1。具体在 [PlayerPage] 的 shellRect/圆角/颜色/内容档位处按
 *  [PlayerDockState.tapExpand] 分派。
 *  这套改派只在「壳还收在迷你条原位」时走；壳已在卡片档再点播放条，[openByTap] 会交回
 *  [PlayerDockState.open]`(toFull = true)`，即拖动路径的形状 + spring 直达全屏。 */

/** 第一档（胶囊→卡片）的分裂点：progress ∈ [0,SPLIT] 是「扩展」，[SPLIT,1] 是「分裂」。 */
internal const val SPLIT = 0.5f

/** 卡片档吸附进度：分裂完成后壳顶继续线性升高到该进度才停 —— 卡片占屏约 2/3、
 *  高度足够装下封面+滑块+控制整组内容（此前卡片只有半屏高，内容溢出被裁、比例失调）。
 *  全屏固定 2；卡片→全屏的形变（贴边/收角/变色/dock淡出）全部在 [HALF_ANCHOR_P, 2] 内插值，
 *  壳顶仍全程随手指线性升降（跟手不变）、一行程直达全屏（行程不变）。 */
internal const val HALF_ANCHOR_P = 1.66f

/** 分裂段内部再分两拍：前一半「挤腰」（交界圆角涨大、两侧内收成腰），后一半「断开」（缝打开）。 */
internal const val SPLIT_SQUEEZE = 0.5f

/** 挤腰峰值圆角（dp）：分裂前拍交界处圆角从 0 涨到它，形成内收的腰。 */
internal val WAIST_CORNER_DP = 36f

/**
 * spring 动画参数：**数值保持原样，但集中到 [Motion]**。
 *
 * 壳与 dock 过去各写一套阻尼/刚度与时长，并排看就是「两个不同 App 的手感」；现在两处
 * 同源于 [Motion]。刻意不改弹性数值——细胞分裂的吸附手感已在真机调过，盲改风险高于收益。
 */
internal val SPRING_CLOSE = Motion.SheetSettle
/** 点「播放全部」直达全屏：低阻尼带一点弹性过冲 + 中低刚度，既有生长过程可见、又跟手不闷。
 *  （点迷你条走的不是这条 —— 那条是 [PlayerDockState.openByTap] 的专用时间轴。） */
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
    /** 松手落档行为：由 [rememberPlayerDockState] 用 [SheetFlingBehavior] 建好。
     *  必须显式传给 `Modifier.anchoredDraggable`，否则库退回它写死的默认值（无惯性、只减速），
     *  我们调的 spring / 曲线一个都读不到。 */
    val flingBehavior: FlingBehavior,
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

    /** 胶囊展开进度 0..2（[0,1]=胶囊→卡片，[1,2]=卡片→全屏）。
     *
     *  **两个数据源**：手势/吸附走 [sheetState.offset]（拖动路径，细胞分裂那套形态语言）；
     *  点迷你条的展开走 [entry]（点按专用时间轴，跳过分裂、内容全程参与运镜）。
     *  选哪一个由 [tapExpand] 决定 —— 两套都归一到同一个 0..2 量纲，所以所有读取方
     *  （壳几何、dock 淡出、系统栏隐藏、内容档位）不必知道自己在哪套里。
     *
     *  拖动中读 offset 是"跟手"的前提，点按时要读一条**形态不同**的路径，这才需要一个独立源；
     *  若强行让点按也走 offset，就只能靠 `animateTo` 一条曲线跑完，形态仍是拖动那套。
     *
     *  读取方既有组合阶段的（PlayerPage 的壳几何、圆角、颜色），也有绘制阶段的（dock 的
     *  graphicsLayer、系统栏显隐）—— 都走这一个口子，所以换数据源时它们全都跟着切。 */
    val progress: Float
        get() = if (tapExpand) entry.value * 2f
                else (sheetState.offset / travelPx).coerceIn(0f, 2f)

    /**
     * 点按展开的等效行程：0 = 收起在迷你条，1 = 盖满全屏。
     *
     * 与 [sheetState] **完全独立** —— 手势拖动不碰它，它只在 [openByTap] 期间驱动 [progress]。
     */
    val entry = Animatable(0f)

    /** 点按展开是否在跑。[progress] 据此选数据源；手势据此让路（见 [openByTap]）。 */
    var tapExpand by mutableStateOf(false)
        internal set

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

    /** 点击迷你条：整页动画弹出。
     *  [toFull] = false 时两段式先弹到卡片（Half），可继续上滑看全屏；
     *  true 时直接盖满全屏（「播放全部」用）。 */
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
            sheetState.animateTo(target, if (toFull) SPRING_FULL else SPRING_CLOSE)
        }
    }

    /**
     * **点迷你条专用**：从播放条位置直接展开到全屏（点按动画）。
     *
     * ## 为什么不复用 [open] 的 `toFull = true`
     * `open` 是让 `p` 从 0 跑到 2 —— 这条路径**是给手势拖动准备的**：它沿途会走
     * 「气泡充气 → 挤腰 → 断缝成悬浮卡 → 再盖满全屏」四拍。点按并不需要「分裂」这一拍
     * （那是从迷你条往上拖的探索语言），更糟的是 [PlayerPage] 里内容档位被钉在
     * `HALF_ANCHOR_P`：`p ≤ 1.66` 期间 `sc` 恒为 0，**83% 的行程里内容形态一动不动**，
     * 真正的运镜全挤在最后 17%，再叠上 [SPRING_FULL]（低刚度 spring 跑长距离）的长尾
     * 减速 —— 观感就是「先变出一个卡片，再慢吞吞扩到全屏」。用户 2026-10-03：
     * 「现在是先到卡片状态再扩展到全屏，太拖沓」。
     *
     * ## 这条时间轴做了什么
     * 1. 形态上跳过分裂：不挤腰、不断缝、左右不收，一路贴着屏边充气（[PlayerPage] 的
     *    `shellRect`/`shellShape` 在 [tapExpand] 期间把分裂的形态量改派）。
     * 2. 让「盖满全屏」铺开到后半段，而不是最后 17% 做一次突袭。
     * 3. 内容档位 `sc` 随行程**全程** 0→1：壳长大与内容运镜同时发生 —— 展开感来自这里。
     *
     * ## 与拖动路径的交接
     * 跑完时把 [sheetState] 直接 `snapTo(Full)`，再撤 [tapExpand] —— 此刻两侧都等于
     * `p = 2`，切换数据源无跳变，之后上滑/下拉/收起一切照旧。
     * 动画期间 [PlayerPage] 会禁用手势（见那里的 `anchoredDraggable(enabled = …)`），
     * 否则手动拖动会改 offset、而 [progress] 仍在读 [entry]，两者状态不一致。
     *
     * ## 只负责「从迷你条原位起跳」：已在卡片档再点走回 [open]
     * 上面这套形态改派全部按 `entry: 0→1` 这段独立行程标定 —— 它成立的前提是**壳此刻还
     * 收在迷你条原位**。壳已经在卡片上时再点一次，若还从 `entry = 0` 跑，画面会先掉回迷你条
     * 再重新长一遍（`progress` 从 1.66 瞬跳到 0）。卡片→全屏本来就只剩一段贴边/收角/dock 淡出
     * 的形变，用 [open] 的 `toFull = true`（[SPRING_FULL] 那条低刚度 spring）从当前 offset
     * 弹上去即可 —— 那也正是点按时间轴出现之前的行为，用户 2026-10-03 明确要保留：
     * 「我在卡片状态再点播放条应该用曾经的那个方式」。
     */
    fun openByTap() {
        // 点按时间轴正在跑（420ms 窗口）：重复点击忽略 —— 这段时间 [progress] 读的是 [entry]，
        // 中途切回 offset 会让壳跳回迷你条。
        if (tapExpand) return
        // 壳已离开迷你条原位（卡片档、或拖到半路）：交回 [open]，从当前 offset 直达全屏。
        // 阈值取 offset > 0 而非「== 卡片锚点」，是为了连「上滑松手后还在回弹的半路」也算进去——
        // 那种位置再点，用户期望的也是继续往上走，而不是塌回迷你条。
        if (sheetState.offset > 0f) {
            open(toFull = true)
            return
        }
        animJob?.cancel()
        animJob = scope.launch {
            if (!open) {
                open = true
                // 同上：等壳组合就位，第一帧就是迷你条本身。
                withFrameNanos { }
            }
            // 先 snap 到 0 再开门：若上一次点按动画被中途取消，entry 可能停在半路，
            // 直接开 tapExpand 会让第一帧从半路起跳。
            entry.snapTo(0f)
            tapExpand = true
            entry.animateTo(1f, tween(Motion.TapExpandMs, easing = Motion.EmphasizedDecelerate))
            snapshotFlow { anchorsReady }.first { it }
            sheetState.snapTo(PlayerSheet.Full)
            tapExpand = false
        }
    }

    /** 收起箭头/返回键：无论当前在哪个档位，都缩回迷你条。 */
    fun close() {
        animJob?.cancel()
        animJob = scope.launch {
            // 点按展开还没落位就点了收起（窗口极短：收起键在点按行程 15% 之后才可见）：
            // 先把行程交接给锚点状态再照常收起。不交接的话 [progress] 会从 entry 切回
            // `offset`（此刻仍是 Closed），壳**瞬间跳回迷你条**。
            if (tapExpand) {
                snapshotFlow { anchorsReady }.first { it }
                sheetState.snapTo(PlayerSheet.Full)
                tapExpand = false
            }
            runClose()
        }
    }

    private suspend fun runClose() {
        sheetState.animateTo(PlayerSheet.Closed, SPRING_CLOSE)
        // animateTo 返回后再等两帧，确保尾帧真正落地才卸载，避免收起闪最后一帧。
        withFrameNanos {}
        withFrameNanos {}
        open = false
    }
}

/** 在 [PlayerDock] 外部持有同一状态（NumeApp 需要让歌单页的「播放全部」弹开播放页；
 *  点迷你条的展开由 [PlayerDock] 内部自己发起）。 */
@Composable
fun rememberPlayerDockState(): PlayerDockState {
    val scope = rememberCoroutineScope()
    // 官方 AnchoredDraggableState：三锚点（收起/半高/全屏），锚点像素值（offset）在
    // PlayerDock 布局后由 updateAnchors 填充（依赖 dock 高/屏高）。初始只有一个锚点。
    //
    // 注意：**只传新构造器参数**（initialValue/anchors/confirmValueChange）。旧版那四个参数
    // （positionalThreshold/velocityThreshold/snapAnimationSpec/decayAnimationSpec）在 1.12
    // 已移除——即使硬传，默认 fling 也会写死自己的行为（无惯性、只减速），读都读不到。
    // 落档手感统一由下面的 [SheetFlingBehavior] 接管。
    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = PlayerSheet.Closed,
            anchors = DraggableAnchors {
                PlayerSheet.Closed at 0f
            },
            confirmValueChange = { true },
        )
    }
    // 松手落档行为：**必须显式传给 `Modifier.anchoredDraggable`**，否则壳落档走的是库写死的
    // 默认（NoOpDecay 无惯性 + FastOutSlowIn 只减速），加速减速读都读不出来——这是此前
    // 「改了没感觉」的根因。这里按档位分派：全屏那一段用缓入缓出 S 曲线，卡片/收起保持弹簧。
    val flingBehavior: FlingBehavior = remember(sheetState) {
        SheetFlingBehavior(
            state = sheetState,
            cardSpec = SPRING_CLOSE,
            fullSpecFor = Motion::sheetFullFrom,
            closedSpec = SPRING_CLOSE,
        )
    }
    return remember { PlayerDockState(sheetState, flingBehavior, scope) }
}

internal const val SWIPE_THRESHOLD_DP = 56
