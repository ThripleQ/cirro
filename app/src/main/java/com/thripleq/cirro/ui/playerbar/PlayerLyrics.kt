package com.thripleq.cirro.ui.playerbar

import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.thripleq.cirro.core.model.LyricLine
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

/** 歌词行距：内联/沉浸两档共用（原内联 14dp、沉浸 20dp —— 统一取沉浸档，字号也是同一套）。 */
private val LyricLineSpacing = 20.dp

/** 上下留白：两档共用；上下各留出一条渐隐带（36dp）的高度，首尾行不会被边缘蒙版裁掉。 */
private val LyricVerticalPadding = 40.dp

/** 当前行在视口里的停靠比例（约 2/5，上方留足上下文）。两档共用，避免换档时重排。 */
private const val LyricAnchor = 0.40f

/**
 * 歌词面板：随播放进度自动滚动 + 高亮当前行。**行本身不做任何点击/seek** —— 用户
 * 2026-10-24 明确「按歌词 seek」是以后的功能区域，现在歌词区的任何位置都算「无关地方」
 * （回封面由调用方的整块点击负责）。
 * 用户手动滚动后暂停自动滚动 4s，避免和阅读抢（用 [LazyListState.isScrollInProgress]
 * 分辨，本组件自己触发的滚动用 programmatic 标记排除）。
 *
 * **[positionState] 收的是 State 而不是 Long**：进度是 250ms 一次的轮询，若在调用方解包成
 * Long 传进来，那份值的变化会落在调用方的重组作用域里（播放页内容区整块陪跑）。
 * 解包点放在本组件内部 —— 只有歌词面板在场时才会有这个订阅。
 *
 * **两级版式完全一致**（用户 2026-10-24：「进入沉浸模式时，已显示的歌词不被顶走，行间距
 * 也不变」）：字号、行距、上下留白、自动滚动停靠位都不随档位变化 —— 正文 19sp / 选中 24sp、
 * 行距 [LyricLineSpacing]、留白 [LyricVerticalPadding]、停靠 [LyricAnchor]。换档只改变
 * 视口底边（底部控制簇退场、歌词向下变高），已显示的行原地不动。
 *
 * 列表两级都可滚动：内联档不再是「冻结的预览」，手指翻歌词时列表照常跟手，
 * 换档判定由调用方的嵌套滚动观察（不拦截、不消费），所以进入沉浸的过程不会把手势吃掉。
 */
@Composable
internal fun LyricsView(
    uiState: LyricsUiState,
    positionState: State<Long>,
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
            val lineSpacing = LyricLineSpacing
            val padVertical = LyricVerticalPadding

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
                    // 当前行滚到视口约 [LyricAnchor] 处（留上方上下文），负偏移把行往下带。
                    // 停靠比例两档共用 —— 换档时视口只向下变高，已有行不重排。
                    val viewport = listState.layoutInfo.viewportSize.height
                    listState.animateScrollToItem(
                        currentIndex,
                        -(viewport * LyricAnchor).roundToInt(),
                    )
                } finally {
                    programmatic = false
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier
                    // 上下边缘淡出：歌词档的视口边界正好压在标题/页眉上，硬切会在
                    // 「行被裁一半」处留下一条很脏的截断线。用离屏层做 DstIn 蒙版，
                    // 与父级渐变底色无关（壳色在展开中会变，不能用固定色块盖）。
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = 36.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black,
                                startY = 0f,
                                endY = fade,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Transparent,
                                startY = size.height - fade,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    // 抢滚动检测：自动滚动进行中（programmatic）用户按下，原实现只在
                    // isScrollInProgress 翻转时记录、且排除 programmatic——按下未滚的瞬间
                    // 不记抑制，等动画几百 ms 跑完、 currentIndex 再变时歌词会立刻把用户
                    // 刚按住的位置抢回去，观感就是「列表自己跳」。按下即记，抢滚动从
                    // 手指落下那一刻起就让位 4s。
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
                contentPadding = PaddingValues(vertical = padVertical, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(lineSpacing),
            ) {
                itemsIndexed(lines) { i, line ->
                    LyricRow(
                        line = line,
                        selected = i == currentIndex,
                    )
                }
            }
        }
    }
}

/** 单行歌词：选中行放大加粗高亮，未选中行弱化；有翻译时在下方补一行小字。
 *
 * 选中态做**连续插值**（进度值 sel: 0→1）：颜色 onSurfaceVariant→primary、透明度 0.55→1
 * 全部随 sel 平滑过渡，字重在 sel 过半瞬间切换（Compose 不支持字重插值，
 * 中点切换落在两条曲线交叉处，视觉突兀最小）。tween(Standard) 与全局微交互同族——
 * 逐行高亮是播放页最频繁的动画，默认 spring 的尾巴会拖出"果冻感"。
 * 只有发生选中/失选的行在动画，其余行 target 不变零开销。
 *
 * 字号**两级统一**（用户 2026-10-24：「歌词页 1 级的字号应该和沉浸页面用一个字号」）：
 * 未选中 19sp、选中 24sp，内联档与沉浸档完全一致；换档时只有行距/留白在变，
 * 歌词本身不跳字号。 */
@Composable
internal fun LyricRow(line: LyricLine, selected: Boolean) {
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "lyricSel",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = androidx.compose.ui.unit.lerp(19.sp, 24.sp, sel),
                fontWeight = if (sel > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary,
                sel,
            ).copy(alpha = lerp(CirroFade.LYRIC, 1f, sel)),
            textAlign = TextAlign.Center,
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
