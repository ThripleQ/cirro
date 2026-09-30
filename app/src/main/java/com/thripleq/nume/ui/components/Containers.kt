package com.thripleq.nume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 列表条目容器令牌：所有**可点条目**（歌曲行 / 歌单行 / 专辑卡 …）统一从这里取
 * 内缩与底色，保证「条目即卡片」的语言一致。
 */
object NumeContainer {
    /** 通栏列表行的左右内缩：条目卡距屏幕边 [Inset]，内容再内缩后落到 16dp 基线。 */
    val Inset: Dp = 8.dp

    /** 条目卡上下内缩：相邻两条自动留 2×[InsetV] 的缝（配合各列表既有 spacedBy）。 */
    val InsetV: Dp = 3.dp
}

/**
 * 条目容器：左右内缩 + 圆角裁剪 + `surfaceContainer` 底色。
 *
 * 放在 `.clickable` **之前**（ripple 被裁进卡片），内容 padding 放在之后。
 * 网格/横滑里的瓦片用 `inset = 0.dp`（间距由列表的 contentPadding/spacedBy 给）。
 */
@Composable
fun Modifier.numeEntrySurface(
    shape: Shape = NumeShape.CardSmall,
    inset: Dp = NumeContainer.Inset,
    vertical: Dp = NumeContainer.InsetV,
): Modifier = this
    .padding(horizontal = inset, vertical = vertical)
    .clip(shape)
    .background(MaterialTheme.colorScheme.surfaceContainer)
