package com.thripleq.cirro.ui.screens

import com.thripleq.cirro.ui.theme.CirroShape
import com.thripleq.cirro.ui.components.cirroEntrySurface
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
import androidx.compose.runtime.LaunchedEffect
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
import com.thripleq.cirro.core.model.Chart
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.CirroMediaRow
import com.thripleq.cirro.ui.components.CirroMediaRowSkeleton
import com.thripleq.cirro.ui.components.CirroSectionHeader
import com.thripleq.cirro.ui.components.CirroSectionHeaderSkeleton
import com.thripleq.cirro.ui.components.ShimmerImagePlaceholder
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.library.LibraryUiState
import com.thripleq.cirro.ui.library.LibraryViewModel
import com.valentinilk.shimmer.shimmer

/** 免登录首页：列出排行榜，点进榜单到统一列表页。 */
@Composable
fun LibraryScreen(
    onOpenChart: (String, String, Rect) -> Unit,
) {
    val vm: LibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 页面重新可见时问一句要不要取新数据（冷却门在 VM 里，见 RefreshGate）。榜单的
    // 名称/封面会随官方调整而变，只靠 init 那一次就永远是第一次进页面时那份。
    LaunchedEffect(Unit) { vm.onEnterVisible() }

    when (val s = state) {
        is LibraryUiState.Loading -> LibrarySkeleton()
        is LibraryUiState.Error -> CirroErrorState(onRetry = vm::load)
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
            start = 0.dp,
            top = 12.dp,
            end = 0.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { CirroSectionHeader("排行榜") }
        items(charts, key = { it.id }) { c ->
            var rect by remember { mutableStateOf(Rect.Zero) }
            CirroMediaRow(
                title = c.name,
                coverUrl = c.coverUrl,
                onClick = { onChart(c, rect) },
                modifier = Modifier.onGloballyPositioned { rect = it.boundsInWindow() },
            )
        }
    }
}

/** 排行榜骨架：标题 + 榜单行（52dp 封面 + 名称）。 */
@Composable
private fun LibrarySkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CirroSectionHeaderSkeleton()
        repeat(8) { CirroMediaRowSkeleton() }
    }
}