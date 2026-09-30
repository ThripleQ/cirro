package com.thripleq.nume.ui.screens

import com.thripleq.nume.ui.theme.NumeShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.PlaylistSummary
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.CoverExpandShell
import com.thripleq.nume.ui.components.LocalShellHeroAlpha
import com.thripleq.nume.ui.components.LocalShellSettled
import com.thripleq.nume.ui.components.ShellPanel
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.profile.ProfileUiState
import com.thripleq.nume.ui.profile.ProfileViewModel
import com.thripleq.nume.ui.theme.Motion
import com.valentinilk.shimmer.shimmer

/**
 * 我的页：2×2 大卡（喜欢的音乐 / 已购 / 创建的歌单 / 收藏的歌单），风格同探索页大封面卡。
 * 每张卡点开都是「大卡 → 全屏面板」，封面 morph 到内容里的 banner 封面。
 * 喜欢的音乐 / 已购是曲目列表；创建 / 收藏是歌单网格面板，点网格内的歌单再进入该歌单的曲目列表。
 *
 * 两条壳实现并存、由 [Motion.SharedShellEnabled] 切换：
 * - 官方共享元素：[ShellPanel] + [shellSharedCover]（与探索页/歌手头像同一套语义，只转封面）。
 * - 自研 [CoverExpandShell]：[ProfilePanelLegacy]，整壳几何动画（保留不删，开关关闭时启用）。
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
) {
    // 避让必须放在滚动内容内部（同 TrackListScreen 的 contentPadding 做法）：
    // 放在外层 padding 会在岛背后留一条永久空白带，卡片进不去、岛像贴在画布上。
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        when (val s = state) {
            ProfileUiState.Loading -> ProfileSkeleton()
            is ProfileUiState.Error -> ErrorRow(onRetry)
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
private sealed interface ProfilePanel {
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

/* ── header states ─────────────────────────────────────── */

/** 我的页骨架：与已登录内容同构——用户卡 + 四颗胶囊（内部图标/标题/尾部占位）。 */
@Composable
private fun ProfileSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(widthFraction = 0.4f, height = 18.dp)
                SkeletonLine(widthFraction = 0.22f, height = 12.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        repeat(4) {
            SkeletonCapsule()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkeletonCapsule() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NumeShape.Card)
            // 骨架家族统一用 surfaceVariant（与 Skeleton.kt 一致）；Highest 是播放页壳的层次
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(36.dp), NumeShape.CardSmall)
        Spacer(Modifier.width(14.dp))
        SkeletonLine(widthFraction = 0.34f, height = 16.dp)
        Spacer(Modifier.weight(1f))
        SkeletonBox(Modifier.size(24.dp), CircleShape)
    }
}

@Composable
private fun ErrorRow(onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 24.dp)) {
        Text("加载失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LoggedOutContent(onLogin: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            LoginCard(onLogin)
            Spacer(Modifier.height(16.dp))

            // 未登录也摆出与已登录同构的 2×2 大卡，点击引导登录。
            ProfileCardGrid(
                entries = listOf(
                    ProfileCardEntry(Icons.Filled.Favorite, "喜欢的音乐", "登录后查看", null),
                    ProfileCardEntry(Icons.Filled.ShoppingCart, "已购", "登录后查看", null),
                    ProfileCardEntry(Icons.Filled.List, "创建的歌单", "登录后查看", null),
                    ProfileCardEntry(Icons.Filled.Star, "收藏的歌单", "登录后查看", null),
                ),
                onClick = { _, _ -> onLogin() },
                shared = null,
                avScope = null,
            )
        }
    }
}

@Composable
private fun LoginCard(onLogin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onLogin() },
        shape = NumeShape.Card,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("登录网易云", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用官方网页登录,解锁喜欢 / 已购 / 歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "登录",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/* ── logged-in content ─────────────────────────────────── */

@Composable
private fun LoggedInContent(
    data: ProfileData,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onOpenPanel: (ProfilePanel, Rect?) -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    // 强制 LocalContentColor = onSurface, 兜底所有未显式指定 color 的 Text
    // (Material You 在某些设备/壁纸下派生的 onBackground 偏深, 不指定 color
    // 的 Text 会显示成接近背景的颜色, 在深色主题下看不清)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            UserCard(data.account)
            Spacer(Modifier.height(8.dp))

            // 卡片与 hero/banner 共用同一份数据行文案：数字只算一次，卡片、hero、列表 banner
            // 三处一致，展开全程数量可见、不闪。
            val likedMeta = "${data.likedCount} 首"
            val purchasedMeta = "${data.purchasedSongCount + data.purchasedAlbums.size} 项"
            val createdMeta = "${data.createdPlaylists.size} 个歌单"
            val subscribedMeta = "${data.subscribedPlaylists.size} 个歌单"

            // 2×2 大卡：与探索页大封面卡同风格（封面 + 底部标题/数量 + 内容属性水印），
            // 点击从该卡位置撑开对应全屏面板。
            val panels = listOf(
                ProfilePanel.Tracks(
                    "liked", "", "喜欢的音乐",
                    coverUrl = data.likedCoverUrl,
                    icon = Icons.Filled.Favorite,
                    meta = likedMeta,
                ),
                ProfilePanel.Tracks(
                    "purchased", "", "已购",
                    coverUrl = data.purchasedCoverUrl,
                    icon = Icons.Filled.ShoppingCart,
                    meta = purchasedMeta,
                ),
                ProfilePanel.Playlists(
                    "创建的歌单",
                    data.createdPlaylists,
                    coverUrl = data.createdPlaylists.firstOrNull()?.coverUrl,
                    icon = Icons.Filled.List,
                    meta = createdMeta,
                    shellKey = "shell:profile:created",
                ),
                ProfilePanel.Playlists(
                    "收藏的歌单",
                    data.subscribedPlaylists,
                    coverUrl = data.subscribedPlaylists.firstOrNull()?.coverUrl,
                    icon = Icons.Filled.Star,
                    meta = subscribedMeta,
                    shellKey = "shell:profile:subscribed",
                ),
            )
            val entries = listOf(
                ProfileCardEntry(
                    Icons.Filled.Favorite,
                    "喜欢的音乐",
                    likedMeta,
                    data.likedCoverUrl,
                    shellKey = panels[0].shellKey,
                ),
                ProfileCardEntry(
                    Icons.Filled.ShoppingCart,
                    "已购",
                    purchasedMeta,
                    data.purchasedCoverUrl,
                    shellKey = panels[1].shellKey,
                ),
                ProfileCardEntry(
                    Icons.Filled.List,
                    "创建的歌单",
                    createdMeta,
                    data.createdPlaylists.firstOrNull()?.coverUrl,
                    shellKey = panels[2].shellKey,
                ),
                ProfileCardEntry(
                    Icons.Filled.Star,
                    "收藏的歌单",
                    subscribedMeta,
                    data.subscribedPlaylists.firstOrNull()?.coverUrl,
                    shellKey = panels[3].shellKey,
                ),
            )
            ProfileCardGrid(
                entries = entries,
                onClick = { i, rect -> onOpenPanel(panels[i], rect) },
                shared = shared,
                avScope = avScope,
            )
        }
    }
}

/** 「我的」大卡的展示数据。 */
private data class ProfileCardEntry(
    val icon: ImageVector,
    val title: String,
    val count: String,
    val coverUrl: String?,
    /** 官方共享元素键（与对应面板一致）；null = 本卡无面板（未登录占位）。 */
    val shellKey: String? = null,
)

/** 2×2 大卡网格：每行两张，行间距 12dp；奇数个时末行留空（[ProfileBigCard] 自带 weight）。 */
@Composable
private fun ProfileCardGrid(
    entries: List<ProfileCardEntry>,
    onClick: (Int, Rect?) -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
) {
    Column(Modifier.fillMaxWidth()) {
        entries.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { col, e ->
                    ProfileBigCard(
                        icon = e.icon,
                        title = e.title,
                        count = e.count,
                        coverUrl = e.coverUrl,
                        shared = shared,
                        avScope = avScope,
                        sharedKey = e.shellKey,
                        onClick = { rect -> onClick(rowIndex * 2 + col, rect) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 「我的」页 2×2 大卡：封面（缺则 secondaryContainer 底）+ 内容属性水印图标 +
 * 底部渐变遮罩上的标题/数量。视觉与探索页大封面卡（[com.thripleq.nume.ui.components.BigCoverVisual]）
 * 同参数（0.5 起渐变、0.66 黑、白字），保证两页风格统一。
 *
 * 点击回调携带卡片的窗口坐标 Rect，作为展开壳的起点，收起尾帧与卡片精确重合。
 */
@Composable
private fun ProfileBigCard(
    icon: ImageVector,
    title: String,
    count: String,
    coverUrl: String?,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    sharedKey: String?,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    // 直接复用探索页大卡组件：封面 + 底部渐变 + 白字，外加内容属性水印（缺封面即兜底主视觉）。
    // 同一份视觉也让面板 banner / hero 传同一 watermarkIcon，三处完全一致。
    BigCoverVisual(
        coverUrl = coverUrl,
        name = title,
        modifier = modifier
            .aspectRatio(1f)
            .onGloballyPositioned { coords ->
                rect = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
            }
            // 官方容器变换（sharedBounds）：与面板 banner 封面同 key，框架把这张封面从
            // 卡片位置/尺寸 morph 到 banner，内容按 scaleToBounds 缩放（不逐帧重排）。
            .then(
                if (shared != null && avScope != null && sharedKey != null) {
                    Modifier.shellSharedCover(shared, avScope, sharedKey)
                } else {
                    Modifier
                },
            )
            .clip(NumeShape.Card)
            .clickable { onClick(rect) },
        meta = count,
        watermarkIcon = icon,
    )
}

@Composable
private fun UserCard(account: Account) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape)) {
            if (account.avatarUrl != null) {
                val context = LocalContext.current
                val model = remember(account.avatarUrl) {
                    ImageRequest.Builder(context).data(account.avatarUrl).size(128).build()
                }
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = account.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.nickname,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.vipType > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 通用全屏面板：复用 [CoverExpandShell]，从大卡位置（[capsuleRect]）长成全屏，
 * hero 封面 morph 到内容里的 banner 封面（与探索页同一套观感与契约）。
 * 内容按 [target] 分派：曲目列表 → [TrackListScreen]；歌单网格 → [PlaylistGridPanel]。
 */
@Composable
private fun ProfilePanelLegacy(
    target: ProfilePanel,
    uid: String?,
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    bottomPadding: Dp,
    capsuleRect: Rect?,
    onDismiss: () -> Unit,
) {
    CoverExpandShell(
        fromRect = capsuleRect,
        coverUrl = target.coverUrl,
        title = target.title(),
        onDismiss = onDismiss,
        // hero 带卡片同一份数据行：否则展开时 hero 盖掉卡片，数量消失、结尾再冒出（闪）。
        meta = target.meta,
        watermarkIcon = target.icon,
    ) { onCoverReady ->
        when (target) {
            is ProfilePanel.Tracks -> {
                val src = if (uid != null && target.source == "liked") uid else target.id
                TrackListScreen(
                    source = target.source,
                    id = src,
                    title = target.title,
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    showTopBar = false,
                    // 壳内容：返回键交回 ExpandableShell 处理（本屏不再注册 BackHandler），
                    // 否则本屏后注册的 BackHandler 会抢在壳之前触发 onBack、跳过壳的收起动画。
                    backHandlerEnabled = false,
                    // 面板走「内容固定终态排版 + 壳裁剪露出」，封面内缩用常量，
                    // 使列表 measure 在展开动画期间被跳过（封面形变交给 hero）。
                    coverInsetFollowsShell = false,
                    onCoverReady = onCoverReady,
                    previewCoverUrl = target.coverUrl,
                    watermarkIcon = target.icon,
                    bottomPadding = bottomPadding,
                )
            }
            is ProfilePanel.Playlists -> PlaylistGridPanel(
                title = target.title,
                playlists = target.playlists,
                coverUrl = target.coverUrl,
                watermarkIcon = target.icon,
                onCoverReady = onCoverReady,
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
            )
        }
    }
}

private fun ProfilePanel.title(): String = when (this) {
    is ProfilePanel.Tracks -> title
    is ProfilePanel.Playlists -> title
}

/**
 * 官方容器变换版的面板内容：外壳/关闭键/scrim 由 [ShellPanel] 负责；封面 morph 由内容 banner
 * 挂 [shellSharedCover] 完成。与自研 [ProfilePanelLegacy] 并存，由 [Motion.SharedShellEnabled] 切换。
 */
@Composable
private fun ProfileSharedPanelContent(
    target: ProfilePanel,
    uid: String?,
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    bottomPadding: Dp,
    shared: SharedTransitionScope,
    avScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
) {
    // 共享元素版没有「壳进度」，但 TrackListScreen 仍靠 LocalShellSettled 把「切到真列表」
    // 推迟到入场动画（含封面 sharedBounds morph）结束之后，避免双层封面 / 动画期首次组合整列表。
    val settled = remember(avScope) {
        derivedStateOf {
            val t = avScope.transition
            t.currentState == EnterExitState.Visible && !t.isRunning
        }
    }
    CompositionLocalProvider(LocalShellSettled provides settled) {
        when (target) {
            is ProfilePanel.Tracks -> {
                val src = if (uid != null && target.source == "liked") uid else target.id
                TrackListScreen(
                    source = target.source,
                    id = src,
                    title = target.title,
                    onBack = onDismiss,
                    onOpenPlayer = onOpenPlayer,
                    showTopBar = false,
                    previewCoverUrl = target.coverUrl,
                    watermarkIcon = target.icon,
                    bottomPadding = bottomPadding,
                    coverSharedModifier = Modifier.shellSharedCover(shared, avScope, target.shellKey),
                )
            }
            is ProfilePanel.Playlists -> PlaylistGridPanel(
                title = target.title,
                playlists = target.playlists,
                coverUrl = target.coverUrl,
                watermarkIcon = target.icon,
                onCoverReady = {},
                onOpenTracks = onOpenTracks,
                bottomPadding = bottomPadding,
                coverSharedModifier = Modifier.shellSharedCover(shared, avScope, target.shellKey),
            )
        }
    }
}

/** 歌单网格面板内容：首个 banner 封面 + 全屏懒加载网格，点格子进歌单曲目列表。
 *  banner 位于 16dp 内缩、状态栏下 4dp（[CoverExpandShell] 的 hero 终点契约）。 */
@Composable
private fun PlaylistGridPanel(
    title: String,
    playlists: List<PlaylistSummary>,
    coverUrl: String?,
    watermarkIcon: ImageVector,
    onCoverReady: () -> Unit,
    onOpenTracks: (source: String, id: String, name: String) -> Unit,
    bottomPadding: Dp,
    /** 官方共享元素：附加到 banner 封面（与入口大卡同 key）；默认空即无共享元素。 */
    coverSharedModifier: Modifier = Modifier,
) {
    // LazyVerticalGrid 自带滚动，不再外包一层 verticalScroll + 全量 Column：
    // 歌单多时只组合可见格，避免每帧重排整棵树。
    // 展开动画期间 hero 正顶着封面：banner 与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "banner") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // 官方共享元素：与入口大卡封面同 key，面板一出现即可 morph。
                    .then(coverSharedModifier)
                    .clip(NumeShape.Card)
                    // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                    .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f },
            ) {
                BigCoverVisual(
                    coverUrl = coverUrl,
                    name = title,
                    modifier = Modifier.fillMaxSize(),
                    // 恒显示数量（含 0）：与卡片/hero 同文案，末尾交接不出现数据消失。
                    meta = "${playlists.size} 个歌单",
                    scrimTop = 0.35f,
                    scrimAlpha = 0.85f,
                    requestSize = 1024,
                    onLoadSuccess = onCoverReady,
                    watermarkIcon = watermarkIcon,
                )
            }
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无歌单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(playlists, key = { it.id }) { p ->
                PlaylistCell(
                    playlist = p,
                    onClick = { onOpenTracks("playlist", p.id, p.name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlaylistCell(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .numeEntrySurface(inset = 0.dp, vertical = 0.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(NumeShape.CardSmall),
        ) {
            // model 整体 remember：AsyncImagePainter 以 model 为 key，避免每次重组
            // 新建 ImageRequest 重走请求分发；按 320px（160dp 封面 @2x）尺寸请求。
            val context = LocalContext.current
            val model = remember(playlist.coverUrl) {
                playlist.coverUrl?.let {
                    ImageRequest.Builder(context).data(it).size(320).build()
                }
            }
            if (model != null) {
                val painter = rememberAsyncImagePainter(model)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${playlist.trackCount} 首",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

