package com.thripleq.cirro.ui.screens

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.cirro.core.model.Comment
import com.thripleq.cirro.ui.comments.CommentsViewModel
import com.thripleq.cirro.ui.components.CirroArt
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.CirroEmptyState
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.CirroLoadMoreIndicator
import com.thripleq.cirro.ui.components.CirroPaperPage
import com.thripleq.cirro.ui.components.CirroSectionHeader
import com.thripleq.cirro.ui.components.ShimmerImagePlaceholder
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.theme.CirroShape
import com.valentinilk.shimmer.shimmer

/**
 * 评论页（热门 + 最新，触底翻页）。
 *
 * 入口有三处，靠 [threadId] 区分资源：播放页的「评论」按钮（单曲 `R_SO_4_`）、
 * 歌单 / 榜单页头部的「评论」胶囊（`A_PL_0_`）、专辑页（`R_AL_3_`）。
 * 作为浮层盖在被覆盖的页之上（那一页保持打开），因此根布局需要不透明底色，
 * 并自行处理系统返回键。
 */
@Composable
fun CommentsScreen(
    threadId: String,
    onBack: () -> Unit,
    islandHeight: Float = 0f,
    vm: CommentsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(threadId) { vm.load(threadId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val bottomPadding = (islandHeight + 16f).dp
    val listState = rememberLazyListState()

    // 点赞失败的提示（成功不打扰）：与站内其他写操作同一套语言。
    val context = LocalContext.current.applicationContext
    LaunchedEffect(vm) {
        vm.message.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 顶部换成站内统一的那一份（[CirroPaperPage]）：容器色条 + 大标题 + 圆角纸，
    // 右上角一颗浮层收起键。原来这里是左上角返回箭头 + 小一号的 `titleLarge` 标题、
    // 且没有圆角纸（2026-10-05 用户：「改成一模一样的，返回按钮也是一模一样」）。
    CirroPaperPage(
        title = if (state.total > 0) "评论 ${state.total}" else "评论",
        onClose = onBack,
    ) {
        when {
            state.loading -> CommentsSkeleton()
            state.error -> CirroErrorState(text = "评论加载失败，点此重试", onRetry = vm::retry)
            state.hot.isEmpty() && state.latest.isEmpty() -> CirroEmptyState("还没有评论")
            else -> {
                LoadMoreWatcher(listState, vm::loadMore)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    if (state.hot.isNotEmpty()) {
                        item(key = "hot_header") { CirroSectionHeader("热门评论") }
                        items(state.hot, key = { "h_${it.id}" }) { CommentRow(it, vm::toggleLike) }
                    }
                    item(key = "latest_header") { CirroSectionHeader("最新评论") }
                    items(state.latest, key = { "l_${it.id}" }) { CommentRow(it, vm::toggleLike) }
                    if (state.loadingMore) {
                        item(key = "loading_more") { CirroLoadMoreIndicator() }
                    } else if (!state.hasMore && state.latest.isNotEmpty()) {
                        item(key = "end") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, onLike: (Comment) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CirroArtwork(
            url = comment.avatarUrl,
            contentDescription = comment.nickname,
            size = CirroArt.AvatarSm,
            shape = CircleShape,
            requestSize = 120,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = comment.nickname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (comment.repliedContent.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CirroShape.Chip)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(8.dp),
                ) {
                    if (comment.repliedNickname.isNotBlank()) {
                        Text(
                            text = "@${comment.repliedNickname}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = comment.repliedContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = relativeTime(comment.timeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.location.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = comment.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                // 点赞：**整块（图标 + 数字）都是热区**，不只图标本身 —— 那个心只有
                // 14dp，单独点它在真机上很别扭。热区往外撑到 32dp 高，视觉位置不变。
                Row(
                    modifier = Modifier
                        .clip(CirroShape.Pill)
                        .clickable { onLike(comment) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        // 空/实心两种心：点过就是实心 + 主色，与播放页那颗同一套语言。
                        imageVector = if (comment.liked) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        contentDescription = if (comment.liked) "取消点赞" else "点赞",
                        tint = if (comment.liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    if (comment.likedCount > 0) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = comment.likedCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (comment.liked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreWatcher(listState: LazyListState, onLoadMore: () -> Unit) {
    val shouldLoad by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }
}

/** 评论加载骨架：顶栏下方重复评论行（头像 + 昵称/内容行）。 */
@Composable
private fun CommentsSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(top = 8.dp),
    ) {
        repeat(7) { CommentSkeletonRow() }
    }
}

@Composable
private fun CommentSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SkeletonBox(Modifier.size(36.dp), CircleShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.3f, height = 12.dp)
            SkeletonLine(widthFraction = 0.92f, height = 14.dp)
            SkeletonLine(widthFraction = 0.66f, height = 14.dp)
        }
    }
}

private fun relativeTime(timeMs: Long): String {
    if (timeMs <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    if (diff < 0) return "刚刚"
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute}分钟前"
        diff < day -> "${diff / hour}小时前"
        diff < 30 * day -> "${diff / day}天前"
        else -> {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            fmt.format(java.util.Date(timeMs))
        }
    }
}
