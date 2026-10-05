package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeShape
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.thripleq.nume.Profile
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.swallowPointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 常驻底部 dock + 全屏播放页（合体）。
 *
 * 一个组件、一份 [state]：收起时只露底部 dock（拉手+迷你播放条+导航行，两段），
 * 迷你条**上滑 1:1** 跟手把全屏播放面从底部拉出盖满屏；点击直接整页弹出。
 * 全屏面只在 draw（graphicsLayer）读 progress，绝不因动画数值重组/挂载（上次翻车的坑）。
 *
 * ⚠️ 上面这句说的是**本组件（dock 这一侧）**：圆角、阴影、透明度全在 `graphicsLayer {}` 的
 * block 里算，动画数值再快也不会让 dock 重组。**别把它推广到全屏面** —— `PlayerPage` 的
 * 壳几何与内容版式必须每帧参与布局（封面尺寸、字号、边距都要按壳的真实宽高排版），它的
 * progress 就是在组合期读的，**每帧重组是设计的一部分，不是待修的 bug**。
 * 那一侧的纪律是另外三条：
 *   ① 能下沉到 draw 的量必须下沉（阴影速度项、内容 alpha 都在 `graphicsLayer` 里读）；
 *   ② 每帧路径上不许有昂贵操作 —— 文本重测、对象分配、协程重启（`FadingMarqueeText` 曾把
 *      逐帧变的宽度写进 `LaunchedEffect` 的 key，于是整个展开过程一圈都滚不起来）；
 *   ③ 高频值（进度）的解包点要压在最小消费作用域里（`PlayerProgressRow` / 歌词面板），
 *      绝不在内容区函数体里 `by` —— 重组作用域是函数级的，一解包就是整页陪跑。
 */
@Composable
fun PlayerDock(
    player: Player,
    state: PlayerDockState,
    selected: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    /** 是否显示底部导航行：展开壳看列表时收起，只保留迷你播放条。 */
    navVisible: Boolean = true,
    /** 全屏播放页里的占位动作（尚未接通的入口统一给「开发中」）。 */
    onPlaceholderAction: () -> Unit = {},
    /** 全屏播放页的评论键：打开当前曲目的评论页（携带按钮窗口矩形作浮现起点）。 */
    onComments: (Rect) -> Unit = {},
    /** dock 总高（dp）实时上报，供上层内容避让/Profile 展开壳让位。 */
    onIslandHeightChange: (Float) -> Unit = {},
) {
    val playerState = rememberPlayerState(player)
    // 进度是高频状态：单独订阅，只有进度条随 250ms 轮询重组。
    val positionState = rememberPlayerPosition(player)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // 是否已进入「卡片→全屏」档：决定 dock 与气泡的叠放层级。
    // 只在跨过 p=1 时翻转，derivedStateOf 保证不因每帧 progress 变化而重组。
    val isFullscreen by remember { derivedStateOf { state.progress > 1f } }
    val barHeight = 68.dp

    // 全屏播放面需盖满整个窗口（含状态栏/导航栏）：用根布局实测高度（edge-to-edge 下
    // 才是真正的物理屏高），screenHeightDp 不含系统栏，会短一截、底部露背景。
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }
    val fullHeightPx = if (measuredHeightPx > 0f) {
        measuredHeightPx
    } else {
        with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }

    // 总高上报：实际测量 dock 高度（含底部手势条 inset）。
    // 底部导航行是 AnimatedVisibility：其 200ms 收放会让 dockHeightPx **每帧变化**，若每帧
    // 上报，父级 islandHeight→各屏底部 padding 会跟着每帧重组/重排（展开壳时尤其明显）。
    // collectLatest 把「最后一帧之后的稳定值」延迟一小段再上报——动画期间只发一次稳态值。
    var dockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { dockHeightPx }.collectLatest { h ->
            if (h > 0) {
                delay(120)
                onIslandHeightChange(with(density) { h.toDp() }.value)
            }
        }
    }

    // 定时关闭倒数：放在**始终常驻的 PlayerDock**（不是只在播放页组合的芯片）里，播放页收起成
    // 迷你条后计时仍继续。墙钟判定，进程挂起/休眠不漂移。
    LaunchedEffect(state.sleepEndAt) {
        if (state.sleepEndAt == 0L) return@LaunchedEffect
        while (true) {
            if (System.currentTimeMillis() >= state.sleepEndAt) {
                player.pause()
                state.sleepEndAt = 0L
                return@LaunchedEffect
            }
            delay(1000)
        }
    }

    // 导航行被收起时 dock 变矮（只留迷你条）。播放页几何按「导航可见」的名义 dock 高度算，
    // 再把整块壳下移被收起的高度（[dockShiftPx]）—— 卡片尺寸/比例不变，只是整体随迷你条下移，
    // 拖拽仍 1:1 跟手。导航可见时名义高度 == 实测高度，一切照旧。
    val navRowReservePx = with(density) { 65.dp.toPx() } // 分隔线 1dp + 导航行 64dp
    val geometryDockHeightPx =
        if (navVisible) dockHeightPx.toFloat() else dockHeightPx + navRowReservePx
    val dockShiftPx = geometryDockHeightPx - dockHeightPx

    // 手势总行程 = 一个 progress 档位的位移。全屏锚点 = 2*travelPx = 屏高-dock 高 =
    // 手指从 dock 顶一路拉到屏顶的可见距离 —— **一行程直达全屏**，跟手不费劲，
    // 不再像以前那样全屏锚点在两倍行程、手指拖满一屏都够不到就弹回。
    val travelPx = ((fullHeightPx - geometryDockHeightPx) / 2f).coerceAtLeast(1f)

    // 把行程同步进 state，并把三档锚点像素注册给 AnchoredDraggableState（拖动/吸附据此 1:1）。
    // 锚点 = 胶囊展开进度（px）：Closed=0、Half=HALF_ANCHOR_P*travelPx（高卡片，内容装得下）、
    // Full=2*travelPx（盖满全屏）。
    LaunchedEffect(travelPx) {
        state.travelPx = travelPx
        state.sheetState.updateAnchors(
            DraggableAnchors {
                PlayerSheet.Closed at 0f
                PlayerSheet.Half at HALF_ANCHOR_P * travelPx
                PlayerSheet.Full at 2f * travelPx
            },
            newTarget = state.sheetState.currentValue,
        )
        state.anchorsReady = true
        // 锚点/行程换了口径（导航显隐会让 dock 变矮 → travelPx 变），`offset/travelPx` 整体
        // 平移。跟随器要一起对齐，否则会从旧口径一路追到新口径（壳自己滑一段）。
        state.syncFollow()
    }

    // 拖动跟随器：只在播放页在场时跑（open 起跑、收起后自己退出并复位）。
    // 它让卡片↔全屏这一段「不严格跟手」—— 壳追着手指走、内容再慢一拍，见 [PlayerDockState.runFollowLoop]。
    // 点按时间轴期间它也空转（progress 读的是 entry），但每帧只做几次浮点运算，可忽略。
    LaunchedEffect(state.open) {
        if (state.open) state.runFollowLoop()
    }

    // 组合与否由锚点状态驱动：迷你条一拖动（offset>0）就组合播放面；
    // 完全落回 dock 锚点（settled）才卸载。替代手搓的 beginDrag。
    LaunchedEffect(state.sheetState) {
        snapshotFlow { state.sheetState.offset > 1f }
            .distinctUntilChanged()
            .collect { moved -> if (moved) state.open = true }
    }
    LaunchedEffect(state.sheetState) {
        snapshotFlow { state.sheetState.settledValue }.collect { v ->
            if (v == PlayerSheet.Closed) state.open = false
            // 松手吸附到位：半高/全屏给一个轻 tick，让「滑档成功」有明确触感（跟手）。
            if (v == PlayerSheet.Half || v == PlayerSheet.Full) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { measuredHeightPx = it.height.toFloat() }) {
        // ---- 底部常驻 dock ----
        // 展开/分裂档（p≤1）时 dock 叠在气泡之上：气泡底边向下包住 dock 的圆角，
        // 背景基底不会从圆角漏出；全屏档（p>1）气泡反过来盖住 dock。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(if (isFullscreen) -1f else 1f)
                .graphicsLayer {
                    // dock 顶圆角随「扩展→分裂→断开」连续变化：
                    // 扩展段顶角收平（26→0，母细胞顶边与气泡连成一线）；
                    // 分裂段「挤腰」时顶角涨到 36dp，两侧内收成腰（dock 位置不动，
                    // 靠圆角涨大产生内收，不会把底部抬离屏幕底）；「断开」后落回 26dp。
                    val p = state.progress
                    val t0 = p.coerceIn(0f, 1f)
                    val extT = (t0 / SPLIT).coerceIn(0f, 1f)
                    val splitT = ((t0 - SPLIT) / (1f - SPLIT)).coerceIn(0f, 1f)
                    val waistT = splitWaistCurve(splitT)
                    val cornerFrac = if (t0 < SPLIT) 1f - extT else splitT
                    // 挤腰：分裂段顶角随 splitT 长回（0→26），同时 waistT 在挤腰峰值叠一个
                    // 36dp 的鼓包、断开后回落 —— 腰真正在挤腰段挤出来。
                    // 峰值修正：splitT=0.5 时 26*0.5 + 增量*1 = 36 → 增量 = 36 - 26*0.5。
                    val basePx = with(density) { 26.dp.toPx() }
                    val bumpPx = with(density) { WAIST_CORNER_DP.dp.toPx() } - basePx * SPLIT_SQUEEZE
                    val cornerPx = basePx * cornerFrac + bumpPx * waistT
                    this.shape = RoundedCornerShape(
                        topStart = with(density) { cornerPx.toDp() },
                        topEnd = with(density) { cornerPx.toDp() },
                    )
                    clip = true
                    shadowElevation = 2.dp.toPx() * (1f - t0)
                    // 卡片档（p≤HALF_ANCHOR_P）dock 完整可见；只有从卡片继续拉向全屏才淡出。
                    alpha = if (p <= HALF_ANCHOR_P) 1f
                    else ((2f - p) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .onSizeChanged { dockHeightPx = it.height },
        ) {
            // 漏风补漏（2026-10-05 用户：「点 dock 栏，点到按钮外面会穿透到下层内容，造成
            // 误触」）：dock 自己只有一块底色、不是指针命中目标，于是点在**按钮之外**的空白处
            // （迷你条上下的留白、导航行两格之间、分隔线一带）时，事件会继续落到底下正在滚动的
            // 列表上 —— 观感就是"点了一下 dock，底下那行却被点开了"。
            //
            // 【必须与迷你条/导航行**平级**、垫在它们下面（声明在前 = 画在下、命中测试在后）】
            // 最初是把 [swallowPointerInput] 挂在**外层 Column 上**（= 迷你条的祖先）：那一层每帧
            // 把 Main 阶段的 change 全消费掉，而任何靠 `awaitPointerSlopOrCancellation` 起步的手势
            // （`anchoredDraggable`、各类 drag detector）在**未越过 touch slop** 时会在 Final 阶段
            // 复核「有没有别人消费过」，一被消费就 `return null` 取消 —— 于是竖拖只剩"单个事件里
            // 就跨过 slop"（= 甩）能活，慢拖在第一帧就被掐掉。**这是源码级可推的风险**（该复核分支
            // 见 `awaitPointerSlopOrCancellation` 的 `awaitPointerEvent(Final)`），2026-10-05 那批
            // 从 `74e4767` 起的包（含 987f31b / 662adc2）都带着它。
            //
            // 注意别把它说成"用户慢拖症状的唯一真凶"：真机实测（K40）显示，**慢拖拉不出来在更早
            // 的包上就已经存在**，那条是落档门槛造成的（详见 [SheetFlingBehavior.CARD_COMMIT_FRAC]）；
            // 本层是**另一条**会让慢拖彻底死掉的路径，两条一起修才对得上用户描述的全部现象。
            //
            // 平级之后：手指落在胶囊/导航格上时，命中测试停在它们自己身上，本层不进命中路径
            // （真机实测：慢拖、横滑、整格点按都正常）；只有落在真正的空白处才由本层接住
            // （真机实测：点 dock 左侧空白，内容区 0 像素变化 = 没穿透）。见 [swallowPointerInput]。
            Box(Modifier.matchParentSize().swallowPointerInput())

            Column(Modifier.fillMaxWidth()) {
                // 迷你播放条：点击进播放页；上滑 1:1 拉出播放页；左右滑切歌。
                PlayerBar(
                    state = state,
                    playerState = playerState,
                    positionState = positionState,
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )

                // 分隔线 + 底部导航行：展开壳看列表时整体收起（保留迷你播放条）。
                //
                // 2026-10-05 用户：「dock 中间那一行删掉」—— 原先把列表详情页的操作行
                // （收藏 / 播放全部 / 评论三枚胶囊）插在迷你条与导航行之间，头部按钮滚出视口
                // 时显示。删掉后 dock 永远只有「迷你条 + 导航行」两段，任何页面都不再多长一行；
                // 代价是滚离头部后不再有「随时播放全部」的入口（头部那三枚胶囊里有，
                // 但会随头部滚走），评论页也只剩播放页那个入口。
                AnimatedVisibility(
                    visible = navVisible,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
                    ) + fadeIn(animationSpec = tween(Motion.MicroMs, easing = Motion.Standard)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
                    ) + fadeOut(animationSpec = tween(Motion.MicroMs, easing = Motion.Standard)),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // 分隔线 = 播放条与导航之间的分隔线（内缩与胶囊对齐）。
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        NavRow(
                            selected = selected,
                            onSelect = onSelectTab,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 手势条 inset 始终占位：导航收起时也保证播放条不压到系统导航区。
                Box(Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }

        // ---- 全屏播放面（最上层）：open 才组合；p=0 整块沉在屏下（不可见/不可点）----
        // 组合与否由锚点状态驱动：open=true 组合、收起动画跑完（尾帧落地）才卸载。
        if (state.open) {
            PlayerPage(
                state = state,
                player = player,
                fullHeightPx = fullHeightPx,
                dockHeightPx = geometryDockHeightPx,
                dockShiftPx = dockShiftPx,
                onPlaceholderAction = onPlaceholderAction,
                onComments = onComments,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 迷你播放条：视觉本体 + 三套手势。**视觉本体另有一份**（[PlayerBarContent]，播放页低进度时
 *  共用），本函数只负责把手势挂上去。
 *
 * 三套手势各自独立 detector，全挂在同一个节点的同一条 modifier chain 上：
 * - `anchoredDraggable` → 竖向 1:1 拉起播放面，松手按位置/甩速落档（[SheetFlingBehavior]）；
 * - 本文件那颗 `pointerInput` → 横向切歌；
 * - `clickable` → 点击展开（拖动已被消费时不会触发）。
 *
 * **同一节点链上的多个 detector 是"谁先跨过自己的 slop 谁消费当帧"**，被消费的那一帧会让
 * 另一方 `awaitPointerSlopOrCancellation` 直接 `return null` —— 也就是说胜负一旦靠先后就
 * 变成看运气，所以横滑那颗用**方向判据**把胜负定死（横向必须明确压过纵向才接管）。
 *
 * ⚠️ 还有一条链**外侧**的约束：胶囊外那圈留白（`padding(10dp, 6dp)`）不在手势区内 ——
 * 所有 pointer 修饰符都在 padding 之内。那一圈与导航行格间空白由 dock 的拦截层负责接住
 * （见 [com.thripleq.nume.ui.components.swallowPointerInput] 的「不要当祖先」）。
 */
@Composable
internal fun PlayerBar(
    state: PlayerDockState,
    playerState: PlayerUiState,
    positionState: State<Long>,
    player: Player,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val capsule = NumeShape.Capsule

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, capsule, clip = false)
            .clip(capsule)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // 上报迷你条胶囊的窗口坐标：胶囊展开动画的起点（从胶囊原位长成卡片/全屏）。
            // 必须在 padding 之前测，bounds 才是视觉胶囊本身。
            .onGloballyPositioned { coords ->
                @Suppress("DEPRECATION") // 遗留上报：保留 capsule-origin 起点数据，当前无读取方。
                state.capsuleRect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            }
            // 官方 anchoredDraggable：竖向把播放面拉起来（内部处理 slop 仲裁 / 松手吸附 / 甩动）。
            // reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开；下滑 → 收起。
            // 迷你条在 dock 里、dock 被播放面盖住时（全屏）不可点，天然不冲突。
            // 点按展开期间禁用：那段时间 progress 读的是点按时间轴（entry），此时若手动拖动改了
            // offset，两个数据源会打架（手指在动、画面不动）。420ms 后自动交回。
            .anchoredDraggable(
                state.sheetState,
                flingBehavior = state.flingBehavior,
                enabled = !state.tapExpand,
                reverseDirection = true,
                orientation = Orientation.Vertical,
            )
            // 点击 → 展开播放页。两条路，由 PlayerDockState.openByTap 自己分派：
            // 壳还收在迷你条原位 → 点按专用时间轴（跳过分裂那一拍，内容全程参与运镜）；
            // 壳已在卡片档再点 → 走 open(toFull=true)，spring 从当前 offset 直达全屏。
            // 若拖动被 anchoredDraggable 消费，点击不会触发。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { state.openByTap() }
            // 横滑切歌：独立 detector；与竖向 anchoredDraggable 方向正交。
            //
            // 【别再改写它 —— 2026-10-05 真机实测的结论】
            // 我一度把它换成手写的"方向占优仲裁"（理由：怀疑拇指上滑带横向漂移时，横向先过
            // slop 会把竖拖掐死）。**那是错的，而且是有害的**：换成手写版之后，「带漂移的弧线
            // 慢拉」就再也拉不出卡片了（真机实测：竖拖压根没启动、offset 恒 0），而同一操作
            // 在内置版上是能拉出来的。三条证据：
            //  1. 源码：`awaitPointerSlopOrCancellation` / `TouchSlopDetector.getPostSlopOffset`
            //     只按**主轴**判 slop（`finalChange.mainAxis()`），**交叉轴再大也不取消**；
            //  2. 源码：内置 `detectHorizontalDragGestures` 的 KDoc 明说它与竖向 detector
            //     "coordinate … only vertical or horizontal dragging is locked, but not both"，
            //     协调由框架负责（横纵互斥锁定），不是"谁先到 slop 谁赢"的抢；
            //  3. 真机：旧包（内置版）上直线上拉与带漂移弧线上拉**都能**拉出卡片。
            // 手写版的问题：一旦 `dx > slop && dx > dy` 就把整条流判成横滑并 consume，
            // 竖拖随即被取消 —— 恰好制造出用户描述的那个症状。
            // 真机复现手法：`input motionevent DOWN/MOVE.../UP` 打一条前几帧横向为主的弧线。
            .pointerInput(player) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
                            // 左滑 → 下一首；右滑 → 上一首。
                            accumulated <= -swipeThresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                PlayerHolder.skipNext(player)
                            }
                            accumulated >= swipeThresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                PlayerHolder.skipPrevious(player)
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            },
    ) {
        PlayerBarContent(playerState, positionState, player)
    }
}

/** 迷你条视觉本体（无手势）：真实迷你条与播放页壳低进度时共用的同一份布局，
 *  保证「点击迷你条 → 壳展开」第一帧与迷你条原内容无缝衔接。
 *  真实迷你条 [PlayerBar] = 手势 + 本内容；壳内副本 = 本内容（alpha 随进度淡出）。 */
@Composable
internal fun PlayerBarContent(
    playerState: PlayerUiState,
    positionState: State<Long>,
    player: Player,
) {
    val context = LocalContext.current
    // 迷你条一拿到封面就按播放页的固定解码尺寸预取，展开播放页时命中内存缓存，
    // 避免冷加载时封面先闪 shimmer 占位再出现（需与 [CoverArt] 同一 data+size 才命中）。
    val prefetchPx = playerCoverDecodePx()
    LaunchedEffect(playerState.coverUrl) {
        playerState.coverUrl?.let {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context).data(it).size(prefetchPx).build(),
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(NumeShape.CardSmall),
        ) {
            val uri = playerState.coverUrl
            if (uri == null) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
            } else {
                val model = remember(uri) {
                    ImageRequest.Builder(context)
                        .data(Uri.parse(uri))
                        .size(120)
                        .build()
                }
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = playerState.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playerState.title.ifEmpty { "暂无播放" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (playerState.hasTrack) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = playerState.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (playerState.hasTrack) {
            SpectrumPlaceholder()
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = { PlayerHolder.togglePlay(player) }) {
            Icon(
                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // 进度条：唯一读 positionState 的组合，250ms 轮询只让它重组。
    if (playerState.hasTrack) {
        MiniProgressBar(
            state = playerState,
            positionState = positionState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
}

/** 迷你进度条：从 [positionState] 读值，独立重组，不带动播放条/岛。 */
@Composable
internal fun MiniProgressBar(
    state: PlayerUiState,
    positionState: State<Long>,
    modifier: Modifier = Modifier,
) {
    val positionMs = positionState.value
    val fraction =
        if (state.durationMs > 0) (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier.height(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Reserved slot for the future live spectrum; static bars for now. */
@Composable
internal fun SpectrumPlaceholder() {
    Box(
        modifier = Modifier.size(width = 36.dp, height = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(8.dp, 16.dp, 10.dp).forEach { h ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height(h)
                        .clip(NumeShape.Track)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** 导航胶囊的**视觉**高度（未选中时只是一圈描边图标位）。 */
private val NavPillHeight = 40.dp

/**
 * 导航胶囊的**触摸槽**高度（48dp = Material 最小可点尺寸）。
 *
 * 胶囊视觉仍是 [NavPillHeight]，触摸槽比它高 8dp、并且**撑满整格宽**，于是点在胶囊
 * 四周的空白上也命中（见 [DockNavPill]）。
 *
 * 导航行把上下内缩收到 8dp 抵消这 8dp 增量，**总高不变**：8×2 + 48 = 64dp
 * （与 `navRowReservePx` 的 65dp 口径一致）—— dock 几何与播放页的手势行程都不受影响。
 */
private val NavSlotHeight = 48.dp

/**
 * M3 **Expressive** 导航项：选中 = 一颗品牌色实底胶囊，**图标 + 文字一起装进胶囊**
 * （不是官方经典导航栏那种"图标套小胶囊、文字在下方"）；未选 = 只留描边图标。
 * 参考作品：Rhythm（Material You 播放器）的浮动底栏。胶囊里带标签，才读得出"这是当前项、
 * 且是一颗按钮"。宽高用填充色 transition，切换时胶囊在格内淡入/展开。
 *
 * ## 触摸槽 vs 视觉胶囊（2026-10-05 用户：「按钮点击范围调大一些」）
 *
 * 视觉上那颗胶囊只有图标+文字的宽度（~64dp），而它在导航行里占的格是 **1/3 屏宽**。
 * 原来点击区就是胶囊本身 —— 点在旁边那圈明显的空白上没有任何反应。现在把**整格**做成
 * 触摸目标（[NavSlotHeight] 高 × 整格宽），胶囊仍然居中、尺寸不变：
 * - 触摸面撑满整格后 ripple 会铺满一整格，反馈"太重"且不再对应那颗胶囊，故改用
 *   **按压回弹**（按到 0.94、spring 弹回）—— 反馈落在胶囊上，命中区是整格。
 * - 槽高取 [NavSlotHeight]（48dp，Material 最小触摸尺寸）：配合导航行把上下内缩从
 *   12dp 收到 8dp，导航行总高仍是 64dp，dock 几何/手势行程不受影响。
 */
@Composable
private fun DockNavPill(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = NumeShape.Pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockPillPress",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "navContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "navContentColor",
    )
    // 触摸槽（整格）：`fillMaxWidth` 让命中区吃掉整格宽（调用方每格是 weight(1f)，
    // 约束是确定值），`height(NavSlotHeight)` 给足竖向尺寸；胶囊在槽里居中、尺寸不变。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavSlotHeight)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 视觉胶囊：[modifier] 挂在它身上 —— 调用方（导航项）的 modifier 若带测量，
        // 量到的必须是**胶囊**的矩形，不能变成整格的（一格有 1/3 屏宽）。
        Row(
            modifier = modifier
                .height(NavPillHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(pill)
                .background(containerColor, pill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                AnimatedVisibility(visible = selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

/** The tab row: three equal slots, each a [DockNavPill]（仅选中项展开出文字）。
 *  上下内缩 8dp 是为容纳 [NavSlotHeight] 的触摸槽（48 + 8×2 = 64dp，与原来总高一致）。 */
@Composable
internal fun NavRow(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DockNavPill(
                    selected = isSelected,
                    icon = if (isSelected) tab.iconSelected else tab.icon,
                    contentDescription = tab.label,
                    label = tab.label,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(tab)
                    },
                )
            }
        }
    }
}
