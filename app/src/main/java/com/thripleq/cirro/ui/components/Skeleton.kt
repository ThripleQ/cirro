package com.thripleq.cirro.ui.components

import com.thripleq.cirro.ui.theme.CirroShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import com.valentinilk.shimmer.shimmer

/**
 * 骨架占位基元：一块圆角纯色矩形。
 *
 * 微光由**容器**上的 `Modifier.shimmer()` 统一提供（见 shimmer 库）——骨架屏根节点挂一次，
 * 所有子占位块共用同一次扫光，不会各转各的。本组件只负责形状与底色，故不做任何动画。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = CirroShape.Chip,
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
}

/** 文字行占位：按屏宽比例给出宽度。 */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    shape: Shape = CirroShape.Chip,
) {
    SkeletonBox(modifier.fillMaxWidth(widthFraction).height(height), shape)
}

/**
 * 图片加载中的微光占位。
 *
 * - **Loading/Empty**：跑扫光动画的 `surfaceVariant`。
 * - **Error**：静态 `surfaceVariant`（加载失败也要留一块灰底，否则外层 Box 无底色 → 空白洞）。
 * - **Success**：不组合（由不透明图片自然覆盖）。
 *
 * 扫光仅在 Loading/Empty 时跑，加载结束即从组合中移除，不会像「图片下方常驻 shimmer Box」
 * 那样空转无限动画（那是之前列表/网格卡顿的主要来源之一）。
 *
 * 与图片同层、放在图片之前即可。
 */
@Composable
fun ShimmerImagePlaceholder(
    painter: AsyncImagePainter,
    modifier: Modifier = Modifier,
) {
    when (painter.state) {
        is AsyncImagePainter.State.Loading,
        is AsyncImagePainter.State.Empty,
        -> Box(
            modifier
                .shimmer()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        is AsyncImagePainter.State.Error -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
        else -> Unit
    }
}
