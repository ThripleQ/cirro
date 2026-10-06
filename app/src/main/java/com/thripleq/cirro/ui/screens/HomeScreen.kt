package com.thripleq.cirro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.core.repo.Track
import com.thripleq.cirro.ui.components.LocalShellSettled
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.ShellPanel
import com.thripleq.cirro.ui.components.shellSharedCover
import com.thripleq.cirro.ui.home.HomeUiState
import com.thripleq.cirro.ui.home.HomeViewModel
import com.thripleq.cirro.ui.theme.Motion

/** 探索 tab（布局抄 kanade 主页，2026-10-04）：精选推荐 / 猜你喜欢的好歌 / 雷达歌单 / 场景音乐。
 *  精选推荐是功能卡横滑（图 + 左上角类型徽标 + 底部名称条），点开与歌单/榜单一样走
 *  胶囊伸展壳进全屏列表；猜你喜欢是单曲竖列表，点了直接播、不弹播放页。
 *
 *  [onOpenPlayer] 只服务于**面板里的列表**（[HomePanelContent] / [HomeExpandShell] → TrackListScreen
 *  的「播放全部」）；单曲行的点播不再需要它（2026-10-03 起点了只播、不进播放页）。 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onWebLogin: () -> Unit,
    islandHeight: Float = 0f,
    onShellOpenChange: (Boolean) -> Unit = {},
    shared: SharedTransitionScope? = null,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    var expand by remember { mutableStateOf<ExpandTarget?>(null) }
    val islandClearance = with(LocalDensity.current) { islandHeight.dp }
    // 滚动位置 hoist：容器变换用 AnimatedContent 在「网格 ↔ 面板」间切换，关闭面板时
    // 网格会重新组合——不 hoist 就会跳回顶部。
    val listState = rememberLazyListState()
    // 横滑列表状态同样 hoist（见 [HomeRowStates]）：面板开合会重组网格，留在内层会归零。
    val rowStates = rememberRowStates()
    val bottomPad = islandClearance + 16.dp

    // 展开壳打开时通知上层收起底部导航（保留迷你播放条）；离开页面时复位。
    val shellOpen = expand != null
    LaunchedEffect(shellOpen) { onShellOpenChange(shellOpen) }
    DisposableEffect(Unit) { onDispose { onShellOpenChange(false) } }

    // 页面重新可见（切回「探索」tab、从详情页返回）时问一句要不要取新数据。
    // 本页是导航目的地，切走会被移出组合、切回重新组合，所以这个 effect 每次可见都会重跑；
    // 但 VM 挂在回退栈上不销毁 —— 真正要不要发请求由 VM 里的冷却门决定
    // （core/util/RefreshGate：秒级的反复可见不发请求，几十秒后才真刷一次）。
    LaunchedEffect(Unit) { vm.onEnterVisible() }

    // 稳定的回调：开/关壳只改 expand，若 lambda 每次重组都新建会把整页列表（HomeContent）
    // 一起重组，产生尖峰帧。用 remember 固定后 expand 变化不会再重组底下列表。
    val onPlay = remember(vm) { vm::onPlayTrack }
    val onPlayFeatured = remember(vm) { vm::onPlayFeatured }
    val onRefresh = remember(vm) { { vm.load() } }
    val onExpand = remember { { t: ExpandTarget -> expand = t } }
    val onDismiss = remember { { expand = null } }

    if (Motion.SharedShellEnabled && shared != null) {
        // 官方容器变换：源卡片（CarouselRow 里的 BigCoverCard）与全屏面板挂同一个
        // key 的 sharedBounds，由框架把「同一个表面」从卡片 morph 到全屏。
        with(shared) {
            AnimatedContent(
                targetState = expand,
                transitionSpec = {
                    // 网格必须像歌手页搜索列表那样**真实淡出**（90ms），绝不能是 ExitTransition.None：
                    // None 会让退出内容在首帧就被判定完成、立刻移除，共享元素的「起点」随之消失
                    // → 框架匹配不到两端、封面直接闪现在 banner（就是「某些情况跳过动画」）。
                    // 网格底色与面板同为 surface，短暂的空档不可见。封面 morph 由 sharedBounds
                    // 主导（350ms），面板只跑 260ms 铺底。
                    if (targetState != null) {
                        fadeIn(tween(Motion.ShellPanelInMs, easing = Motion.EmphasizedDecelerate)) togetherWith
                            fadeOut(tween(Motion.NavExitMs, easing = Motion.Standard))
                    } else {
                        // 收起：网格快速铺底（接住飞回的封面），面板随后淡出。
                        fadeIn(tween(Motion.NavExitMs, easing = Motion.Standard)) togetherWith
                            fadeOut(tween(Motion.ShellPanelOutMs, easing = Motion.Emphasized))
                    }
                },
                label = "homeShell",
            ) { target ->
                val scope = this
                if (target == null) {
                    HomeBodyUi(
                        state = state,
                        listState = listState,
                        bottomPadding = bottomPad,
                        onPlay = onPlay,
                        onPlayFeatured = onPlayFeatured,
                        onExpand = onExpand,
                        onWebLogin = onWebLogin,
                        onRefresh = onRefresh,
                        rowStates = rowStates,
                        shared = shared,
                        avScope = scope,
                    )
                } else {
                    ShellPanel(onDismiss = onDismiss) {
                        HomePanelContent(
                            target = target,
                            bottomPadding = bottomPad,
                            onOpenPlayer = onOpenPlayer,
                            onDismiss = onDismiss,
                            shared = shared,
                            avScope = scope,
                        )
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            HomeBodyUi(
                state = state,
                listState = listState,
                bottomPadding = bottomPad,
                onPlay = onPlay,
                onPlayFeatured = onPlayFeatured,
                onExpand = onExpand,
                onWebLogin = onWebLogin,
                onRefresh = onRefresh,
                rowStates = rowStates,
                shared = null,
                avScope = null,
            )
            expand?.let { target ->
                HomeExpandShell(
                    target = target,
                    bottomPadding = bottomPad,
                    onOpenPlayer = onOpenPlayer,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

internal data class ExpandTarget(
    val source: String,
    val id: String,
    val title: String,
    val coverUrl: String?,
    val rect: Rect,
    /**
     * 这次开合要不要走封面 morph —— **点击那一刻**起点卡片是否完整可见
     * （判定见 [com.thripleq.cirro.ui.components.SharedSourceGuard]）。
     *
     * 决定必须记在**目标数据**上，不能只靠源端自己判断：关闭面板时网格会重新组合，
     * 源卡片是全新的组合（其可见性 state 回到默认「可见」），判断随之丢失；而目标
     * 一直带着这份数据，开关才算数。所以两头都挂闸：源端挡「起点被切」，目标端挡
     * 「这次压根不该飞」。
     */
    val morph: Boolean = true,
) {
    /** 容器变换共享键：源卡片与目标面板必须一致。 */
    fun shellKey(): Any = "shell:$source:$id"
}

/** 探索页主体（骨架/错误/内容）：官方容器变换与自研壳两条路径共用。 */
@Composable
private fun HomeBodyUi(
    state: HomeUiState,
    listState: LazyListState,
    rowStates: HomeRowStates,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayFeatured: (String) -> Unit,
    onExpand: (ExpandTarget) -> Unit,
    onWebLogin: () -> Unit,
    onRefresh: () -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    when (state) {
        HomeUiState.Loading -> HomeSkeleton(bottomPadding = bottomPadding)
        HomeUiState.Error -> CirroErrorState(onRetry = onRefresh)
        is HomeUiState.Ready -> HomeContent(
            data = state,
            bottomPadding = bottomPadding,
            onPlay = onPlay,
            onPlayFeatured = onPlayFeatured,
            onExpand = onExpand,
            onWebLogin = onWebLogin,
            onRefresh = onRefresh,
            listState = listState,
            rowStates = rowStates,
            shared = shared,
            avScope = avScope,
        )
    }
}

/** 探索页横滑列表的滚动状态，hoist 到 [HomeScreen]（见 [HomeRowStates] 注释）。 */
@Composable
private fun rememberRowStates(): HomeRowStates = HomeRowStates(
    featured = rememberLazyListState(),
    guess = rememberLazyListState(),
    radar = rememberLazyListState(),
    scene = rememberLazyListState(),
)

/** 官方容器变换版的面板内容：直接渲染曲目列表（面板外壳/关闭键/scrim 由 [ShellPanel] 负责）。
 *  封面的 morph 由 [TrackListScreen] 的 banner 封面挂 [shellSharedCover] 完成（同歌手头像）。 */
@Composable
private fun HomePanelContent(
    target: ExpandTarget,
    bottomPadding: Dp,
    onOpenPlayer: () -> Unit,
    onDismiss: () -> Unit,
    shared: SharedTransitionScope,
    avScope: AnimatedVisibilityScope,
) {
    // 共享元素版没有「壳进度」概念，但 TrackListScreen 仍靠 LocalShellSettled 把「切到真列表」
    // 推迟到入场动画结束之后。这里把它接上**面板自己的 AnimatedVisibilityScope**：
    // 面板入场转场跑完（含封面的 sharedBounds morph）前保持骨架。
    //
    // 这一步是「两个大封面叠着」的根治：共享 morph 期间挂共享 modifier 的是**骨架封面**
    // （面板里第一个存在的封面宿主）；若此时数据已到、列表封面也在正常绘制，且它不挂
    // 共享 modifier，就会同时出现「overlay 里飞的封面」+「banner 位置自己画的封面」两份。
    // 压到 morph 结束后再切列表（骨架封面的占位几何与 banner 封面一致），交接瞬间无感，
    // 也顺带避开动画期间首次组合整张列表的开销。
    val settled = remember(avScope) {
        derivedStateOf {
            val t = avScope.transition
            t.currentState == EnterExitState.Visible && !t.isRunning
        }
    }
    CompositionLocalProvider(LocalShellSettled provides settled) {
        TrackListScreen(
            source = target.source,
            id = target.id,
            title = target.title,
            onBack = onDismiss,
            onOpenPlayer = onOpenPlayer,
            showBackButton = false,
            previewCoverUrl = target.coverUrl,
            bottomPadding = bottomPadding,
            // target.morph = false（点它时起点封面被顶部圆角纸切着）→ **不挂**共享元素：
            // 挂上就有 counterpart，overlay 里那份不受裁切的封面会画在纸上方飞出来。
            // 不挂则框架匹配不到两端，面板与封面各自淡入淡出：少了飞行，但没有错动画。
            coverSharedModifier = if (target.morph) {
                Modifier.shellSharedCover(shared, avScope, target.shellKey())
            } else {
                Modifier
            },
        )
    }
}


