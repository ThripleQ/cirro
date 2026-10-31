package com.thripleq.cirro.ui.playerbar

/**
 * 进度行：滑块 + 左右两个时间。**播放页里唯一解包进度值的地方**。
 *
 * 从 `PlayerPageContent.kt` 分出来的理由见下面 KDoc —— 它是全页最高频重组的一行，
 * 单独成文件是为了让「谁在这里读进度」这件事一眼可见，改它不会碰到逐帧几何。
 */
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.core.playback.ProgressBarStyle
import com.thripleq.cirro.ui.theme.CirroFade
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 为什么单列一个组件：进度是 250ms 一次的轮询，拖动进度条时更是逐帧写入。这些读取若留在
 * `PlayerPageContent` 的函数体里，按 Compose「重组作用域是函数级」的规则，整块内容区
 * （封面、跑马标题、控制行、功能胶囊）都会跟着重跑一遍 —— 而它们与进度无关。收进这里之后，
 * 高频写入的重组范围就只剩这一行。
 *
 * 轨道与拇指全部自绘（Slider 的 thumb 留空）：
 * ① M3 1.4 的默认轨道有「起步阈值」，已播段要先宽过 8dp 拇指间隙 + 8dp 圆角才画得出来，
 *    4 分钟的歌前十几秒一点红都没有、越线时一次蹦出一大块；自绘从 0 连续生长。
 * ② 六种样式只在「画成什么样」上不同，触摸/拖动仍走同一条 M3 Slider 手势，拇指为空后
 *    轨道铺满整宽，样式里的圆点正好落在 `width * fraction` 上。
 *
 * @param seekFrozen 「拖动中」标志：本组件写入、进度订阅方读取。拖动期间必须冻结那个
 *    250ms 轮询，否则播放器回报的真实位置会与手指的位置打架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerProgressRow(
    player: Player,
    positionState: State<Long>,
    seekFrozen: MutableState<Boolean>,
    durationMs: Long,
) {
    var dragMs by remember { mutableLongStateOf(0L) }
    val seekPending = seekFrozen.value
    val positionMs = positionState.value
    val context = LocalContext.current
    val style = ProgressStyleState.current(context)
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val mediaId = player.currentMediaItem?.mediaId
    // 拖动中途若曲目结束/切歌使 durationMs 归零，Slider 会被移除、onValueChangeFinished
    // 不再触发，seekPending 会永久卡住（进度轮询被冻结在拖动值）。这里兜底复位。
    LaunchedEffect(durationMs) {
        if (durationMs <= 0L) seekFrozen.value = false
    }
    if (durationMs > 0) {
        Slider(
            value = if (seekPending) dragMs.toFloat() else positionMs.toFloat(),
            onValueChange = { dragMs = it.toLong(); seekFrozen.value = true },
            onValueChangeFinished = {
                PlayerHolder.seekTo(player, dragMs)
                seekFrozen.value = false
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                // 拇指、轨道都自绘；这里只保留 M3 默认值给无障碍服务兜底。
                thumbColor = Color.Transparent,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = CirroFade.TRACK),
            ),
            interactionSource = interactionSource,
            thumb = { },
            track = { sliderState ->
                ProgressTrack(
                    style = style,
                    fraction = sliderState.coercedValueAsFraction,
                    elapsedMs = if (seekPending) dragMs else positionMs,
                    durationMs = durationMs,
                    dragging = dragging,
                    mediaId = mediaId,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // 暂无播放：只画一条干净的静态轨道。时间胶囊样式把时间画在轨道左端，
        // 没有时长可画，因此这里统一只留轨道。
        EmptyTrack(style)
    }
    // 时间胶囊样式把左右两个时间并进了轨道左端，不再重复一行。
    if (style != ProgressBarStyle.TimeChip) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 轨道画布高度；M3 再补到 48dp 的最小触控区。 */
private val RowHeight = 44.dp

/** 各样式对应的轨道粗细（空轨兜底也用它）。 */
private val ProgressBarStyle.trackHeight: Dp
    get() = when (this) {
        ProgressBarStyle.Capsule -> 16.dp
        ProgressBarStyle.Dot -> 3.dp
        ProgressBarStyle.Hairline,
        ProgressBarStyle.Solid,
        ProgressBarStyle.TimeChip,
        ProgressBarStyle.Waveform,
        -> 2.dp
    }

@Composable
private fun ProgressTrack(
    style: ProgressBarStyle,
    fraction: Float,
    elapsedMs: Long,
    durationMs: Long,
    dragging: Boolean,
    mediaId: String?,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = active.copy(alpha = CirroFade.TRACK)
    val surface = MaterialTheme.colorScheme.surface
    val chipLayout = rememberTimeChipLayout(
        label = "${formatTime(elapsedMs)} / ${formatTime(durationMs)}",
    )
    val wave = remember(mediaId) { buildWaveHeights(seed = mediaId.hashCode()) }
    Canvas(Modifier.fillMaxWidth().height(RowHeight)) {
        drawProgress(
            style = style,
            fraction = fraction,
            dragging = dragging,
            wave = wave,
            chip = chipLayout,
            active = active,
            inactive = inactive,
            surface = surface,
        )
    }
}

/** 设置页每行的静态小样：固定 38% 进度，拖动状态关闭。 */
@Composable
internal fun ProgressStyleSample(
    style: ProgressBarStyle,
    modifier: Modifier = Modifier,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = active.copy(alpha = CirroFade.TRACK)
    val surface = MaterialTheme.colorScheme.surface
    val chipLayout = rememberTimeChipLayout(label = "0:18 / 0:44")
    val wave = remember(style) { buildWaveHeights(seed = 0x5EED) }
    Canvas(modifier.height(RowHeight)) {
        drawProgress(
            style = style,
            fraction = 0.38f,
            dragging = false,
            wave = wave,
            chip = chipLayout,
            active = active,
            inactive = inactive,
            surface = surface,
        )
    }
}

@Composable
private fun rememberTimeChipLayout(label: String): TextLayoutResult {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(label, color) {
        measurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color,
            ),
        )
    }
}

@Composable
private fun EmptyTrack(style: ProgressBarStyle) {
    val inactive = MaterialTheme.colorScheme.primary.copy(alpha = CirroFade.TRACK)
    Box(
        modifier = Modifier.fillMaxWidth().height(RowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(style.trackHeight)) {
            drawRoundRect(color = inactive, cornerRadius = CornerRadius(size.height / 2f))
        }
    }
}

private fun DrawScope.drawProgress(
    style: ProgressBarStyle,
    fraction: Float,
    dragging: Boolean,
    wave: FloatArray,
    chip: TextLayoutResult,
    active: Color,
    inactive: Color,
    surface: Color,
) {
    val f = fraction.coerceIn(0f, 1f)
    when (style) {
        ProgressBarStyle.Capsule -> drawCapsule(f, active, inactive)
        ProgressBarStyle.Hairline -> drawDotLine(f, 2.dp.toPx(), 5.5.dp.toPx(), active, inactive)
        ProgressBarStyle.Dot -> drawDotLine(f, 3.dp.toPx(), 7.5.dp.toPx(), active, inactive)
        ProgressBarStyle.Solid -> drawDotLine(f, 2.dp.toPx(), 7.dp.toPx(), active, inactive)
        ProgressBarStyle.TimeChip -> {
            drawDotLine(f, 2.dp.toPx(), 6.dp.toPx(), active, inactive, showThumb = dragging)
            drawTimeChip(chip, surface, inactive)
        }
        ProgressBarStyle.Waveform ->
            drawWaveform(f, wave, active, active.copy(alpha = CirroFade.WAVE_IDLE))
    }
}

/** 胶囊：16dp 粗轨 + 4×44dp 竖条拇指 + 尾端 4dp 圆点（0.3.x 的观感）。 */
private fun DrawScope.drawCapsule(fraction: Float, active: Color, inactive: Color) {
    val trackH = 16.dp.toPx()
    val radius = trackH / 2f
    val y = center.y
    drawRoundRect(
        color = inactive,
        topLeft = Offset(0f, y - radius),
        size = Size(size.width, trackH),
        cornerRadius = CornerRadius(radius),
    )
    val played = size.width * fraction
    if (played > 0f) {
        drawRoundRect(
            color = active,
            topLeft = Offset(0f, y - radius),
            size = Size(played, trackH),
            cornerRadius = CornerRadius(min(radius, played / 2f)),
        )
    }
    drawCircle(
        color = active,
        radius = SliderDefaults.TrackStopIndicatorSize.toPx() / 2f,
        center = Offset(size.width - radius, y),
    )
    val thumbW = 4.dp.toPx()
    val thumbH = 44.dp.toPx().coerceAtMost(size.height)
    drawRoundRect(
        color = active,
        topLeft = Offset(played - thumbW / 2f, y - thumbH / 2f),
        size = Size(thumbW, thumbH),
        cornerRadius = CornerRadius(thumbW / 2f),
    )
}

/**
 * 细线 + 圆点：已播段从 0 连续生长，圆点骑在已播段末端。
 * [showThumb] 给时间胶囊样式用 —— 只有拖动时才露出圆点。
 */
private fun DrawScope.drawDotLine(
    fraction: Float,
    trackH: Float,
    dotDiameter: Float,
    active: Color,
    inactive: Color,
    showThumb: Boolean = true,
) {
    val y = center.y
    val radius = trackH / 2f
    drawRoundRect(
        color = inactive,
        topLeft = Offset(0f, y - radius),
        size = Size(size.width, trackH),
        cornerRadius = CornerRadius(radius),
    )
    val played = size.width * fraction
    if (played > 0f) {
        drawRoundRect(
            color = active,
            topLeft = Offset(0f, y - radius),
            size = Size(played, trackH),
            cornerRadius = CornerRadius(min(radius, played / 2f)),
        )
    }
    if (showThumb) {
        drawCircle(color = active, radius = dotDiameter / 2f, center = Offset(played, y))
    }
}

/** 时间胶囊：已播/总时长嵌在轨道左端；底色半透明，红条从下面透出来。 */
private fun DrawScope.drawTimeChip(chip: TextLayoutResult, surface: Color, border: Color) {
    val padH = 6.dp.toPx()
    val padV = 1.5.dp.toPx()
    val w = chip.size.width + padH * 2f
    val h = chip.size.height + padV * 2f
    val top = center.y - h / 2f
    val corner = CornerRadius(h / 2f)
    drawRoundRect(
        color = surface.copy(alpha = 0.92f),
        topLeft = Offset(0f, top),
        size = Size(w, h),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(0f, top),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    drawText(chip, topLeft = Offset(padH, top + padV))
}

/** 波形：64 根竖条 + 1.5dp 播放头；柱高按曲目固定，是示意图形，不是音频波形。 */
private fun DrawScope.drawWaveform(
    fraction: Float,
    heights: FloatArray,
    active: Color,
    inactive: Color,
) {
    val n = heights.size
    // 设置页的小样只有 96dp 宽；固定 2dp 间隙会把 64 根柱子挤到视图外面，按宽度收间隙。
    val gap = min(2.dp.toPx(), size.width / (n * 2f))
    val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
    val maxH = 30.dp.toPx()
    val minH = 2.dp.toPx()
    val playedCount = (fraction * n).roundToInt()
    heights.forEachIndexed { index, value ->
        val barH = max(minH, value * maxH)
        drawRoundRect(
            color = if (index < playedCount) active else inactive,
            topLeft = Offset(index * (barW + gap), center.y - barH / 2f),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f),
        )
    }
    val x = size.width * fraction
    drawRoundRect(
        color = active,
        topLeft = Offset(x - 0.75.dp.toPx(), center.y - 21.dp.toPx()),
        size = Size(1.5.dp.toPx(), 42.dp.toPx()),
        cornerRadius = CornerRadius(0.75.dp.toPx()),
    )
}

private fun buildWaveHeights(seed: Int): FloatArray {
    val n = 64
    var state = if (seed == 0) 1 else seed
    return FloatArray(n) { i ->
        state = state * 1103515245 + 12345
        val noise = ((state ushr 16) and 0x7FFF) / 32767f
        // 两个不同周期的正弦叠成「几段起伏」，不压到地板：单条低频正弦走到负值再夹到
        // 0.12 的话，轨道中段会排成一串 2dp 小点，看起来像整首歌中间没有声音。
        val t = i / (n - 1f)
        val hump = 0.62f +
            0.24f * sin(t * PI.toFloat() * 3.4f + 0.5f) +
            0.14f * sin(t * PI.toFloat() * 8.7f + 2.1f)
        (hump * (0.55f + noise * 0.6f)).coerceIn(0.22f, 1f)
    }
}
