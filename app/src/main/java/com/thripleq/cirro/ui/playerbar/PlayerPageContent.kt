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
import android.widget.Toast
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape
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
import androidx.compose.runtime.State
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
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.ui.components.CirroTrialBadge
import com.thripleq.cirro.ui.components.FadingMarqueeText
import com.thripleq.cirro.ui.components.quantizedFontSize
import kotlinx.coroutines.flow.collect

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
    modifier: Modifier = Modifier,
) {
    var queueOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var commentRect by remember { mutableStateOf(Rect.Zero) }
    val state = rememberPlayerState(player)
    // 封面「点一下切歌词」：lambda 必须 remember 固定 —— 本组合随展开进度逐帧重组，
    // 每次新建会让封面的 clickable 节点每帧重建（Modifier 链 + 手势节点）。
    // 捕获的是 `lyricsOpen` 的 state 委托与 `state` 引用，两者都稳定，读到的仍是最新值。
    val toggleLyrics = remember { { if (state.trackId != null) lyricsOpen = !lyricsOpen } }
    // 红心：真状态 + 真接口（[PlayerActionsViewModel]）。状态来自进程级的「喜欢」集合，
    // 切歌不产生网络请求；未载入完时显示空心的代价写在那个类的注释里。
    val actionsVm: PlayerActionsViewModel = hiltViewModel()
    val liked by actionsVm.liked.collectAsStateWithLifecycle()
    LaunchedEffect(state.trackId) { actionsVm.onTrackChanged(state.trackId) }
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(actionsVm) {
        actionsVm.message.collect { Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show() }
    }
    // 歌词：惰性加载 —— 只有歌词面板打开时才按当前曲目请求（切换曲目自动重载）。
    val lyricsVm: LyricsViewModel = hiltViewModel()
    val lyricsState by lyricsVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(lyricsOpen, state.trackId) {
        if (lyricsOpen) lyricsVm.load(state.trackId)
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
                ) { toggleLyrics() },
        ) {
            if (lyricsOpen) {
                LyricsView(
                    uiState = lyricsState,
                    positionState = positionState,
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
                    style = titleStyle,
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
                    IconButton(
                        onClick = actionsVm::toggleLike,
                        modifier = Modifier.size(TitleActionButton),
                    ) {
                        Icon(
                            // 空/实心随真状态走：以前这颗心是「永远实心 + 主色」的假状态，
                            // 点下去只弹「开发中」，读起来像「这首歌已经喜欢了」。
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (liked) "取消喜欢" else "喜欢",
                            tint = if (liked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 歌手为空时整行折叠，不留空洞 —— 但**试听标不跟着折**：它是这一行的主要信息
            // （歌手名缺失时，用户更需要知道「为什么这首只有 30 秒」）。
            if (state.artist.isNotEmpty() || state.trial != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 「试听」标贴在歌手名前，与曲目列表行「徽标贴在副标题前」同一处语言。
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

        Spacer(Modifier.weight(1f))

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
        // ② 插在进度行**下方**：进度行上方有一条 `Spacer(weight(1f))`，弹性空白会吸收这点
        //    新高度，所以底部三键与功能胶囊**不会**被顶下去（这也是为什么不插在标题那块：
        //    那里动一寸，下面全跟着走）。
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
