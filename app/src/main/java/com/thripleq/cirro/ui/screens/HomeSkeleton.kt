package com.thripleq.cirro.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.components.CirroMediaRowSkeleton
import com.thripleq.cirro.ui.components.CirroSectionHeaderSkeleton
import com.thripleq.cirro.ui.components.CirroTitleBarHeight
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.theme.CirroShape
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
        // 标题条骨架与 [CirroPageTitleBar] 同构：透明铺在容器色上、statusBarsPadding、
        // 高度取同一个 [CirroTitleBarHeight]（纸顶必须与真内容同高，否则加载完成时整张纸跳一下）、
        // 左内缩 24dp、右侧 28dp 圆钮槽。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(CirroTitleBarHeight)
                .padding(start = 24.dp, end = 8.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(widthFraction = 0.24f, height = 28.dp, shape = CirroShape.Chip)
            Spacer(Modifier.weight(1f))
            SkeletonBox(Modifier.size(28.dp), CircleShape)
        }

        // 内容圆角纸，与真内容的 LazyColumn 同形（区块顺序照 kanade 主页，2026-10-04）：
        // 精选推荐大卡 → 猜你喜欢单曲行 → 雷达歌单方卡 → 场景音乐窄卡。
        // 卡片尺寸直接引用真内容的 [KanadeCardSpec]，骨架与真内容不会走偏
        // （雷达行是 110 方形、场景行是 110×121，两者不等高）。
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CirroShape.SheetTop)
                .background(MaterialTheme.colorScheme.surface)
                .shimmer()
                .padding(bottom = bottomPadding),
        ) {
            CirroSectionHeaderSkeleton()
            SkeletonCarousel(FeaturedCardSpec)

            CirroSectionHeaderSkeleton()
            repeat(3) { CirroMediaRowSkeleton() }

            CirroSectionHeaderSkeleton()
            SkeletonCarousel(RadarCardSpec)

            CirroSectionHeaderSkeleton()
            SkeletonCarousel(SceneCardSpec)
        }
    }
}

@Composable
private fun SkeletonCarousel(spec: KanadeCardSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // 间距与真内容一致（11dp，见 KanadeCardRow）。
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        repeat(3) {
            SkeletonBox(
                Modifier.size(spec.width, spec.coverHeight + spec.stripHeight),
                CirroShape.Card,
            )
        }
    }
}


