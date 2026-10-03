package com.thripleq.nume.ui.playerbar

import android.provider.Settings
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.ui.components.FadingMarqueeText
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
    onPlaceholderAction: () -> Unit,
    onComments: (Rect) -> Unit,
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

    fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
        a.left + (b.left - a.left) * t,
        a.top + (b.top - a.top) * t,
        a.right + (b.right - a.right) * t,
        a.bottom + (b.bottom - a.bottom) * t,
    )

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

        // 顶边 / 左右 / 圆角 / 颜色 / **底边**全走同一条 t1：壳是一个刚体，两条边必须同时到位。
        //
        // 曾让底边提前 0.22 档到位（治「下边软软的」），代价是：底边在离全屏还有 ≈114px 时
        // 突然停死，顶边却继续往上走 —— 读作「滑行中突然收到很大阻力，但距离结束还有不小距离」
        // （用户 2026-10-03）。既然效果窗口已经推到 1.76→1.99（底边那时已走完 97%），
        // 「底边拖着一截没走完」的旧问题已由窗口推后解决，不需要再让底边提前了。
        return lerpRect(bubbleOrCard, full, t1)
    }

    val p = state.progress
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
    val bottomCornerPx =
        waistCornerPx * squeezeT * (1f - breakT) + dockCornerPx * breakT
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
    val shellColor = lerp(
        elevatedColor,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        t1,
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
    val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
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
                    // 速度项还要乘接入权重 [PlayerDockState.dragFollowMix] —— 这一项与位置无关，
                    // 不乘的话中途甩动就变沉，与「这些效果集中在展开末端」的意图相反。
                    shadowElevation = 4.dp.toPx() * breakT +
                        Motion.ShellShadowSpeedDp.dp.toPx() *
                        (state.followSpeed / Motion.ShellShadowSpeedRef).coerceIn(0f, 1f) *
                        state.dragFollowMix
                    shape = shellShape
                    clip = true
                }
                .background(shellColor)
                // 拦截触摸：只拦壳实际区域；半高时 dock 区域不在此矩形内 → 仍可点。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                // 官方 anchoredDraggable：卡内上滑续开到全屏、下拉回 dock/收起。
                // 松手吸附/甩动由 flingBehavior 处理：全屏那一段用缓入缓出 S 曲线（先加速后减速），
                // 卡片/收起保持弹簧。reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开。
                // 点按展开期间禁用：那段时间 progress 读的是点按时间轴（entry），此时若手动拖动
                // 改了 offset，两个数据源会打架（手指在动、画面不动）。落位后自动交回。
                .anchoredDraggable(
                    state.sheetState,
                    flingBehavior = state.flingBehavior,
                    enabled = !tapExpand,
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
                onPlaceholderAction = onPlaceholderAction,
                onComments = onComments,
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
                            .clip(NumeShape.Track)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NumeFade.HANDLE),
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

/**
 * 歌名右侧红心/评论的图标尺寸。
 *
 * 2026-10-03 用户：「红心和评论太小了……最终调成一样高」。原来是 `Icon` 的默认 24dp，
 * 而全屏档歌名是 28sp —— 图标明显矮一头；现在歌名收到 24sp（见 `titleFont`）、
 * 图标抬到 28dp，两者的**字面/图形高度**相当（中文字面高 ≈ 0.86em ≈ 21dp，
 * Material 图标墨迹 ≈ 0.83×28 ≈ 23dp）。
 *
 * 触控盒仍是 40dp（见 [TitleActionRowWidth]）：图标只动图形，不动点按区域。
 */
private val TitleActionIcon = 28.dp

/** 红心/评论的触控盒边长（图标只动图形，不动这个）。 */
private val TitleActionButton = 40.dp

/**
 * 红心 + 评论那一行的宽度（也是展开时宽度动画的终点值）。
 *
 * = 2 × [TitleActionButton]。这个值必须与盒宽之和严格相等：小的那个会被裁
 * （外层 `clipToBounds`），大的那个会凭空占走歌名的宽度。
 */
private val TitleActionRowWidth = TitleActionButton * 2

/**
 * 三键（切歌/播放）之下、底部功能胶囊行之上的间距。
 *
 * 原来是 24dp。这里额外多出 33dp —— 那是上半段**那条 1dp 分割线连同上下各 16dp 内缩**
 * 让出来的量（用户：「歌手下边的分割线删掉」）。整块撤掉之后原样挪到这里，于是：
 * - 整列总高**不变**（不会把胶囊行挤出屏幕，也不会挤压封面）；
 * - 进度条与三键整体**上移 33dp**（用户：「进度条上移，下面的操作按钮也都上移」）——
 *   因为这两者的绝对位置是在它们**之下**的内容高度决定的（块底贴着列底），
 *   把分割线的高度挪到三键之下，就等于把这两者往上抬。
 */
private val TransportBottomGap = 24.dp + 33.dp

/** 播放页主体：单一布局随 sc（0=卡片档，1=全屏）连续形变，卡片→全屏无割裂。
 *  骨架全程一致：封面 → 标题（含右侧红心/评论）→ 歌手 →（弹性空白）→ 进度 → 控制；
 *  全屏专属的动作行/功能胶囊行以「高度+透明度」随 sc 长出，播放键**始终是裸图标**、
 *  只从小到大（2026-10-03 撤掉了原先那层 primaryContainer 圆）。卡片档（sc=0）即原紧凑布局。 */
@Composable
internal fun PlayerPageContent(
    player: Player,
    contentProgress: Float,
    /** 三键导航：系统栏常显，全屏时内容底部要让出导航栏，避免被三键压住。 */
    threeButtonNav: Boolean,
    shellHeightPx: Float,
    shellWidthPx: Float,
    sleepEndAt: Long,
    onSleepEndAtChange: (Long) -> Unit,
    onPlaceholderAction: () -> Unit,
    onComments: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var seekPending by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }
    var queueOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var commentRect by remember { mutableStateOf(Rect.Zero) }
    val state = rememberPlayerState(player)
    // 歌词：惰性加载 —— 只有歌词面板打开时才按当前曲目请求（切换曲目自动重载）。
    val lyricsVm: LyricsViewModel = hiltViewModel()
    val lyricsState by lyricsVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(lyricsOpen, state.trackId) {
        if (lyricsOpen) lyricsVm.load(state.trackId)
    }
    // 进度是高频状态：单独订阅（拖动时冻结，避免轮询跟手指打架）。
    val positionMs by rememberPlayerPosition(player) { seekPending }
    val rangeMax = state.durationMs.toFloat().coerceAtLeast(1f)
    // 拖动中途若曲目结束/切歌使 durationMs 归零，Slider 会被移除、onValueChangeFinished 不再触发，
    // seekPending 会永久卡住（进度轮询被冻结在拖动值）。这里兜底复位。
    LaunchedEffect(seekPending, state.durationMs) {
        if (seekPending && state.durationMs <= 0L) seekPending = false
    }

    val density = LocalDensity.current
    // 上限放到 1 以上（[Motion.ContentOvershootMax]）：内容跟随器是欠阻尼的，收尾会越过一点
    // 再收回 —— 钳在 1 就正好把这一下的余振抹掉了。
    val sc = ((contentProgress - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P))
        .coerceIn(0f, Motion.ContentOvershootMax)
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    // 三键导航常显，全屏时底部的功能胶囊行要抬到三键之上；随 sc 渐入，卡片档不动，
    // 避免半档处布局跳变（三键 inset 恒定，不存在"隐藏后归零"的抖动）。
    val navBottomDp = if (threeButtonNav) {
        with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    } else {
        0.dp
    }
    val widthDp = with(density) { shellWidthPx.toDp() }
    val heightDp = with(density) { shellHeightPx.toDp() }

    // 封面：卡片 = 内容区同宽（24dp 边距）；全屏 = 16dp 边距 + 屏高钳制。
    val coverCard = minOf(widthDp - 48.dp, heightDp * 0.58f)
    val coverFull = minOf(widthDp - 32.dp, heightDp * 0.45f)
    val coverDim = androidx.compose.ui.unit.lerp(coverCard, coverFull, sc)
    // 封面圆角：卡片档=Card(16)、全屏档=Capsule(22)（胶囊呼应）；Dp.lerp 需裸值，注释锚定语义
    val coverCorner = androidx.compose.ui.unit.lerp(16.dp, 22.dp, sc)
    val titleGap = androidx.compose.ui.unit.lerp(14.dp, 20.dp, sc)
    val ctrlGap = androidx.compose.ui.unit.lerp(10.dp, 18.dp, sc)
    val sideBtnDim = androidx.compose.ui.unit.lerp(26.dp, 34.dp, sc)
    // 2026-10-03 用户：「标题字号太大」——全屏档 28sp → 24sp（卡片档 21sp 不动）。
    // 与右侧红心/评论（[TitleActionIcon] = 28dp）一起收，两者最终高度相当。
    val titleFont = androidx.compose.ui.unit.lerp(21.sp, 24.sp, sc)
    val artistFont = androidx.compose.ui.unit.lerp(14.sp, 16.sp, sc)
    val titleColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        sc,
    )
    // 播放键：用户「不要这种有红色圆圈的」「更大」。
    // 撤掉原来那层 primaryContainer 圆（原来随 sc 渐显），只剩裸图标；
    // 盒子 48→64 只当触控区（图标变大后要留出余量），图标 44→48（**变大**而不是原来的变小）。
    val playBox = androidx.compose.ui.unit.lerp(48.dp, 64.dp, sc)
    val playIcon = androidx.compose.ui.unit.lerp(44.dp, 48.dp, sc)
    // 没有容器色了，播放图标与切歌图标同色 —— 靠尺寸（48 vs 34）分主次。
    val playTint = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = androidx.compose.ui.unit.lerp(24.dp, 16.dp, sc))
            // 封面到卡片上边的距离取左右边距的 5:4（30dp vs 24dp），略长一点更透气。
            // 底部额外让出导航栏（仅三键导航、且随全屏进度渐入）。
            .padding(top = 30.dp, bottom = 20.dp + navBottomDp * sc),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 全屏时顶部让出状态栏（卡片档壳顶已在状态栏下，无需让位）。
        Spacer(Modifier.height(androidx.compose.ui.unit.lerp(0.dp, statusBarDp + 16.dp, sc)))

        // 封面 / 歌词二选一：点封面切到歌词（有曲目时），再点回封面。同尺寸同圆角，
        // 切换零位移 —— 歌词占用封面矩形，标题/进度/控制不动。
        Box(
            modifier = Modifier
                .size(coverDim)
                .clip(RoundedCornerShape(coverCorner))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (state.trackId != null) lyricsOpen = !lyricsOpen },
        ) {
            if (lyricsOpen) {
                LyricsView(
                    uiState = lyricsState,
                    positionMs = positionMs,
                    onSeek = { PlayerHolder.seekTo(player, it) },
                    modifier = Modifier.fillMaxSize(),
                )
                // 歌词打开时点行=跳转，需要一个明确的「回封面」出口（点空白处也不保险）。
                IconButton(
                    onClick = { lyricsOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Album,
                        contentDescription = "封面",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CoverArt(
                    state = state,
                    dim = coverDim,
                    corner = coverCorner,
                    iconSize = androidx.compose.ui.unit.lerp(88.dp, 96.dp, sc),
                )
            }
        }

        Spacer(Modifier.height(titleGap))

        // Track / metadata + 红心/评论：参照网易云 —— 歌名/歌手左对齐上下叠放。
        // 右侧红心+评论与**歌名那一行**同轴居中对齐（不是与「歌名+歌手」整块居中）：
        // 原来是外层 Row 整块 CenterVertically，图标因此落在整块的中线上，比歌名低半个
        // 歌手行 —— 用户：「红心，评论和标题对齐，齐平」。改成把图标放进歌名那一行之后，
        // 行高 = max(歌名行高, 图标钮 40dp)，两者各按自己的中心对齐，自然齐平；
        // 歌手另起一行、占满宽度（不受图标挤压）。
        // 文字过长横向跑马而不截断；右侧图标随全屏进度 sc 渐显（卡片档宽度收为 0）。
        // 播放列表按钮按用户要求暂隐藏。
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FadingMarqueeText(
                    text = state.title.ifEmpty { "暂无播放" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = titleFont),
                    color = titleColor,
                    // 权重：图标行贴右端、歌名占剩下的宽度（跑马按容器宽判定）。
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp * sc))
                // 红心/评论：全屏专属，横向并排，随 sc 长出。
                Row(
                    modifier = Modifier
                        .width(TitleActionRowWidth * sc)
                        // 高度也跟着 sc 归零：否则卡片档这一行仍按 40dp 触控盒撑高，
                        // 把标题块从 32+4+20 顶成 40+4+20（卡片档是逐像素调过的紧凑版式，
                        // 不能因为一个 width=0 的隐藏行而整体长胖 8dp）。
                        .height(TitleActionButton * sc)
                        .clipToBounds()
                        .graphicsLayer { alpha = sc },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPlaceholderAction, modifier = Modifier.size(TitleActionButton)) {
                        Icon(
                            Icons.Filled.Favorite,
                            "收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TitleActionIcon),
                        )
                    }
                    IconButton(
                        onClick = { onComments(commentRect) },
                        modifier = Modifier
                            .size(TitleActionButton)
                            .onGloballyPositioned { commentRect = it.boundsInWindow() },
                    ) {
                        Icon(
                            Icons.Filled.Chat,
                            "评论",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TitleActionIcon),
                        )
                    }
                }
            }
            // 歌手为空时整行折叠，不留空洞。
            if (state.artist.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FadingMarqueeText(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = artistFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 进度条之上不再画东西：原来这里有一条 1dp 分割线（上下各 16dp 内缩，共占 33dp），
        // 用户：「歌手下边的分割线删掉」。整块撤掉之后把这 33dp 原样挪到三键之下
        // （见 [TransportBottomGap]）—— 进度条与三键因此整体上移 33dp，而整列总高不变。
        if (state.durationMs > 0) {
            Slider(
                value = if (seekPending) dragMs.toFloat() else positionMs.toFloat(),
                onValueChange = { dragMs = it.toLong(); seekPending = true },
                onValueChangeFinished = {
                    PlayerHolder.seekTo(player, dragMs)
                    seekPending = false
                },
                valueRange = 0f..rangeMax,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // 暂无播放：只画一条干净的静态轨道 —— M3 disabled Slider 会留一个悬浮 thumb
            // 和端点小圆点，像坏掉；这里用同样的轨道高度/内缩，但去掉 thumb。
            Box(
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(NumeShape.Track)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = NumeFade.TRACK)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTime(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(ctrlGap))

        // Transport controls：三枚都是**裸图标**（用户：「播放按钮不要这种有红色圆圈的」）。
        // 图标一律用 Rounded 变体（用户：「切歌，播放按钮都换成圆润一点的」）—— Filled 的
        // 跳曲/播放是硬直边，Rounded 的圆角与整页的胶囊语言一致。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                // 40 → 32：撤掉圆播放键之后，盒子比图标大出来的那圈（(64-48)/2 = 8dp）
                // 会让视觉间距凭空变宽，这里减回去，让三枚图标的**边缘**间距与改前一致。
                androidx.compose.ui.unit.lerp(0.dp, 32.dp, sc),
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlayerHolder.skipPrevious(player) }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(sideBtnDim),
                )
            }
            // 盒子只当触控区（图标变大后要留余量），本身不画任何底。
            Box(
                modifier = Modifier.size(playBox),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { PlayerHolder.togglePlay(player) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = when {
                            state.isBuffering -> Icons.Rounded.MoreHoriz
                            state.isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = playTint,
                        modifier = Modifier.size(playIcon),
                    )
                }
            }
            IconButton(onClick = { PlayerHolder.skipNext(player) }) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(sideBtnDim),
                )
            }
        }

        // 全屏专属：功能胶囊行，高度与透明度随 sc 长出。
        // 间距从 24dp 变 [TransportBottomGap]（见该常量的说明：分割线让出的 33dp 挪到了这里）。
        Spacer(Modifier.height(TransportBottomGap * sc))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp * sc)
                .clipToBounds()
                .graphicsLayer { alpha = sc },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FullChip(
                icon = Icons.Filled.Shuffle,
                contentDescription = "随机播放",
                active = state.shuffleEnabled,
                onClick = { PlayerHolder.toggleShuffle(player) },
            )
            FullChip(
                icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = "循环模式",
                active = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = { PlayerHolder.cycleRepeat(player) },
            )
            SleepTimerChip(endAt = sleepEndAt, onEndAtChange = onSleepEndAtChange)
            FullChip(
                icon = Icons.Filled.MoreVert,
                contentDescription = "更多",
                active = false,
                onClick = { settingsOpen = true },
            )
        }

        if (queueOpen) {
            PlayerQueueSheet(player = player, onDismiss = { queueOpen = false })
        }
        if (settingsOpen) {
            PlayerSettingsSheet(onDismiss = { settingsOpen = false })
        }

        state.errorText?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
