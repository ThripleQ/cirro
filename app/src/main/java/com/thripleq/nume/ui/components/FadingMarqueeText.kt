package com.thripleq.nume.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
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
 * 滚动策略是「单向无缝循环」（与 AOSP `BasicMarquee` 一致）：内容 = 文本 + 间距 + 文本，
 * 始终**向左**匀速滚过「文本宽 + 间距」，滚到第二份文本正好落在行首时位置与第一份的
 * 行首重合 —— 视觉上自然接回开头，不反向、也不跳。两份文本默认**硬贴**（[spacingSpaces] = 0）：
 * 一份读完，紧接着就是下一份的开头，行首永远有字；只要留缝，那段空白就一定压在下一份的开头前，
 * 看起来就像「开头被空格占了」。需要缝时把 [spacingSpaces] 设成几个空格宽即可。
 * 放得下（不溢出）时只渲染**一份**文本、不做任何位移 —— 双份内容只在真的需要滚动时存在。
 * 每份文本的宽度按实测值写死（不靠 Row 分配剩余宽），行程恰好 = 文本宽 + 间距，所以每一轮
 * 的行首都严格落在容器左边缘 —— 开头不会多出、也不会被吃掉一段空白。
 * **停顿只发生在行首**（与 AOSP 的 repeatDelay 语义一致），末尾不停留；遮罩在起滚时
 * 淡入、回到行首后淡出，带渐变过渡。
 *
 * @param velocity 滚动速度，dp/秒（AOSP 默认 30）。
 * @param delayMillis 行首停顿（每轮起滚前），毫秒。
 * @param fadeWidth 左右遮罩的渐变宽度。
 * @param fadeMillis 遮罩淡入/淡出的过渡时长。
 * @param spacingSpaces 两份之间的空档 = 同字体下几个空格字符宽；0（默认）= 两份硬贴。
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
    spacingSpaces: Int = 0,
) {
    // 标题/歌手来自接口，首尾空白不参与展示与测量 —— 否则行首会先顶出一段空白。
    val label = remember(text) { text.trim() }
    val measurer = rememberTextMeasurer()
    // 量一次单行宽度，用来判断是否溢出、以及滚动行程（不随每帧重算）。
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
    val velocityPx = with(density) { velocity.toPx() }
    val fadePx = with(density) { fadeWidth.toPx() }
    // 首尾空档 = 「一些空格」的字面实现：同字体下 spacingSpaces 个空格字符的实测宽度。
    // 用 "| |" 与 "||" 的宽差量一个空格 —— 直接量 "    " 会因行尾空白被裁而量成 0。
    val spacing = remember(style, spacingSpaces, density) {
        if (spacingSpaces <= 0) {
            0.dp
        } else {
            fun widthOf(s: String) = measurer.measure(
                text = AnnotatedString(s),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            ).size.width
            val spacePx = (widthOf("| |") - widthOf("||")).coerceAtLeast(0)
            with(density) { (spacePx * spacingSpaces).toDp() }.coerceAtLeast(8.dp)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerPx = constraints.maxWidth
        val scrolling = contentPx > containerPx

        val spacingPx = with(density) { spacing.toPx() }

        val offset = remember { Animatable(0f) }
        // 1 = 两侧完全淡出，0 = 不淡出。只在滚动相位里抬到 1。
        val fade = remember { Animatable(0f) }

        LaunchedEffect(label, contentPx, containerPx, velocityPx, spacingPx) {
            if (!scrolling || velocityPx <= 0f) {
                offset.snapTo(0f)
                fade.snapTo(0f)
                return@LaunchedEffect
            }
            // 一份「文本 + 间距」，滚过它就等于把下一份文本带到行首。
            val loopWidth = contentPx + spacingPx
            val duration = ceil(loopWidth / (velocityPx / 1000f)).toInt().coerceAtLeast(1)
            while (true) {
                // 停在行首：归位 + 停顿（停顿只发生在这里）。
                offset.snapTo(0f)
                fade.snapTo(0f)
                delay(delayMillis.toLong())
                // 起滚的同时淡入：两条动画并行，读完即到终点（第二份文本刚好对齐行首）。
                coroutineScope {
                    launch { fade.animateTo(1f, tween(durationMillis = fadeMillis)) }
                    offset.animateTo(loopWidth, tween(duration, easing = LinearEasing))
                }
                // 回卷到行首后遮罩淡出，紧接着就是下一次行首停顿。
                fade.animateTo(0f, tween(durationMillis = fadeMillis))
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
            if (scrolling) {
                // 两份文本（中间可选空档）；整体向左平移 offset（0 ~ 文本宽 + 间距）即无缝循环。
                // 每份的宽度都按测量值写死：Row 只把「剩余宽」分给下一份，不写死第二份会被
                // 压成一个字 —— 那个残缺的开头正是「标题开头不对」的来源。
                val contentWidth = with(density) { contentPx.toDp() }
                val rowWidth = with(density) { (2f * contentPx + spacingPx).toDp() }
                Row(
                    modifier = Modifier
                        .requiredWidth(rowWidth)
                        .graphicsLayer { translationX = -offset.value },
                ) {
                    Text(
                        text = label,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = textAlign,
                        modifier = Modifier.requiredWidth(contentWidth),
                    )
                    Spacer(Modifier.width(spacing))
                    Text(
                        text = label,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = textAlign,
                        modifier = Modifier.requiredWidth(contentWidth),
                    )
                }
            } else {
                // 放得下就只画一份：画两份会在右缘露出重复的文字和空档。
                Text(
                    text = label,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = textAlign,
                    modifier = Modifier.wrapContentWidth(unbounded = true, align = Alignment.Start),
                )
            }
        }
    }
}
