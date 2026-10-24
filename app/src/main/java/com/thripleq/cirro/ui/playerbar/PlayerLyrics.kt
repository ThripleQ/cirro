package com.thripleq.cirro.ui.playerbar

import android.os.SystemClock
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.thripleq.cirro.ui.theme.Motion
import com.thripleq.cirro.ui.theme.CirroFade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.thripleq.cirro.core.model.LyricLine
import com.thripleq.cirro.core.model.LyricWord
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
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

/** 上下留白：两档共用；上下各留出一条渐隐带（36dp）的高度，首尾行不会被边缘蒙版裁掉。
 *
 *  它同时也是列表的 `contentPadding`，比黄金位（见 [LyricGoldenAnchor]）小得多 —— 所以
 *  **开头几行贴视口顶、随着内容变多才逐渐落到黄金位**。这是刻意的：内联档视口只有约
 *  530dp，若把顶部留白直接按黄金位（约 210dp）铺开，第一句歌词要压到视口 4 成的位置，
 *  标题下方会空出一大片。中段之后焦点行稳稳停在黄金位，不受影响。 */
private val LyricVerticalPadding = 40.dp

/**
 * 焦点行的**黄金分割停靠点**：从**屏幕**顶部量 0.382（= 1 − 0.618，短的一段在上）。
 *
 * 用户 2026-10-24：「焦点歌词行应该在屏幕靠上部分，而不是中间。取个黄金比例吧。」
 *
 * ⚠️ 口径是**屏幕**，不是视口 —— 这是全部要点。此前按「视口高度 × 0.40」停靠，而
 * 视口高度在两档之间差很多（内联档底部压着控制簇约 208dp，沉浸档整簇退场），于是同一个
 * 比例算出来两个不同的像素值：
 *
 * | 档 | 视口高 | 旧停靠(×0.40) | 焦点行落到屏幕 |
 * |---|---|---|---|
 * | 内联 | ≈534dp | ≈214dp | ≈38% ✅ 靠上 |
 * | 沉浸 | ≈742dp | ≈297dp | ≈48% ❌ **正中** |
 *
 * 而且换档瞬间 `LaunchedEffect` 不重跑（焦距没变），歌词当场不动；可是**下一句**一出来
 * 就按新的视口高度重算 —— 观感就是「一进沉浸，焦点行自己出溜到屏幕中间」。
 *
 * 改成「屏幕高 × 0.382 − 视口顶的 y」之后，锚点是个**与档位无关的绝对值**（视口顶在
 * 两档里都不动：标题常驻顶部），换档后切句也不再移动，两个诉求一次满足。
 */
private const val LyricGoldenAnchor = 0.382f

/**
 * 量不到窗口尺寸时的**退化口径**：视口高度 × 0.40（即改前那套算法）。
 *
 * 存在的唯一意义是「宁可退回旧观感，也不能把自动滚动弄坏」—— 停靠值一旦缺失，若直接跳过
 * 滚动，歌词就再也不跟进度走了，那比焦点行位置偏低严重得多。这条路径正常走不到。
 */
private const val LyricFallbackAnchor = 0.40f

/**
 * 焦点行在**视口内**的停靠位置（px）：屏幕高 × [LyricGoldenAnchor] 减去视口顶的偏移。
 *
 * 抽成纯函数是为了能单测 —— 它只有两个整数输入，却是「焦点行到底落在屏幕哪里」的全部
 * 依据（见 `LyricAnchorTest`）。
 *
 * @param windowHeightPx 窗口高度（含系统栏，与 [viewportTopPx] 同坐标系）
 * @param viewportTopPx 歌词视口顶边在窗口中的 y；未测量时传负值
 * @return 视口内锚点；窗口尺寸不可用时返回 -1，调用方退回 [LyricFallbackAnchor]。
 *   `coerceAtLeast(0)` 兜的是横屏/悬浮窗那种「视口顶本身就在黄金线以下」的极端情形 ——
 *   此时给负锚点会把目标行推出视口上方，列表再也不会回正。
 */
internal fun lyricAnchorPx(windowHeightPx: Int, viewportTopPx: Int): Float {
    if (viewportTopPx < 0 || windowHeightPx <= 0) return -1f
    return (windowHeightPx * LyricGoldenAnchor - viewportTopPx).coerceAtLeast(0f)
}

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
 * 行距 [LyricLineSpacing]、留白 [LyricVerticalPadding]、停靠 [LyricGoldenAnchor]（换算成
 * 与视口高度**无关**的绝对像素值，见该常量的说明）。换档只改变视口底边（底部控制簇退场、
 * 歌词向下变高），已显示的行原地不动，之后切句也不会移动。
 *
 * 列表两级都可滚动：内联档不再是「冻结的预览」，手指翻歌词时列表照常跟手，
 * 换档判定由调用方的嵌套滚动观察（不拦截、不消费），所以进入沉浸的过程不会把手势吃掉。
 */
@Composable
internal fun LyricsView(
    uiState: LyricsUiState,
    positionState: State<Long>,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        LyricsUiState.Idle, LyricsUiState.Loading ->
            LyricsMessage("加载歌词…", modifier, loading = true)

        LyricsUiState.Empty -> LyricsMessage("暂无歌词", modifier)

        is LyricsUiState.Ready -> {
            val lines = uiState.lyrics.lines
            // 只在**跨行**时重组列表，而不是每 250ms 一次：逐字高亮自己按帧推进，
            // 列表没必要陪着进度轮询重跑。
            val currentIndex by remember(lines, positionState) {
                derivedStateOf { currentLyricIndex(lines, positionState.value) }
            }
            val listState = rememberLazyListState()
            var programmatic by remember { mutableStateOf(false) }
            var lastUserScrollAt by remember { mutableLongStateOf(0L) }
            val lineSpacing = LyricLineSpacing
            val padVertical = LyricVerticalPadding
            // 视口顶在窗口中的 y 与窗口高度：两者在**同一个** onGloballyPositioned 里取，
            // 坐标系自洽（都是 window 坐标）。视口顶用来把「屏幕 38.2%」换算成「视口内多少
            // px」—— 视口不顶屏（上面压着标题），不减这个偏移焦点行就会偏低半屏标题高。
            // 未测量时 -1 标记，滚动逻辑等到测量完再动手，避免首帧按错值滚一下。
            val view = LocalView.current
            var viewportTopPx by remember { mutableIntStateOf(-1) }
            var windowHeightPx by remember { mutableIntStateOf(0) }
            val anchorPx = remember(viewportTopPx, windowHeightPx) {
                lyricAnchorPx(windowHeightPx, viewportTopPx)
            }

            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (scrolling && !programmatic) lastUserScrollAt = System.currentTimeMillis()
                }
            }
            LaunchedEffect(currentIndex, anchorPx) {
                if (currentIndex < 0) return@LaunchedEffect
                if (System.currentTimeMillis() - lastUserScrollAt < 4_000L) return@LaunchedEffect
                // 量到窗口尺寸就用黄金位（与档位无关的绝对值）；量不到退回视口比例，
                // 保证「停靠值缺了也照样自动滚」（见 [LyricFallbackAnchor]）。
                val targetPx = if (anchorPx >= 0f) {
                    anchorPx
                } else {
                    listState.layoutInfo.viewportSize.height * LyricFallbackAnchor
                }
                programmatic = true
                try {
                    // 目标行没被测量（seek 跳远）时先粗定位；随后统一用下面的逐帧跟随后收敛。
                    if (listState.layoutInfo.visibleItemsInfo.none { it.index == currentIndex }) {
                        listState.scrollToItem(currentIndex, -targetPx.roundToInt())
                    }
                    // **逐帧跟随**而不是 animateScrollToItem：后者按“第几行”估算距离，一次性
                    // 滚完；而选中切换会一步改字号（24sp↔19sp，长行折行数可能变化），
                    // 列表行高随之跳变。这里每帧读目标行的真实 offset 再逼近，高度变化被
                    // 持续吸收，最终精确停在 [LyricGoldenAnchor] 对应的 [targetPx]，没有尾巴。
                    var lastFrame = 0L
                    var startedAt = 0L
                    while (true) {
                        val layout = listState.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: break
                        val now = withFrameMillis { it }
                        if (startedAt == 0L) startedAt = now
                        // 手指按下就交还控制权（pointerInput 会同时记 lastUserScrollAt）。
                        if (System.currentTimeMillis() - lastUserScrollAt < 4_000L) break
                        // 首行贴顶时目标锚点不可达，delta 永远不为 0 —— 用超时兜底。
                        if (now - startedAt > Motion.MicroMs * 4) break
                        val delta = item.offset - targetPx
                        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).toFloat().coerceIn(4f, 40f)
                        lastFrame = now
                        if (abs(delta) < 0.5f && now - startedAt >= Motion.MicroMs) break
                        // 70ms 时间常数：起步跟手、约 250ms 收敛；尾巴是渐变而不是回弹。
                        val step = delta * (1f - exp(-dt / 70f))
                        if (abs(step) < 0.02f) {
                            if (now - startedAt >= Motion.MicroMs) break
                        } else {
                            // 已经到边界（列表首/尾）就停，别空转。
                            if (abs(listState.scrollBy(step)) < 0.02f) break
                        }
                    }
                } finally {
                    programmatic = false
                }
            }

            LazyColumn(
                state = listState,
                modifier = modifier
                    // 量停靠基准：视口顶在窗口中的位置 + 窗口高度（同一个回调里取，坐标系
                    // 一致）。换档时视口顶不变、只有底边在变，所以锚点算出来两档相同 ——
                    // 这正是「焦点行换档后不再往下跑」的兑现方式。
                    .onGloballyPositioned {
                        viewportTopPx = it.boundsInWindow().top.roundToInt()
                        windowHeightPx = view.rootView.height
                    }
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
                        positionState = positionState,
                        playing = playing,
                    )
                }
            }
        }
    }
}

/** 已唱完的片段数：最后一个 `timeMs <= positionMs` 的下标 + 1。二分。 */
private fun litWordCount(words: List<LyricWord>, positionMs: Long): Int {
    var lo = 0
    var hi = words.size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (words[mid].timeMs <= positionMs) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * 最后一条已开始片段的**渐染进度** 0..1。
 *
 * 不是"整段按时长从灰染到红"—— 长音符会半灰半红拖一秒，像没唱完；改成**固定染窗**：
 * 每个片段起唱后最多 260ms 染满，短片段按自身时长收短。听感上是"这一字亮起来"，
 * 而不是颜色一直追着人声跑。
 */
private fun wordFillAt(words: List<LyricWord>, lit: Int, positionMs: Long): Float {
    if (lit <= 0) return 0f
    val word = words[lit - 1]
    if (word.durationMs <= 0L) return 1f
    val elapsed = positionMs - word.timeMs
    if (elapsed <= 0L) return 0f
    return (elapsed.toFloat() / min(word.durationMs, 260L)).coerceIn(0f, 1f)
}

/**
 * 逐字文本：已唱片段 accent，**当前片段从灰到 accent 渐染**（[wordFillAt]），未唱片段弱化。
 *
 * 进度源是 250ms 轮询 —— 对逐字太粗（中文一个字常只有 200~400ms，会一次点亮两三个字、
 * 甚至跳字）。这里用采样值 + 帧时钟插值：每 250ms 用新采样重设基准，其余时间按
 * `elapsedRealtime` 线性推进。
 *
 * **排版只做一次，逐帧只重绘**（用户 2026-10-24 定位的抖动根因）：此前每帧重建
 * AnnotatedString，`Text` 会跟着重新测量/排版整段；长行在字号动画中折行数一变，
 * 整行高度就跳，列表跟着重排 —— 这正是"滚到位后抖一下"。现在用 [TextMeasurer] 把
 * 段落布局缓存起来（键：文本/字号/字重/宽度），整行先画弱化色，再把已唱片段和当前片段
 * 按 `TextLayoutResult` 的字形范围 [clipPath] 叠 accent；`lit`/`fill` 变化只触发 draw，
 * 不再触发布局。字号/字重也由调用方在选中切换时一次性定死，动画期间不变。
 *
 * 暂停时 `sampledMs` 已是权威值（seek 也会经 position 回调立即刷新），不做外推。
 */
@Composable
private fun WordTimedText(
    words: List<LyricWord>,
    sampledMs: Long,
    playing: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    sungColor: Color,
    unsungColor: Color,
) {
    var lit by remember(words) { mutableIntStateOf(litWordCount(words, sampledMs)) }
    var fill by remember(words) { mutableFloatStateOf(wordFillAt(words, lit, sampledMs)) }

    LaunchedEffect(words, sampledMs, playing) {
        fun applyAt(positionMs: Long) {
            val nextLit = litWordCount(words, positionMs)
            if (nextLit != lit) lit = nextLit
            val nextFill = wordFillAt(words, nextLit, positionMs)
            // 小于 0.4% 的变化肉眼不可见，不写 State，避免无意义重绘。
            if (abs(nextFill - fill) > 0.004f) fill = nextFill
        }
        applyAt(sampledMs)
        if (!playing) return@LaunchedEffect
        val baseAt = SystemClock.elapsedRealtime()
        while (true) {
            withFrameMillis { }
            applyAt(sampledMs + (SystemClock.elapsedRealtime() - baseAt))
        }
    }

    val plain = remember(words) { words.joinToString("") { it.text } }
    // 每段的字符起点，用来把 TextLayoutResult 的字符下标映射回片段边界。
    val starts = remember(words) {
        IntArray(words.size).also { out ->
            var acc = 0
            words.forEachIndexed { i, word ->
                out[i] = acc
                acc += word.text.length
            }
        }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 宽度定死（与父约束同宽），这样 textAlign=Center 的居中结果和原来 Text 一致。
        val widthPx = constraints.maxWidth
        val layout = remember(plain, style, widthPx) {
            measurer.measure(
                text = plain,
                style = style,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { layout.size.height.toDp() }),
        ) {
            drawText(layout, color = unsungColor)
            val current = lit - 1
            if (current >= 0) {
                val start = starts[current]
                if (start > 0) {
                    clipPath(layout.getPathForRange(0, start)) {
                        drawText(layout, color = sungColor)
                    }
                }
                val end = start + words[current].text.length
                if (end > start) {
                    // 渐染：整段一次性提亮，靠 alpha 与底下的弱化色混合，不需要拆分字形。
                    clipPath(layout.getPathForRange(start, end)) {
                        drawText(layout, color = sungColor, alpha = fill)
                    }
                }
            }
        }
    }
}

/** 单行歌词：选中行放大加粗高亮，未选中行弱化；有翻译时在下方补一行小字。
 *
 * **字号/字重在选中切换时一步到位，不做插值**（用户 2026-10-24 定位的抖动根因）：
 * Android 的文字排版随字号/字重连续变化，长行会在 200ms 动画中途改变折行数 —— 行高
 * 跳一次、下面的行全跟着重排，滚动就会"到位后抖一下"。所以布局只在选中瞬间定死一次：
 * 未选中 19sp/Normal、选中 24sp/SemiBold。
 * 视觉上的"长大"改用 [graphicsLayer] 缩放（纯绘制，不参与测量）：新选中的行从
 * 19/24 缩放到 1，邻居和行高都不动。缩放顶点取顶边中点，起止位置与未选中的 19sp
 * 文本对齐。失选行直接切回小字号（它是往上退场的，不需要再缩一次）。
 *
 * 颜色/透明度仍随 sel 连续插值：onSurfaceVariant→primary、0.55→1。tween(Standard)
 * 与全局微交互同族——逐行高亮是播放页最频繁的动画，默认 spring 的尾巴会拖出"果冻感"。
 *
 * 字号**两级统一**（用户 2026-10-24：「歌词页 1 级的字号应该和沉浸页面用一个字号」）：
 * 未选中 19sp、选中 24sp，内联档与沉浸档完全一致；换档时只有行距/留白在变，
 * 歌词本身不跳字号。
 *
 * **逐字**（2026-10-24）：当前行有 yrc 片段时，已唱片段染 accent、未唱片段保持弱化色，
 * **正在唱的那一段用 260ms 从灰渐染到 accent**（拼音/英文整词，中文一字一段；边界完全
 * 来自服务端，不猜）。没有 yrc 的歌（`words` 为空）仍是整行变 accent 的原行为。 */
@Composable
internal fun LyricRow(
    line: LyricLine,
    selected: Boolean,
    positionState: State<Long>,
    playing: Boolean,
) {
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "lyricSel",
    )
    // 只有当前行订阅进度：其余行读不到 positionState，不会随轮询重组。
    val sampledMs = if (selected) positionState.value else 0L
    val wordTimed = selected && line.words.isNotEmpty()

    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val unsungColor = dimColor.copy(alpha = lerp(CirroFade.LYRIC, 0.9f, sel))
    val sungColor = accentColor.copy(alpha = lerp(0.85f, 1f, sel))
    val lineColor = lerp(dimColor, accentColor, sel)
        .copy(alpha = lerp(CirroFade.LYRIC, 1f, sel))

    // 排版尺寸只由 selected 决定，不跟着 sel 走（见上方注释）。
    val baseFontSize = if (selected) 24.sp else 19.sp
    val baseFontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    // 绘制缩放：新选中行从 19sp 的视觉大小长到 24sp；不影响布局。
    val grow = if (selected) lerp(19f / 24f, 1f, sel) else 1f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            if (wordTimed) {
                WordTimedText(
                    words = line.words,
                    sampledMs = sampledMs,
                    playing = playing,
                    fontSize = baseFontSize,
                    fontWeight = baseFontWeight,
                    sungColor = sungColor,
                    unsungColor = unsungColor,
                )
            } else {
                Text(
                    text = remember(line.text) { AnnotatedString(line.text) },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = baseFontSize,
                        fontWeight = baseFontWeight,
                    ),
                    color = lineColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
