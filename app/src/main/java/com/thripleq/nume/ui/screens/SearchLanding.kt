package com.thripleq.nume.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.NumeSectionHeader
import com.thripleq.nume.ui.theme.NumeShape

/* ── 落地页：分类标签 ───────────────────────────────────── */

private val TAG_SECTIONS: List<Pair<String, List<String>>> = listOf(
    "语种" to listOf("华语", "欧美", "日语", "韩语", "粤语"),
    "风格" to listOf(
        "流行", "摇滚", "民谣", "电子", "说唱", "轻音乐", "爵士", "乡村",
        "R&B/Soul", "古典", "英伦", "金属", "朋克", "蓝调", "雷鬼", "世界音乐",
        "拉丁", "另类/独立", "New Age", "古风", "Bossa Nova", "后摇", "舞曲", "音乐剧",
    ),
    "场景" to listOf("清晨", "夜晚", "学习", "工作", "午休", "通勤", "运动", "旅行", "派对", "咖啡"),
)

@Composable
internal fun LandingContent(bottomPadding: Dp, onTag: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        TAG_SECTIONS.forEach { (title, tags) ->
            item(key = "h_$title") { NumeSectionHeader(title) }
            itemsIndexed(
                items = tags.chunked(2),
                key = { index, _ -> "$title-$index" },
            ) { index, pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index == 0) 0.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { tag ->
                        TagChip(tag, Modifier.weight(1f)) { onTag(tag) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(NumeShape.CardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
