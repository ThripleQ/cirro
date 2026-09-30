package com.thripleq.nume.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 区块标题 —— 全 app 统一：`titleMedium` + Bold + `primary`，边距 16/18/10。
 *
 * 取代 5 套实现（探索 `titleLarge` / 歌手 & 搜索 `titleMedium+Bold` / 评论 `titleSmall+Bold`
 * + `onSurface` / 榜单内联）。
 */
@Composable
fun NumeSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    )
}

/** 区块标题骨架：与 [NumeSectionHeader] 同位置同字形高度。 */
@Composable
fun NumeSectionHeaderSkeleton(modifier: Modifier = Modifier) {
    SkeletonLine(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        widthFraction = 0.4f,
        height = 18.dp,
    )
}
