package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
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
import com.thripleq.cirro.core.repo.Program
import com.thripleq.cirro.core.repo.RadioDetail
import com.thripleq.cirro.ui.components.CirroArt
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.CirroLoadMoreIndicator
import com.thripleq.cirro.ui.components.CirroMediaRow
import com.thripleq.cirro.ui.components.CirroMediaRowSkeleton
import com.thripleq.cirro.ui.components.CirroPaperPage
import com.thripleq.cirro.ui.components.ShimmerImagePlaceholder
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.components.cirroEntrySurface
import com.thripleq.cirro.ui.podcast.PodcastUiState
import com.thripleq.cirro.ui.podcast.PodcastViewModel
import com.thripleq.cirro.ui.theme.CirroShape
import com.valentinilk.shimmer.shimmer
import java.util.Locale

/**
 * 播客/电台详情：电台资料 + 节目列表（触底翻页）。
 * 点节目以可播放节目为队列整单起播（**不进播放页**，2026-10-03 起）。
 */
@Composable
fun PodcastScreen(
    id: String,
    onBack: () -> Unit,
    islandHeight: Float = 0f,
    vm: PodcastViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(id) { vm.load(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val bottomPadding = (islandHeight + 16f).dp

    // 顶部换成站内统一的那一份（[CirroPaperPage]）：容器色条 + 大标题 + 圆角纸，
    // 右上角一颗浮层收起键。原来这里是左上角返回箭头 + 小一号的 `titleLarge` 标题、
    // 且没有圆角纸（2026-10-05 用户：「改成一模一样的，返回按钮也是一模一样」）。
    CirroPaperPage(title = state.detail?.name ?: "播客", onClose = onBack) {
        when {
            state.loading -> PodcastSkeleton()
            state.error -> CirroErrorState(text = "播客加载失败，点此重试", onRetry = vm::retry)
            else -> ProgramsContent(
                detail = state.detail,
                programs = state.programs,
                loadingMore = state.loadingMore,
                bottomPadding = bottomPadding,
                onLoadMore = vm::loadMore,
                onPlay = vm::onPlayProgram,
            )
        }
    }
}

@Composable
private fun ProgramsContent(
    detail: RadioDetail?,
    programs: List<Program>,
    loadingMore: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLoadMore: () -> Unit,
    onPlay: (Program) -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreWatcher(listState, onLoadMore)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        if (detail != null) {
            item(key = "header") { RadioHeader(detail) }
        }
        itemsIndexed(
            programs,
            key = { _, p -> "pg_${p.id}" },
            contentType = { _, _ -> "program" },
        ) { _, p ->
            CirroMediaRow(
                title = p.name,
                subtitle = "${formatDuration(p.durationMs)} · ${formatCount(p.listenerCount)} 听过",
                coverUrl = p.coverUrl,
                onClick = { onPlay(p) },
            )
        }
        if (loadingMore) {
            item(key = "loading_more") { CirroLoadMoreIndicator() }
        }
    }
}

@Composable
private fun RadioHeader(detail: RadioDetail) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        CirroArtwork(
            url = detail.coverUrl,
            contentDescription = detail.name,
            size = CirroArt.Radio,
            shape = CirroShape.CardSmall,
            requestSize = CirroArt.RequestLarge,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.djName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "DJ：${detail.djName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${detail.programCount} 期 · 播放 ${formatCount(detail.playCount)} · 订阅 ${formatCount(detail.subCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.desc.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** 播客详情骨架：电台头（大封面 + 标题/元信息） + 节目行。 */
@Composable
private fun PodcastSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            SkeletonBox(Modifier.size(112.dp), CirroShape.CardSmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(widthFraction = 0.7f, height = 16.dp)
                SkeletonLine(widthFraction = 0.4f, height = 12.dp)
                SkeletonLine(widthFraction = 0.85f, height = 12.dp)
            }
        }
        repeat(6) { CirroMediaRowSkeleton() }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> trim1(n / 100_000_000.0) + "亿"
    n >= 10_000 -> trim1(n / 10_000.0) + "万"
    else -> n.toString()
}

private fun trim1(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
