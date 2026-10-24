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
 * 2026-10-24 又按同一个标准切了一次（此前一个文件 920 行，改哪块都要在一整页布局里找）：
 *
 * | 改什么 | 去哪个文件 |
 * |---|---|
 * | 封面、标题块、歌词区的**版式与随 sc 的形变** | 本文件（如下的 `PlayerPageContent`） |
 * | 标题块内部（红心/评论、歌名歌手、徽标顺序） | `PlayerHeading.kt` |
 * | 进度条、试听结束提示、三键、功能胶囊行 | `PlayerTransportSection.kt` |
 * | 滑块与两个时间标签（唯一解包进度值的地方） | `PlayerProgressRow.kt` |
 * | 歌词三档的定义、时长/距离常量、换档手势 | `PlayerLyricsStage.kt` |
 * | 歌词列表本身 | `PlayerLyrics.kt` |
 *
 * ⚠️ 这里是全项目**唯一逐帧重组**的组合（组合期读展开进度 `sc`，排版量全是 `sc` 的连续
 * 函数）⇒ 往里加东西要按「每帧都会重跑」估价，别照抄普通页面的写法。圆角与投影已在
 * `PlayerPage` 那层量化成台阶（`Motion.quantPx`），**别在这里再引入逐帧连续的 layer 属性**
 * （每帧重建 clip outline + 重算阴影，正是此前掉帧的真因）。
 */
import androidx.media3.common.util.UnstableApi
import com.thripleq.cirro.ui.theme.Motion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
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
    // 内联 ⇄ 沉浸的换档手势：嵌套滚动**旁路观察**，不消费任何位移。判定与去抖全部在
    // [LyricsScrollConnector] 里（见 PlayerLyricsStage.kt 的完整说明），这里只把当前档位的
    // 读法与两个迁移动作接上 —— λ 里读 `lyricsStage` 走的是 remember 的 delegate，
    // 拿到的永远是最新档位，不会固化在创建时那一份。
    val lyricsScroll = remember(density) {
        LyricsScrollConnector(
            density = density,
            stage = { lyricsStage },
            onEnterImmersive = { lyricsStage = LyricsStage.Immersive },
            onExitImmersive = { lyricsStage = LyricsStage.Inline },
        )
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
                //
                // ⚠️ 封面尺寸**只由 sc 决定**，不要再拿「槽位剩下的高度」去夹它（2026-10-24
                // 用户指出「出场退场方式变了，原来宽度不变」）：展开/收起时壳是逐帧长高的，
                // 中途槽位比卡片档的封面还矮 —— 一旦按实时 maxHeight 夹一下，封面就会跟着
                // 壳从小长到大，「定宽上滑」的出场变成了「放大浮现」。要留空间只能在**静态**
                // 几何上算，不能挂在动画中的测量值上。
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
        // 整簇自带退场动画与全部间距量，收在 [PlayerTransportSection] 里（自成一个文件）：
        // 增删按钮、调间距只动那边，不会碰到上面这块逐帧几何。沉浸档整簇退场时，
        // 进度行在里面随之卸载 —— [positionState] 的 250ms 订阅也一并停掉。
        PlayerTransportSection(
            player = player,
            state = state,
            positionState = positionState,
            seekFrozen = seekFrozen,
            visible = lyricsStage != LyricsStage.Immersive,
            sc = sc,
            lyricsLayout = lyricsLayout,
            sleepEndAt = sleepEndAt,
            onSleepEndAtChange = onSleepEndAtChange,
            onSettings = { settingsOpen = true },
        )

        // 两张 sheet 留在底部整簇外面：它们是 modal 浮层，不该跟着底部簇一起收拢
        //（真在沉浸档打开它们时，也应当原样盖满）。
        if (queueOpen) {
            PlayerQueueSheet(player = player, onDismiss = { queueOpen = false })
        }
        if (settingsOpen) {
            PlayerSettingsSheet(onDismiss = { settingsOpen = false })
        }
    }
}

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
internal val TransportBottomGap = 24.dp + 33.dp
