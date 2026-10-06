package com.thripleq.cirro.ui.playerbar

import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.thripleq.cirro.core.repo.LyricLine
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/** 当前行索引：最后一个 timeMs <= position 的行；position 早于首行时返回 -1。二分。 */
internal fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

/**
 * 歌词面板：随播放进度自动滚动 + 高亮当前行，点某行跳到该行时间。
 * 用户手动滚动后暂停自动滚动 4s，避免和阅读抢（用 [LazyListState.isScrollInProgress]
 * 分辨，本组件自己触发的滚动用 programmatic 标记排除）。
 *
 * **[positionState] 收的是 State 而不是 Long**：进度是 250ms 一次的轮询，若在调用方解包成
 * Long 传进来，那份值的变化会落在调用方的重组作用域里（播放页内容区整块陪跑）。
 * 解包点放在本组件内部 —— 只有歌词面板在场时才会有这个订阅。
 */
@Composable
internal fun LyricsView(
    uiState: LyricsUiState,
    positionState: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        LyricsUiState.Idle, LyricsUiState.Loading ->
            LyricsMessage("加载歌词…", modifier, loading = true)

        LyricsUiState.Empty -> LyricsMessage("暂无歌词", modifier)

        is LyricsUiState.Ready -> {
            val positionMs = positionState.value
            val lines = uiState.lyrics.lines
            val currentIndex = currentLyricIndex(lines, positionMs)
            val listState = rememberLazyListState()
            var programmatic by remember { mutableStateOf(false) }
            var lastUserScrollAt by remember { mutableLongStateOf(0L) }

            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling && !programmatic) lastUserScrollAt = System.currentTimeMillis()
                }
            }
            LaunchedEffect(currentIndex) {
                if (currentIndex < 0) return@LaunchedEffect
                if (System.currentTimeMillis() - lastUserScrollAt < 4_000L) return@LaunchedEffect
                programmatic = true
                try {
                    // 当前行滚到视口约 1/3 处（留上方上下文），负偏移把行往下带。
                    val viewport = listState.layoutInfo.viewportSize.height
                    listState.animateScrollToItem(currentIndex, -(viewport * 0.35f).roundToInt())
                } finally {
                    programmatic = false
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier
                    // 抢滚动检测：自动滚动进行中（programmatic）用户按下，原实现只在
                    // isScrollInProgress 翻转时记录、且排除 programmatic——按下未滚的瞬间
                    // 不记抑制，等动画几百 ms 跑完、 currentIndex 再变时歌词会立刻把用户
                    // 刚按住的位置抢回去，观感就是「列表自己跳」。按下即记，抢滚动从
                    // 手指落下那一刻起就让位 4s；普通点击（非自动滚动中）不记——点行
                    // seek 后歌词仍立即滚到目标行，不被抑制误伤。
                    .pointerInput(listState) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                if (programmatic) {
                                    lastUserScrollAt = System.currentTimeMillis()
                                }
                                waitForUpOrCancellation()
                            }
                        }
                    },
                contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lines) { i, line ->
                    LyricRow(
                        line = line,
                        selected = i == currentIndex,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

/** 单行歌词：选中行放大加粗高亮，未选中行弱化；有翻译时在下方补一行小字。
 *
 * 选中态做**连续插值**（进度值 sel: 0→1）：颜色 onSurfaceVariant→primary、字号 16→19sp、
 * 透明度 0.55→1 全部随 sel 平滑过渡，字重在 sel 过半瞬间切换（Compose 不支持字重插值，
 * 中点切换落在两条曲线交叉处，视觉突兀最小）。tween(Standard) 与全局微交互同族——
 * 逐行高亮是播放页最频繁的动画，默认 spring 的尾巴会拖出"果冻感"。
 * 只有发生选中/失选的行在动画，其余行 target 不变零开销。 */
@Composable
internal fun LyricRow(line: LyricLine, selected: Boolean, onClick: () -> Unit) {
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "lyricSel",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = androidx.compose.ui.unit.lerp(16.sp, 19.sp, sel),
                fontWeight = if (sel > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary,
                sel,
            ).copy(alpha = lerp(CirroFade.LYRIC, 1f, sel)),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        line.translation?.let { translation ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = lerp(CirroFade.LYRIC, 0.85f, sel),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 歌词占位态：加载中（转圈+文案）或空（仅文案），在封面矩形内居中。 */
@Composable
internal fun LyricsMessage(text: String, modifier: Modifier = Modifier, loading: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
