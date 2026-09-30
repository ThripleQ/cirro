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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
internal fun LandingContent(
    bottomPadding: Dp,
    history: List<String>,
    showHistory: Boolean,
    onTag: (String) -> Unit,
    onHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 历史是往列表头部插入的：LazyColumn 默认把滚动锚点钉在原首个可见项（「语种」）上，
    // 于是刚插入的历史被顶到可视区之上。聚焦时先滚回顶部，把它顺势带出来。
    LaunchedEffect(showHistory) {
        if (showHistory) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        // 搜索历史（最近在最前）。只在搜索框聚焦（且还没输入）时露头，失焦即收起；
        // `animateItem` 让整块淡入、下方分类顺势下移——不打断落地页默认视野。
        // 清空只清历史，不影响当前搜索。
        if (showHistory && history.isNotEmpty()) {
            item(key = "h_history") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "搜索历史",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(NumeShape.Chip)
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            chipRows(history, keyPrefix = "history", animated = true) { kw -> onHistory(kw) }
        }

        TAG_SECTIONS.forEach { (title, tags) ->
            item(key = "h_$title") { NumeSectionHeader(title, Modifier.animateItem()) }
            chipRows(tags, keyPrefix = title, animated = true) { tag -> onTag(tag) }
        }
    }
}

/** 两列一行的标签块（LazyListScope 扩展）。 */
private fun androidx.compose.foundation.lazy.LazyListScope.chipRows(
    items: List<String>,
    keyPrefix: String,
    animated: Boolean = false,
    onClick: (String) -> Unit,
) {
    itemsIndexed(
        items = items.chunked(2),
        key = { index, _ -> "$keyPrefix-$index" },
    ) { index, pair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (animated) Modifier.animateItem() else Modifier)
                .padding(horizontal = 16.dp)
                .padding(top = if (index == 0) 0.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pair.forEach { label -> TagChip(label, Modifier.weight(1f)) { onClick(label) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TagChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(NumeShape.CardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
