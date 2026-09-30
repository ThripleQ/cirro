package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeShape
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
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
import coil.request.ImageRequest
import com.thripleq.nume.Profile
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

/**
 * 常驻底部 dock + 全屏播放页（合体）。
 *
 * 一个组件、一份 [state]：收起时只露底部 dock（拉手+迷你播放条+操作行+导航），
 * 迷你条**上滑 1:1** 跟手把全屏播放面从底部拉出盖满屏；点击直接整页弹出。
 * 全屏面只在 draw（graphicsLayer）读 progress，绝不因动画数值重组/挂载（上次翻车的坑）。
 */
@Composable
fun PlayerDock(
    player: Player,
    state: PlayerDockState,
    selected: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    /** 列表详情页操作行是否顶替迷你条上方的空间。 */
    actionVisible: Boolean = false,
    /** 是否显示底部导航行：展开壳看列表时收起，只保留迷你播放条。 */
    navVisible: Boolean = true,
    onPlayAll: () -> Unit = {},
    onPlaceholderAction: () -> Unit = {},
    /** 评论按钮：打开当前曲目的评论页（携带按钮窗口矩形作浮现起点）。 */
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
    val actionHeight = 57.dp

    // 全屏播放面需盖满整个窗口（含状态栏/导航栏）：用根布局实测高度（edge-to-edge 下
    // 才是真正的物理屏高），screenHeightDp 不含系统栏，会短一截、底部露背景。
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }
    val fullHeightPx = if (measuredHeightPx > 0f) {
        measuredHeightPx
    } else {
        with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }

    // 总高上报：实际测量 dock 高度（含底部手势条 inset）。
    // 导航/操作行是 AnimatedVisibility：其 200ms 收放会让 dockHeightPx **每帧变化**，若每帧
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
    }

    // 组合与否由锚点状态驱动：迷你条一拖动（offset>0）就组合播放面；
    // 完全落回 dock 锚点（settled）才卸载。替代手搓的 beginDrag。
    LaunchedEffect(state.sheetState) {
        snapshotFlow { state.sheetState.offset }.collect { offset ->
            if (offset > 1f) state.open = true
        }
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
        Column(
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

            // 列表详情页操作行（滚动把头部按钮顶出视口时显示）。
            // 统一走 [Motion.MicroMs]：原先这里 180ms、下方导航行 enter 200ms，
            // 两者是**同一事件驱动的成对切换**（操作行进、导航行退），时长不同 ⇒ 换行时
            // 有 20ms 相位差，看着就是「不齐」。弹性 spec 也不用——显隐要利落。
            AnimatedVisibility(
                visible = actionVisible,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
                ) + fadeIn(animationSpec = tween(Motion.MicroMs, easing = Motion.Standard)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
                ) + fadeOut(animationSpec = tween(Motion.MicroMs, easing = Motion.Standard)),
            ) {
                Column(Modifier.fillMaxWidth().height(actionHeight)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    ActionNavRow(
                        onPlayAll = onPlayAll,
                        onPlaceholderAction = onPlaceholderAction,
                        onComments = onComments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            // 分隔线 + 底部导航行：展开壳看列表时整体收起（保留迷你播放条）。
            // 与上方操作行**完全同 spec**（同 [Motion.MicroMs] + 同曲线）：二者是同一次
            // 滚动触发的成对换行，只有同相位才不会各走各的。
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    )
                }
            }

            // 手势条 inset 始终占位：导航收起时也保证播放条不压到系统导航区。
            Box(Modifier.fillMaxWidth().navigationBarsPadding())
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

/** Mini player bar: cover + metadata + tap/vertical-drag/horizontal-swipe.
 *
 * 手势仲裁（单 pointerInput，手动 awaitEachGesture）：
 * - 未过 touch slop 就抬手 → 点击（开全屏播放页）
 * - 竖向为主 → 1:1 拉起播放页，松手按进度/甩速吸附
 * - 横向为主 → 不消费，交给横滑切歌
 * - 子节点已消费（播放/暂停按钮）→ 立即退出，不当作点击
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
            .anchoredDraggable(
                state.sheetState,
                reverseDirection = true,
                orientation = Orientation.Vertical,
            )
            // 点击 → 胶囊原位展开到全屏（经过卡片矩形，一气呵成）。
            // 若拖动被 anchoredDraggable 消费，点击不会触发。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { state.open(toFull = true) }
            // 横滑切歌：独立 detector；与竖向 anchoredDraggable 方向正交，互不干扰。
            .pointerInput(player) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
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

/** The tab row: evenly split tabs, selected one on a theme-color pill.
 * M3 Expressive 导航栏：选中 = secondaryContainer pill + onSecondaryContainer 图标,
 * 未选 = onSurfaceVariant 灰图标, 图标+标签竖排。 */
@Composable
internal fun NavRow(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = NumeShape.Pill
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(tab)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 列表操作行：与 [NavRow] 同构——均分岛宽、胶囊圆角弧与岛平行、图标居中。
 *  播放 = secondaryContainer 胶囊（对应导航"选中"pill）；收藏 / 评论 = 透明（对应"未选中"）。 */
@Composable
internal fun ActionNavRow(
    onPlayAll: () -> Unit,
    onPlaceholderAction: () -> Unit,
    onComments: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = NumeShape.Pill
    val haptics = LocalHapticFeedback.current
    var commentRect by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(Color.Transparent)
                .clickable { onPlaceholderAction() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "收藏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayAll()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(pill)
                .background(Color.Transparent)
                .onGloballyPositioned { commentRect = it.boundsInWindow() }
                .clickable { onComments(commentRect) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = "评论",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
