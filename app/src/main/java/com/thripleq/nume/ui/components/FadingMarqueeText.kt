package com.thripleq.nume.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 单行文字：放得下就静置；放不下则横向跑马（单向无缝），并在左右边缘做透明度渐变遮罩。
 *
 * 自研的目的是那层遮罩 —— 只在滚动时浮现：起滚时淡入、滚回行首后淡出，带渐变过渡。
 * 滚动**逻辑抄的是 AOSP `BasicMarquee`**（即 `Modifier.basicMarquee` 的内部实现）：
 *
 * 1. **量一次、画两遍**：文本只测量一次，滚动时把同一份 layout 按「文本宽 + 空档」的间距
 *    画两遍，整体裁到容器宽度。不用 `Row` 排两份文本 —— `Row` 会把「剩余宽」分给第二份，
 *    第二份被压成一个字，那个残缺的开头正是「标题开头不对」的来源。
 * 2. **单向循环**：始终向左匀速滚过「文本宽 + 空档」，滚到第二份的行首正好落在容器左缘时，
 *    与第一份的行首逐像素重合 —— 无缝接回开头、不反向、不跳。
 * 3. **尺寸一变就复位**：内容宽 / 容器宽 / 空档 任一变化都重新从行首起滚（AOSP 用
 *    `snapshotFlow` + `collectLatest`，这里用 `LaunchedEffect` 的 key 达到同一效果）。
 *    播放页的标题块在展开壳里逐帧变字号与可用宽度，靠这条保证「一进播放页先看到开头」，
 *    而不是让相位停在半路、把开头埋在屏幕外。
 * 4. **停顿只在行首**（AOSP 的 repeatDelay 语义）：每轮从行首静置起步，末尾不停留。
 *
 * 行首位置可证：第一份的行首 x = -offset，`offset = 0` 时恰为容器左缘。空档的意义是让
 * 「读完 → 回到开头」看得见：两份硬贴时循环点两侧像素相同，开头会被当成无尽长条的一部分埋掉。
 *
 * @param velocity 滚动速度，dp/秒（AOSP 默认 30）。
 * @param repeatDelayMillis 每轮之间的行首停顿，毫秒（AOSP 默认 1200）。
 * @param initialDelayMillis 首次起滚前的停顿，默认同 [repeatDelayMillis]。
 * @param fadeWidth 左右遮罩的渐变宽度。
 * @param fadeMillis 遮罩淡入/淡出的过渡时长。
 * @param spacingSpaces 首尾之间的空档 = 同字体下几个空格字符宽（「首尾之间加一些空格」）；0 = 两份硬贴。
 */
@Composable
fun FadingMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    velocity: Dp = 30.dp,
    repeatDelayMillis: Int = 1200,
    initialDelayMillis: Int = repeatDelayMillis,
    fadeWidth: Dp = 18.dp,
    fadeMillis: Int = 280,
    spacingSpaces: Int = 4,
) {
    // 标题/歌手来自接口，首尾空白不参与展示与测量 —— 否则行首会先顶出一段空白。
    val label = remember(text) { text.trim() }
    val measurer = rememberTextMeasurer()
    val layout = remember(label, style) {
        measurer.measure(
            text = AnnotatedString(label),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
    val contentPx = layout.size.width
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val velocityPx = with(density) { velocity.toPx() }
    val fadePx = with(density) { fadeWidth.toPx() }
    // 高度按实测写死：本组件自己画文本（不再由 Text 组合撑高），别让行高跟 Text 版不一致。
    val textHeight = with(density) { layout.size.height.toDp() }
    // 一个空格的实际宽度 = w("| |") - w("||")：直接量 "    " 会因行尾空白被裁而量成 0。
    val spacePx = remember(style, density) {
        fun widthOf(s: String) = measurer.measure(
            text = AnnotatedString(s),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        ).size.width
        (widthOf("| |") - widthOf("||")).coerceAtLeast(0)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerPx = constraints.maxWidth
        val scrolling = contentPx > containerPx
        // 空档不超过半个容器：否则滚动中会出现「窗口里全是空档」的一段（AOSP 也按容器比例取）。
        val spacingPx = (spacePx * spacingSpaces.coerceAtLeast(0)).coerceAtMost(containerPx / 2)

        val offset = remember { Animatable(0f) }
        // 1 = 两侧完全淡出，0 = 不淡出。只在滚动相位里抬到 1。
        val fade = remember { Animatable(0f) }

        LaunchedEffect(
            label, contentPx, containerPx, spacingPx, velocityPx,
            initialDelayMillis, repeatDelayMillis, fadeMillis,
        ) {
            if (!scrolling || velocityPx <= 0f) {
                offset.snapTo(0f)
                fade.snapTo(0f)
                return@LaunchedEffect
            }
            // 一份「文本 + 空档」：滚过它就等于把下一份文本带到行首。
            val loopWidth = (contentPx + spacingPx).toFloat()
            val duration = ceil(loopWidth / (velocityPx / 1000f)).toInt().coerceAtLeast(1)
            offset.snapTo(0f)
            fade.snapTo(0f)
            delay(initialDelayMillis.toLong())
            while (true) {
                // 起滚的同时淡入：两条动画并行，读完即到终点（第二份文本刚好对齐行首）。
                coroutineScope {
                    launch { fade.animateTo(1f, tween(durationMillis = fadeMillis)) }
                    offset.animateTo(loopWidth, tween(duration, easing = LinearEasing))
                }
                // 回卷到行首（与第二份的位置逐像素重合，画面无变化），再让遮罩平滑淡出。
                offset.snapTo(0f)
                fade.animateTo(0f, tween(durationMillis = fadeMillis))
                delay(repeatDelayMillis.toLong())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(textHeight)
                .clipToBounds()
                // 离屏合成，DstIn 遮罩才只作用于本组件画出的内容（不影响下层）。
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val o = offset.value
                    val cycle = (contentPx + spacingPx).toFloat()
                    val ltr = layoutDirection == LayoutDirection.Ltr
                    // 第一份的行首 = -o（o = 0 时贴容器左缘）；第二份在第一份之后一个 cycle。
                    // RTL 镜像：行首换到右缘，两份位置整体镜像。
                    val firstX = if (ltr) -o else containerPx - contentPx + o
                    val secondX = if (ltr) cycle - o else o - spacingPx
                    drawText(layout, color = color, topLeft = Offset(firstX, 0f))
                    // 第二份只在真的滚动时画：放得下时 offset 恒为 0，第二份会停在
                    // 「文本宽 + 空档」处，把同一句话露出第二遍（短标题最明显）。
                    if (scrolling) {
                        drawText(layout, color = color, topLeft = Offset(secondX, 0f))
                    }
                    val f = fade.value
                    if (f > 0.001f) {
                        val fw = fadePx.coerceAtMost(size.width / 2f)
                        val stop = (fw / size.width).coerceIn(0f, 0.5f)
                        val edge = Color.White.copy(alpha = 1f - f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to edge,
                                stop to Color.White,
                                1f - stop to Color.White,
                                1f to edge,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        )
    }
}
