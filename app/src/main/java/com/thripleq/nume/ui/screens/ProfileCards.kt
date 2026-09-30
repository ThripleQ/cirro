package com.thripleq.nume.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Account
import com.thripleq.nume.core.repo.ProfileData
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.NumeArt
import com.thripleq.nume.ui.components.NumeArtwork
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.shellSharedCover
import com.thripleq.nume.ui.theme.NumeShape

@Composable
internal fun LoggedOutContent(onLogin: () -> Unit) {
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(NumeShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onLogin() },
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

@Composable
internal fun LoggedInContent(
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
 * 底部渐变遮罩上的标题/数量。视觉与探索页大封面卡（[BigCoverVisual]）同参数
 * （0.5 起渐变、0.66 黑、白字），保证两页风格统一。
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
        NumeArtwork(
            url = account.avatarUrl,
            contentDescription = account.nickname,
            size = NumeArt.AvatarMd,
            shape = CircleShape,
            requestSize = 128,
            fallbackIcon = Icons.Filled.AccountCircle,
            fallbackIconSize = 56.dp,
        )
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
