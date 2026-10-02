package com.thripleq.nume.ui.playerbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.thripleq.nume.Home
import com.thripleq.nume.Profile
import com.thripleq.nume.Search
import kotlinx.coroutines.delay

/**
 * The top-level tabs shown in the docked capsule.
 *
 * [icon] 为未选中态（描边）、[iconSelected] 为选中态（实心）：M3 导航栏惯例——
 * 选中换实心图标，配合指示胶囊把"当前项"立起来。
 */
enum class BottomTab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    ExploreTab(route = Home, label = "探索", icon = Icons.Outlined.Explore, iconSelected = Icons.Filled.Explore),
    SearchTab(route = Search, label = "搜索", icon = Icons.Outlined.Search, iconSelected = Icons.Filled.Search),
    ProfileTab(route = Profile, label = "我的", icon = Icons.Outlined.Person, iconSelected = Icons.Filled.Person),
}

/** Live snapshot of the shared [Player] for the mini player bar. */
data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val trackId: String? = null,
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasTrack: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val errorText: String? = null,
)

/**
 * Observes [player] (process-scoped singleton) with a metadata listener + 250ms poll.
 * 状态拆成快照：元数据/播放态（标题/封面/是否播放/时长/有无曲目）低频，
 * 仅在真实变化时才写，避免无谓重组。
 *
 * **进度条优化**：进度 positionMs 不进入返回值 —— [rememberPlayerState] 从不读它，
 * 调用方（迷你条/播放页）不会随 250ms 轮询重组。进度条这类需要逐帧更新的小部件
 * 单独订阅 [rememberPlayerPosition]，只有它随轮询重组。
 */
@Composable
fun rememberPlayerState(
    player: Player,
): PlayerUiState {
    var meta by remember { mutableStateOf(PlayerUiState()) }

    LaunchedEffect(player) {
        // MediaItem 元数据里声明的时长（trackMediaItem 写入）。恢复态停在 STATE_IDLE：
        // 未 prepare/未联网，player.duration 为 0，靠它兜底，进度条与总时长在
        // 「已恢复、未加载」时也能正确显示；一旦真实时长可用（READY）则以真实值为准。
        var declaredDurationMs = 0L
        fun effectiveDurationMs(): Long =
            player.duration.takeIf { it > 0L } ?: declaredDurationMs

        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                declaredDurationMs = mediaMetadata.durationMs?.takeIf { it > 0L } ?: 0L
                meta = meta.copy(
                    title = mediaMetadata.title?.toString() ?: "",
                    artist = mediaMetadata.artist?.toString() ?: "",
                    trackId = player.currentMediaItem?.mediaId,
                    coverUrl = mediaMetadata.artworkUri?.toString(),
                    durationMs = effectiveDurationMs(),
                    errorText = null,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (meta.trackId != mediaItem?.mediaId) meta = meta.copy(trackId = mediaItem?.mediaId)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                meta = meta.copy(isPlaying = playing)
            }

            override fun onPlaybackStateChanged(s: Int) {
                meta = meta.copy(isBuffering = s == Player.STATE_BUFFERING)
                if (s == Player.STATE_READY) {
                    meta = meta.copy(durationMs = effectiveDurationMs())
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // message 更贴近人话（errorCodeName 是 ERROR_CODE_* 这类技术串），
                // 留给用户看的主文案优先 message；万一为空再退回枚举名。
                meta = meta.copy(errorText = error.message ?: error.errorCodeName)
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                meta = meta.copy(shuffleEnabled = enabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                meta = meta.copy(repeatMode = repeatMode)
            }
        }
        player.addListener(listener)
        // Seed everything from the player's current state so the UI reflects
        // reality the moment it appears (e.g. already playing when the screen
        // opens) instead of defaulting to "not playing".
        val mediaMetadata = player.mediaMetadata
        declaredDurationMs = mediaMetadata.durationMs?.takeIf { it > 0L } ?: 0L
        meta = PlayerUiState(
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            trackId = player.currentMediaItem?.mediaId,
            coverUrl = mediaMetadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = effectiveDurationMs(),
            hasTrack = player.currentMediaItem != null,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        try {
            while (true) {
                // hasTrack / duration 由 listener 与这里共同维护；只在真实变化时写 meta。
                val hasTrack = player.currentMediaItem != null
                val duration = effectiveDurationMs()
                if (meta.hasTrack != hasTrack || meta.durationMs != duration) {
                    meta = meta.copy(hasTrack = hasTrack, durationMs = duration)
                }
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return meta
}

/**
 * 高频进度订阅：250ms 轮询写入 positionMs。只有读取返回 [State] 的组合
 * （迷你条进度条、播放页 Slider/时间）才随轮询重组；不读它的组合零开销。
 * [positionFrozen] 用于拖动进度时冻结位置，避免轮询跟手指打架。
 *
 * **seek 立即对齐**：拖动时 [positionMs] 冻结在「拖动前」的旧值，若只靠 250ms
 * 轮询，松手瞬间 Slider 会先回跳到这个旧值、下一个 tick 才跳到目标 —— 看起来就是
 * 指针抖一下/闪一下。监听 [Player.Listener.onPositionDiscontinuity]（seek/跳转时
 * 同步触发）即时写入新位置，松手前就把旧值换成目标，回跳消失。切歌/跳转同理。
 */
@Composable
fun rememberPlayerPosition(
    player: Player,
    positionFrozen: () -> Boolean = { false },
): State<Long> {
    val positionMs = remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                positionMs.longValue = newPosition.positionMs.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                if (!positionFrozen()) positionMs.longValue = player.currentPosition
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return positionMs
}
