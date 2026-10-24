package com.thripleq.cirro.ui.playerbar

/**
 * 进度行：滑块 + 左右两个时间。**播放页里唯一解包进度值的地方**。
 *
 * 从 `PlayerPageContent.kt` 分出来的理由见下面 KDoc —— 它是全页最高频重组的一行，
 * 单独成文件是为了让「谁在这里读进度」这件事一眼可见，改它不会碰到逐帧几何。
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 为什么单列一个组件：进度是 250ms 一次的轮询，拖动进度条时更是逐帧写入。这些读取若留在
 * `PlayerPageContent` 的函数体里，按 Compose「重组作用域是函数级」的规则，整块内容区
 * （封面、跑马标题、控制行、功能胶囊）都会跟着重跑一遍 —— 而它们与进度无关。收进这里之后，
 * 高频写入的重组范围就只剩这一行。
 *
 * @param seekFrozen 「拖动中」标志：本组件写入、进度订阅方读取。拖动期间必须冻结那个
 *    250ms 轮询，否则播放器回报的真实位置会与手指的位置打架。
 */
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
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // 暂无播放：只画一条干净的静态轨道 —— M3 disabled Slider 会留一个悬浮 thumb
        // 和端点小圆点，像坏掉；这里用同样的轨道高度/内缩，但去掉 thumb。
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CirroShape.Track)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = CirroFade.TRACK)),
            )
        }
    }
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
