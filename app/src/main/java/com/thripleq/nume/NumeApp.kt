package com.thripleq.nume

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.thripleq.nume.core.playback.PlayerHolder
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.components.RevealLayer
import com.thripleq.nume.ui.playerbar.BottomTab
import com.thripleq.nume.ui.playerbar.PlayerDock
import com.thripleq.nume.ui.playerbar.rememberPlayerDockState
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import com.thripleq.nume.ui.screens.ArtistScreen
import com.thripleq.nume.ui.screens.CommentsScreen
import com.thripleq.nume.ui.screens.HomeScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.PodcastScreen
import com.thripleq.nume.ui.screens.ProfileScreen
import com.thripleq.nume.ui.screens.SearchScreen
import com.thripleq.nume.ui.screens.TrackListScreen
import com.thripleq.nume.ui.screens.WebLoginScreen
import com.thripleq.nume.ui.theme.Motion
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. Navigation lives only in [NumeApp]. */
@Serializable
object Home

@Serializable
object Library

@Serializable
object Search

@Serializable
object Profile

/** [TrackListScreen] for a chart: chart id IS a playlist id. */
@Serializable
data class ChartDestination(val chartId: String, val name: String)

/**
 * Generic track list opened from the Profile tab: liked tracks, purchases, a
 * playlist or an album (see [TrackListSource] for the `source` values).
 */
@Serializable
data class TrackListDestination(val source: String, val id: String, val title: String)

/** Full-screen WebView login (official NetEase login page, Kanade-style). */
@Serializable
object WebLogin

/** 歌手主页（资料 + 热门单曲 + 专辑）。name/avatarUrl 由来源页带过来，
 *  让歌手页在数据回来前就能先把头部头像画出来（共享元素目标必须立即存在）。 */
@Serializable
data class ArtistDestination(
    val id: String,
    val name: String = "",
    val avatarUrl: String = "",
)

/** 播客/电台详情（资料 + 节目列表）。 */
@Serializable
data class RadioDestination(val id: String)

/** origin-reveal 浮现的目标：这些详情页从被点对象的矩形浮现进入。开关见 [Motion.RevealEnabled]。 */
private fun androidx.navigation.NavBackStackEntry.isRevealTarget(): Boolean =
    Motion.RevealEnabled && (
        destination.hasRoute<ChartDestination>() ||
            destination.hasRoute<TrackListDestination>() ||
            destination.hasRoute<ArtistDestination>() ||
            destination.hasRoute<RadioDestination>()
        )

/**
 * 详情页浮现进度：0=起点，1=全屏。方向感知——只有「正在被打开的子页」才从起点浮现，
 * 父页在被覆盖时保持满屏留底；返回时**被弹出的子页**收回，父页直接露底。
 *
 * 返回 `(进度, arm)`：前进进入时，进度在 [arm]（内容首次布局完成）前**恒为 0**——
 * 重详情页首次组合会吃掉一两帧，若不等布局就开跑，第一个画出来的帧已经长大、
 * 不贴着被点对象。[RevealLayer.onFirstLayout] 负责调用 [arm]。
 */
@Composable
private fun AnimatedVisibilityScope.rememberRevealProgress(
    forward: Boolean,
): Pair<State<Float>, () -> Unit> {
    // 关闭时：进度恒 1（满屏、无浮现）；RevealLayer 也不叠层，直接透传。
    if (!Motion.RevealEnabled) {
        return remember { mutableStateOf(1f) } to {}
    }
    var armed by remember { mutableStateOf(false) }
    val entering = transition.targetState == EnterExitState.Visible
    val target = when {
        entering && forward -> if (armed) 1f else 0f // 前进进入：从起点长出（等布局起手）
        !entering && !forward -> 0f                  // 返回时被弹出的子页：收回
        else -> 1f                                   // 父页留底 / 返回露底的父页：满屏
    }
    val progress = animateFloatAsState(
        targetValue = target,
        animationSpec = if (entering) Motion.revealEnter() else Motion.revealExit(),
        label = "revealProgress",
    )
    return progress to { armed = true }
}

/**
 * Root of the Compose UI: navigation graph + docked island + full-screen player.
 *
 * 常驻底部 dock（拉手+迷你播放条+底部导航）与全屏播放页合体在 [PlayerDock]：
 * 收起时只露 dock；点击迷你条整页弹出，迷你条上滑 1:1 跟手拉出全屏播放页。
 * 播放页盖住 dock 本身与下方内容，收起箭头/返回键回落。
 */
@Composable
fun NumeApp() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val player = remember { PlayerHolder.get(context) }
    // Profile 的 ViewModel 提升到 Activity 作用域，Profile 页与 WebLogin 页共用同一实例。
    // 登录成功 loadProfile 后 Profile 页自动刷新；且不依赖 Profile 是否在回退栈上
    // （原先 WebLogin 里 getBackStackEntry<Profile>() 在当前不在 Profile 栈时会崩）。
    val profileVm: ProfileViewModel = hiltViewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val currentTab = when {
        destination?.hasRoute<Home>() == true -> BottomTab.ExploreTab
        destination?.hasRoute<Search>() == true -> BottomTab.SearchTab
        destination?.hasRoute<Profile>() == true -> BottomTab.ProfileTab
        else -> null
    }
    // 详情页时保持进入前的 tab，避免导航高亮闪到探索。
    var lastTab by remember { mutableStateOf(BottomTab.ExploreTab) }
    LaunchedEffect(currentTab) {
        if (currentTab != null) lastTab = currentTab
    }
    val selectedTab = currentTab ?: lastTab

    // 列表详情页（榜单/歌单/专辑/喜欢/已购）的滚动操作行：
    // TrackListScreen 上报三按钮是否滑出视口，供 dock 内切换为操作行。
    var listActionsOffscreen by remember { mutableStateOf(false) }
    var listCollection by remember { mutableStateOf<TrackCollection?>(null) }
    var listPlayAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isListDetail = destination?.hasRoute<TrackListDestination>() == true ||
        destination?.hasRoute<ChartDestination>() == true
    // 网页登录是全屏页：不挂 dock，否则迷你条/底部导航会盖住官方登录页、挡住底部操作。
    val isWebLogin = destination?.hasRoute<WebLogin>() == true

    // dock 总高（dp）：PlayerDock 上报，供 Profile 展开壳底部让位。
    var islandHeightDp by remember { mutableStateOf(0f) }

    // 展开壳（探索大封面 / Profile 面板）是否打开：打开时收起底部导航，只留迷你播放条。
    var shellOpen by remember { mutableStateOf(false) }

    // 详情页浮现起点：进入被点对象前记录其窗口矩形，供 RevealLayer 用；返回/无值则居中浮现。
    var navOriginRect by remember { mutableStateOf<Rect?>(null) }
    // 导航方向：决定「谁在浮现」。前进时**子页**从被点对象长出、父页原地留底；返回时
    // **子页**沿原路收回、父页直接露底。若不分方向，父页会在子页进入时同时缩回去（两处
    // 浮现打架）。openWithOrigin=前进，goBack=返回。
    var navForward by remember { mutableStateOf(true) }
    fun openWithOrigin(rect: Rect?, navigate: () -> Unit) {
        navOriginRect = rect?.takeIf { it.width > 0f && it.height > 0f }
        navForward = true
        navigate()
    }
    fun goBack() {
        navForward = false
        navController.popBackStack()
    }

    // 评论浮层：点播放页「评论」按钮打开，**盖在播放页之上、播放页保持打开**（不再收起）。
    // 以「从评论按钮处浮现」的 origin-reveal 进入/退出；关闭后回到播放页。
    // 系统返回键由浮层内的 BackHandler 拦截。
    var commentsSongId by remember { mutableStateOf<String?>(null) }
    // 收起期间仍要渲染内容：id/起点保留到浮现退场跑完（由下面的 derivedStateOf 控制卸载）。
    var commentsShownId by remember { mutableStateOf("") }
    var commentsOrigin by remember { mutableStateOf<Rect?>(null) }
    // 内容首次布局后才起手（否则重列表组合吃掉一两帧，第一个画出来的帧已长大）。
    var commentsArmed by remember { mutableStateOf(false) }
    LaunchedEffect(commentsSongId) { if (commentsSongId == null) commentsArmed = false }
    val commentsProgress = animateFloatAsState(
        targetValue = if (commentsSongId != null && commentsArmed) 1f else 0f,
        animationSpec = if (commentsSongId != null && commentsArmed) Motion.revealEnter() else Motion.revealExit(),
        label = "commentsReveal",
    )
    // 只在「可见或退场未结束」时组合浮层；derivedStateOf 保证逐帧进度变化不触发重组。
    val commentsLayerVisible by remember {
        derivedStateOf { commentsSongId != null || commentsProgress.value > 0.01f }
    }

    // 播放页状态：常驻 dock 与全屏播放页合体（同一组件/同一份 progress）。
    // 点击迷你条/列表项 → state.open() 整页弹出；迷你条上滑 1:1 跟手由组件内手势驱动。
    val dockState = rememberPlayerDockState()
    fun openPlayer() {
        // 列表项点歌：直接盖满全屏（两段式的第二段）。
        dockState.open(toFull = true)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 浮现目标的中性转场时长：**必须 ≥ RevealLayer 自己的动画**，否则 AnimatedContent
        // 会先结束、把下层来源页撤掉，而浮现窗口还没铺满 → 露背景。多给一点余量。
        val revealInMs = Motion.RevealEnterMs + Motion.RevealArmDelayMs + 120
        val revealOutMs = Motion.RevealExitMs + 120
        // 导航转场：克制的 fade + 4% 屏高轻位移（不与展开壳的“卡片生长”抢戏）。
        // 旧实现无自定义转场，走库默认的长 fade，且 tab 间切换无位移反馈。
        // 进入 220ms；退出 90ms 快速让位；pop 逆向稍慢收回。
        // 注意 tween 泛型随上下文推断：fadeIn 是 Float，slide*Vertically 是 Int。
        //
        // ⚠️ 浮现目标的**退出**转场必须用「非零」alpha 变化（这里取 0.999f）撑住时长。
        // 曾写成 `targetAlpha = 1f`（起点==终点），Compose 的 Transition 会判定该动画**瞬时
        // 完成**，于是退出页在 pop 的同一帧就被移除——羽化层根本没机会逐帧收回，观感就是
        // 「关闭没有动画」。0.999f 肉眼不可见，却是一个真实变化，能把退出页保活到窗口收回。
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Home,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (targetState.isRevealTarget()) {
                        // 详情页进入：视觉全交给 RevealLayer。这里让**进入页**全程不透明即可。
                        fadeIn(tween(revealInMs), initialAlpha = 1f)
                    } else {
                        fadeIn(tween(Motion.NavEnterMs, easing = Motion.Standard)) +
                            slideInVertically(tween(Motion.NavEnterMs, easing = Motion.Standard)) { it / 24 }
                    }
                },
                exitTransition = {
                    if (targetState.isRevealTarget()) {
                        // 来源页要在浮现窗口长满屏前一直铺底：0.999f 撑住 revealInMs。
                        fadeOut(tween(revealInMs), targetAlpha = 0.999f)
                    } else {
                        fadeOut(tween(Motion.NavExitMs, easing = Motion.Standard))
                    }
                },
                popEnterTransition = {
                    if (initialState.isRevealTarget()) {
                        // 从详情返回：下层页需在浮现退场期间一直铺底。
                        fadeIn(tween(revealOutMs), initialAlpha = 0.999f)
                    } else {
                        fadeIn(tween(Motion.NavEnterMs, easing = Motion.Standard)) +
                            slideInVertically(tween(Motion.NavEnterMs, easing = Motion.Standard)) { -it / 24 }
                    }
                },
                popExitTransition = {
                    if (initialState.isRevealTarget()) {
                        // 关键：退出子页必须保留 revealOutMs，羽化收回才看得到（见上注）。
                        fadeOut(tween(revealOutMs), targetAlpha = 0.999f)
                    } else {
                        fadeOut(tween(Motion.NavPopExitMs, easing = Motion.Standard)) +
                            slideOutVertically(tween(Motion.NavPopExitMs, easing = Motion.Standard)) { it / 24 }
                    }
                },
            ) {
                composable<Home> {
                    HomeScreen(
                        onOpenPlayer = ::openPlayer,
                        onWebLogin = { navController.navigate(WebLogin) },
                        islandHeight = islandHeightDp,
                        onShellOpenChange = { shellOpen = it },
                        shared = this@SharedTransitionLayout,
                    )
                }
                composable<Library> {
                    LibraryScreen(
                        onOpenChart = { id, name, origin ->
                            openWithOrigin(origin) {
                                navController.navigate(ChartDestination(chartId = id, name = name))
                            }
                        },
                    )
                }
                composable<ChartDestination> { entry ->
                    val args = entry.toRoute<ChartDestination>()
                    val origin = remember { navOriginRect }
                    val (revealProgress, armReveal) = rememberRevealProgress(navForward)
                    RevealLayer(
                        fromRect = origin,
                        progress = revealProgress,
                        onFirstLayout = armReveal,
                    ) {
                    val listVm: TrackListViewModel = hiltViewModel()
                    val listState by listVm.uiState.collectAsStateWithLifecycle()
                    val listCol = (listState as? TrackListUiState.Ready)?.collection
                    LaunchedEffect(listCol) { listCollection = listCol }
                    LaunchedEffect(listCol) {
                        listPlayAll = { listCol?.let(listVm::onPlayAll) }
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            listCollection = null
                            listPlayAll = null
                            listActionsOffscreen = false
                        }
                    }
                    TrackListScreen(
                        source = "chart",
                        id = args.chartId,
                        title = args.name,
                        onBack = { goBack() },
                        onOpenPlayer = ::openPlayer,
                        onActionsOffscreen = { listActionsOffscreen = it },
                    )
                    }
                }
                composable<Search> {
                    SearchScreen(
                        onOpenPlayer = ::openPlayer,
                        onOpenTracks = { source, id, title, origin ->
                            openWithOrigin(origin) {
                                navController.navigate(TrackListDestination(source, id, title))
                            }
                        },
                        onOpenArtist = { id, name, avatarUrl, origin ->
                            openWithOrigin(origin) {
                                navController.navigate(ArtistDestination(id, name, avatarUrl))
                            }
                        },
                        onOpenRadio = { id, _, origin ->
                            openWithOrigin(origin) { navController.navigate(RadioDestination(id)) }
                        },
                        islandHeight = islandHeightDp,
                        shared = this@SharedTransitionLayout,
                        avScope = this,
                    )
                }
                composable<Profile> {
                    ProfileScreen(
                        onOpenTracks = { source, id, title ->
                            // Profile 卡片自有展开壳；其余（喜欢/已购/歌单网格）无起点 → 居中浮现。
                            openWithOrigin(null) {
                                navController.navigate(TrackListDestination(source, id, title))
                            }
                        },
                        onWebLogin = { navController.navigate(WebLogin) },
                        onOpenPlayer = ::openPlayer,
                        islandHeight = islandHeightDp,
                        onShellOpenChange = { shellOpen = it },
                        vm = profileVm,
                    )
                }
                composable<WebLogin> {
                    // 复用 Activity 作用域的 ProfileViewModel（与 Profile 页同一实例）：
                    // 登录成功 loadProfile 直接更新该实例，返回 Profile 页即已刷新。
                    WebLoginScreen(
                        onDone = { navController.popBackStack() },
                        onBack = { goBack() },
                        vm = profileVm,
                    )
                }
                composable<TrackListDestination> { entry ->
                    val args = entry.toRoute<TrackListDestination>()
                    val origin = remember { navOriginRect }
                    val (revealProgress, armReveal) = rememberRevealProgress(navForward)
                    RevealLayer(
                        fromRect = origin,
                        progress = revealProgress,
                        onFirstLayout = armReveal,
                    ) {
                    val listVm: TrackListViewModel = hiltViewModel()
                    val listState by listVm.uiState.collectAsStateWithLifecycle()
                    val listCol = (listState as? TrackListUiState.Ready)?.collection
                    LaunchedEffect(listCol) { listCollection = listCol }
                    LaunchedEffect(listCol) {
                        listPlayAll = { listCol?.let(listVm::onPlayAll) }
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            listCollection = null
                            listPlayAll = null
                            listActionsOffscreen = false
                        }
                    }
                    TrackListScreen(
                        source = args.source,
                        id = args.id,
                        title = args.title,
                        onBack = { goBack() },
                        onOpenPlayer = ::openPlayer,
                        onActionsOffscreen = { listActionsOffscreen = it },
                    )
                    }
                }
                composable<ArtistDestination> { entry ->
                    val args = entry.toRoute<ArtistDestination>()
                    val avScope = this
                    val shared = this@SharedTransitionLayout
                    val origin = remember { navOriginRect }
                    val (revealProgress, armReveal) = rememberRevealProgress(navForward)
                    RevealLayer(
                        fromRect = origin,
                        progress = revealProgress,
                        onFirstLayout = armReveal,
                    ) {
                    ArtistScreen(
                        id = args.id,
                        name = args.name,
                        avatarUrl = args.avatarUrl,
                        onBack = { goBack() },
                        onOpenPlayer = ::openPlayer,
                        onOpenAlbum = { albumId, title, rect ->
                            openWithOrigin(rect) {
                                navController.navigate(TrackListDestination("album", albumId, title))
                            }
                        },
                        islandHeight = islandHeightDp,
                        shared = shared,
                        avScope = avScope,
                    )
                    }
                }
                composable<RadioDestination> { entry ->
                    val args = entry.toRoute<RadioDestination>()
                    val origin = remember { navOriginRect }
                    val (revealProgress, armReveal) = rememberRevealProgress(navForward)
                    RevealLayer(
                        fromRect = origin,
                        progress = revealProgress,
                        onFirstLayout = armReveal,
                    ) {
                    PodcastScreen(
                        id = args.id,
                        onBack = { goBack() },
                        onOpenPlayer = ::openPlayer,
                        islandHeight = islandHeightDp,
                    )
                    }
                }
            }
        }

        // 常驻 dock + 全屏播放页（合体，单点挂载）：覆盖在内容层之上。
        // 网页登录页是全屏 WebView，不挂 dock，避免遮挡官方页面操作。
        if (!isWebLogin) {
            PlayerDock(
                player = player,
                state = dockState,
                selected = selectedTab,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                actionVisible = isListDetail && listActionsOffscreen,
                navVisible = !shellOpen,
                onPlayAll = { listPlayAll?.invoke() },
                onPlaceholderAction = {
                    android.widget.Toast.makeText(context, "开发中", android.widget.Toast.LENGTH_SHORT).show()
                },
                onComments = { rect ->
                    // 播放页盖在导航图之上：评论以浮层形式再盖在播放页之上，
                    // 播放页保持打开；以评论按钮矩形为起点浮现。
                    player.currentMediaItem?.mediaId?.let { id ->
                        commentsShownId = id
                        commentsOrigin = rect.takeIf { it.width > 0f && it.height > 0f }
                        commentsSongId = id
                    }
                },
                onIslandHeightChange = { islandHeightDp = it },
            )
        }

        // ---- 评论浮层（最上层）----
        // 置于 PlayerDock 之后：绘制顺序在播放页之上；播放页仍在组合中、保持打开。
        // BackHandler 在 CommentsScreen 内，晚于 PlayerPage 注册，返回键优先关评论。
        if (commentsLayerVisible && commentsShownId.isNotEmpty()) {
            RevealLayer(
                fromRect = commentsOrigin,
                progress = commentsProgress,
                onFirstLayout = { commentsArmed = true },
            ) {
                CommentsScreen(
                    songId = commentsShownId,
                    onBack = { commentsSongId = null },
                    islandHeight = 0f,
                )
            }
        }
    }
}
