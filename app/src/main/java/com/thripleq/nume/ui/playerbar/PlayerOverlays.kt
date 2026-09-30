package com.thripleq.nume.ui.playerbar

import com.thripleq.nume.ui.theme.NumeShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.thripleq.nume.core.playback.PlaybackPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 全屏档功能胶囊：56×44 圆角矩形，激活态用 primaryContainer 高亮。 */
@Composable
internal fun FullChip(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = 44.dp),
        shape = NumeShape.Card,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

/**
 * 定时关闭：点开选择时长，倒计时结束后暂停。用墙钟（System.currentTimeMillis）判定，
 * 而不是 delay 累加 —— 进程被挂起/系统休眠时 delay 会漂移，墙钟不会。
 */
@Composable
internal fun SleepTimerChip(endAt: Long, onEndAtChange: (Long) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        FullChip(
            icon = Icons.Filled.Bedtime,
            contentDescription = "定时关闭",
            active = endAt > 0L,
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(0 to "关闭定时", 15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟", 90 to "90 分钟")
                .forEach { (min, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onEndAtChange(
                                if (min == 0) 0L
                                else System.currentTimeMillis() + min * 60_000L,
                            )
                            menuOpen = false
                        },
                    )
                }
        }
    }
}

/** 播放队列面板：列出当前队列、高亮在播曲目、点按跳转。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerQueueSheet(player: Player, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(player.mediaItemCount) }
    var current by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                count = player.mediaItemCount
                current = player.currentMediaItemIndex
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                count = player.mediaItemCount
                current = player.currentMediaItemIndex
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    // onDismissRequest 先跑完 sheet 自己的 hide 动画再移除组合，否则面板会被当场拆掉、
    // 看不出收起动画（直接用 onDismiss 的常见坑）。
    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        Text(
            text = "播放队列 · ${if (count == 0) 0 else current + 1}/$count",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(count) { i ->
                // count 来自 500ms 轮询，可能比真实时间线短暂偏大；越界取值会抛。
                if (i >= player.mediaItemCount) return@items
                val meta = player.getMediaItemAt(i).mediaMetadata
                val isCurrent = i == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            player.seekToDefaultPosition(i)
                            player.play()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (i + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = meta.title?.toString() ?: "未知曲目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val artist = meta.artist?.toString().orEmpty()
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 播放设置：目前只有音质档位（写入 [PlaybackPreferences]，[PlaybackUrls] 解析时读取）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var quality by remember { mutableStateOf(PlaybackPreferences.quality(context)) }
    val labels = mapOf(
        "standard" to "标准",
        "higher" to "较高",
        "exhigh" to "极高",
        "lossless" to "无损",
    )

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        Text(
            text = "播放音质",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        PlaybackPreferences.QUALITIES.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        quality = q
                        PlaybackPreferences.setQuality(context, q)
                    }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = quality == q,
                    onClick = {
                        quality = q
                        PlaybackPreferences.setQuality(context, q)
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = labels[q] ?: q,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
