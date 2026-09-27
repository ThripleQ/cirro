package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeShape
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.Home
import com.thripleq.nume.Profile
import com.thripleq.nume.Search
import com.thripleq.nume.core.playback.PlaybackPreferences
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.core.repo.LyricLine
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** The top-level tabs shown in the docked capsule. */
enum class BottomTab(val route: Any, val label: String, val icon: ImageVector) {
    ExploreTab(route = Home, label = "探索", icon = Icons.Filled.Explore),
    SearchTab(route = Search, label = "搜索", icon = Icons.Filled.Search),
    ProfileTab(route = Profile, label = "我的", icon = Icons.Filled.Person),
}

/** Live snapshot of the shared [Player] for the mini player bar. */
data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val trackId: String? = null,
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasTrack: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val errorText: String? = null,
)

/**
 * Observes [player] (process-scoped singleton) with a metadata listener + 250ms poll.
 * 状态拆成快照：元数据/播放态（标题/封面/是否播放/时长/有无曲目）低频，
 * 仅在真实变化时才写，避免无谓重组。
 *
 * **进度条优化**：进度 positionMs 不进入返回值 —— [rememberPlayerState] 从不读它，
 * 调用方（迷你条/播放页）不会随 250ms 轮询重组。进度条这类需要逐帧更新的小部件
 * 单独订阅 [rememberPlayerPosition]，只有它随轮询重组。
 */
@Composable
fun rememberPlayerState(
    player: Player,
): PlayerUiState {
    var meta by remember { mutableStateOf(PlayerUiState()) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                meta = meta.copy(
                    title = mediaMetadata.title?.toString() ?: "",
                    artist = mediaMetadata.artist?.toString() ?: "",
                    trackId = player.currentMediaItem?.mediaId,
                    coverUrl = mediaMetadata.artworkUri?.toString(),
                    errorText = null,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (meta.trackId != mediaItem?.mediaId) meta = meta.copy(trackId = mediaItem?.mediaId)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                meta = meta.copy(isPlaying = playing)
            }

            override fun onPlaybackStateChanged(s: Int) {
                meta = meta.copy(isBuffering = s == Player.STATE_BUFFERING)
                if (s == Player.STATE_READY) {
                    meta = meta.copy(durationMs = player.duration.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                meta = meta.copy(errorText = error.errorCodeName ?: error.message)
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                meta = meta.copy(shuffleEnabled = enabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                meta = meta.copy(repeatMode = repeatMode)
            }
        }
        player.addListener(listener)
        // Seed everything from the player's current state so the UI reflects
        // reality the moment it appears (e.g. already playing when the screen
        // opens) instead of defaulting to "not playing".
        val mediaMetadata = player.mediaMetadata
        meta = PlayerUiState(
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            trackId = player.currentMediaItem?.mediaId,
            coverUrl = mediaMetadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.coerceAtLeast(0L),
            hasTrack = player.currentMediaItem != null,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        try {
            while (true) {
                // hasTrack / duration 由 listener 与这里共同维护；只在真实变化时写 meta。
                val hasTrack = player.currentMediaItem != null
                val duration = player.duration.coerceAtLeast(0L)
                if (meta.hasTrack != hasTrack || meta.durationMs != duration) {
                    meta = meta.copy(hasTrack = hasTrack, durationMs = duration)
                }
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return meta
}

/**
 * 高频进度订阅：250ms 轮询写入 positionMs。只有读取返回 [State] 的组合
 * （迷你条进度条、播放页 Slider/时间）才随轮询重组；不读它的组合零开销。
 * [positionFrozen] 用于拖动进度时冻结位置，避免轮询跟手指打架。
 *
 * **seek 立即对齐**：拖动时 [positionMs] 冻结在「拖动前」的旧值，若只靠 250ms
 * 轮询，松手瞬间 Slider 会先回跳到这个旧值、下一个 tick 才跳到目标 —— 看起来就是
 * 指针抖一下/闪一下。监听 [Player.Listener.onPositionDiscontinuity]（seek/跳转时
 * 同步触发）即时写入新位置，松手前就把旧值换成目标，回跳消失。切歌/跳转同理。
 */
@Composable
fun rememberPlayerPosition(
    player: Player,
    positionFrozen: () -> Boolean = { false },
): State<Long> {
    val positionMs = remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                positionMs.longValue = newPosition.positionMs.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                if (!positionFrozen()) positionMs.longValue = player.currentPosition
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return positionMs
}

/** 播放页「打开程度」：0 = 完全收起在迷你条胶囊，2 = 盖满全屏。
 *  两段式：p∈[0,1] 胶囊原位展开成悬浮卡（dock 保持可见）；
 *  p∈[1,2] 卡片放大盖满全屏（dock 淡出）。 */

/** 第一档（胶囊→卡片）的分裂点：progress ∈ [0,SPLIT] 是「扩展」，[SPLIT,1] 是「分裂」。 */
private const val SPLIT = 0.5f

/** 卡片档吸附进度：分裂完成后壳顶继续线性升高到该进度才停 —— 卡片占屏约 2/3、
 *  高度足够装下封面+滑块+控制整组内容（此前卡片只有半屏高，内容溢出被裁、比例失调）。
 *  全屏固定 2；卡片→全屏的形变（贴边/收角/变色/dock淡出）全部在 [HALF_ANCHOR_P, 2] 内插值，
 *  壳顶仍全程随手指线性升降（跟手不变）、一行程直达全屏（行程不变）。 */
private const val HALF_ANCHOR_P = 1.66f

/** 分裂段内部再分两拍：前一半「挤腰」（交界圆角涨大、两侧内收成腰），后一半「断开」（缝打开）。 */
private const val SPLIT_SQUEEZE = 0.5f

/** 挤腰峰值圆角（dp）：分裂前拍交界处圆角从 0 涨到它，形成内收的腰。 */
private val WAIST_CORNER_DP = 36f

/**
 * spring 动画参数：**数值保持原样，但集中到 [Motion]**。
 *
 * 壳与 dock 过去各写一套阻尼/刚度与时长，并排看就是「两个不同 App 的手感」；现在两处
 * 同源于 [Motion]。刻意不改弹性数值——细胞分裂的吸附手感已在真机调过，盲改风险高于收益。
 */
private val SPRING_CLOSE = Motion.SheetSettle
/** 点击整页展开：低阻尼带一点弹性过冲 + 中低刚度，既有生长过程可见、又跟手不闷。 */
private val SPRING_FULL = Motion.SheetExpand

/** 分裂段进度 [0,1] 拆成两拍：挤腰 ([0,SPLIT_SQUEEZE]) 与 断开 ([SPLIT_SQUEEZE,1])。 */
private fun splitSqueezeT(splitT: Float): Float = (splitT / SPLIT_SQUEEZE).coerceIn(0f, 1f)
private fun splitBreakT(splitT: Float): Float =
    ((splitT - SPLIT_SQUEEZE) / (1f - SPLIT_SQUEEZE)).coerceIn(0f, 1f)

/** 挤腰用的圆形过渡：0 起涨、到 1、收 0（让腰先挤出来再松开，模拟细胞缢裂）。 */
private fun splitWaistCurve(x: Float): Float {
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
    private val scope: CoroutineScope,
) {
    /** 全屏播放面是否在组合中：进入会话即 true，完全收起（尾帧落地）后才复位。
     *  内部由 [AnchoredDraggableState] 的 offset/settledValue 观察驱动，外部只读。 */
    var open by mutableStateOf(false)
        internal set

    /** 手势行程（px）：从迷你条胶囊到「全屏」的展开总距（卡片是它的一半）。 */
    var travelPx by mutableFloatStateOf(1f)

    /** 胶囊展开进度 0..2（[0,1]=胶囊→卡片，[1,2]=卡片→全屏），由 [AnchoredDraggableState.offset] 归一化。
     *  只应在 draw 阶段（graphicsLayer）读，勿在组合读。 */
    val progress: Float
        get() = (sheetState.offset / travelPx).coerceIn(0f, 2f)

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

    /** 点击迷你条/列表项/我的：整页动画弹出。
     *  [toFull] = false 时两段式先弹到卡片（Half），可继续上滑看全屏；
     *  true 时直接盖满全屏（列表项点歌/点击迷你条用）。 */
    fun open(toFull: Boolean = false) {
        animJob?.cancel()
        animJob = scope.launch {
            if (!open) {
                open = true
                // 等一帧，等壳（PlayerPage）组合就位，从当前 offset 续跑展开，
                // 而不是先 snap 到 0 再展开——第一帧就是迷你条本身。
                withFrameNanos { }
                sheetState.animateTo(
                    if (toFull) PlayerSheet.Full else PlayerSheet.Half,
                    if (toFull) SPRING_FULL else SPRING_CLOSE,
                )
            } else {
                // 已打开：直接动画到目标档。
                sheetState.animateTo(
                    if (toFull) PlayerSheet.Full else PlayerSheet.Half,
                    if (toFull) SPRING_FULL else SPRING_CLOSE,
                )
            }
        }
    }

    /** 收起箭头/返回键：无论当前在哪个档位，都缩回迷你条。 */
    fun close() {
        animJob?.cancel()
        animJob = scope.launch { runClose() }
    }

    private suspend fun runClose() {
        sheetState.animateTo(PlayerSheet.Closed, SPRING_CLOSE)
        // animateTo 返回后再等两帧，确保尾帧真正落地才卸载，避免收起闪最后一帧。
        withFrameNanos {}
        withFrameNanos {}
        open = false
    }
}

/** 在 [PlayerDock] 外部持有同一状态（NumeApp 需要列表项/我的点歌弹开播放页）。 */
@Composable
fun rememberPlayerDockState(): PlayerDockState {
    val scope = rememberCoroutineScope()
    // 官方 AnchoredDraggableState：三锚点（收起/半高/全屏），锚点像素值（offset）在
    // PlayerDock 布局后由 updateAnchors 填充（依赖 dock 高/屏高）。初始只有一个锚点。
    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = PlayerSheet.Closed,
            anchors = DraggableAnchors {
                PlayerSheet.Closed at 0f
            },
            positionalThreshold = { distance -> distance * 0.4f },
            velocityThreshold = { 800f },
            snapAnimationSpec = SPRING_CLOSE,
            // 甩动衰减：低摩擦让「甩」更顺滑跟手（滑得远、不顿）。
            decayAnimationSpec = exponentialDecay(frictionMultiplier = 0.7f),
            confirmValueChange = { true },
        )
    }
    return remember { PlayerDockState(sheetState, scope) }
}

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
    var dockHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(dockHeightPx) {
        if (dockHeightPx > 0) onIslandHeightChange(with(density) { dockHeightPx.toDp() }.value)
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
private fun PlayerBar(
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
private fun PlayerBarContent(
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
private fun MiniProgressBar(
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
private fun SpectrumPlaceholder() {
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
private fun NavRow(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
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
private fun ActionNavRow(
    onPlayAll: () -> Unit,
    onPlaceholderAction: () -> Unit,
    onComments: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
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

/** 全屏播放面：壳（surfaceContainerHighest + 顶 18dp 圆角 + 1dp 阴影）从迷你条胶囊
 *  原位伸展成悬浮卡、再盖满全屏；壳顶停在状态栏下沿（与 ExpandableShell 面板一致）。
 *  几何读 progress 在组合里（与 ExpandableShell 同构，壳随 progress 每帧布局）。 */
@Composable
private fun PlayerPage(
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
    //   p∈[HALF_ANCHOR_P,2] 卡片→全屏：盖满含状态栏/导航栏（卡片档先稳定到 1.35）。
    //
    //   **连续性保证**：分裂点（t0=SPLIT）上 dock 圆角=0、气泡底边=dockTop+edgePx，
    //   扩展段末与分裂段初逐值相等，无跳变（分裂不再割裂）。
    fun shellRect(p: Float): Rect {
        val dockTopPx = fullHeightPx - dockHeightPx
        val full = Rect(0f, 0f, fullWidthPx, fullHeightPx)
        val t0 = p.coerceIn(0f, 1f)
        // 卡片→全屏的插值在 [HALF_ANCHOR_P, 2] 段内进行：卡片档先稳定停留到 1.35，
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
    // 内容淡入：扩展段气泡长起来时内容浮现，分裂完成（p=1）已基本可见。
    val contentAlpha = (p / SPLIT).coerceIn(0f, 1f)
    // 顶部拉手/收起：分裂成卡后才浮现。
    val headerAlpha = splitT

    // 沉浸：只在接近全屏时隐藏系统导航栏（半高时保持显示，dock 的 navigationBarsPadding
    // 布局稳定）；收起/回落到半高时恢复。用 snapshotFlow 轮询 progress，不引重组。
    val view = LocalView.current
    val activity = LocalActivity.current
    val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
    LaunchedEffect(controller) {
        if (controller != null) {
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
                // 松手吸附/甩动由 AnchoredDraggableState 原生处理（位置阈值 + 速度阈值）。
                // reverseDirection=true：上滑（y 减小）→ offset 增大 → 展开。
                .anchoredDraggable(
                    state.sheetState,
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
                contentProgress = p,
                shellHeightPx = rect.height,
                shellWidthPx = rect.width,
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
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
private fun PlayerPageContent(
    player: Player,
    contentProgress: Float,
    shellHeightPx: Float,
    shellWidthPx: Float,
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

    val density = LocalDensity.current
    val sc = ((contentProgress - HALF_ANCHOR_P) / (2f - HALF_ANCHOR_P)).coerceIn(0f, 1f)
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
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
            .padding(top = 30.dp, bottom = 20.dp),
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

        // Track / metadata（标题全程在封面下方）
        Text(
            text = state.title.ifEmpty { "暂无播放" },
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = titleFont),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        // 歌手为空时整行折叠，不留空洞。
        if (state.artist.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = artistFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        // 全屏专属：动作行（收藏/评论 左，播放列表 右），高度与透明度随 sc 长出。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp * sc)
                .clipToBounds()
                .graphicsLayer { alpha = sc },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { queueOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.QueueMusic,
                    "播放列表",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp * sc))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp * sc)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f * sc)),
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
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
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
            SleepTimerChip(player = player)
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

/** 全屏档功能胶囊：56×44 圆角矩形，激活态用 primaryContainer 高亮。 */
@Composable
private fun FullChip(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = 44.dp),
        shape = NumeShape.Card,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

/**
 * 定时关闭：点开选择时长，倒计时结束后暂停。用墙钟（System.currentTimeMillis）判定，
 * 而不是 delay 累加 —— 进程被挂起/系统休眠时 delay 会漂移，墙钟不会。
 */
@Composable
private fun SleepTimerChip(player: Player) {
    var endAt by remember { mutableLongStateOf(0L) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(endAt) {
        if (endAt == 0L) return@LaunchedEffect
        while (true) {
            if (System.currentTimeMillis() >= endAt) {
                player.pause()
                endAt = 0L
                return@LaunchedEffect
            }
            delay(1000)
        }
    }

    Box {
        FullChip(
            icon = Icons.Filled.Bedtime,
            contentDescription = "定时关闭",
            active = endAt > 0L,
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(0 to "关闭定时", 15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟", 90 to "90 分钟")
                .forEach { (min, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            endAt = if (min == 0) 0L
                            else System.currentTimeMillis() + min * 60_000L
                            menuOpen = false
                        },
                    )
                }
        }
    }
}

/** 播放队列面板：列出当前队列、高亮在播曲目、点按跳转。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerQueueSheet(player: Player, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var count by remember { mutableIntStateOf(player.mediaItemCount) }
    var current by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                count = player.mediaItemCount
                current = player.currentMediaItemIndex
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                count = player.mediaItemCount
                current = player.currentMediaItemIndex
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "播放队列 · ${if (count == 0) 0 else current + 1}/$count",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(count) { i ->
                val meta = player.getMediaItemAt(i).mediaMetadata
                val isCurrent = i == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            player.seekToDefaultPosition(i)
                            player.play()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (i + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = meta.title?.toString() ?: "未知曲目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val artist = meta.artist?.toString().orEmpty()
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 播放设置：目前只有音质档位（写入 [PlaybackPreferences]，[PlaybackUrls] 解析时读取）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var quality by remember { mutableStateOf(PlaybackPreferences.quality(context)) }
    val labels = mapOf(
        "standard" to "标准",
        "higher" to "较高",
        "exhigh" to "极高",
        "lossless" to "无损",
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "播放音质",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        PlaybackPreferences.QUALITIES.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        quality = q
                        PlaybackPreferences.setQuality(context, q)
                    }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = quality == q,
                    onClick = {
                        quality = q
                        PlaybackPreferences.setQuality(context, q)
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = labels[q] ?: q,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 当前行索引：最后一个 timeMs <= position 的行；position 早于首行时返回 -1。二分。 */
private fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

/**
 * 歌词面板：随播放进度自动滚动 + 高亮当前行，点某行跳到该行时间。
 * 用户手动滚动后暂停自动滚动 4s，避免和阅读抢（用 [LazyListState.isScrollInProgress]
 * 分辨，本组件自己触发的滚动用 programmatic 标记排除）。
 */
@Composable
private fun LyricsView(
    uiState: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        LyricsUiState.Idle, LyricsUiState.Loading ->
            LyricsMessage("加载歌词…", modifier, loading = true)

        LyricsUiState.Empty -> LyricsMessage("暂无歌词", modifier)

        is LyricsUiState.Ready -> {
            val lines = uiState.lyrics.lines
            val currentIndex = currentLyricIndex(lines, positionMs)
            val listState = rememberLazyListState()
            var programmatic by remember { mutableStateOf(false) }
            var lastUserScrollAt by remember { mutableLongStateOf(0L) }

            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling && !programmatic) lastUserScrollAt = System.currentTimeMillis()
                }
            }
            LaunchedEffect(currentIndex) {
                if (currentIndex < 0) return@LaunchedEffect
                if (System.currentTimeMillis() - lastUserScrollAt < 4_000L) return@LaunchedEffect
                programmatic = true
                try {
                    // 当前行滚到视口约 1/3 处（留上方上下文），负偏移把行往下带。
                    val viewport = listState.layoutInfo.viewportSize.height
                    listState.animateScrollToItem(currentIndex, -(viewport * 0.35f).roundToInt())
                } finally {
                    programmatic = false
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lines) { i, line ->
                    LyricRow(
                        line = line,
                        selected = i == currentIndex,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

/** 单行歌词：选中行放大加粗高亮，未选中行弱化；有翻译时在下方补一行小字。 */
@Composable
private fun LyricRow(line: LyricLine, selected: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (selected) 1f else 0.55f, label = "lyricAlpha")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (selected) 19.sp else 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = (
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                ).copy(alpha = alpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        line.translation?.let { translation ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 歌词占位态：加载中（转圈+文案）或空（仅文案），在封面矩形内居中。 */
@Composable
private fun LyricsMessage(text: String, modifier: Modifier = Modifier, loading: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 封面：卡片/全屏共用；无图时用弱化音符占位，有图按目标像素解码。 */
@Composable
private fun CoverArt(
    state: PlayerUiState,
    dim: Dp,
    corner: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val px = with(density) { dim.toPx() }.roundToInt().coerceIn(64, 1280)
    Box(
        modifier = modifier
            .size(dim)
            .clip(RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        val coverUrl = state.coverUrl
        if (coverUrl == null) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
            // 空状态：一块空白封面太秃，放个弱化的音符占位。
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(iconSize),
            )
        } else {
            val model = remember(coverUrl, px) {
                ImageRequest.Builder(context).data(coverUrl).size(px).build()
            }
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private const val SWIPE_THRESHOLD_DP = 56
