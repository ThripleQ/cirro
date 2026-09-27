package com.thripleq.nume.ui.screens

import com.thripleq.nume.ui.theme.NumeShape
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.thripleq.nume.core.repo.Chart
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.library.LibraryUiState
import com.thripleq.nume.ui.library.LibraryViewModel
import com.valentinilk.shimmer.shimmer

/** 免登录首页：列出排行榜，点进榜单到统一列表页。 */
@Composable
fun LibraryScreen(
    onOpenChart: (String, String, Rect) -> Unit,
) {
    val vm: LibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is LibraryUiState.Loading -> LibrarySkeleton()
        is LibraryUiState.Error -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载失败，请检查网络", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(12.dp))
                Button(onClick = vm::load) { Text("重试") }
            }
        }
        is LibraryUiState.Charts -> ChartList(
            charts = s.charts,
            onChart = { c, origin -> onOpenChart(c.id, c.name, origin) },
        )
    }
}

@Composable
private fun ChartList(charts: List<Chart>, onChart: (Chart, Rect) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("排行榜", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
        items(charts, key = { it.id }) { c ->
            var rect by remember { mutableStateOf(Rect.Zero) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NumeShape.CardSmall)
                    .onGloballyPositioned { rect = it.boundsInWindow() }
                    .clickable { onChart(c, rect) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(NumeShape.Chip)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    c.coverUrl?.let { url ->
                        val painter = rememberAsyncImagePainter(Uri.parse(url))
                        ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                        Image(
                            painter = painter,
                            contentDescription = c.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = c.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 排行榜骨架：标题 + 榜单行（52dp 封面 + 名称）。 */
@Composable
private fun LibrarySkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .shimmer()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonLine(widthFraction = 0.3f, height = 22.dp)
        repeat(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(52.dp), NumeShape.Chip)
                Spacer(Modifier.width(12.dp))
                SkeletonLine(widthFraction = 0.5f, height = 16.dp)
            }
        }
    }
}