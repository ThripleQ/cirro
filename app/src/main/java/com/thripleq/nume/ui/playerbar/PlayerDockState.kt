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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

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
 *  一行程直达全屏（行程不变）。「壳顶全程随手指线性升降」这一条**已不再成立**：拖动路径下
 *  这一段改由跟随器驱动（[PlayerDockState.runFollowLoop]），末端刻意不严格跟手 ——
 *  滞后、末端阻力、「内容后到」都是从这里来的，也就是「质量感」本身。 */
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
    /**
     * 落位标志：松手后的 `performFling` 期间为 true（由 [SheetFlingBehavior] 的回调写）。
     *
     * 之所以用一个共享标志位、而不是本类的字段：`flingBehavior` 必须在本类**之前**构造
     * （构造顺序），那时还没有 this 可回调。刻意不用 Compose 状态 —— 只有 [step] 读它。
     */
    private val settlingFlag: AtomicBoolean,
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
     *  **两个数据源**：点迷你条的展开走 [entry]（点按专用时间轴，跳过分裂、内容全程参与运镜）；
     *  手势拖动走 [visualP]（**跟随器的输出**，不是手指量本身 —— 见 [runFollowLoop]）。
     *  选哪一个由 [tapExpand] 决定 —— 两套都归一到同一个 0..2 量纲，所以所有读取方
     *  （壳几何、dock 淡出、系统栏隐藏、内容档位）不必知道自己在哪套里。
     *
     *  为什么拖动不再是 `offset / travelPx` 本身：线性 1:1 读起来是「UI 在等比缩放」，
     *  不是「一个有重量的东西被抬起来」。`progress` 因此不读手指量，读的是追着手指量的
     *  跟随器输出（滞后 + 追上来的尾巴 + 末端变稠 = 质量感）。**手指量仍然存在于
     *  [rawProgress]，锚点/甩动选档/吸附判定全部继续读它** —— 变的只是「看起来怎么走」，
     *  不是「松手落到哪」。
     *
     *  读取方既有组合阶段的（PlayerPage 的壳几何、圆角、颜色），也有绘制阶段的（dock 的
     *  graphicsLayer、系统栏显隐）—— 都走这一个口子，所以换数据源时它们全都跟着切。 */
    val progress: Float
        get() = if (tapExpand) entry.value * 2f else visualP.coerceIn(0f, 2f)

    /** 内容档位（0..2）：与 [progress] 同量纲，但**比壳更慢**（内容跟随器 + 欠阻尼余振）。
     *  [PlayerPage] 的 `contentProgress` 读它 —— 壳先到位、内容后到，这一个相位差就是纵深。
     *  卡片锚点以下钳在 [HALF_ANCHOR_P]（那段内容固定为卡片版式、由壳裁剪揭示，不重新排版）。 */
    val contentProgress: Float
        get() = if (tapExpand) HALF_ANCHOR_P + (2f - HALF_ANCHOR_P) * entry.value
                else visualContentP.coerceAtLeast(HALF_ANCHOR_P)

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
     * 落位（松手后的 `SheetFlingBehavior.performFling`）期间为 true：跟随器改用
     * [Motion.DragFollowReleaseMs] 这条很小的时间常数，让壳立刻贴住既定落位轨迹。
     */
    private val settling: Boolean get() = settlingFlag.get()

    // ── 拖动跟随器：卡片↔全屏这一段「不严格跟手」的来源 ──────────────────────────
    // 拖动路径下 [progress] 读的不是手指量（[rawProgress]），而是这里的跟随器输出：
    //   壳 = 一阶低通追 raw（τ 上/下行不同 → 重力感；接近全屏时 τ 变大 → 末端阻力）
    //   内容 = 更慢的欠阻尼二阶追 raw（相位差 = 层次；ζ<1 → 余振 = 质量）
    // 三处「raw 会瞬变」的交接（点按落位 / 收起交接 / 锚点重注册）必须调 [syncFollow]，
    // 否则视觉量会从旧值一路追过去 —— 表现为「动画都结束了，壳又自己滑一段」。
    //
    // 只在 [open] 期间跑（[PlayerDock] 发起 [runFollowLoop]），收敛后挂起：停下来零成本。

    /** 壳的跟随量（0..2）。一阶，不会过冲 —— 壳位置要稳，过冲交给内容层。 */
    private var shellFollow = 0f

    /** 内容的跟随量与速度（0..2 / 每秒）：二阶欠阻尼，允许越过 raw 一点再收回。 */
    private var contentFollow = 0f
    private var contentVel = 0f

    /** 上一帧的视觉量：用来取跟随速度（阴影等速度耦合表现读 [followSpeed]）。 */
    private var lastVisual = 0f

    /** 壳的平滑进度：非点按期间 [progress] 读它。 */
    var visualP by mutableFloatStateOf(0f)
        private set

    /** 内容的平滑档位（0..2）：[contentProgress] 读它。 */
    var visualContentP by mutableFloatStateOf(0f)
        private set

    /** 跟随速度（progress 单位/秒）：拖动越快越大，落位后归零。 */
    var followSpeed by mutableFloatStateOf(0f)
        private set

    /**
     * 跟随的**接入权重**（0..1）= 惯量强度：只在展开末端起作用（区间见 [Motion.DragFollowMixFrom]），
     * 启动段严格 1:1。
     *
     * [step] 里它用来**缩放时间常数**（壳 `τ_eff = τ·w`、内容 `ω_eff = ω/w`），
     * **不参与输出插值** —— 那写法会让位置瞬移，理由见 `step` 里的长注释。
     *
     * 单独暴露是给**速度耦合**那类「与位置无关」的表现用（[PlayerPage] 的阴影速度项）：
     * 不乘它的话，中途甩动就会变沉，与「效果集中在末端」矛盾。
     */
    var dragFollowMix by mutableFloatStateOf(0f)
        private set

    /** 手指量：锚点、甩动选档、吸附判定用的那条（跟随器不动它）。
     *
     *  上限就是全屏锚点：`AnchoredDraggableState.offset` 本身已被钳在锚点范围内，
     *  这里再钳一道只是防 `travelPx` 换口径时的瞬态。 */
    private fun rawProgress(): Float = (sheetState.offset / travelPx).coerceIn(0f, RAW_MAX)

    /**
     * 把跟随器直接对齐到当前 raw —— **所有会让 raw 瞬变的交接点都要调它**。
     *
     * 三处：点按时间轴落位（offset 被 snap 到 Full）、点按期间点收起（同）、
     * 锚点重注册（travelPx 变化会让 `offset/travelPx` 整体改口径）、循环起停。
     */
    fun syncFollow() {
        val raw = rawProgress()
        shellFollow = raw
        contentFollow = raw
        contentVel = 0f
        visualP = raw
        visualContentP = raw
        lastVisual = raw
        followSpeed = 0f
        // 权重跟位置走：收敛挂起期间不会再算，留在旧值上会让阴影速度项的口径对不上
        // （比如从「拖到 1.7 停住」收敛时，旧值还可能是上一次全屏的 1）。
        dragFollowMix = smoothstep(Motion.DragFollowMixFrom, Motion.DragFollowMixTo, raw)
            .pow(Motion.DragFollowMixEase)
    }

    /**
     * 跟随循环：[open] 期间每帧积分，收敛后**挂起等 raw 变化**（不空转唤醒 Choreographer）。
     *
     * dt 取 [withFrameNanos] 的真实帧间隔而不是固定 16ms：掉帧时手感的「时间常数」才是真的
     * 时间常数，不会随帧率变软。
     */
    suspend fun runFollowLoop() {
        syncFollow()
        var lastNs = 0L
        var lastRaw = Float.NaN
        while (open) {
            val raw = rawProgress()
            // 「收敛」必须是**两个条件**：视觉量贴上了 raw，**且 raw 自己也停了**。
            //
            // 只看残差不够 —— 落位曲线末段本来就越来越慢（速度单调衰减到 0），滞后会一直落在阈值内，
            // 于是「刚贴上就挂起 → raw 只走一点点又唤醒」反复发生；而唤醒的第一帧按「无历史」
            // 处理、白白丢一帧。视觉量因此变成「冻结 → 跳一个阈值 → 冻结」的台阶，在壳上读成
            // 一顿一顿的抖动（用户 2026-10-03：「惯性滑行时候，手一直送着就不会」—— 拖得快时
            // 滞后远大于阈值，根本进不了这条分支，所以只有松手后才看得见）。
            val still = raw == lastRaw
            lastRaw = raw
            if (still &&
                abs(raw - shellFollow) < FOLLOW_EPS && abs(raw - contentFollow) < FOLLOW_EPS
            ) {
                // 静止且已贴合：**先精确对齐再挂起**。留残差会在全屏档露出发丝缝
                // （壳差一两像素没盖到顶），卡片档同理会让内容比例差一点点。
                syncFollow()
                snapshotFlow { rawProgress() }.first { it != raw }
                // 挂起期间的时间不能当一帧用（否则第一帧 dt 巨大、跟随器一步跨过去）。
                lastNs = 0L
                lastRaw = Float.NaN
                continue
            }
            val nowNs = withFrameNanos { it }
            val dt = frameDt(nowNs, lastNs)
            lastNs = nowNs
            step(raw, dt, rawStill = still)
        }
        // 收起后复位：raw 已回到 0，视觉量不能留在旧值上（下次打开第一帧就是迷你条本身）。
        syncFollow()
    }

    /**
     * 一帧的积分：壳（一阶 + 末端阻力）、内容（欠阻尼二阶）、接入权重。
     *
     * @param rawStill 本帧 [rawProgress] 与上一帧完全相同（手指没动、落位曲线也已跑完）。
     *   只用来关掉末端阻力 —— 见 [step] 内 `stick` 处的说明。
     */
    private fun step(raw: Float, dt: Float, rawStill: Boolean) {
        // 接入权重 w：**惯量强度**，整段压在展开末端（区间与幂次见 Motion 那边的 KDoc）。
        // 只用来缩放时间常数，**绝不参与输出插值** —— 理由见下面两处。
        val w = smoothstep(Motion.DragFollowMixFrom, Motion.DragFollowMixTo, raw)
            .pow(Motion.DragFollowMixEase)
        dragFollowMix = w

        // 末端阻力：临近全屏时 τ 放大，壳变「稠」——手指继续走、壳越走越慢。
        // 只改 τ、不改端点映射，所以锚点与视觉终点严格对应：跟随器最终一定收敛到 raw，
        // 松手后落位曲线接手把最后这段走完，读成「它自己滑进去」。
        // 增长取 stickT² ：前一半几乎不生效，最后 10% 才陡然压住。
        val stickT = ((raw / 2f - Motion.DragFollowStickFrom) / (1f - Motion.DragFollowStickFrom))
            .coerceIn(0f, 1f)
        val baseTauMs = when {
            // 松手落位：那条曲线（SettleWithVelocity）就是**最终轨迹**，不该再被滤一遍。
            // τ 收到很小让壳立刻贴住它，否则滞后会一路累积到 0.054 p，等曲线跑完 raw 冻住、
            // 壳才开始追一个静止目标 —— 真机实测用 ~900ms 慢慢挪完最后 ~51px，那截
            // 「本该结束、画面还在动」的蠕行就是尾巴。
            settling -> Motion.DragFollowReleaseMs
            raw >= shellFollow -> Motion.DragFollowRiseMs
            else -> Motion.DragFollowFallMs
        }
        // 末端阻力只在**手指（或落位曲线）还在推进**时才有意义。raw 已经停住而壳还没贴合，
        // 说明是「壳在追一个静止目标」——此刻再放大 τ 只会把这最后几像素的补齐拖成一条尾巴
        // （落位曲线收敛时残差约 6px，τ 被放大 1.8 倍后要 ~600ms 才抹平）。
        val stick = if (rawStill) 0f else stickGain()
        val tau = baseTauMs * (1f + stick * stickT * stickT) / 1000f

        // 壳：τ 随 w 缩放（w→0 时 τ→0 = 严格跟手），**输出直接取积分量**。
        //
        // 为什么不写 `vis = raw + (shell - raw) * w`：那把**滞后量**与**权重**相乘，w 一突变
        // 乘积就瞬移。真机实测（120Hz、travelPx=952px）：向下甩时 w 在 ~20ms 内由 0.974 掉到
        // 0.089，凭空多推壳 ~97px；向上甩反向少走 ~56px —— 用户 2026-10-03 报的「颤动」，
        // 且改窗宽/τ 都治不了（所以他「感受不到变化」）。
        // 两种写法的**稳态滞后完全相同**（w=0.5 时都是 0.5·v·τ），所以手感目标值不变，
        // 变的只是「不再有瞬移」。
        val tauEff = (tau * w).coerceAtLeast(MIN_TAU_S)
        shellFollow += (raw - shellFollow) * (1f - exp(-dt / tauEff))
        // 滞后上限：甩得越快，无界的滞后越离谱（也越会在权重渐入处产生拖动中的倒退）。
        // 钳住之后「重量」有个恒定的上限，慢拖又不会被钳到（滞后≈速度×τ 本身更小）。
        shellFollow = shellFollow.coerceIn(raw - Motion.DragFollowMaxLagP, raw + Motion.DragFollowMaxLagP)
        visualP = shellFollow

        // 内容：欠阻尼二阶，同样只缩放 ω、不插值输出（理由同壳）。
        // ω 越大跟得越紧：w→0 时内容与壳同步跟手（窗口外本就该如此），w=1 才出现相位差。
        // ω 由时间常数换算：一阶「τ 秒收敛」≈ 二阶 ω = 5/τ（同一稳态带）。
        // 半隐式欧拉要分子步：ω·h 过大时会发散；w 小时 ω 被放大，子步长要跟着变小。
        val omega = 5f / (Motion.ContentFollowMs / 1000f) / w.coerceAtLeast(MIN_OMEGA_SCALE)
        var remain = dt
        while (remain > 0f) {
            val h = minOf(remain, MAX_SUBSTEP_S, SUBSTEP_OMEGA_LIMIT / omega)
            val acc = omega * omega * (raw - contentFollow) -
                2f * Motion.ContentFollowDamping * omega * contentVel
            contentVel += acc * h
            contentFollow += contentVel * h
            remain -= h
        }
        visualContentP = contentFollow

        followSpeed = (visualP - lastVisual) / dt
        lastVisual = visualP
    }

    /**
     * 末端阻力增益：**落位期间为 0**。
     *
     * 阻力是「手指继续走、壳越走越慢」那个手感，手指都离开了就无从谈起；而它按 `stickT²`
     * 增长，贴到全屏时正好是满的（×1.8）—— 会把上面刚为落位收小的 τ 又放大回来。
     * 实测（回放脚本 `follow_check2.py`，真机参数）：不禁用时落位残余 16px/225ms，
     * 禁掉后 **4.9px/75ms**。
     */
    private fun stickGain(): Float = if (settling) 0f else Motion.DragFollowStickGain

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
            // 交接给拖动路径：跟随器也直接落到 2。不调的话它还在 0，撤掉 tapExpand 之后
            // 会从 0 一路追上来 —— 表现为「点按动画都结束了，壳又自己滑一段」。
            syncFollow()
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
                // 同 openByTap 的交接：跟随器一起落到 2，否则壳会从 0 追过来。
                syncFollow()
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
    // 「改了没感觉」的根因。这里按档位分派：全屏那一段走**继承松手速度**的落位曲线
    // （[SettleWithVelocity]），卡片/收起保持弹簧。
    //
    // 落位标志走一个独立的 holder：flingBehavior 必须先于 PlayerDockState 构造，
    // 那时还没有 this 可以回调（见 [PlayerDockState] 的 settlingFlag）。
    val settlingFlag = remember { AtomicBoolean(false) }
    val flingBehavior: FlingBehavior = remember(sheetState) {
        SheetFlingBehavior(
            state = sheetState,
            cardSpec = SPRING_CLOSE,
            closedSpec = SPRING_CLOSE,
            onSettling = { settlingFlag.set(it) },
        )
    }
    return remember { PlayerDockState(sheetState, flingBehavior, settlingFlag, scope) }
}

internal const val SWIPE_THRESHOLD_DP = 56

/**
 * 收敛阈值（progress 单位）：两侧残差都小于它、且 raw 已静止，才精确对齐并挂起。
 *
 * 它同时是**挂起前允许的最大残差**，也就是每次「对齐」可能产生的最大跳变（0.0004 p ≈ 0.46px，
 * 肉眼不可见）。曾经取 0.002（≈2.28px）：配上下面的挂起条件一起，就成了落位末段的台阶抖动。
 */
private const val FOLLOW_EPS = 0.0004f

/**
 * 名义帧长（s）：**首帧与从挂起唤醒的那一帧**用它，而不是丢弃该帧。
 *
 * 丢弃会凭空少一帧更新；在落位末段（每帧位移本就很小）读起来就是一记可见的停顿。
 * 挂起期间经过的真实时间也不能用（可能是几百毫秒），否则跟随器会一步跨过整段行程。
 */
private const val NOMINAL_FRAME_S = 1f / 60f

/** 一帧的 dt（s）：首帧/唤醒帧走 [NOMINAL_FRAME_S]，其余用真实帧间隔（掉帧时手感的时间常数才是真的）。 */
private fun frameDt(nowNs: Long, lastNs: Long): Float =
    if (lastNs == 0L) NOMINAL_FRAME_S
    else ((nowNs - lastNs) / 1_000_000_000f).coerceIn(1e-4f, MAX_FRAME_S)

/** 单帧 dt 上限（s）：掉帧或从后台回来时不能一步跨过整段行程。 */
private const val MAX_FRAME_S = 0.05f

/** 二阶内容弹簧的最大子步长（s）：半隐式欧拉的稳定性要求 ω·h 别太大。 */
private const val MAX_SUBSTEP_S = 0.016f

/** 子步长的 ω 上限：半隐式欧拉要求 ω·h 小（取 0.35，稳定且相位误差可忽略）。 */
private const val SUBSTEP_OMEGA_LIMIT = 0.35f

/**
 * τ 的下限（s）：接入权重 w→0 时要让壳**本帧直接对齐** raw，此时 τ 必须是正的
 * （否则除零）。取 1e-5 时 `exp(-dt/τ)` 下溢为 0，等效于「一步到位」。
 */
private const val MIN_TAU_S = 1e-5f

/**
 * ω 的放大幅度上限（= w 的下限）：内容 ω 要除以 w，w→0 时 ω→∞ 会让子步长过小。
 * 限在 4 倍（ω≤111 rad/s）既够「与壳同步」，又保住子步数在个位数。
 */
private const val MIN_OMEGA_SCALE = 0.25f

/** raw 的上限：即全屏锚点（见 [rawProgress]）。 */
private const val RAW_MAX = 2f

/** 平滑阶跃：0 在 edge0 以下、1 在 edge1 以上，中间走 S 曲线（两端导数为 0，无突变）。 */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
