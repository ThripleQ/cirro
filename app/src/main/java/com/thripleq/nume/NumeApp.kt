package com.thripleq.nume

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import com.thripleq.nume.ui.playerbar.BottomTab
import com.thripleq.nume.ui.playerbar.PlayerDock
import com.thripleq.nume.ui.playerbar.rememberPlayerDockState
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import com.thripleq.nume.ui.screens.HomeScreen
import com.thripleq.nume.ui.screens.LibraryScreen
import com.thripleq.nume.ui.screens.ProfileScreen
import com.thripleq.nume.ui.screens.SearchScreen
import com.thripleq.nume.ui.screens.TrackListScreen
import com.thripleq.nume.ui.screens.WebLoginScreen
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

    // 播放页状态：常驻 dock 与全屏播放页合体（同一组件/同一份 progress）。
    // 点击迷你条/列表项 → state.open() 整页弹出；迷你条上滑 1:1 跟手由组件内手势驱动。
    val dockState = rememberPlayerDockState()
    fun openPlayer() {
        // 列表项点歌：直接盖满全屏（两段式的第二段）。
        dockState.open(toFull = true)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 导航转场：克制的 fade + 4% 屏高轻位移（不与展开壳的“卡片生长”抢戏）。
        // 旧实现无自定义转场，走库默认的长 fade，且 tab 间切换无位移反馈。
        // 进入 220ms；退出 90ms 快速让位；pop 逆向稍慢收回。
        // 注意 tween 泛型随上下文推断：fadeIn 是 Float，slide*Vertically 是 Int。
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 24 }
            },
            exitTransition = { fadeOut(tween(90, easing = LinearEasing)) },
            popEnterTransition = {
                fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 24 }
            },
            popExitTransition = {
                fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it / 24 }
            },
        ) {
            composable<Home> {
                HomeScreen(
                    onOpenPlayer = ::openPlayer,
                    onWebLogin = { navController.navigate(WebLogin) },
                    islandHeight = islandHeightDp,
                    onShellOpenChange = { shellOpen = it },
                )
            }
            composable<Library> {
                LibraryScreen(
                    onOpenChart = { id, name ->
                        navController.navigate(ChartDestination(chartId = id, name = name))
                    },
                )
            }
            composable<ChartDestination> { entry ->
                val args = entry.toRoute<ChartDestination>()
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
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = ::openPlayer,
                    onActionsOffscreen = { listActionsOffscreen = it },
                )
            }
            composable<Search> {
                SearchScreen(
                    onOpenPlayer = ::openPlayer,
                    onOpenTracks = { source, id, title ->
                        navController.navigate(TrackListDestination(source, id, title))
                    },
                    onPlaceholder = {
                        android.widget.Toast.makeText(context, "开发中", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    islandHeight = islandHeightDp,
                )
            }
            composable<Profile> {
                ProfileScreen(
                    onOpenTracks = { source, id, title ->
                        navController.navigate(TrackListDestination(source, id, title))
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
                    onBack = { navController.popBackStack() },
                    vm = profileVm,
                )
            }
            composable<TrackListDestination> { entry ->
                val args = entry.toRoute<TrackListDestination>()
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
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = ::openPlayer,
                    onActionsOffscreen = { listActionsOffscreen = it },
                )
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
                onIslandHeightChange = { islandHeightDp = it },
            )
        }
    }
}
