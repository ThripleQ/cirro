package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.thripleq.cirro.core.repo.Track
import com.thripleq.cirro.ui.components.BannerCoverSize
import com.thripleq.cirro.ui.components.BigCoverVisual
import com.thripleq.cirro.ui.components.LocalShellHeroAlpha
import com.thripleq.cirro.ui.components.CirroContainer
import com.thripleq.cirro.ui.components.CirroPayBadge
import com.thripleq.cirro.ui.components.PayTag
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.CirroArt
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.components.cirroEntrySurface
import com.thripleq.cirro.ui.theme.CirroShape
import com.valentinilk.shimmer.shimmer

/* ── 曲目行 ────────────────────────────────────────────────────── */

/**
 * 曲目行：封面 + 标题 + 「歌手 - 专辑」+ 右侧 ⋮。
 *
 * 行仍是 cirro 的**条目卡**（[cirroEntrySurface]：8dp 外缩 + 圆角 + `surfaceContainer`），
 * 浮在面板底上；卡内再内缩到 [TrackListMetrics.RowInset]。与官方一致的是**封面到屏边**的
 * 距离（8 + 8 = 16dp），卡边是 cirro 自己的条目语言。行高由 44dp 封面 + 上下 8dp 内缩撑起。
 *
 * [payTags] 非空时在「歌手 - 专辑」前面贴付费/VIP 徽标（kanade 的歌曲信息设计，
 * 位置与尺寸见 [CirroPayBadge]；取值见 [rememberPayTags]，别在调用点手工拼）。
 */
@Composable
internal fun TrackListRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    payTags: List<PayTag> = emptyList(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cirroEntrySurface()
            .clickable(onClick = onClick)
            .padding(
                start = TrackListMetrics.RowInset - CirroContainer.Inset,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CirroArtwork(
            url = track.artworkUrl,
            contentDescription = track.name,
            size = TrackListMetrics.RowCover,
            shape = CirroShape.Chip,
            requestSize = CirroArt.RequestRow,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                track.albumName.takeIf { it.isNotBlank() },
            ).joinToString(" - ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                // 与 [CirroMediaRow] 同一形状：徽标在左、文字吃剩余宽度并在末尾省略号。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    payTags.forEach { tag ->
                        CirroPayBadge(tag)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        // ⋮ 用 44dp 触控盒而不是 [IconButton]：M3 的 IconButton 有 48dp 最小高度，
        // 大于 44dp 封面，会把整行撑到 66dp —— 行距就和官方（62dp）对不上了。
        Box(
            Modifier
                .size(TrackListMetrics.RowCover)
                .clip(CircleShape)
                .clickable { /* 三点菜单：暂无功能 */ },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* ── 骨架 ──────────────────────────────────────────────────────── */

/**
 * 列表骨架：与真头部**同构同位**——98dp 方封面（同位、挂同一共享元素）+ 右侧两行标题 +
 * 三枚胶囊 + 播放全部行 + 曲目行。位置一致是硬要求：骨架→真列表是直接切，错位就是「跳一下」。
 *
 * [coverUrl] 非空时封面位置直接渲染**真实高清封面**（不再等整张列表），其余仍 shimmer——
 * 这样封面与整表下载解耦，hero 能尽早交接。封面块**不能**包在 shimmer 容器里，
 * 否则扫光会扫到真封面；故此时 shimmer 只挂在下方按钮/行。
 */
@Composable
internal fun TrackListSkeleton(
    coverUrl: String? = null,
    title: String = "",
    onCoverRect: ((Rect) -> Unit)? = null,
    onCoverReady: (() -> Unit)? = null,
    coverSharedModifier: Modifier = Modifier,
    /**
     * 顶部标题条的实测高度（[TrackListMetrics] 的 stickyTop）。
     *
     * 骨架整体装在**壳子**里，而壳子顶就在标题条下沿 —— 真列表的头部 item 因此把顶空档
     * 记成 `HeroCoverTop - stickyTop`（见 LazyColumn 的 `item(key = "header")`）。骨架若还按
     * `HeroCoverTop` 铺，就整整低一个标题条（≈32dp），骨架→真列表的交接会「跳一下」。
     */
    stickyTop: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // 展开动画期间 hero 正顶着封面：骨架封面与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    Column(
        modifier
            .fillMaxSize()
            .then(if (coverUrl == null) Modifier.shimmer() else Modifier)
            // 与真列表同一个顶部空档（见 LazyColumn 的头部 item）：差一点就是「跳一下」。
            .padding(top = TrackListMetrics.HeroCoverTop - stickyTop),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TrackListMetrics.SideInset),
            verticalAlignment = Alignment.Top,
        ) {
            val coverModifier = Modifier
                .size(TrackListMetrics.CoverSize)
                .then(
                    if (onCoverRect != null) {
                        Modifier.onGloballyPositioned {
                            onCoverRect.invoke(Rect(it.localToWindow(Offset.Zero), it.size.toSize()))
                        }
                    } else {
                        Modifier
                    },
                )
                // 官方共享元素：与入口卡片封面同 key（骨架阶段先挂，面板一出现即可 morph）。
                .then(coverSharedModifier)
                // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐。
                .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f }
            if (coverUrl != null) {
                Box(coverModifier.clip(CirroShape.CardSmall)) {
                    BigCoverVisual(
                        coverUrl = coverUrl,
                        name = title,
                        modifier = Modifier.fillMaxSize(),
                        showName = false,
                        scrimAlpha = 0f,
                        requestSize = BannerCoverSize,
                        onLoadSuccess = onCoverReady,
                    )
                }
            } else {
                SkeletonBox(coverModifier, CirroShape.CardSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
            ) {
                SkeletonLine(widthFraction = 0.9f, height = 20.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonLine(widthFraction = 0.55f, height = 14.dp)
                Spacer(Modifier.height(14.dp))
                SkeletonLine(widthFraction = 0.75f, height = 12.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TrackListMetrics.SideInset)
                .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonBox(Modifier.weight(1f).height(40.dp), CirroShape.Pill)
            }
        }
        Spacer(Modifier.height(24.dp))
        // 面板整块：**不自带圆角** —— 顶角由壳子统一给（与真列表的吸顶「播放全部」行同一种
        // 关系）。自带一份会在页面中间多立一个圆角顶，角外还露出被裁过的糊底。
        // 但**必须自带不透明底**：糊底一直铺到这一带，不铺底的话骨架行之间会透出封面色。
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = TrackListMetrics.SideInset, end = 4.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(TrackListMetrics.DiscSize), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(widthFraction = 0.35f, height = 16.dp)
                    SkeletonLine(widthFraction = 0.5f, height = 12.dp)
                }
            }
            repeat(6) {
                // 与真行同一套容器与内缩（条目卡 + 16dp 封面内缩）：骨架↔列表是直接切，
                // 差一点就是「跳一下」。
                Box(Modifier.fillMaxWidth().cirroEntrySurface()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = TrackListMetrics.RowInset - CirroContainer.Inset,
                                end = 4.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(Modifier.size(TrackListMetrics.RowCover), CirroShape.Chip)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
                            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
                        }
                    }
                }
            }
        }
    }
}
