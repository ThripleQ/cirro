package com.thripleq.nume.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * 单行文字：放得下就静置；放不下则横向跑马，并在左右边缘做透明度渐变遮罩。
 *
 * 与 `Modifier.basicMarquee` 的区别 —— 自带的两侧淡出**只在滚动时出现**：起滚时淡入、
 * 停顿时淡出。`basicMarquee` 不对外暴露滚动相位，做不到这件事，所以这里自持动画。
 *
 * 滚动策略是「单程读完」：从行首匀速滚到行尾（末字对齐右缘）→ 停下 → 从头再来；
 * 而不是首尾相接无限循环。位移用线性 tween（与 AOSP 一致的线性手感），左右遮罩的
 * 透明度由独立的 `fade` 动画驱动，和滚动同时起、同时收，带渐变过渡。
 *
 * @param velocity 滚动速度，dp/秒（AOSP 默认 30）。
 * @param delayMillis 起滚前、以及每次读完后的停顿，毫秒。
 * @param fadeWidth 左右遮罩的渐变宽度。
 * @param fadeMillis 遮罩淡入/淡出的过渡时长。
 */
@Composable
fun FadingMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    velocity: Dp = 30.dp,
    delayMillis: Int = 1200,
    fadeWidth: Dp = 18.dp,
    fadeMillis: Int = 280,
) {
    val measurer = rememberTextMeasurer()
    // 量一次单行宽度，用来判断是否溢出、以及滚动行程（不随每帧重算）。
    val layout = remember(text, style) {
        measurer.measure(
            text = AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
    val contentPx = layout.size.width
    val density = LocalDensity.current
    val velocityPx = with(density) { velocity.toPx() }
    val fadePx = with(density) { fadeWidth.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerPx = constraints.maxWidth
        val overflow = (contentPx - containerPx).coerceAtLeast(0)
        val scrolling = overflow > 0

        val offset = remember { Animatable(0f) }
        // 1 = 两侧完全淡出，0 = 不淡出。只在滚动相位里抬到 1。
        val fade = remember { Animatable(0f) }

        LaunchedEffect(text, contentPx, containerPx, velocityPx) {
            if (!scrolling || velocityPx <= 0f) {
                offset.snapTo(0f)
                fade.snapTo(0f)
                return@LaunchedEffect
            }
            val duration = ceil(overflow / (velocityPx / 1000f)).toInt().coerceAtLeast(1)
            while (true) {
                offset.snapTo(0f)
                fade.snapTo(0f)
                delay(delayMillis.toLong())
                // 起滚的同时淡入：两条动画并行，读完即到终点。
                coroutineScope {
                    launch { fade.animateTo(1f, tween(durationMillis = fadeMillis)) }
                    offset.animateTo(overflow.toFloat(), tween(duration, easing = LinearEasing))
                }
                fade.animateTo(0f, tween(durationMillis = fadeMillis))
                delay(delayMillis.toLong())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                // 离屏合成，DstIn 遮罩才只作用于本组件的内容（不影响下层）。
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
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
        ) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = textAlign,
                modifier = Modifier
                    // 允许文字宽于容器（否则会先被约束截断，量不出溢出）。
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
                    // 负向：文字向左滚出（offset 是正的行程度）。
                    .graphicsLayer { translationX = -offset.value },
            )
        }
    }
}
