package com.thripleq.nume.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.thripleq.nume.core.repo.Program
import com.thripleq.nume.core.repo.RadioDetail
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.podcast.PodcastUiState
import com.thripleq.nume.ui.podcast.PodcastViewModel
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer
import java.util.Locale

/**
 * 播客/电台详情：电台资料 + 节目列表（触底翻页）。
 * 点节目以可播放节目为队列整单起播并弹出播放页。
 */
@Composable
fun PodcastScreen(
    id: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    islandHeight: Float = 0f,
    vm: PodcastViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val bottomPadding = (islandHeight + 16f).dp

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = onBack, title = state.detail?.name ?: "播客")
        when {
            state.loading -> PodcastSkeleton()
            state.error -> Center {
                Text(
                    text = "播客加载失败",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { vm.retry() },
                )
            }
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
            ProgramRow(p) { onPlay(p) }
        }
        if (loadingMore) {
            item(key = "loading_more") {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp)) }
            }
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
        Cover(detail.coverUrl, detail.name, 112.dp, circle = false)
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
private fun ProgramRow(program: Program, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .numeEntrySurface()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(program.coverUrl, program.name, 52.dp, circle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatDuration(program.durationMs)} · ${formatCount(program.listenerCount)} 听过",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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

@Composable
private fun Cover(
    url: String?,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    circle: Boolean,
) {
    val shape = if (circle) CircleShape else NumeShape.CardSmall
    val context = LocalContext.current
    val model = url?.takeIf { it.isNotBlank() }?.let {
        ImageRequest.Builder(context).data(it).size(360).build()
    }
    Box(Modifier.size(size).clip(shape)) {
        if (model != null) {
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
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
            SkeletonBox(Modifier.size(112.dp), NumeShape.CardSmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(widthFraction = 0.7f, height = 16.dp)
                SkeletonLine(widthFraction = 0.4f, height = 12.dp)
                SkeletonLine(widthFraction = 0.85f, height = 12.dp)
            }
        }
        repeat(6) { ProgramSkeletonRow() }
    }
}

@Composable
private fun ProgramSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(52.dp), NumeShape.CardSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.85f, height = 14.dp)
            SkeletonLine(widthFraction = 0.45f, height = 12.dp)
        }
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
