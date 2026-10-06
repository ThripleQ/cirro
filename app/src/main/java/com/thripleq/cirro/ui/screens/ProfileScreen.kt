package com.thripleq.cirro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.core.repo.Album
import com.thripleq.cirro.core.repo.PlaylistSummary
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.CirroPageTitleBar
import com.thripleq.cirro.ui.components.ShellPanel
import com.thripleq.cirro.ui.components.rememberSharedSourceGuard
import com.thripleq.cirro.ui.profile.ProfileUiState
import com.thripleq.cirro.ui.profile.ProfileViewModel
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 我的页：账号抬头 + 「喜欢的音乐」全宽横幅 + 「已购」原地撑开的壳卡 + 创建/收藏两张行卡。
 * 横幅与行卡点开都是「大卡 → 全屏面板」，封面 morph 到内容里的 banner 封面。
 * 喜欢的音乐是曲目列表；创建 / 收藏是歌单网格面板，点网格内的歌单再进入该歌单的曲目列表。
 * 「已购」**不直接打开面板**：点一下原地撑开成一只壳，壳里是「单曲」「专辑」两个入口，
 * 各自再点一下才进**全屏**（单曲 → 曲目列表，专辑 → 2 列大卡网格）。
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
    // 这次面板要不要走封面 morph：点击那一刻起点封面是否完整可见
    // （被顶部圆角纸切着就不飞，见 [SharedSourceGuard]）。**必须存在这里** ——
    // 关闭面板时卡片网格会重新组合，源端的判断随之归位，只有目标端留得住。
    var panelMorph by remember { mutableStateOf(true) }

    // 面板打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = panel != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }

    // 页面重新可见（切回「我的」tab、从详情页返回）时问一句要不要取新数据：
    // 已购、收藏镜像、红心数都会在**别处**（官方 App）变，原来只有 init 那一次。
    // 本页是导航目的地，切走会移出组合、切回重新组合，所以这个 effect 每次可见都重跑；
    // VM 提升到 Activity 作用域常驻，真正要不要发请求由 VM 里的冷却门决定。
    LaunchedEffect(Unit) { vm.onEnterVisible() }
    val uid = (state as? ProfileUiState.LoggedIn)?.data?.account?.uid?.toString()
    // 胶囊壳底部让位量 = 导航岛实时高度（dp，由 PlayerCapsule 上报，含拉手+nav行+手势条 inset）。
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }
    val panelBottomPad = islandClearance + 16.dp

    // 滚动位置 hoist：共享壳用 AnimatedContent 在「卡片网格 ↔ 面板」间切换，关闭面板时
    // 网格会重新组合——不 hoist 就会跳回顶部（同 HomeScreen 的 listState）。
    val scrollState = rememberScrollState()

    val onOpenPanel = remember {
        { target: ProfilePanel, rect: Rect?, morph: Boolean ->
            panelRect = rect
            panelMorph = morph
            panel = target
        }
    }
    val onDismiss = remember { { panel = null } }
    // 「无条件重取一次」：错误态的重试与顶栏那颗刷新键共用同一个动作。
    val onRefresh = remember(vm) { { vm.refresh() } }

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
                        onRefresh = onRefresh,
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
                            morph = panelMorph,
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
                onRefresh = onRefresh,
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
    onOpenPanel: (ProfilePanel, Rect?, morphable: Boolean) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    bottomPadding: Dp,
) {
    // 与探索页 / 搜索页同一套外壳：顶层铺 `surfaceContainer` 放「我的」大标题，内容是一张
    // `surface` 圆角纸 —— 纸的顶角把容器色露出来，就是状态栏那条容器色 + 下方圆角内容的关系。
    // 标题条走 [CirroPageTitleBar]（三页共用一份实现，内缩 24dp 与字形永远一致），
    // 状态栏让位随之从内容挪到标题条上。
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // 顶栏刷新键与探索页**同规格**：28dp 的 IconButton、默认右侧内缩
        // （[TitleBarEndInset]），三个 tab 页看起来是一套。
        CirroPageTitleBar("我的") {
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 源可见性守卫：卡片被这张圆角纸的顶边切掉时不许挂共享元素
        // （overlay 不裁，飞的那份会画在纸上方 —— 见 [SharedSourceGuard]）。
        val guard = rememberSharedSourceGuard()

        // 底部避让必须放在滚动内容内部（同 TrackListScreen 的 contentPadding 做法）：
        // 放在外层 padding 会在岛背后留一条永久空白带，卡片进不去、岛像贴在画布上。
        // 顶部反过来：状态栏让位**不再**由内容承担，改由标题条兜住 —— 内容从纸上沿起，
        // 第一张卡（登录卡 / hero）压不到状态栏与挖孔上。
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CirroShape.SheetTop)
                .background(MaterialTheme.colorScheme.surface)
                // 视口 = 这张纸的裁切边界。**必须挂在 `.padding(...)` 之前**：
                // onGloballyPositioned 量的是它所在链条位置的尺寸，放到 padding 之后
                // 会量到内缩后的小矩形，贴着纸边的卡会被误判成「没被切」。
                .then(guard.viewportModifier())
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        ) {
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                ProfileUiState.Loading -> ProfileSkeleton()
                is ProfileUiState.Error -> CirroErrorState(onRetry = onRefresh)
                // 未登录也先把完整窗口摆好：登录卡置顶，四个区块以占位呈现，
                // 结构与已登录完全一致，点击任意区块引导登录。
                ProfileUiState.LoggedOut -> LoggedOutContent(onWebLogin)
                is ProfileUiState.LoggedIn -> LoggedInContent(
                    data = s.data,
                    onOpenTracks = onOpenTracks,
                    onOpenPanel = onOpenPanel,
                    shared = shared,
                    avScope = avScope,
                    guard = guard,
                )
            }
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

    /**
     * 已购专辑的大卡网格（已购 → 专辑）。**没有 banner**：网格自己就是主体，
     * 顶上只放一行面板标题（网格首行即专辑卡），所以也不参与共享元素 ——
     * 起点是「已购」行卡的图标，把一个图标 morph 成整屏网格没有意义。
     */
    data class Albums(
        val title: String,
        val albums: List<Album>,
        override val coverUrl: String?,
        override val icon: ImageVector,
        override val meta: String?,
    ) : ProfilePanel {
        override val shellKey: String get() = "shell:profile:albums"
    }
}
