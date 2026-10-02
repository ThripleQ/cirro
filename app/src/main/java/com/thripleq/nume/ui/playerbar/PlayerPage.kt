package com.thripleq.nume.ui.playerbar

import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
        // 卡片→全屏的插值在 [HALF_ANCHOR_P, 2] 段内进行：卡片档先稳定停留到 1.66，
        // 再继续拉才贴满屏（此前 p>1 就开始盖满，卡片档形同虚设、高度只有半屏）。
        val t1 = ((p - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
        val extT = (t0 / SPLIT).coerceIn(0f, 1f)
        val splitT = ((t0 - SPLIT) / (1f - SPLIT)).coerceIn(0f, 1f)
        val squeezeT = splitSqueezeT(splitT)
        val breakT = splitBreakT(splitT)
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
        val bottom = if (t0 < SPLIT) {
            // 扩展段：底边从背后包住 dock 当前圆角（圆角收平到 0 时恰为 dockTop）。
            dockTopPx + dockCornerCur
        } else {
            // 分裂段：挤腰时底边钉在 dockTop（两侧圆角涨大内收成腰、位置不动）；
            // 断开后缝从 0 连续打开（→ dockTop-gapPx）。
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

        return lerpRect(bubbleOrCard, full, t1)
    }

    val p = state.progress
    val rect = shellRect(p)
    val t0 = p.coerceIn(0f, 1f)
    val t1 = ((p - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
    val splitT = ((t0 - SPLIT) / (1f - SPLIT)).coerceIn(0f, 1f)
    // 圆角统一（修复 18/26/19.5 混用）：壳四角与 dock 同族、全部落在 26dp——
    // 顶角全程 26dp（与 dock 同形，不收平、不换尺寸）；
    // 底角：扩展段方角（与 dock 一体）；分裂段「挤腰」时涨到 36dp 让两侧内收成腰，
    // 「断开」后落回 26dp（与 dock 顶角同半径）——不是两块平板对切。
    val waistCornerPx = with(density) { WAIST_CORNER_DP.dp.toPx() }
    val squeezeT = splitSqueezeT(splitT)
    val breakT = splitBreakT(splitT)
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
    // 内容淡入：扩展段气泡长起来时内容浮现，分裂完成（p=1）已基本可见（≈60%），
    // 壳长到卡片档（p=HALF_ANCHOR_P）才全亮——避免「壳还矮、内容已全亮」的挤压感。
    val contentAlpha = (p / HALF_ANCHOR_P).coerceIn(0f, 1f)
    // 顶部拉手/收起：分裂成卡后才浮现。
    val headerAlpha = splitT
    // 胶囊→卡片段：内容固定为卡片档尺寸，由壳裁剪揭示（封面不随气泡长大）；
    // 过卡片锚点后才切换成逐帧连续过渡到全屏，避免半档处布局跳变。
    val pastCard = p > HALF_ANCHOR_P
    val cardRect = shellRect(HALF_ANCHOR_P)
    val contentProgress = if (pastCard) p else HALF_ANCHOR_P
    val contentRect = if (pastCard) rect else cardRect

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
                    shadowElevation = 4.dp.toPx() * breakT
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
                .anchoredDraggable(
                    state.sheetState,
                    flingBehavior = state.flingBehavior,
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

/** 播放页主体：单一布局随 sc（0=卡片档，1=全屏）连续形变，卡片→全屏无割裂。
 *  骨架全程一致：封面 → 标题 → 歌手 →（弹性空白）→ 进度 → 控制；
 *  全屏专属的动作行/分割线/功能胶囊行以「高度+透明度」随 sc 长出，播放键由裸图标
 *  长成 primaryContainer 圆。卡片档（sc=0）即原紧凑布局。 */
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
    val sc = ((contentProgress - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
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
    val titleFont = androidx.compose.ui.unit.lerp(21.sp, 28.sp, sc)
    val artistFont = androidx.compose.ui.unit.lerp(14.sp, 16.sp, sc)
    val titleColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        sc,
    )
    val playBox = androidx.compose.ui.unit.lerp(48.dp, 72.dp, sc)
    val playIcon = androidx.compose.ui.unit.lerp(44.dp, 36.dp, sc)
    val playTint = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onPrimaryContainer,
        sc,
    )
    val playContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = sc)

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

        // Track / metadata + 红心/评论：参照网易云 —— 歌名/歌手左对齐上下叠放，
        // 右侧红心+评论横向并排、与文字块垂直居中，贴近文字而非飘到最右。文字过长
        // 横向跑马而不截断；右侧图标随全屏进度 sc 渐显（卡片档宽度收为 0）。
        // 播放列表按钮按用户要求暂隐藏。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                FadingMarqueeText(
                    text = state.title.ifEmpty { "暂无播放" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = titleFont),
                    color = titleColor,
                )
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
            Spacer(Modifier.width(8.dp * sc))
            // 红心/评论：全屏专属，横向并排，随 sc 长出。
            Row(
                modifier = Modifier
                    .width(80.dp * sc)
                    .clipToBounds()
                    .graphicsLayer { alpha = sc },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPlaceholderAction, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Favorite, "收藏", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { onComments(commentRect) },
                    modifier = Modifier
                        .size(40.dp)
                        .onGloballyPositioned { commentRect = it.boundsInWindow() },
                ) {
                    Icon(Icons.Filled.Chat, "评论", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Spacer(Modifier.height(16.dp * sc))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp * sc)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NumeFade.PROGRESS_SLOT * sc)),
        )
        Spacer(Modifier.height(16.dp * sc))

        // Seek bar + time labels（卡片/全屏同序：先条后时间）
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

        // Transport controls：圆播放键由「裸图标」长成 primaryContainer 圆。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                androidx.compose.ui.unit.lerp(0.dp, 40.dp, sc),
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlayerHolder.skipPrevious(player) }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(sideBtnDim),
                )
            }
            Box(
                modifier = Modifier
                    .size(playBox)
                    .clip(CircleShape)
                    .background(playContainer),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { PlayerHolder.togglePlay(player) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = when {
                            state.isBuffering -> Icons.Filled.MoreHoriz
                            state.isPlaying -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = playTint,
                        modifier = Modifier.size(playIcon),
                    )
                }
            }
            IconButton(onClick = { PlayerHolder.skipNext(player) }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(sideBtnDim),
                )
            }
        }

        // 全屏专属：功能胶囊行，高度与透明度随 sc 长出。
        Spacer(Modifier.height(24.dp * sc))
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
