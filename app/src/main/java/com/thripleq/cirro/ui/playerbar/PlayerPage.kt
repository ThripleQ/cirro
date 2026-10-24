package com.thripleq.cirro.ui.playerbar

import android.provider.Settings
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import kotlinx.coroutines.flow.collect

/**
 * 设备是否用「三键/两键」经典系统导航（而非手势）。预读 `navigation_mode`
 * （0=三键、1=两键、2=手势）；读不到就退回用导航栏 inset 高度判断（三键约 48dp，
 * 手势条明显更矮）。三键导航下系统栏**无法被真正隐藏**，且 hide/show 会让 inset 突变、
 * 连带 dock 高度与播放页几何抖动——所以要走一套"常显 + 底部让位"的分支。
 */
@Composable
private fun rememberIsThreeButtonNav(): Boolean {
    val context = LocalContext.current
    val density = LocalDensity.current
    val navBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    return remember(navBottomDp) {
        val mode = runCatching {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode")
        }.getOrNull()
        when (mode) {
            0, 1 -> true
            2 -> false
            else -> navBottomDp > 40.dp
        }
    }
}

/** 全屏播放面：壳（surfaceContainerHighest + 顶 18dp 圆角 + 1dp 阴影）从迷你条胶囊
 *  原位伸展成悬浮卡、再盖满全屏；壳顶停在状态栏下沿（与 ExpandableShell 面板一致）。
 *  几何读 progress 在组合里（与 ExpandableShell 同构，壳随 progress 每帧布局）。 */
@Composable
internal fun PlayerPage(
    state: PlayerDockState,
    player: Player,
    fullHeightPx: Float,
    dockHeightPx: Float,
    /** 导航收起时整块壳要下移的量（px）；导航可见时为 0。 */
    dockShiftPx: Float,
    onComments: (Rect) -> Unit,
    /** 当前曲目是否已红心（状态归 [PlayerDock] 管，这里只负责画和转发点击）。 */
    liked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val fullWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edgePx = with(density) { 10.dp.toPx() }
    val gapPx = with(density) { 14.dp.toPx() }
    val dockCornerPx = with(density) { 26.dp.toPx() }
    val statusBarTopPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }

    // ── 点按展开的形态改派（来源见 PlayerDockState.openByTap）──────────────────────
    // 点迷你条走的是一条**独立时间轴**：它与手势共用一个 progress 量纲（0..2），但形态上要
    // 跳过「分裂」那一拍（挤腰/断缝/内收是「从迷你条往上拖」的探索语言，点按只是白走路程），
    // 并把「盖满全屏」铺开到后半段 —— 拖动路径里它挤在最后 17%，点按要的是全程都在变。
    // 两处改派都只在点按期间生效，手势拖动一字不改。
    val tapExpand = state.tapExpand

    /** 「盖满全屏」的起点（点按路径）：从这里开始把壳推满，而不是像拖动路径那样挤在最后 17%。 */
    val tapFillStart = 0.45f

    /** 点按行程的「盖满全屏」权重：输入是行程比 t0（0..1）。 */
    fun tapFillT(t0: Float) = ((t0 - tapFillStart) / (1f - tapFillStart)).coerceIn(0f, 1f)

    /** 点按路径里内容淡入 / 收起键浮现所占的行程比（见下面的 contentAlpha / headerAlpha）。 */
    val tapContentIn = 0.25f
    val tapHeaderIn = 0.15f


    // 三档壳矩形（屏幕坐标，px）——「先扩展、再分裂」细胞分裂观感：
    //   p∈[0,SPLIT] 扩展：气泡底钉 dock 顶、左右贴满屏，顶从 dock 顶充气升到卡片顶
    //                    （状态栏下沿）。dock 内容（迷你条/导航）原位、画在壳下层；
    //                    dock 顶角随气泡长出收平（26→0）→ 气泡与 dock 浑然一体。
    //   p∈[SPLIT,1] 分裂：气泡在迷你条上方「掐断」——顶钉状态栏，底从 dock 顶升到
    //                    「dock 顶上方 edgePx」，左右收进 edgePx、四角转圆 → 悬浮卡；
    //                    下半 dock 顶角长回圆角（0→26）、露出原高。
    //   p∈[HALF_ANCHOR_P,2] 卡片→全屏：盖满含状态栏/导航栏（卡片档先稳定到 1.66）。
    //
    //   **连续性保证**：分裂点（t0=SPLIT）上 dock 圆角=0、气泡底边=dockTop+edgePx，
    //   扩展段末与分裂段初逐值相等，无跳变（分裂不再割裂）。
    fun shellRect(p: Float): Rect {
        val dockTopPx = fullHeightPx - dockHeightPx
        val full = Rect(0f, 0f, fullWidthPx, fullHeightPx)
        val t0 = p.coerceIn(0f, 1f)
        // 点按行程比。**不能用 t0** —— t0 是「分裂是否走完」的归一化量，在 p>1 时恒为 1；
        // 拿它当行程比，会让「盖满全屏」和内容运镜在行程一半就全部做完、后半段只剩壳在收尾
        // （与拖动路径"只动最后 17%"是同一类偏差，只是反了过来）。
        val tapT = (p / 2f).coerceIn(0f, 1f)
        // 卡片→全屏的插值在 [HALF_ANCHOR_P, 2] 段内进行：卡片档先稳定停留到 1.66，
        // 再继续拉才贴满屏（此前 p>1 就开始盖满，卡片档形同虚设、高度只有半屏）。
        // 点按路径不走这套：它没有「卡片档」这个概念，直接从 45% 行程起把壳推满（见 tapFillT）。
        val t1 = if (tapExpand) tapFillT(tapT)
                 else ((p - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
        val extT = (t0 / SPLIT).coerceIn(0f, 1f)
        // 分裂形态量：点按期间整段归零 —— 不挤腰（腰是 `waistT` 从它派生的）、
        // 左右不收（`inset` 由它线性给出）、dock 顶角不回涨（下面的 dockCornerCur）。
        // 于是气泡一路贴着屏边充气，形态上只剩「长大」这一件事。
        val splitT = if (tapExpand) 0f
                     else ((t0 - SPLIT) / (1f - SPLIT)).coerceIn(0f, 1f)
        val squeezeT = splitSqueezeT(splitT)
        // 断缝改用 t1：点按路径里「缝打开」与「壳底推向屏底」本来就是同一件事（都在落位阶段），
        // 且这样终点与拖动路径严格一致（p=2 时 breakT 都是 1 → 底角 26dp、颜色最亮档）。
        val breakT = if (tapExpand) t1 else splitBreakT(splitT)
        val waistT = splitWaistCurve(splitT)
        // dock 当前顶角半径：扩展段 26→0 收平，分裂段随 splitT 长回 26dp 并叠挤腰鼓包
        // （splitT=0.5 峰值 36dp，断开后落回 26dp）。
        val dockCornerCur = if (t0 < SPLIT) {
            dockCornerPx * (1f - extT)
        } else {
            val bumpPx = with(density) { WAIST_CORNER_DP.dp.toPx() } - dockCornerPx * SPLIT_SQUEEZE
            dockCornerPx * splitT + bumpPx * waistT
        }

        // 顶全程随 offset 线性升降（**跟手关键**）：p=0→dock 顶，p=1→屏中，p=2→屏顶。
        // 不再让分裂段把顶"钉在状态栏不动"——那正是之前感觉不跟手的根因：拖一截壳顶却不升。
        // 细胞分裂的形态（左右内收/底缝/圆角、dock 角联动）全部保留，叠加在上升之上。
        val top = dockTopPx * (1f - p / 2f)
        val bottom = if (t0 < SPLIT && !tapExpand) {
            // 扩展段：底边从背后包住 dock 当前圆角（圆角收平到 0 时恰为 dockTop）。
            dockTopPx + dockCornerCur
        } else {
            // 分裂段：挤腰时底边钉在 dockTop（两侧圆角涨大内收成腰、位置不动）；
            // 断开后缝从 0 连续打开（→ dockTop-gapPx）。
            // 点按路径走同一支：它的 breakT = t1 在行程前半恒为 0，底边正好钉在 dockTop，
            // 与上一支在 SPLIT 处的极限（dockCornerCur → 0）逐值相接，不会跳。
            dockTopPx - gapPx * breakT
        }
        val inset = edgePx * splitT
        // 导航收起：整块壳下移 dockShiftPx（气泡底仍贴实际迷你条），尺寸/比例不变；
        // 经 t1 在「卡片→全屏」段归零，全屏时仍精确盖满。
        val bubbleOrCard = Rect(
            inset,
            top + dockShiftPx,
            fullWidthPx - inset,
            bottom + dockShiftPx,
        )

        // ── 卡片 → 全屏：上下边「会合同步」（用户 2026-10-05 的方案）───────────────
        //
        // 两条边的行程天生不等（底边要跨过整个 dock，K40 口径 A≈344px vs B≈413px），
        // 同一条时间轴走不等行程 → 速度恒差 → 「上边效果密、下边效果稀」的几何病。
        // 旧结构用「底边单独的进度 + 末端阻力」压它，代价是底边「钉死→狂奔→急刹」三段断裂。
        //
        // 现在两条边共用 t1，按**剩余距离**（离各自目的地的 px）重排：
        //   前段 t1 ∈ [0, SyncJoinP]：剩余从「进入本段那一刻的快照」**匀速**收敛到
        //   [Motion.SyncRemainDp]；
        //   同步段 t1 ∈ [SyncJoinP, 1]：两边剩余距离**逐帧相等**、同速归零 —— 严格并肩走完，
        //   没有任何一边停住等另一边，也不存在谁先到/被钉住。
        //
        // 快照（t1=0 的等效形位）必须**固定**，不能每帧读当前 bubbleOrCard：那样顶会退化
        // 成双收缩（topNow 与 headT 同向叠加，前段速度比 0.65→9.4 漂移），「匀速走到剩 d」
        // 就没了。两条路径的 t1=0 对应的 p 都是常数：拖动 = HALF_ANCHOR_P（卡片档），
        // 点按 = 2·tapFillStart（t1 随 tapT 提前启动，进入时缝未开、底还贴 dock 顶）——
        // 纯函数即可重算，不需要额外状态。
        //
        // 速度接缝（K40 口径，t1 单位）：前段顶 352 / 底 437（比 1.24:1，底快 24% ——
        // 行程不等 + 会合剩余相等的必然，方案本性）；尾段两边同 306。join 处顶 −13%、
        // 底 −30% 换挡，方向不变。
        //
        // 左右边行程只有 edgePx（10dp），跟随 t1 线性收拢即可，不参与同步。
        if (t1 <= 0f) return bubbleOrCard
        val syncRemainPx = with(density) { Motion.SyncRemainDp.dp.toPx() }
        val p0 = if (tapExpand) 2f * tapFillStart else HALF_ANCHOR_P
        val topTravel = dockTopPx * (1f - p0 / 2f) + dockShiftPx   // 顶的目的地 = 0（屏顶）
        // 进入时刻的底：拖动=卡片档（缝已全开，底=dockTop−gap）；点按=tapFillStart 处
        // （breakT=t1=0，缝未开，底=dockTop）。两者都是 t1=0 时 bubbleOrCard.bottom 的值。
        val bottomAtJoin = dockTopPx - gapPx * (if (tapExpand) 0f else 1f) + dockShiftPx
        val bottomTravel = full.bottom - bottomAtJoin              // 底的目的地 = 屏底
        val headT = (t1 / Motion.SyncJoinP).coerceIn(0f, 1f)
        val tailT = ((t1 - Motion.SyncJoinP) / (1f - Motion.SyncJoinP)).coerceIn(0f, 1f)
        val topRemain = if (t1 < Motion.SyncJoinP) lerp(topTravel, syncRemainPx, headT)
                        else syncRemainPx * (1f - tailT)
        val bottomRemain = if (t1 < Motion.SyncJoinP) lerp(bottomTravel, syncRemainPx, headT)
                           else syncRemainPx * (1f - tailT)
        return Rect(
            inset * (1f - t1),
            topRemain,
            fullWidthPx - inset * (1f - t1),
            full.bottom - bottomRemain,
        )
    }

    val p = state.progress
    // 壳矩形：卡片→全屏段由 shellRect 内的「会合同步」曲线驱动（上下边剩 d 会合后同速收尾）。
    val rect = shellRect(p)
    val t0 = p.coerceIn(0f, 1f)
    // 点按行程比（0..1）：理由见 shellRect 内同名量 —— 不能用被钳位的 t0。
    val tapT = if (tapExpand) (p / 2f).coerceIn(0f, 1f) else 0f
    val t1 = if (tapExpand) tapFillT(tapT)
             else ((p - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
    // 与 shellRect 内同一套改派（同一个 tapFillT）：两处必须一致，否则壳与圆角/颜色错拍。
    val splitT = if (tapExpand) 0f
                 else ((t0 - SPLIT) / (1f - SPLIT)).coerceIn(0f, 1f)
    // 圆角统一（修复 18/26/19.5 混用）：壳四角与 dock 同族、全部落在 26dp——
    // 顶角全程 26dp（与 dock 同形，不收平、不换尺寸）；
    // 底角：扩展段方角（与 dock 一体）；分裂段「挤腰」时涨到 36dp 让两侧内收成腰，
    // 「断开」后落回 26dp（与 dock 顶角同半径）——不是两块平板对切。
    val waistCornerPx = with(density) { WAIST_CORNER_DP.dp.toPx() }
    val squeezeT = splitSqueezeT(splitT)
    // 断缝量：拖动路径由分裂段给出（断开后缝打开）；点按路径改用 t1 —— 理由见 shellRect 内同名处。
    val breakT = if (tapExpand) t1 else splitBreakT(splitT)
    val topCornerPx = dockCornerPx
    // 量化见 [Motion.QuantStepDp]：圆角是壳那层 `graphicsLayer` clip outline 的输入，
    // 逐帧连续变化 = 逐帧重建 outline + 重算投影。量化后值相同的帧会被 layer 的 diff
    // 整段跳过（26→36→26dp 在 0.5dp 台阶下只剩 20 档，而不是每帧一次）。
    val bottomCornerPx = Motion.quantPx(
        density,
        waistCornerPx * squeezeT * (1f - breakT) + dockCornerPx * breakT,
    )
    val shellShape = RoundedCornerShape(
        topStart = with(density) { topCornerPx.toDp() },
        topEnd = with(density) { topCornerPx.toDp() },
        bottomStart = with(density) { bottomCornerPx.toDp() },
        bottomEnd = with(density) { bottomCornerPx.toDp() },
    )
    // 底色：扩展/挤腰段 = dock 色（气泡就是 dock 长出来的，贴合时零色差、不割裂）；
    // 「断开」段（缝开始打开的同一拍）才渐亮到 surfaceContainerHigh——卡片剥落的同时
    // 显出自身层次；全屏段再升到 surfaceContainerHighest。
    val elevatedColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        breakT,
    )
    // 会合段进度（同步段的两边同速收尾）：「再上效果」的挂点 —— 底色升满、速度阴影
    // 都集中在这最后一段（前段两边匀速走位、无效果突变，密度均匀由几何保证）。
    // 点按路径不受影响（它本来就全程渐变、密度均匀）。
    val syncTailT = ((t1 - Motion.SyncJoinP) / (1f - Motion.SyncJoinP)).coerceIn(0f, 1f)
    val shellColor = lerp(
        elevatedColor,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        if (tapExpand) t1 else syncTailT,
    )
    // 内容淡入：拖动路径按「气泡长起来」的节奏渐显（壳长到卡片档才全亮，避免壳还矮、内容
    // 已全亮的挤压感）；点按路径快得多 —— 它全程只有 420ms，慢慢浮现会读成"糊"，前 25%
    // 行程（约 100ms）就该让内容立起来，剩下三帧都在做运镜。
    val contentAlpha = if (tapExpand) (tapT / tapContentIn).coerceIn(0f, 1f)
                       else (p / HALF_ANCHOR_P).coerceIn(0f, 1f)
    // 顶部拉手/收起：拖动路径分裂成卡后才浮现；点按路径要**立刻**出现 —— 否则 420ms 的展开里
    // 前段根本没有收起键，用户点进去想退却发现按钮还没长出来。
    val headerAlpha = if (tapExpand) (tapT / tapHeaderIn).coerceIn(0f, 1f) else splitT
    // 胶囊→卡片段：内容固定为卡片档尺寸，由壳裁剪揭示（封面不随气泡长大）—— 这层"冻结"
    // 现在已经落在 [PlayerDockState.contentProgress] 里（`coerceAtLeast(HALF_ANCHOR_P)`）；
    // 过卡片锚点后内容才逐帧连续过渡到全屏，避免半档处布局跳变。
    // pastCard 用**壳**的进度 p 判：壳是一阶跟随、不会过冲，不会在锚点附近来回翻；
    // 内容量是欠阻尼的（会越过 raw 一点），拿它判会让 contentRect 在阈值上抖。
    val pastCard = p > HALF_ANCHOR_P
    val cardRect = shellRect(HALF_ANCHOR_P)
    // 内容档位由 [PlayerDockState.contentProgress] 给：拖动路径下它比壳**慢半拍**
    // （跟随器留下的相位滞后），点按路径下它随 entry 全程 0→1。两条都在状态里算好，
    // 这里不再自己从 p 推 —— 否则内容就与壳严格同相，纵深感没了。
    val contentProgress = state.contentProgress
    // 点按路径的壳矩形始终交给内容：内容跟着壳一起长（不做"按全屏版式排好再被揭示"，
    // 那样壳还矮时内容会被裁掉大半）。
    val contentRect = if (tapExpand || pastCard) rect else cardRect

    // 沉浸：只在接近全屏时隐藏系统导航栏（半高时保持显示，dock 的 navigationBarsPadding
    // 布局稳定）；收起/回落到半高时恢复。用 snapshotFlow 轮询 progress，不引重组。
    //
    // 三键导航例外：系统栏隐藏不了（hide 只会让它变成半透明条），而 hide/show 触发的
    // inset 突变会让 dock 高度变化 → 播放页几何在临近全屏时"跳一下"。所以三键下**全程不隐藏**，
    // 底部让位改由 [PlayerPageContent] 的 padding 承担。
    val view = LocalView.current
    val activity = LocalActivity.current
    val threeButtonNav = rememberIsThreeButtonNav()
    // ⚠️ 必须 remember：`getInsetsController()` 每次调用都 new 一个对象（未覆写 equals，
    // 按身份比对），而**本组合每帧重组**（下面 272 行在组合期读 `state.progress`）。
    // 不 remember ⇒ 下面两个 effect 的 key 每帧都变 ⇒ ① snapshotFlow 协程每帧被取消重建；
    // ② DisposableEffect 每帧 dispose，`onDispose` 里的 `show()` 变成**每帧一次系统栏 IPC**
    // ——展开/收起那一趟（约 30 帧）就是 30 次跨进程调用，是这一屏最主要的掉帧源。
    val controller = remember(activity, view) {
        activity?.let { WindowCompat.getInsetsController(it.window, view) }
    }
    LaunchedEffect(controller, threeButtonNav) {
        if (controller == null) return@LaunchedEffect
        if (threeButtonNav) {
            controller.show(WindowInsetsCompat.Type.navigationBars())
            return@LaunchedEffect
        }
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        snapshotFlow { state.progress }.collect { p ->
            if (p >= 1.9f) {
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
    // 卸载时务必恢复系统栏：播放面可能被**直接移除**而非经收起动画（如进网页登录时
    // PlayerDock 整体卸载），那样上面的 snapshotFlow 不会再跑到 show 分支，导航栏会一直隐藏。
    DisposableEffect(controller) {
        onDispose { controller?.show(WindowInsetsCompat.Type.navigationBars()) }
    }
    // 返回键收起（仅全屏面在场时生效）。
    BackHandler { state.close() }

    fun doClose() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.close()
    }
    /** 壳上的空点击（吃掉落在壳上的指针）。必须 remember —— 见上面 clickable 的注释。 */
    val swallowClick = remember { {} }
    // 歌词页（内联/沉浸）在场时关掉壳的竖向拖拽：歌词页自己要用「下滑」进沉浸，
    // 同一条手势若还挂在壳上，手指一拉就先把整页收走（用户 2026-10-24：「歌词页与
    // 下滑退出全屏播放页冲突」）。歌词页的退出改由「点歌词回封面 / 返回键逐级退」，
    // 回到封面档后这里自动恢复，下滑收起照旧。
    var lyricsEngaged by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 壳：显式宽高 + 平移（与 ExpandableShell 同构），随 progress 每帧布局。
        // 阴影 + 圆角裁剪 + surfaceContainerHighest 底色，壳顶停在状态栏下沿。
        Box(
            Modifier
                .width(with(density) { (rect.right - rect.left).toDp() })
                .height(with(density) { (rect.bottom - rect.top).toDp() })
                .graphicsLayer {
                    translationX = rect.left
                    translationY = rect.top
                    // 扩展/挤腰段不投影（与 dock 浑然一体），「断开」成卡才浮起。
                    // 再叠一项**速度耦合**：拖得越快壳越压得低（阴影越沉）——「甩起来的东西有风」，
                    // 慢下来自动收回去。读 [PlayerDockState.followSpeed] 在绘制阶段，不引重组。
                    // 速度项乘**会合段进度**（与底色同一挂点）—— 中途甩动不变沉，效果集中在
                    // 上下边同速收尾的那一段（「这时候再上效果」）。
                    // 投影同样量化：它一变就要重算 RenderNode 的阴影，逐帧重算是这一屏
                    // 最贵的一项之一（见 [Motion.QuantStepDp]）。
                    shadowElevation = Motion.quantPx(
                        density,
                        4.dp.toPx() * breakT +
                            Motion.ShellShadowSpeedDp.dp.toPx() *
                            (state.followSpeed / Motion.ShellShadowSpeedRef).coerceIn(0f, 1f) *
                            syncTailT,
                    )
                    shape = shellShape
                    clip = true
                }
                .background(shellColor)
                // 拦截触摸：只拦壳实际区域；半高时 dock 区域不在此矩形内 → 仍可点。
                // onClick 必须 remember 固定：本组合逐帧重组（272 行读 progress），lambda
                // 每次新建 ⇒ clickable 节点每帧重建（Modifier 链 + 手势节点），白扔开销。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = swallowClick,
                )
                // 官方 anchoredDraggable：卡内上滑续开到全屏、下拉回 dock/收起。
                // 松手吸附/甩动由 flingBehavior 处理：全屏那一段用缓入缓出 S 曲线（先加速后减速），
                // 卡片/收起保持弹簧。reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开。
                // 点按展开期间禁用：那段时间 progress 读的是点按时间轴（entry），此时若手动拖动
                // 改了 offset，两个数据源会打架（手指在动、画面不动）。落位后自动交回。
                .anchoredDraggable(
                    state.sheetState,
                    flingBehavior = state.flingBehavior,
                    // 歌词页禁用（见 [lyricsEngaged]）：竖向手势归歌词页，避免下拉歌词
                    // 时把整个播放面收走。
                    enabled = !tapExpand && !lyricsEngaged,
                    reverseDirection = true,
                    orientation = Orientation.Vertical,
                ),
        ) {
        // 壳内两层叠放（同 Box）：
        //   1. 播放页内容（alpha = contentAlpha）：扩展段气泡长起来时浮现。
        //   2. 顶部拉手/收起（alpha = contentAlpha）：分裂成卡/全屏时才有。
        // 迷你条副本已删：壳只画「迷你条上方」的屏幕区，dock 里的真实迷你条
        // 全程原位可见，不需要副本衔接（分裂缝由卡片底留 10dp 表达）。
        Box(Modifier.fillMaxSize()) {
            PlayerPageContent(
                player = player,
                contentProgress = contentProgress,
                threeButtonNav = threeButtonNav,
                shellHeightPx = contentRect.height,
                shellWidthPx = contentRect.width,
                sleepEndAt = state.sleepEndAt,
                onSleepEndAtChange = { state.sleepEndAt = it },
                onComments = onComments,
                liked = liked,
                onToggleLike = onToggleLike,
                onLyricsEngaged = { lyricsEngaged = it },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha },
            ) {
                // 顶部：拉手 + 收起箭头。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CirroShape.Track)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CirroFade.HANDLE),
                            ),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { doClose() }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "收起",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        }
    }
}
