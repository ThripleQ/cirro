@file:OptIn(UnstableApi::class)

package com.thripleq.cirro.ui.playerbar

/**
 * 播放页**内容区**：封面 → 标题 → 歌手 → 进度 → 控制这一整列，以及全屏时才长出来的
 * 动作行 / 功能胶囊行。
 *
 * 从 `PlayerPage.kt` 分出来的边界是「谁和谁一起改」：那边是**整页的壳**（圆角、投影、
 * 展开手势、系统栏），改转场与逐帧几何动那边；这边是**壳里面排什么**，增删按钮、改
 * 进度行或歌词入口动这边。
 *
 * ⚠️ 这里是全项目**唯一逐帧重组**的组合（组合期读展开进度 `sc`，排版量全是 `sc` 的连续
 * 函数）⇒ 往里加东西要按「每帧都会重跑」估价，别照抄普通页面的写法。圆角与投影已在
 * `PlayerPage` 那层量化成台阶（`Motion.quantPx`），**别在这里再引入逐帧连续的 layer 属性**
 * （每帧重建 clip outline + 重算阴影，正是此前掉帧的真因）。
 */
import androidx.media3.common.util.UnstableApi
import android.provider.Settings
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
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
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.ui.components.CirroPayBadge
import com.thripleq.cirro.ui.components.CirroTrialBadge
import com.thripleq.cirro.ui.components.FadingMarqueeText
import com.thripleq.cirro.ui.components.PayTag
import com.thripleq.cirro.ui.components.quantizedFontSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

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
    onComments: (Rect) -> Unit,
    /** 当前曲目是否已红心。状态与动作由常驻的 [PlayerDock] 持有，这里只消费。 */
    liked: Boolean,
    onToggleLike: () -> Unit,
    /** 歌词页是否在场：上报给壳，壳据此关掉自己的「下滑收起」竖向手势。 */
    onLyricsEngaged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var queueOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // 歌词三级状态（用户 2026-10-24 的方案）：封面 → 内联歌词 → 沉浸歌词。
    //   Cover      原版式：封面 + 标题 + 底部控制。点封面（仅全屏档、有曲目）进 Inline。
    //   Inline     标题+歌手常驻顶部（用户 2026-10-24：「标题和艺人名字放高点」），歌词紧接
    //              其下（「歌词区域也可以低一些」）；底部整簇**下移**（[lyricsLayout]）。
    //              无操作 [IdleImmersiveMs]、或**向上翻动歌词**（手指上推、列表往后面的行滚）
    //              累计 [ImmersivePullDp] → Immersive。这一级列表照常滚动，换档只是旁路观察、
    //              不拦手势。点歌词区任意处（这一级还没有功能区域，整块都算「无关地方」）→ Cover。
    //   Immersive  隐藏进度条/三键/功能胶囊；标题+歌手**原地不动**（两档同一位置、同一组件），
    //              歌词视口只向下变高 —— 正在看的行不会被顶走，行距/字号/留白也完全一致。
    //              点击任意处 / 返回键 / 列表滚到底后继续上滑 → 回 Inline。
    var lyricsStage by remember { mutableStateOf(LyricsStage.Cover) }
    var commentRect by remember { mutableStateOf(Rect.Zero) }
    val state = rememberPlayerState(player)
    // 点击 lambda 全部 remember 固定 —— 本组合随展开进度逐帧重组，每次新建会让 clickable
    // 节点每帧重建（Modifier 链 + 手势节点）。lambda 里**不直接捕获 `state`**（它每次重组都是
    // 新实例，remember 住的 lambda 拿到的会是第一次组合时的旧值），要读的最新值一律经
    // [rememberUpdatedState] / [scState] 这类稳定 State 中转，调用时才解包。
    val currentTrackId = rememberUpdatedState(state.trackId)
    val scState = remember { mutableFloatStateOf(0f) }
    // 封面 → 内联歌词：有曲目、且壳已基本到全屏才进（用户：「播放卡片不添加歌词页面」——
    // 卡片档点封面不再切歌词，先把壳拉满）。
    val onCoverClick = remember {
        {
            if (currentTrackId.value != null && scState.floatValue > 0.9f) {
                lyricsStage = LyricsStage.Inline
            }
        }
    }
    // 点歌词区域 → 回封面（用户原话）。歌词行**没有**自己的点击/seek：那属于「以后可能
    // 会做」的功能区域，现在整块歌词区都算「无关地方」，点了就退。沉浸档先退回内联，
    // 内联档回封面（用户没规定沉浸档的返回，取逐级退场）。
    val onLyricsAreaClick = remember {
        {
            lyricsStage = when (lyricsStage) {
                LyricsStage.Immersive -> LyricsStage.Inline
                else -> LyricsStage.Cover
            }
        }
    }
    // 返回键逐级退场：沉浸 → 内联 → 封面 → （交还给 [PlayerPage] 的收起）。本组件在
    // PlayerPage 之后组合，enabled 时优先级压过那边的 `state.close()`。
    BackHandler(enabled = lyricsStage != LyricsStage.Cover) {
        lyricsStage = if (lyricsStage == LyricsStage.Immersive) LyricsStage.Inline else LyricsStage.Cover
    }
    // 歌词页在场 → 壳的竖向拖拽让位（否则下拉歌词会先收起整页）。放 LaunchedEffect：
    // 组合期写上层状态会触发「重组中改状态」的告警。
    LaunchedEffect(lyricsStage) {
        onLyricsEngaged(lyricsStage != LyricsStage.Cover)
    }
    // 红心（[liked] / [onToggleLike]）由 [PlayerDock] 透传：dock 常驻，迷你条与全屏页共用
    // 同一份状态，失败提示也只收一次（见 PlayerDock 里那段注释）。
    // 歌词：惰性加载 —— 只有进入歌词态才按当前曲目请求（切换曲目自动重载）。
    val lyricsVm: LyricsViewModel = hiltViewModel()
    val lyricsState by lyricsVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(lyricsStage, state.trackId) {
        if (lyricsStage != LyricsStage.Cover) lyricsVm.load(state.trackId)
    }
    // 拖动进度条期间冻结轮询（否则播放器回报的位置会和手指打架）。这个标志跨两个组件用
    //（写入发生在 [PlayerProgressRow] 的拖动回调里、读取发生在下面的订阅里），所以单列一个
    // State 传下去，而不是留在本作用域 —— 它一变就重组内容区的话，拖动每一下都要赔上整页。
    val seekFrozen = remember { mutableStateOf(false) }
    // 进度是高频状态：**在这里只创建 State，绝不 `by` 解包**。
    //
    // `positionMs` 每 250ms 变一次（[rememberPlayerPosition] 的轮询）。在本函数体解包的话，
    // 这个变化会落在本组件的重组作用域里 —— 而 Compose 的作用域是**函数级**的：封面、跑马
    // 标题、控制行、功能胶囊全都会跟着每 250ms 重跑一遍（不是「只有进度条重组」）。
    // 解包点必须下沉到真正消费它的那几行：进度行（[PlayerProgressRow]）与歌词面板。
    // 这与 PlayerDock 把 positionState 一路传到迷你进度条内部才取 `.value` 是同一套做法。
    val positionState = rememberPlayerPosition(player) { seekFrozen.value }

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
    //
    // 量化到 0.25sp：字号是**文本重排的输入**，它一变跑马组件就要重新 measure —— 那是本页
    // 最贵的一项，一次展开能跑出几十上百次。量化后只在跨档时重排；3x 屏上 0.25sp 合
    // 0.75px 字号差，肉眼看不出来。
    val titleFont = androidx.compose.ui.unit.lerp(21.sp, 24.sp, sc).quantizedFontSize()
    val artistFont = androidx.compose.ui.unit.lerp(14.sp, 16.sp, sc).quantizedFontSize()
    // 文本样式跟着量化后的字号走：只在跨档时重建，档内直接复用同一份 TextStyle
    //（跑马组件以 style 为 remember key，对象换了就要重排）。
    val typography = MaterialTheme.typography
    val titleStyle = remember(typography, titleFont) {
        typography.headlineSmall.copy(fontSize = titleFont)
    }
    val artistStyle = remember(typography, artistFont) {
        typography.bodyMedium.copy(fontSize = artistFont)
    }
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

    // ── 歌词三级状态的手势与转场 ──────────────────────────────────────────────
    SideEffect { scState.floatValue = sc }
    // 「歌词档」版式因子（0=封面档，1=歌词档）：用户要求进歌词时「进度条、各种按钮下移，
    // 给歌词腾空间」。底部整簇是靠列底排的，能下移的量只能从簇内间距和页面底 padding 里
    // 挤：底 padding 20→6、进度↔三键 18→10、三键↔胶囊 57→30，合计进度条下移约 49dp、
    // 三键约 35dp、胶囊约 14dp，全部变成歌词的可用高度。退出歌词档时原样退回。
    val lyricsLayout by animateFloatAsState(
        targetValue = if (lyricsStage != LyricsStage.Cover) 1f else 0f,
        animationSpec = tween(ImmersiveMs, easing = Motion.Standard),
        label = "lyricsLayout",
    )
    val bottomCtlGap = androidx.compose.ui.unit.lerp(ctrlGap, 10.dp, lyricsLayout)
    val capsuleGap = TransportBottomGap * sc * (1f - 0.47f * lyricsLayout)
    // 无操作自动进沉浸：只在内联档且歌词已就绪时计时（Loading/Empty 没有可读的内容，
    // 自动切到全屏空转没有意义）。任何指针事件（下面根 Column 的 Initial 观察者）都重置计时。
    val lastInteraction = remember { LongArray(1) }
    LaunchedEffect(lyricsStage, lyricsState) {
        if (lyricsStage != LyricsStage.Inline || lyricsState !is LyricsUiState.Ready) return@LaunchedEffect
        lastInteraction[0] = System.currentTimeMillis()
        while (isActive) {
            delay(IdleCheckMs)
            if (System.currentTimeMillis() - lastInteraction[0] >= IdleImmersiveMs) {
                lyricsStage = LyricsStage.Immersive
                break
            }
        }
    }
    // 内联 ⇄ 沉浸的换档手势，全部走嵌套滚动**旁路观察**（不消费任何位移）：
    //  - 内联档「向上翻动歌词」（用户 2026-10-24：「向下翻动歌词进入沉浸」——指往歌曲后面
    //    翻，手指上推、列表向上滚）累计 [ImmersivePullDp] → 沉浸。判定放在 onPreScroll，
    //    列表自己照常吃完这段滚动，所以歌词跟手、不会出现「换档期间滑动被禁用」；
    //    2026-10-24 修正：此前用独立 pointerInput 判「手指下滑」，方向反了，且为了独占
    //    手势把内联列表设成不可滚 —— 两个问题一起改掉。
    //  - 沉浸档：列表滚到底后多余的「上滑」冒到 onPostScroll，攒满即退回内联。
    //  - 刚进入沉浸的那一手不判退出（列表可能正好停在底部，否则会立刻弹回）。
    val lyricsScroll = remember(density) {
        object : NestedScrollConnection {
            private var acc = 0f
            private var justEntered = false
            private var justExited = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (lyricsStage != LyricsStage.Inline) return Offset.Zero
                // 刚在底部退出沉浸的那一手不再判进入：否则同一根手指继续上推会立刻又弹回
                // 沉浸，底部来回打摆（只是「不要再进」，列表照样滚）。
                if (justExited) return Offset.Zero
                val threshold = with(density) { ImmersivePullDp.toPx() }
                if (available.y < 0f) {
                    if (acc > 0f) acc = 0f
                    acc += available.y
                    if (acc <= -threshold) {
                        acc = 0f
                        justEntered = true
                        lyricsStage = LyricsStage.Immersive
                    }
                } else if (available.y > 0f) {
                    acc = 0f
                }
                return Offset.Zero
            }
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (lyricsStage != LyricsStage.Immersive || justEntered) return Offset.Zero
                val threshold = with(density) { ImmersivePullDp.toPx() }
                if (available.y < 0f) {
                    if (acc > 0f) acc = 0f
                    acc += available.y
                    if (acc <= -threshold) {
                        acc = 0f
                        justExited = true
                        lyricsStage = LyricsStage.Inline
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                acc = 0f
                justEntered = false
                justExited = false
                return Velocity.Zero
            }
        }
    }
    // 壳回落时逐级降级：跌破全屏（sc<0.9）先退沉浸（沉浸版式是全屏专属的），跌回卡片
    // 档（sc<0.5）连歌词一起收掉 —— 与进入侧「只有全屏档才进歌词」对称。
    LaunchedEffect(Unit) {
        snapshotFlow { scState.floatValue }
            .distinctUntilChanged()
            .collect { s ->
                if (s < 0.9f && lyricsStage == LyricsStage.Immersive) {
                    lyricsStage = LyricsStage.Inline
                } else if (s < 0.5f && lyricsStage != LyricsStage.Cover) {
                    lyricsStage = LyricsStage.Cover
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 无操作计时的观察者：Initial pass 只偷看不拦截，任何落在播放页上的触摸
            // （含歌词滚动、点按钮）都算「有操作」。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction[0] = System.currentTimeMillis()
                    }
                }
            }
            .padding(horizontal = androidx.compose.ui.unit.lerp(24.dp, 16.dp, sc))
            // 封面到卡片上边的距离取左右边距的 5:4（30dp vs 24dp），略长一点更透气。
            // 歌词档把顶 padding 收到 10dp（标题/艺人要「放高点」，见 [lyricsLayout]）；
            // 底部额外让出导航栏（仅三键导航、且随全屏进度渐入）。歌词档把底 padding
            // 从 20 收到 6dp（连同下面两处间距一起下移）。
            .padding(
                top = 30.dp - 20.dp * lyricsLayout,
                bottom = 20.dp - 14.dp * lyricsLayout + navBottomDp * sc,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 全屏时顶部让出状态栏（卡片档壳顶已在状态栏下，无需让位）。
        Spacer(Modifier.height(androidx.compose.ui.unit.lerp(0.dp, statusBarDp + 16.dp, sc)))

        // 封面档 ↔ 歌词档：同一块弹性槽位内的**交叉淡化**（AnimatedContent）。两套版式
        // 高度结构完全不同（封面是固定方块+标题，歌词是撑满的列表），做不到共享元素级的
        // 连续形变；淡化交叠 + 同槽位零位移是最不抢戏的接法 —— 内联↔沉浸那两级另有
        // 各自的 expand/shrink 转场，这里只管「封面」与「歌词」这一层切换。
        AnimatedContent(
            targetState = lyricsStage == LyricsStage.Cover,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                fadeIn(tween(ImmersiveMs, easing = Motion.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(ImmersiveExitMs, easing = Motion.Emphasized))
            },
            label = "coverOrLyrics",
        ) { isCover ->
            if (isCover) {
                // ── 封面档 ────────────────────────────────────────────────────
                // 点封面进内联歌词（门槛见 [onCoverClick]）。封面尺寸/圆角不变，下面依次是
                // 标题块 → 弹性空白 → 底部控制，与加歌词功能之前的版式完全一致。
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(coverDim)
                            .clip(RoundedCornerShape(coverCorner))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onCoverClick() },
                    ) {
                        CoverArt(
                            state = state,
                            dim = coverDim,
                            corner = coverCorner,
                            iconSize = androidx.compose.ui.unit.lerp(88.dp, 96.dp, sc),
                        )
                    }

                    Spacer(Modifier.height(titleGap))

                    PlayerHeading(
                        state = state,
                        sc = sc,
                        titleStyle = titleStyle,
                        titleColor = titleColor,
                        artistStyle = artistStyle,
                        liked = liked,
                        onToggleLike = onToggleLike,
                        onComments = onComments,
                        commentRect = commentRect,
                        onCommentBounds = { commentRect = it },
                    )
                }
            } else {
                // ── 歌词档（内联 / 沉浸共用**同一套版式**）────────────────────────
                // 2026-10-24 用户：「标题和艺人名字放高点，歌词区域也可以低一些……进入沉浸
                // 时已显示的歌词不被顶走，行间距也不变」—— 原先内联档标题在歌词之下、
                // 沉浸档页眉在顶部展开，换档时歌词会被顶下去一段。现在标题/歌手**常驻顶部**
                // （两档同一位置、同一块组件），歌词紧接其下；换档只收放底部控制簇，
                // 歌词视口在顶部固定、只向下变高，正在看的那一行不会动。
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlayerHeading(
                        state = state,
                        sc = sc,
                        titleStyle = titleStyle,
                        titleColor = titleColor,
                        artistStyle = artistStyle,
                        liked = liked,
                        onToggleLike = onToggleLike,
                        onComments = onComments,
                        commentRect = commentRect,
                        onCommentBounds = { commentRect = it },
                        // 沉浸档：红心/评论淡出但仍占位（见 [PlayerHeading] 的 actionsVisible）。
                        actionsVisible = lyricsStage != LyricsStage.Immersive,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // 整块歌词区都是「点无关地方回封面」（用户原话）：歌词行不做
                            // 任何功能，点哪儿都一样，所以不需要区分行内/行间。无涟漪 ——
                            // 铺一层水波纹反而读成误触。
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onLyricsAreaClick() }
                            // 两级都交给列表自己滚；换档手势由 [lyricsScroll] 旁路观察，
                            // 不消费位移 —— 翻歌词始终跟手（2026-10-24 用户：「进入过程
                            // 很影响操作，滑动被暂时禁用了」）。
                            .nestedScroll(lyricsScroll),
                    ) {
                        LyricsView(
                            uiState = lyricsState,
                            positionState = positionState,
                            playing = state.isPlaying,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // ── 底部整簇（进度 / 三键 / 功能胶囊 / 错误文案）──────────────────────────
        // 沉浸档整簇退场（高度收拢 + 淡出，向下收 —— 它们贴着列底，向下消失才有
        // 「沉出屏幕」的方向感）；回内联时从下方长回来。进度行在里面，随之卸载：
        // [positionState] 的 250ms 订阅在沉浸档也一并停掉。
        AnimatedVisibility(
            visible = lyricsStage != LyricsStage.Immersive,
            modifier = Modifier.fillMaxWidth(),
            enter = fadeIn(tween(ImmersiveMs, easing = Motion.EmphasizedDecelerate)) +
                expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(ImmersiveMs, easing = Motion.EmphasizedDecelerate),
                ),
            exit = fadeOut(tween(ImmersiveExitMs, easing = Motion.Emphasized)) +
                shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(ImmersiveExitMs, easing = Motion.Emphasized),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 进度行：滑块 + 两侧时间整块挪进独立组件 —— 它是全页**唯一解包进度值**的地方，
                // 250ms 的轮询与拖动的逐帧写入因此只重组这一行，不再牵着内容区陪跑。
                // 位置不变：进度条之上不再画东西（原来那条 1dp 分割线已按用户要求删掉，
                // 省下的 33dp 原样挪到三键之下，见 [TransportBottomGap]）。
                PlayerProgressRow(
                    player = player,
                    positionState = positionState,
                    seekFrozen = seekFrozen,
                    durationMs = state.durationMs,
                )

                // 试听片段播完的提示（承接上面「片段末尾停住」的收尾）。
                //
                // 三件事都刻意的：
                // ① **只在全屏档长出来**（高与透明随 sc）—— 卡片档那套版式是逐像素调过的，多一行
                //    就整体错位；高度 lerp 到 0 则完全不参与布局。
                // ② 插在进度行**下方**：进度行上方是弹性空白（封面档的 weight Spacer / 歌词档
                //    的歌词区），会吸收这点新高度，所以底部三键与功能胶囊**不会**被顶下去。
                // ③ 文案用 tertiary（与「试听」标同色）而不是报错色：它不是错误，是权益边界。
                if (state.trialEnded) {
                    Spacer(Modifier.height(androidx.compose.ui.unit.lerp(0.dp, 8.dp, sc)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(androidx.compose.ui.unit.lerp(0.dp, 18.dp, sc))
                            .clipToBounds()
                            .graphicsLayer { alpha = sc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "试听已结束 · 开通 VIP 可听完整版",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(bottomCtlGap))

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
                // 歌词档再收掉约 47%（57→30dp），三键跟着下移，把高度让给歌词。
                Spacer(Modifier.height(capsuleGap))
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

        // 两张 sheet 留在 AnimatedVisibility 外面：它们是 modal 浮层，不该跟着底部簇
        // 一起收拢（真在沉浸档打开它们时，也应当原样盖满）。
        if (queueOpen) {
            PlayerQueueSheet(player = player, onDismiss = { queueOpen = false })
        }
        if (settingsOpen) {
            PlayerSettingsSheet(onDismiss = { settingsOpen = false })
        }
    }
}

/** 进度行：滑块 + 左右两个时间。**播放页里唯一解包 [positionState] 的地方**。
 *
 *  为什么单列一个组件：进度是 250ms 一次的轮询，拖动进度条时更是逐帧写入。这些读取若留在
 *  [PlayerPageContent] 的函数体里，按 Compose「重组作用域是函数级」的规则，整块内容区
 *  （封面、跑马标题、控制行、功能胶囊）都会跟着重跑一遍 —— 而它们与进度无关。收进这里之后，
 *  高频写入的重组范围就只剩这一行。
 *
 *  @param seekFrozen 「拖动中」标志：本组件写入、进度订阅方读取。拖动期间必须冻结那个
 *    250ms 轮询，否则播放器回报的真实位置会与手指的位置打架。
 */
@Composable
private fun PlayerProgressRow(
    player: Player,
    positionState: State<Long>,
    seekFrozen: MutableState<Boolean>,
    durationMs: Long,
) {
    var dragMs by remember { mutableLongStateOf(0L) }
    val seekPending = seekFrozen.value
    val positionMs = positionState.value
    // 拖动中途若曲目结束/切歌使 durationMs 归零，Slider 会被移除、onValueChangeFinished
    // 不再触发，seekPending 会永久卡住（进度轮询被冻结在拖动值）。这里兜底复位。
    LaunchedEffect(durationMs) {
        if (durationMs <= 0L) seekFrozen.value = false
    }
    if (durationMs > 0) {
        Slider(
            value = if (seekPending) dragMs.toFloat() else positionMs.toFloat(),
            onValueChange = { dragMs = it.toLong(); seekFrozen.value = true },
            onValueChangeFinished = {
                PlayerHolder.seekTo(player, dragMs)
                seekFrozen.value = false
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
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
                    .clip(CirroShape.Track)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = CirroFade.TRACK)),
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
            formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 歌词三级状态，见 [PlayerPageContent] 里 `lyricsStage` 处的完整说明。 */
private enum class LyricsStage { Cover, Inline, Immersive }

/** 内联歌词无操作多久后自动进沉浸（用户：「无操作一段时间」—— 取 5s：读完两三行歌词
 *  又不碰屏幕的量；太短会让想点行 seek 的人被抢走控制条）。 */
private const val IdleImmersiveMs = 5_000L

/** 无操作计时的轮询间隔：远小于 [IdleImmersiveMs]，触发误差可忽略。 */
private const val IdleCheckMs = 500L

/** 手势换档（向上翻歌词进沉浸 / 沉浸到底后继续上滑回内联）要攒够的位移量。 */
private val ImmersivePullDp = 72.dp

/** 沉浸进/出的转场时长：与壳的 [Motion.ShellCloseMs] 同量级 —— 沉浸切换是「同一页换形态」，
 *  比浮层显隐（[Motion.MicroMs]）重、比页面导航轻。退场减半：让位要利索（同 [Motion.NavExitMs] 取向）。 */
private const val ImmersiveMs = 360
private const val ImmersiveExitMs = 180

/**
 * 标题块：歌名（带右侧红心/评论）+ 歌手（带试听标）。封面档在封面之下，歌词档**常驻
 * 歌词之上**（内联/沉浸同一位置、同一组件，换档时歌词不被顶走）。
 *
 * 版式沿革（保留自原内联实现）：参照网易云 —— 歌名/歌手左对齐上下叠放；红心+评论与
 * **歌名那一行**同轴居中对齐（用户：「红心，评论和标题对齐，齐平」）；文字过长横向跑马；
 * 右侧图标随全屏进度 sc 渐显（卡片档宽高都收为 0，不撑胖紧凑版式）。
 *
 * @param actionsVisible 沉浸歌词档传 false：红心/评论淡出且不可点（原需求「沉浸页隐藏
 *   各种按钮，标题、艺人名保留」）。**只淡出不撤位** —— 图标行仍占着宽高，标题块高度
 *   不变，换档时歌词不会因为页眉变矮而上移。
 */
@Composable
private fun PlayerHeading(
    state: PlayerUiState,
    sc: Float,
    titleStyle: TextStyle,
    titleColor: Color,
    artistStyle: TextStyle,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onComments: (Rect) -> Unit,
    /** 评论按钮当前的窗口矩形（由 [onCommentBounds] 上报、外层 [PlayerPageContent] 持有），
     *  点评论时原样传给 [onComments] 作为浮层转场的源矩形。 */
    commentRect: Rect,
    onCommentBounds: (Rect) -> Unit,
    actionsVisible: Boolean = true,
) {
    val actionsAlpha by animateFloatAsState(
        targetValue = if (actionsVisible) 1f else 0f,
        animationSpec = tween(ImmersiveExitMs, easing = Motion.Standard),
        label = "headingActions",
    )
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
                style = titleStyle,
                color = titleColor,
                // 权重：图标行贴右端、歌名占剩下的宽度（跑马按容器宽判定）。
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp * sc))
            // 红心：**卡片档就常驻**（用户 2026-10-24 二次确认：「我要的是播放卡片的红心，
            // 不是迷你条」）。卡片档 sc=0，之前整行的宽度/高度/透明度都乘 sc，心和评论一起
            // 被收成了 0 —— 现在红心不乘 sc，只随它从 24dp 长到 28dp。
            // 评论仍是全屏专属：卡片档没它的位置，sc>0 才组合进场（透明度也随 sc）。
            Row(
                modifier = Modifier
                    .width(TitleActionButton + TitleActionButton * sc)
                    .height(TitleActionButton)
                    .clipToBounds()
                    .graphicsLayer { alpha = actionsAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleLike,
                    enabled = actionsVisible,
                    modifier = Modifier.size(TitleActionButton),
                ) {
                    Icon(
                        // 空/实心随真状态走：以前这颗心是「永远实心 + 主色」的假状态，
                        // 点下去只弹「开发中」，读起来像「这首歌已经喜欢了」。
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "取消喜欢" else "喜欢",
                        tint = if (liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        // 卡片档 24dp（与 21sp 歌名相称）→ 全屏 28dp（压住 24sp 歌名）。
                        modifier = Modifier.size(
                            androidx.compose.ui.unit.lerp(24.dp, TitleActionIcon, sc),
                        ),
                    )
                }
                if (sc > 0.01f) {
                    IconButton(
                        onClick = { onComments(commentRect) },
                        enabled = actionsVisible,
                        modifier = Modifier
                            .size(TitleActionButton)
                            .graphicsLayer { alpha = sc * actionsAlpha }
                            .onGloballyPositioned { onCommentBounds(it.boundsInWindow()) },
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
        }
        // 歌手为空时整行折叠，不留空洞 —— 但**徽标不跟着折**：它们是这一行的主要信息
        // （歌手名缺失时，用户更需要知道「这是 VIP 歌、当前只有 30 秒」）。
        if (state.artist.isNotEmpty() || state.trial != null || state.vip) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 徽标贴在歌手名前，与曲目列表行「徽标贴在副标题前」同一处语言。
                // VIP 在左、「试听」在右：前者是这首歌的静态属性，后者是此刻的播放状态，
                // 顺序与列表行一致（VIP 永远第一枚）。两枚**同时出现**时信息才完整。
                if (state.vip) {
                    CirroPayBadge(PayTag.VIP)
                    Spacer(Modifier.width(2.dp))
                }
                if (state.trial != null) {
                    CirroTrialBadge()
                    Spacer(Modifier.width(2.dp))
                }
                if (state.artist.isNotEmpty()) {
                    // weight(1f) 是必须的：FadingMarqueeText 内部是
                    // `BoxWithConstraints(modifier.fillMaxWidth())`，不给权重它会把整行
                    // 宽度吃光、把左边那枚徽标挤出去（无徽标时它仍占满整行，与改前一致）。
                    FadingMarqueeText(
                        text = state.artist,
                        style = artistStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

