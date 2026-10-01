package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.ui.components.NumeErrorState
import com.thripleq.nume.ui.components.ShellPanel
import com.thripleq.nume.ui.profile.ProfileUiState
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.thripleq.nume.ui.theme.Motion

/**
 * 我的页：2×2 大卡（喜欢的音乐 / 已购 / 创建的歌单 / 收藏的歌单），风格同探索页大封面卡。
 * 每张卡点开都是「大卡 → 全屏面板」，封面 morph 到内容里的 banner 封面。
 * 喜欢的音乐 / 已购是曲目列表；创建 / 收藏是歌单网格面板，点网格内的歌单再进入该歌单的曲目列表。
 *
 * 两条壳实现并存、由 [Motion.SharedShellEnabled] 切换：
 * - 官方共享元素：[ShellPanel] + shellSharedCover（与探索页/歌手头像同一套语义，只转封面）。
 * - 自研 CoverExpandShell：[ProfilePanelLegacy]，整壳几何动画（保留不删，开关关闭时启用）。
 *
 * 底部落地岛全程常驻；面板内列表不再抬岛让位，内容自然滚到岛下方。
 */
@Composable
fun ProfileScreen(
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onWebLogin: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    islandHeight: Float = 0f,
    onShellOpenChange: (Boolean) -> Unit = {},
    shared: SharedTransitionScope? = null,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 当前打开的面板（null = 无面板）。
    var panel by remember { mutableStateOf<ProfilePanel?>(null) }
    // 被点击大卡的屏幕坐标（自研 CoverExpandShell 路径的动画起点）。
    var panelRect by remember { mutableStateOf<Rect?>(null) }

    // 面板打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = panel != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }
    val uid = (state as? ProfileUiState.LoggedIn)?.data?.account?.uid?.toString()
    // 胶囊壳底部让位量 = 导航岛实时高度（dp，由 PlayerCapsule 上报，含拉手+nav行+手势条 inset）。
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }
    val panelBottomPad = islandClearance + 16.dp

    // 滚动位置 hoist：共享壳用 AnimatedContent 在「卡片网格 ↔ 面板」间切换，关闭面板时
    // 网格会重新组合——不 hoist 就会跳回顶部（同 HomeScreen 的 listState）。
    val scrollState = rememberScrollState()

    val onOpenPanel = remember {
        { target: ProfilePanel, rect: Rect? -> panelRect = rect; panel = target }
    }
    val onDismiss = remember { { panel = null } }
    val onRetry = remember(vm) { { vm.refresh() } }

    if (Motion.SharedShellEnabled && shared != null) {
        // 官方容器变换：源大卡封面与面板 banner 封面挂同一 key 的 sharedBounds，
        // 框架把「同一张封面」从卡片 morph 到全屏。外壳（scrim + 底 + 关闭键）由 ShellPanel 担。
        with(shared) {
            AnimatedContent(
                targetState = panel,
                transitionSpec = {
                    // 网格必须**真实淡出**（90ms），绝不能是 ExitTransition.None：None 会让退出内容
                    // 首帧即判定完成、立刻移除，共享元素起点随之消失→框架匹配不到两端、封面直接闪现在
                    // banner（就是「某些情况跳过动画」）。收起时网格快速铺底接住飞回的封面。
                    if (targetState != null) {
                        fadeIn(tween(Motion.ShellPanelInMs, easing = Motion.EmphasizedDecelerate)) togetherWith
                            fadeOut(tween(Motion.NavExitMs, easing = Motion.Standard))
                    } else {
                        fadeIn(tween(Motion.NavExitMs, easing = Motion.Standard)) togetherWith
                            fadeOut(tween(Motion.ShellPanelOutMs, easing = Motion.Emphasized))
                    }
                },
                label = "profileShell",
            ) { target ->
                val scope = this
                if (target == null) {
                    ProfileBodyUi(
                        state = state,
                        scrollState = scrollState,
                        onOpenTracks = onOpenTracks,
                        onOpenPanel = onOpenPanel,
                        onWebLogin = onWebLogin,
                        onRetry = onRetry,
                        shared = shared,
                        avScope = scope,
                        bottomPadding = panelBottomPad,
                    )
                } else {
                    ShellPanel(onDismiss = onDismiss) {
                        ProfileSharedPanelContent(
                            target = target,
                            uid = uid,
                            onOpenPlayer = onOpenPlayer,
                            onOpenTracks = onOpenTracks,
                            bottomPadding = panelBottomPad,
                            shared = shared,
                            avScope = scope,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    } else {
        // 自研 CoverExpandShell 路径（共享元素开关关闭时启用；动画保留不删）。
        Box(Modifier.fillMaxSize()) {
            ProfileBodyUi(
                state = state,
                scrollState = scrollState,
                onOpenTracks = onOpenTracks,
                onOpenPanel = onOpenPanel,
                onWebLogin = onWebLogin,
                onRetry = onRetry,
                shared = null,
                avScope = null,
                bottomPadding = panelBottomPad,
            )
            // 全屏列表面板：从被点击大卡的位置伸展成全屏（hero 封面 morph 到 banner 封面）。
            panel?.let { target ->
                ProfilePanelLegacy(
                    target = target,
                    uid = uid,
                    onOpenPlayer = onOpenPlayer,
                    onOpenTracks = onOpenTracks,
                    bottomPadding = panelBottomPad,
                    capsuleRect = panelRect,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** 我的页主体（骨架/错误/未登录/已登录）：共享元素壳与自研壳两条路径共用。 */
@Composable
private fun ProfileBodyUi(
    state: ProfileUiState,
    scrollState: ScrollState,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onOpenPanel: (ProfilePanel, Rect?) -> Unit,
    onWebLogin: () -> Unit,
    onRetry: () -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    bottomPadding: Dp,
) {
    // 避让必须放在滚动内容内部（同 TrackListScreen 的 contentPadding 做法）：
    // 放在外层 padding 会在岛背后留一条永久空白带，卡片进不去、岛像贴在画布上。
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
    ) {
        Spacer(Modifier.height(20.dp))
        when (val s = state) {
            ProfileUiState.Loading -> ProfileSkeleton()
            is ProfileUiState.Error -> NumeErrorState(onRetry = onRetry)
            // 未登录也先把完整窗口摆好：登录卡置顶，四个区块以占位呈现，
            // 结构与已登录完全一致，点击任意区块引导登录。
            ProfileUiState.LoggedOut -> LoggedOutContent(onWebLogin)
            is ProfileUiState.LoggedIn -> LoggedInContent(
                data = s.data,
                onOpenTracks = onOpenTracks,
                onOpenPanel = onOpenPanel,
                shared = shared,
                avScope = avScope,
            )
        }
    }
}

/** 我的页可打开的全屏面板类型。 */
internal sealed interface ProfilePanel {
    /** 展开壳 hero 封面（与起点大卡同源）。 */
    val coverUrl: String?

    /** 内容属性图标：卡片水印 / hero 水印 / banner 缺封面兜底三处共用。 */
    val icon: ImageVector

    /** 起点卡片标题下方那行数据（如「114 首」）。hero 带同一份，展开时数量不闪。 */
    val meta: String?

    /** 官方共享元素键：起点大卡封面与面板 banner 封面必须一致。 */
    val shellKey: String

    /** 曲目列表（喜欢的音乐 / 已购）。 */
    data class Tracks(
        val source: String,
        val id: String,
        val title: String,
        override val coverUrl: String?,
        override val icon: ImageVector,
        override val meta: String?,
    ) : ProfilePanel {
        override val shellKey: String get() = "shell:profile:$source"
    }

    /** 歌单网格（创建 / 收藏）。 */
    data class Playlists(
        val title: String,
        val playlists: List<PlaylistSummary>,
        override val coverUrl: String?,
        override val icon: ImageVector,
        override val meta: String?,
        override val shellKey: String,
    ) : ProfilePanel
}
