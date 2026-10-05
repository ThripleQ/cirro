package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.Album
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.ui.components.ArtistAvatarSize
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.NumeArt
import com.thripleq.nume.ui.components.NumeArtwork
import com.thripleq.nume.ui.components.NumeMediaRow
import com.thripleq.nume.ui.components.SharedSourceGuard
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeShape

/* ──────────────────────────────────────────────────────────────────
 * 「我的」页 v3（2026-10-05）
 *
 * 版式（自上而下）：
 *   1. 账号抬头：一行，小圆头像 + 昵称（+ VIP）。**不带卡底、不带数据行**
 *      —— 旧版是「模糊放大头像铺底 + 大字昵称 + N 首·N 项·N 个歌单」整块
 *      独占第一屏，用户判定为「太丑，直接删掉」。
 *   2. 「喜欢的音乐」全宽横幅主卡（21:10）：最高频入口单独放大，封面即主视觉。
 *   3. 「已购」**原地展开**卡：点一下展开出「单曲 / 专辑」，
 *      「单曲」再展开一层原地铺曲目，「专辑」进全屏大卡网格。
 *   4. 创建 / 收藏两张紧凑行卡（小封面 + 数量 + chevron）→ 全屏面板。
 *
 * 壳契约：liked / created / subscribed 三个 shellKey 与起点 rect 测量不变
 * （rect 仍挂在封面 onGloballyPositioned，面板路由 ProfilePanels 零改动）；
 * 已购与已购专辑这两条链**不挂共享元素**（前者不打开面板，后者起点是个图标）。
 *
 * 动效（参数集中在 [ProfileMotion]，真机上不满意只调这一处）：
 * 入场 stagger 用 Material 运动规范推荐区间（间隔 45ms、时长 280ms、
 * EmphasizedDecelerate、位移 ≤ 容器 1/6）——克制起步，不盲调。
 * ────────────────────────────────────────────────────────────────── */

/** 入场动效参数集中地（盲调无真机反馈的教训：参数只在一处改）。 */
private object ProfileMotion {
    /** 每块之间的入场间隔（Material 规范 40-60ms 区间取中）。 */
    const val STAGGER_MS = 45

    /** 单块入场时长。 */
    const val ENTER_MS = 280

    /** 头像按压回弹的 spring 刚度（偏紧，回弹快而不飘）。 */
    const val PRESS_STIFFNESS = Spring.StiffnessMediumLow
}

/**
 * 块级入场：淡入 + 轻微上浮，delay 按 [index] 递进。
 * [MutableTransitionState] 保证首次组合即从 0 起播（不走「先布局后动画」的
 * AnimatedVisibility(visible=) 惯用陷阱——后者首帧是目标态）。
 */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    val delay = index * ProfileMotion.STAGGER_MS
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(
            animationSpec = tween(ProfileMotion.ENTER_MS, delay, Motion.EmphasizedDecelerate),
        ) + slideInVertically(
            animationSpec = tween(ProfileMotion.ENTER_MS, delay, Motion.EmphasizedDecelerate),
            initialOffsetY = { it / 6 },
        ),
    ) {
        content()
    }
}

/** 按压缩放 + 点击：0.97 + 弹簧回弹（spring 而非 tween，松手有物理感）。
 * 只挂在有真实动作的入口卡上——无动作的纯装饰按压是假反馈，不放头像上。 */
@Composable
private fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = ProfileMotion.PRESS_STIFFNESS),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/* ── 账号抬头 ───────────────────────────────────────────────── */

/**
 * 顶部账号行（2026-10-05 取代杂志版 hero）。
 *
 * 旧 hero 是「模糊放大头像铺底 + 上下渐隐带 + 96dp 圆头像 + displaySmall 大字昵称 +
 * 「N 首 · N 项 · N 个歌单」数据行」，整块独占第一屏。用户判定为「太丑，直接删掉」
 * —— 收成**一行**：小圆头像 + 昵称（+ VIP 标）。
 *
 * 两处刻意不做：
 * - **不要数据行**。同一批数字在下面每张卡的副标题里各有一份
 *   （N 首 / N 项 / N 个歌单），抬头再来一行汇总是纯重复。
 * - **不铺卡底**。铺了它就是「又一张入口卡」，与下面的真入口卡抢层级；
 *   它只是一条抬头，左内缩对齐行卡内容即可。
 */
@Composable
private fun ProfileHeaderRow(account: Account) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        NumeArtwork(
            url = account.avatarUrl,
            contentDescription = account.nickname,
            modifier = Modifier
                .size(NumeArt.AvatarMd)
                .clip(CircleShape),
            size = NumeArt.AvatarMd,
            shape = CircleShape,
            requestSize = ArtistAvatarSize,
            fallbackIcon = Icons.Filled.AccountCircle,
            fallbackIconSize = 40.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.nickname,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (account.vipType > 0) VipBadge()
    }
}

@Composable
private fun VipBadge() {
    Text(
        "VIP",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(NumeShape.Chip)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/* ── 入口卡 ─────────────────────────────────────────────────── */

/** 「喜欢的音乐」全宽横幅主卡（最高频入口，单独放大成主视觉）。 */
@Composable
private fun LikedHeroCard(
    coverUrl: String?,
    meta: String,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    guard: SharedSourceGuard,
    sharedKey: String?,
    /** morphable = 封面此刻完整可见（点击时一并交出去，决定这次开合飞不飞）。 */
    onClick: (Rect?, morphable: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    // 封面此刻是否**完整**落在滚动视口里（被纸的顶边切掉一段就是 false）——
    // 被切时不挂共享元素：overlay 里飞的那份不受裁切，会画在纸上方
    // （见 [SharedSourceGuard]）。只在布尔翻转时写 state，滚动途中不重组。
    var shareable by remember { mutableStateOf(true) }
    BigCoverVisual(
        coverUrl = coverUrl,
        name = "喜欢的音乐",
        meta = meta,
        watermarkIcon = Icons.Filled.Favorite,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(21f / 10f)
            .onGloballyPositioned { coords ->
                val r = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                rect = r
                val ok = guard.isFullyVisible(r)
                if (ok != shareable) shareable = ok
            }
            .then(
                if (shared != null && avScope != null && sharedKey != null && shareable) {
                    Modifier.shellSharedCover(shared, avScope, sharedKey)
                } else {
                    Modifier
                },
            )
            .clip(NumeShape.Card)
            .pressScale { onClick(rect, shareable) },
    )
}

/** 紧凑行卡：小封面（承载壳 morph）+ 标题/数量 + chevron。 */
@Composable
private fun ProfileRowCard(
    icon: ImageVector,
    title: String,
    count: String,
    coverUrl: String?,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    /** null = 这条路径不挂共享元素（未登录占位卡），无需判定可见性。 */
    guard: SharedSourceGuard?,
    sharedKey: String?,
    onClick: (Rect?, morphable: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** chevron 朝向（度）：0 = 朝右（进入下一级）；90 = 朝下（原地展开已开）。 */
    chevronAngle: Float = 0f,
) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    // 判定的是**小封面**而不是整行卡：卡片被纸边切掉一截时，只要 64dp 的封面还完整
    // 露着，morph 的起点就还是有效的 —— 门槛按真正的共享元素（封面）算。
    var shareable by remember { mutableStateOf(true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(NumeShape.Card)
            .pressScale { onClick(rect, shareable) }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .onGloballyPositioned { coords ->
                    val r = Rect(coords.localToWindow(Offset.Zero), coords.size.toSize())
                    rect = r
                    val ok = guard?.isFullyVisible(r) ?: true
                    if (ok != shareable) shareable = ok
                }
                .then(
                    if (shared != null && avScope != null && sharedKey != null && shareable) {
                        Modifier.shellSharedCover(shared, avScope, sharedKey)
                    } else {
                        Modifier
                    },
                )
                // 圆角 clip 必须在 sharedBounds **内层**（链更靠后）—— 与探索页大卡
                // 同一条规矩：转场时在 overlay 里飞的是 sharedBounds 圈住的这一份，
                // 圆角只挂在外层的话，飞的那份就是直角（2026-10-05 修正：本行卡原先
                // 把 .clip() 写在了 .then(shellSharedCover) 之前，正是「动画中封面没圆角」）。
                .clip(NumeShape.CardSmall),
        ) {
            if (coverUrl != null) {
                // 真实封面：纯图展示，scrim 关掉（小图上 0.5 黑渐变占过半
                // = 真机看到的「深色阴影」；无文字不需要遮罩）。
                BigCoverVisual(
                    coverUrl = coverUrl,
                    name = title,
                    showName = false,
                    scrimAlpha = 0f,
                    watermarkIcon = icon,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                // 无封面/未登录占位：干净底 + primary 图标。
                // 不走 BigCoverVisual 的 secondaryContainer 兜底——那个
                // 渐变底在深色模式下呈脏暗红褐（真机 2026-10-03 实拍）。
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NumeFade.ARROW_MUTED),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = chevronAngle },
        )
    }
}

/* ── 已购：原地展开（单曲 / 专辑） ─────────────────────────── */

/**
 * 内联展开时最多铺多少首已购单曲。
 *
 * 「我的」页外层是 `verticalScroll` + 普通 Column（不是 LazyColumn），内联列表每多一行
 * 就多一个真实组合节点；已购上百首时这一屏会明显变慢。超出部分交给「查看全部」——
 * 面板里是懒加载列表，多少首都不怕。
 */
private const val PurchasedInlineCap = 30

/**
 * 「已购」区块：行卡点一下**原地展开**（不打开面板），露出「单曲」「专辑」两个子项；
 * 「单曲」再展开一层、原地铺开曲目；「专辑」才进全屏面板（大卡陈列）。
 *
 * ## 几处刻意的决定
 *
 * - **不用封面，用图标**：已购本身没有"一张代表封面"——它是单曲 + 专辑两个集合的容器，
 *   拿首曲专辑封面当门脸是借用（用户判定为不必要）。行卡的图标分支本来就支持这条路。
 * - **不挂共享元素**：这条链不打开面板，挂 sharedBounds 等于给转场注册一个找不到对侧
 *   目标的孤儿 bounds（探索页同类注释）。面板侧（专辑网格）相应地也不挂。
 * - **展开态是独立 state，不进 hiltViewModel**：它是纯 UI 开关，切 tab 回来收起
 *   （与「进页面先看到概览」一致）比记着更合理。
 */
@Composable
private fun PurchasedEntry(
    songs: List<Track>,
    albums: List<Album>,
    /** null = 已登录；非 null = 未登录，点任一子项都走它（拉起登录）。 */
    onLogin: (() -> Unit)?,
    onPlaySong: (Int) -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenAllSongs: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var songsOpen by remember { mutableStateOf(false) }
    val loggedIn = onLogin == null
    // `tween` 的 T 无法从参数推断，必须显式给 <Float>（否则报 Cannot infer type for 'T'）。
    // expand/shrink 那一对是 <IntSize>，所以在调用处就地构造、由参数类型推断。
    val ease = tween<Float>(ProfileMotion.ENTER_MS, easing = Motion.EmphasizedDecelerate)
    val mainChevron by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = ease,
        label = "purchasedChevron",
    )
    val songChevron by animateFloatAsState(
        targetValue = if (songsOpen) 90f else 0f,
        animationSpec = ease,
        label = "purchasedSongsChevron",
    )

    Column(Modifier.fillMaxWidth()) {
        ProfileRowCard(
            icon = Icons.Filled.ShoppingCart,
            title = "已购",
            count = if (loggedIn) {
                "${songs.size} 首单曲 · ${albums.size} 张专辑"
            } else {
                "登录后查看"
            },
            coverUrl = null,
            shared = null,
            avScope = null,
            guard = null,
            sharedKey = null,
            onClick = { _, _ -> open = !open },
            chevronAngle = mainChevron,
        )
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(ProfileMotion.ENTER_MS, easing = Motion.EmphasizedDecelerate)) +
                fadeIn(ease),
            exit = shrinkVertically(tween(ProfileMotion.ENTER_MS, easing = Motion.Emphasized)) +
                fadeOut(tween(ProfileMotion.ENTER_MS, easing = Motion.Emphasized)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SubEntryRow(
                    title = "单曲",
                    meta = if (loggedIn) "${songs.size} 首" else "登录后查看",
                    chevronAngle = songChevron,
                    onClick = { if (loggedIn) songsOpen = !songsOpen else onLogin?.invoke() },
                )
                AnimatedVisibility(
                    visible = songsOpen,
                    enter = expandVertically(
                        tween(ProfileMotion.ENTER_MS, easing = Motion.EmphasizedDecelerate),
                    ) + fadeIn(ease),
                    exit = shrinkVertically(
                        tween(ProfileMotion.ENTER_MS, easing = Motion.Emphasized),
                    ) + fadeOut(tween(ProfileMotion.ENTER_MS, easing = Motion.Emphasized)),
                ) {
                    InlinePurchasedSongs(
                        songs = songs,
                        onPlay = onPlaySong,
                        onOpenAll = onOpenAllSongs,
                    )
                }
                SubEntryRow(
                    title = "专辑",
                    meta = if (loggedIn) "${albums.size} 张" else "登录后查看",
                    onClick = { if (loggedIn) onOpenAlbums() else onLogin?.invoke() },
                )
            }
        }
    }
}

/**
 * 已购展开后的子项行（单曲 / 专辑）：比主行卡轻一档 —— 无封面、无卡底，
 * 只比主行左缩进一点，读作「上面那张卡里的两项」而不是两个并列入口。
 */
@Composable
private fun SubEntryRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    chevronAngle: Float = 0f,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 2.dp)
            .clip(NumeShape.CardSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NumeFade.ARROW_MUTED),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = chevronAngle },
        )
    }
}

/**
 * 已购单曲的内联列表：点行**播放**（队列是完整已购，见 ProfileViewModel.playPurchased）。
 *
 * 展示上限 [PurchasedInlineCap]，超出给「查看全部 N 首」落到面板 —— 内联铺一千行
 * 既拖慢这一屏，也没人会在"我的"页里滚完它。
 */
@Composable
private fun InlinePurchasedSongs(
    songs: List<Track>,
    onPlay: (Int) -> Unit,
    onOpenAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 4.dp)) {
        if (songs.isEmpty()) {
            Text(
                "还没有已购单曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            )
        } else {
            // 保序取前 N：`onPlay(i)` 里的 i 就是**完整已购列表**里的下标，与
            // ProfileViewModel.playPurchased(index) 的契约对齐（不能传可见段的下标）。
            songs.take(PurchasedInlineCap).forEachIndexed { i, track ->
                NumeMediaRow(
                    title = track.name,
                    subtitle = track.artist.takeIf { it.isNotBlank() },
                    coverUrl = track.artworkUrl,
                    onClick = { onPlay(i) },
                )
            }
            if (songs.size > PurchasedInlineCap) {
                Text(
                    "查看全部 ${songs.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NumeShape.CardSmall)
                        .clickable(onClick = onOpenAll)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/* ── 页面内容（internal 契约不变） ──────────────────────────── */

@Composable
internal fun LoggedOutContent(onLogin: () -> Unit) {
    // 深底深字教训（2026-10-03 真机）：Box.background 不设置 contentColor，
    // Text 全靠外层 LocalContentColor——显式钉死 onSurface，不赌容器。
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        StaggerIn(0) {
            LoginHeroCard(onLogin)
        }
        Spacer(Modifier.height(16.dp))

        // 未登录同构：结构与已登录完全一致 —— 「已购」照样能原地展开看到「单曲 / 专辑」，
        // 点任一子项都拉起登录（onLogin 非空即未登录，见 [PurchasedEntry]）。
        StaggerIn(1) {
            PurchasedEntry(
                songs = emptyList(),
                albums = emptyList(),
                onLogin = onLogin,
                onPlaySong = {},
                onOpenAlbums = {},
                onOpenAllSongs = {},
            )
        }
        Spacer(Modifier.height(10.dp))

        // 其余两张行卡：登录后才有内容，未登录只是占位。
        val placeholders = listOf(
            Triple(Icons.Filled.List, "创建的歌单", "登录后查看"),
            Triple(Icons.Filled.Star, "收藏的歌单", "登录后查看"),
        )
        placeholders.forEachIndexed { i, (icon, title, hint) ->
            StaggerIn(i + 2) {
                ProfileRowCard(
                    icon = icon,
                    title = title,
                    count = hint,
                    coverUrl = null,
                    shared = null,
                    avScope = null,
                    guard = null,
                    sharedKey = null,
                    onClick = { _, _ -> onLogin() },
                )
            }
            if (i < placeholders.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
    }
}

@Composable
private fun LoginHeroCard(onLogin: () -> Unit) {
    // 未登录页唯一主 CTA：primaryContainer 成块存在感（深浅色协调），
    // 文字钉死 onPrimaryContainer——不再依赖外层 contentColor。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(NumeShape.Card)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onLogin() }
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "登录网易云",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "解锁喜欢 / 已购 / 歌单",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = NumeFade.ON_CONTAINER_BODY),
            )
        }
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = "登录",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun LoggedInContent(
    data: ProfileData,
    onOpenTracks: (source: String, id: String, title: String) -> Unit,
    onOpenPanel: (ProfilePanel, Rect?, morphable: Boolean) -> Unit,
    /** 已购内联列表的行点击（下标为在**完整已购**里的位置）。 */
    onPlayPurchased: (Int) -> Unit,
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    /** 滚动视口的源可见性守卫（见 [SharedSourceGuard]）。 */
    guard: SharedSourceGuard,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        val likedMeta = "${data.likedCount} 首"
        val createdMeta = "${data.createdPlaylists.size} 个歌单"
        val subscribedMeta = "${data.subscribedPlaylists.size} 个歌单"

        // 面板定义与 shellKey 契约保持不变（ProfilePanels 路由零改动）。
        // 注意 panels[1]（已购曲目列表）**不再是某张行卡的直接目标**：已购改成原地展开后，
        // 它只作为「查看全部 N 首」的落点（内联列表有展示上限）。
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
                meta = "${data.purchasedSongCount} 首单曲",
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

        // 已购专辑网格（「已购 → 专辑」的目的地）。这条链两端都不挂共享元素
        // （起点是图标，见 PurchasedEntry / ProfilePanel.Albums），所以 morph 传 false。
        val albumsPanel = ProfilePanel.Albums(
            title = "已购专辑",
            albums = data.purchasedAlbums,
            coverUrl = data.purchasedAlbums.firstOrNull()?.coverUrl,
            icon = Icons.Filled.ShoppingCart,
            meta = "${data.purchasedAlbums.size} 张",
        )

        StaggerIn(0) {
            ProfileHeaderRow(data.account)
        }
        Spacer(Modifier.height(12.dp))

        // 主入口：喜欢的音乐（全宽横幅，与面板 banner 同 key morph）。
        StaggerIn(1) {
            LikedHeroCard(
                coverUrl = data.likedCoverUrl,
                meta = likedMeta,
                shared = shared,
                avScope = avScope,
                guard = guard,
                sharedKey = panels[0].shellKey,
                onClick = { rect, morphable -> onOpenPanel(panels[0], rect, morphable) },
            )
        }
        Spacer(Modifier.height(10.dp))

        // 已购：原地展开（单曲 / 专辑），不打开面板 —— 所以这张卡不挂共享元素。
        StaggerIn(2) {
            PurchasedEntry(
                songs = data.purchasedSongs,
                albums = data.purchasedAlbums,
                onLogin = null,
                onPlaySong = onPlayPurchased,
                onOpenAlbums = { onOpenPanel(albumsPanel, null, false) },
                onOpenAllSongs = { onOpenPanel(panels[1], null, false) },
            )
        }
        Spacer(Modifier.height(10.dp))

        // 尾部两张行卡：仍是「大卡 → 面板」，封面与面板 banner 同 key morph。
        val rows = listOf(
            Triple(Icons.Filled.List, "创建的歌单", createdMeta),
            Triple(Icons.Filled.Star, "收藏的歌单", subscribedMeta),
        )
        val rowCovers = listOf(
            data.createdPlaylists.firstOrNull()?.coverUrl,
            data.subscribedPlaylists.firstOrNull()?.coverUrl,
        )
        rows.forEachIndexed { i, (icon, title, meta) ->
            StaggerIn(i + 3) {
                ProfileRowCard(
                    icon = icon,
                    title = title,
                    count = meta,
                    coverUrl = rowCovers[i],
                    shared = shared,
                    avScope = avScope,
                    guard = guard,
                    sharedKey = panels[i + 2].shellKey,
                    onClick = { rect, morphable -> onOpenPanel(panels[i + 2], rect, morphable) },
                )
            }
            if (i < rows.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
    }
}
