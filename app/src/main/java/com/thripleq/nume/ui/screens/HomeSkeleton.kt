package com.thripleq.nume.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.NumeMediaRowSkeleton
import com.thripleq.nume.ui.components.NumeSectionHeaderSkeleton
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer

/* ── 加载骨架 ─────────────────────────────────────────── */

/** 探索页骨架：与 [HomeContent] 同构——容器色标题条 + `surface` 圆角纸（区块标题 +
 *  横滑大封面卡 + 单曲行）。 */
@Composable
internal fun HomeSkeleton(bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // 标题条骨架与 [NumePageTitleBar] 同构：透明铺在容器色上、statusBarsPadding、
        // 左内缩 24dp、右侧 28dp 圆钮槽。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(28.dp)
                .padding(start = 24.dp, end = 8.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(widthFraction = 0.24f, height = 28.dp, shape = NumeShape.Chip)
            Spacer(Modifier.weight(1f))
            SkeletonBox(Modifier.size(28.dp), CircleShape)
        }

        // 内容圆角纸，与真内容的 LazyColumn 同形（区块顺序照 kanade 主页，2026-10-04）：
        // 精选推荐大卡 → 猜你喜欢单曲行 → 雷达歌单大卡 → 场景音乐窄卡。
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = HomeSheetRadius, topEnd = HomeSheetRadius))
                .background(MaterialTheme.colorScheme.surface)
                .shimmer()
                .padding(bottom = bottomPadding),
        ) {
            NumeSectionHeaderSkeleton()
            SkeletonCarousel(FeaturedCardSize)

            NumeSectionHeaderSkeleton()
            repeat(3) { NumeMediaRowSkeleton() }

            // 区块顺序与 HomeContent 对齐：精选推荐 / 猜你喜欢 / 场景音乐
            // （原「雷达歌单」区已删除，骨架同步少一块）。
            NumeSectionHeaderSkeleton()
            SkeletonCarousel(SceneCardSize)
        }
    }
}

@Composable
private fun SkeletonCarousel(cardWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { SkeletonBox(Modifier.size(cardWidth), NumeShape.Card) }
    }
}


