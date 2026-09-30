package com.thripleq.nume.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer

/* ── 加载骨架 ─────────────────────────────────────────── */

/** 探索页骨架：与 [HomeContent] 同构——顶栏 + 区块标题 + 横滑大封面卡 + 单曲行。 */
@Composable
internal fun HomeSkeleton(bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(bottom = bottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(widthFraction = 0.24f, height = 28.dp, shape = NumeShape.Chip)
            Spacer(Modifier.weight(1f))
            SkeletonBox(Modifier.size(28.dp), CircleShape)
        }

        SkeletonSectionHeader()
        repeat(4) { SkeletonTrackRow(artSize = 52.dp) }

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        SkeletonCarousel()

        SkeletonSectionHeader()
        repeat(4) { SkeletonTrackRow(artSize = 52.dp) }
    }
}

@Composable
private fun SkeletonSectionHeader() {
    SkeletonLine(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        widthFraction = 0.3f,
        height = 22.dp,
        shape = NumeShape.Chip,
    )
}

@Composable
private fun SkeletonCarousel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { SkeletonBox(Modifier.size(BigCoverSize), NumeShape.Card) }
    }
}

@Composable
private fun SkeletonTrackRow(artSize: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(artSize), NumeShape.Chip)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.55f, height = 14.dp)
            SkeletonLine(widthFraction = 0.3f, height = 12.dp)
        }
    }
}
