package com.thripleq.nume.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 通用媒体行：`封面 + 标题 + 副标题 (+ 尾部)`。
 *
 * 取代各屏 7 套同构实现（`SearchSongRow`/`SearchMediaRow`/`TrackRow`/`SongRow`/`ProgramRow`/
 * `SmallTrackRow`/榜单行），统一到单一规范：
 * - 封面 [NumeArt.Row]（52dp）+ [NumeShape.Chip]，解码 [NumeArt.RequestRow]；
 * - 标题 `bodyLarge`/`onSurface`，副标题 `bodySmall`/`onSurfaceVariant`；
 * - 行容器 `numeEntrySurface()` + 内缩 8/8；
 * - [payTag] 非空时在**副标题前面**贴一枚付费/VIP 徽标（[NumePayBadge]），与副标题同一行、
 *   垂直居中对齐 —— kanade 的歌曲信息设计，位置与间距见 `PayBadge.kt` 的注释。
 */
@Composable
fun NumeMediaRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    coverUrl: String? = null,
    coverShape: Shape = NumeShape.Chip,
    coverSize: Dp = NumeArt.Row,
    coverRequestSize: Int = NumeArt.RequestRow,
    coverModifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
    payTag: PayTag? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .numeEntrySurface()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumeArtwork(
            url = coverUrl,
            contentDescription = title,
            modifier = coverModifier,
            size = coverSize,
            shape = coverShape,
            requestSize = coverRequestSize,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                // 副标题这一行恒用 Row 包（无徽标时只有一个带权重的子项，观感与裸 Text 一致）：
                // 徽标在左、文字吃剩余宽度并在末尾省略号，是唯一能让「徽标不被挤走」的形状。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (payTag != null) {
                        NumePayBadge(payTag)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill = false：文字短时行宽跟着内容收，徽标仍紧贴文字左侧。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * 媒体行骨架：尺寸/容器与 [NumeMediaRow] 一一对应（同一 `numeEntrySurface` + 8/8 内缩 +
 * 同一封面尺寸），避免骨架切真实行时底色/位置跳动。
 *
 * 微光由**调用方根节点**的 `Modifier.shimmer()` 统一提供，本组件不自挂动画。
 */
@Composable
fun NumeMediaRowSkeleton(
    modifier: Modifier = Modifier,
    coverSize: Dp = NumeArt.Row,
    coverShape: Shape = NumeShape.Chip,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .numeEntrySurface()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(coverSize), coverShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
        }
    }
}
