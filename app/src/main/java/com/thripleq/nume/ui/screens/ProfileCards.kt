package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.ui.components.ArtistAvatarSize
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.NumeArt
import com.thripleq.nume.ui.components.NumeArtwork
import com.thripleq.nume.ui.components.SharedSourceGuard
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeShape

/* ──────────────────────────────────────────────────────────────────
 * 「我的」页 v2 —— 个人主页杂志版式（2026-10 重设计）
 *
 * 旧版问题：小头像行 + 2×2 同尺寸方卡——列表既视感、无层级、无动效、
 * 「喜欢的音乐」这个最高频入口和其他入口平权。
 *
 * 新版式（三层视觉锚点）：
 *   1. 人物 hero：居中大头像（背景 = 同一张头像的柔焦放大，上下渐隐融
 *      入底色）+ 大字昵称 + 数据行（tabular 数字）。个人主页的存在感。
 *   2. 「喜欢的音乐」全宽横幅主卡（21:10）：最高频入口单独放大，
 *      封面即主视觉。
 *   3. 已购 / 创建 / 收藏三张紧凑行卡（小封面 + 数量 + chevron）。
 *
 * 壳契约完全保留：四个 shellKey 不变、起点 rect 测量仍挂在封面
 * onGloballyPositioned、面板路由（ProfilePanels）零改动——
 * sharedBounds 从新布局的任意矩形照常 morph 到面板 banner。
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

/* ── 人物 hero ──────────────────────────────────────────────── */

@Composable
private fun ProfileHero(account: Account, likedCount: Int, purchasedCount: Int, playlistCount: Int) {
    Box(Modifier.fillMaxWidth()) {
        // 背景 = 同一张头像的柔焦放大层（Coil 内存命中：同 url 同解码尺寸）。
        // blur 在 <API 31 是 no-op：退化为低透明度原图，仍成立不崩。
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = 1.6f
                    scaleY = 1.6f
                }
                .blur(32.dp),
        ) {
            NumeArtwork(
                url = account.avatarUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                size = null,
                shape = NumeShape.Card,
                requestSize = ArtistAvatarSize,
                fallbackIcon = Icons.Filled.AccountCircle,
                fallbackIconSize = 96.dp,
            )
        }
        // 上下渐隐把柔焦层融进底色（否则是一块突兀的色带）。
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        0.22f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            NumeArtwork(
                url = account.avatarUrl,
                contentDescription = account.nickname,
                modifier = Modifier
                    .size(NumeArt.AvatarLg)
                    .clip(CircleShape),
                size = NumeArt.AvatarLg,
                shape = CircleShape,
                requestSize = ArtistAvatarSize,
                fallbackIcon = Icons.Filled.AccountCircle,
                fallbackIconSize = 96.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                account.nickname,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            ProfileStatLine(
                likedCount = likedCount,
                purchasedCount = purchasedCount,
                playlistCount = playlistCount,
                vip = account.vipType > 0,
            )
        }
    }
}

/** 数据行：tabular 数字 + 中点分隔；VIP 胶囊排最前（有的话）。 */
@Composable
private fun ProfileStatLine(likedCount: Int, purchasedCount: Int, playlistCount: Int, vip: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vip) VipBadge()
        val muted = MaterialTheme.typography.labelMedium
            .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        val strong = muted.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            buildAnnotatedString {
                withStyle(strong.toSpanStyle()) { append(compact(likedCount)) }
                withStyle(muted.toSpanStyle()) { append(" 首 · ") }
                withStyle(strong.toSpanStyle()) { append(compact(purchasedCount)) }
                withStyle(muted.toSpanStyle()) { append(" 项 · ") }
                withStyle(strong.toSpanStyle()) { append(compact(playlistCount)) }
                withStyle(muted.toSpanStyle()) { append(" 个歌单") }
            },
            style = muted,
            maxLines = 1,
        )
    }
}

/** 1234 → 「1,234」；≥10000 → 「1.2 万」（数字短了 hero 才稳）。 */
private fun compact(n: Int): String = when {
    n >= 10000 -> {
        val w = n / 10000.0
        if (w >= 10 || w == w.toInt().toDouble()) "${w.toInt()} 万" else "%.1f 万".format(w)
    }
    else -> "%,d".format(n)
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
                .clip(NumeShape.CardSmall)
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
                ),
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
            modifier = Modifier.size(20.dp),
        )
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

        // 未登录同构占位：与已登录相同的三行卡，点击引导登录。
        val placeholders = listOf(
            Triple(Icons.Filled.ShoppingCart, "已购", "登录后查看"),
            Triple(Icons.Filled.List, "创建的歌单", "登录后查看"),
            Triple(Icons.Filled.Star, "收藏的歌单", "登录后查看"),
        )
        placeholders.forEachIndexed { i, (icon, title, hint) ->
            StaggerIn(i + 1) {
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
    shared: SharedTransitionScope?,
    avScope: AnimatedVisibilityScope?,
    /** 滚动视口的源可见性守卫（见 [SharedSourceGuard]）。 */
    guard: SharedSourceGuard,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        val likedMeta = "${data.likedCount} 首"
        val purchasedMeta = "${data.purchasedSongCount + data.purchasedAlbums.size} 项"
        val createdMeta = "${data.createdPlaylists.size} 个歌单"
        val subscribedMeta = "${data.subscribedPlaylists.size} 个歌单"

        // 面板定义与 shellKey 契约保持不变（ProfilePanels 路由零改动）。
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

        StaggerIn(0) {
            ProfileHero(
                account = data.account,
                likedCount = data.likedCount,
                purchasedCount = data.purchasedSongCount + data.purchasedAlbums.size,
                playlistCount = data.createdPlaylists.size + data.subscribedPlaylists.size,
            )
        }
        Spacer(Modifier.height(8.dp))

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

        // 次入口：三张行卡，依次 stagger。
        val rows = listOf(
            Triple(Icons.Filled.ShoppingCart, "已购", purchasedMeta),
            Triple(Icons.Filled.List, "创建的歌单", createdMeta),
            Triple(Icons.Filled.Star, "收藏的歌单", subscribedMeta),
        )
        rows.forEachIndexed { i, (icon, title, meta) ->
            StaggerIn(i + 2) {
                ProfileRowCard(
                    icon = icon,
                    title = title,
                    count = meta,
                    coverUrl = when (i) {
                        0 -> data.purchasedCoverUrl
                        1 -> data.createdPlaylists.firstOrNull()?.coverUrl
                        else -> data.subscribedPlaylists.firstOrNull()?.coverUrl
                    },
                    shared = shared,
                    avScope = avScope,
                    guard = guard,
                    sharedKey = panels[i + 1].shellKey,
                    onClick = { rect, morphable -> onOpenPanel(panels[i + 1], rect, morphable) },
                )
            }
            if (i < rows.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
    }
}
