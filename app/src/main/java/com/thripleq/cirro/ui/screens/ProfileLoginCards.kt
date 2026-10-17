package com.thripleq.cirro.ui.screens

/**
 * 「我的」页的**登录态版面**：未登录那一整套（登录引导卡 + 四个占位区块）与已登录那一整套
 * （账号行 / 各入口卡 / 退出登录）。
 *
 * 从 `ProfileCards.kt` 分出来的边界是「谁和谁一起改」：那边是**卡片零件**（行卡、已购壳、
 * hero 卡），改版式动那边；这里是**登录态下的页面组合**，增删区块、改登录引导或退出登录
 * 动这边。两边共用那批卡片零件（`PurchasedEntry` / `VipBadge` …），所以它们被放宽成
 * `internal` —— 跨文件时**只放宽真正被用到的那几个**（顶层声明同包内不许同名，放宽多了
 * 容易撞上）。
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.core.model.Account
import com.thripleq.cirro.core.model.Album
import com.thripleq.cirro.core.model.ProfileData
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.ui.components.ArtistAvatarSize
import com.thripleq.cirro.ui.components.BigCoverVisual
import com.thripleq.cirro.ui.components.CirroArt
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.SharedSourceGuard
import com.thripleq.cirro.ui.components.shellSharedCover
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape

@Composable
internal fun VipBadge() {
    Text(
        "VIP",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(CirroShape.Chip)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
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

        // 未登录同构：结构与已登录完全一致 —— 「已购」照样能原地撑开壳看到「单曲 / 专辑」，
        // 点任一子项都拉起登录（onLogin 非空即未登录，见 [PurchasedEntry]）。
        StaggerIn(1) {
            PurchasedEntry(
                songs = emptyList(),
                albums = emptyList(),
                onLogin = onLogin,
                onOpenSongs = {},
                onOpenAlbums = {},
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
            .clip(CirroShape.Card)
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
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = CirroFade.ON_CONTAINER_BODY),
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
    /** 退出登录（VM 里那套：清 cookie + 失效镜像 + 回未登录态）。 */
    onLogout: () -> Unit,
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
        // 注意 panels[1]（已购曲目列表）的起点不是大卡封面而是「已购」壳里的「单曲」行：
        // 两端都没有 counterpart，所以开合一律 morph = false（不挂共享元素）。
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

        // 已购：原地撑开成壳（单曲 / 专辑），点壳里的项才进全屏 —— 所以这张卡不挂共享元素。
        StaggerIn(2) {
            PurchasedEntry(
                songs = data.purchasedSongs,
                albums = data.purchasedAlbums,
                onLogin = null,
                onOpenSongs = { onOpenPanel(panels[1], null, false) },
                onOpenAlbums = { onOpenPanel(albumsPanel, null, false) },
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

        // 退出登录：放在所有内容之下，低调但找得到（见 [LogoutButton]）。
        LogoutButton(onLogout = onLogout)
    }
    }
}

/**
 * 退出登录：一行低调的按钮，点了**先确认**。
 *
 * 为什么必须确认：这不是「切个开关」，登出会清掉本机登录态 + 收藏/已购镜像，误触就得重走
 * 一遍 WebView 登录。三重降噪：放在页面最底部、用 error 色（读作「有后果」）、二次确认。
 *
 * 用 error 色而不是 onSurfaceVariant：后者和上面的行卡副标题一个色阶，会被当成又一个入口。
 */
@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = { confirm = true },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("退出登录")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("退出登录？") },
            text = { Text("会清除本机的登录状态，需要重新登录才能看到「我的」里的内容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消") }
            },
        )
    }
}
