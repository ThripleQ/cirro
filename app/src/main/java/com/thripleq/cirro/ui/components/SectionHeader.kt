package com.thripleq.cirro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 区块标题 —— 全 app 统一：`titleLarge` + Bold + `primary`，边距 12/18/10。
 *
 * 取代 5 套实现（探索 `titleLarge` / 歌手 & 搜索 `titleMedium+Bold` / 评论 `titleSmall+Bold`
 * + `onSurface` / 榜单内联）。
 *
 * 【字号与左边距按 kanade 主页实测定（2026-10-17 重对照）】kanade 的区块标题字高约
 * 20dp、单字宽 21dp ≈ **22sp**（`titleLarge`），左边距 **12dp**；此前用的是
 * `titleMedium`（16sp，字高 14dp）+ 16dp 边距，比 kanade 小一圈也偏右一点。
 * 换算同样是 px ÷ 2.75（440dpi），见 `KanadeCardSpec` 那条注释。
 */
@Composable
fun CirroSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
    )
}

/** 区块标题骨架：与 [CirroSectionHeader] 同位置同字形高度。 */
@Composable
fun CirroSectionHeaderSkeleton(modifier: Modifier = Modifier) {
    SkeletonLine(
        modifier = modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        widthFraction = 0.4f,
        height = 22.dp,
    )
}
