@file:OptIn(UnstableApi::class)

package com.thripleq.cirro.ui.playerbar

import androidx.media3.common.util.UnstableApi
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
import com.thripleq.cirro.Home
import com.thripleq.cirro.Profile
import com.thripleq.cirro.Search
import com.thripleq.cirro.core.playback.MEDIA_EXTRA_FEE
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.core.playback.TrialClip
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
    /** 当前曲是试听片段（非会员播 VIP 歌）时的区间；不是试听则 null。 */
    val trial: TrialClip? = null,
    /** 试听片段已播完并停在末尾（不是「正在播」也不是「缓冲中」）。 */
    val trialEnded: Boolean = false,
    /**
     * 当前曲是 VIP 歌（`fee == 1`）。与 [trial] **并存**：试听是「你现在只能听 30 秒」
     * 的临时状态，VIP 是这首歌的静态属性 —— 两件事都要说，别用一枚标替换另一枚。
     * 值从 MediaItem 元数据 extras 取（见 `trackMediaItem`）。
     */
    val vip: Boolean = false,
)

/** MediaItem 元数据是否带 VIP 档位（判据口径同 `payTagsOf`：只看 `fee == 1`）。 */
private fun MediaMetadata.isVip(): Boolean =
    extras?.getInt(MEDIA_EXTRA_FEE, 0) == 1

/**
 * 当前曲是否 VIP —— **两条来源取或**：MediaItem 里的 `fee`，或者「正在放试听片段」。
 *
 * 为什么不能只看 `fee`：`fee` 是入队那一刻写进 MediaItem 的，之后没有任何机会纠正。
 * 两种情况下它是错的（都表现为「试听标出来了、VIP 标却不显示」）：
 *  1. **旧快照恢复的队列**：`PlaybackStateStore` 早期版本没存 `fee`，恢复时按 0 补；
 *  2. 来源接口没带 `fee` 的曲目（模型里就是 0）。
 * 而试听身份是**解析响应现学的**（`freeTrialInfo`），不受入队时那份数据影响。
 *
 * 反过来，「试听 ⇒ VIP」在实测范围里成立且方向安全：试听片段只在 `fee=1` 且未购的曲目上
 * 出现（已购单曲同样 `fee=1`，实测拿到的是**整曲**、`freeTrialInfo` 为 null，见
 * `PlayableUrl` 的实测表）。所以一旦认出了试听，它就是一首 VIP 歌 —— 这条推断只会把
 * 「少了一枚标」补回来，不会把非 VIP 歌标成 VIP。
 */
private fun vipOf(mediaItem: MediaItem?, trial: TrialClip?): Boolean =
    mediaItem?.mediaMetadata?.isVip() == true || trial != null

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
                    // 读 MediaItem 而不用回调里的 mediaMetadata：extras 是我们自己写进
                    // MediaItem 的静态属性，媒体源自带的元数据没有义务把它带回回调。
                    // VIP 另有一条来源（试听身份），见 [vipOf]。
                    vip = vipOf(player.currentMediaItem, meta.trial),
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (meta.trackId == mediaItem?.mediaId) return
                // 切歌时**同时**换掉试听身份，不能只改 trackId：`onMediaMetadataChanged` 与这里的
                // `meta.copy` 都不碰 trial，上一首的「试听」标会挂到新歌头上、直到下一次 250ms
                // 轮询才被纠正 —— 那是一次肉眼可见的闪动，正好发生在新歌刚开始播的时候。
                // 此刻读到的是 PlayerHolder 刚在 transition 里登记好的值（它的 listener 先注册、
                // 先执行），所以这里是权威的、不是抢跑。
                val trial = PlayerHolder.currentTrial()
                meta = meta.copy(
                    trackId = mediaItem?.mediaId,
                    trial = trial,
                    // 新曲刚起播，必然不在片段末尾；trialEnded 交给轮询与 isTrialEnded 判。
                    trialEnded = false,
                    // VIP 与试听并存：切歌时两枚标一起换，不留下上一首的任何一枚。
                    // vip 由 [vipOf] 算：媒体项里的 fee **或**试听身份（后者见该函数说明）。
                    vip = vipOf(mediaItem, trial),
                )
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
            trial = PlayerHolder.currentTrial(),
            trialEnded = PlayerHolder.isTrialEnded(player),
            vip = vipOf(player.currentMediaItem, PlayerHolder.currentTrial()),
        )
        try {
            while (true) {
                // hasTrack / duration / 试听状态由 listener 与这里共同维护；只在真实变化时写 meta。
                //
                // 试听这两项**必须在这里轮询**：它们的变化（解析回来、片段播完停下）都不伴随
                // 上面那些事件 —— 片段末尾停住时没有 onMediaItemTransition（正是我们想要的
                // 不切歌），isPlaying 变 false 也只是普通暂停事件，单看它分不出「暂停」与
                // 「试听播完」。所以判据统一走 PlayerHolder.isTrialEnded。
                val hasTrack = player.currentMediaItem != null
                val duration = effectiveDurationMs()
                val trial = PlayerHolder.currentTrial()
                val trialEnded = PlayerHolder.isTrialEnded(player)
                // 试听身份是轮询才学到的（解析回来 / 补解析成功），而 VIP 现在也要看它 ——
                // 不在这里一起算，一首 fee 缺失的 VIP 歌会「试听标都出来了、VIP 标还不出来」，
                // 只能等下一次切歌。所以把 vip 也放进判重条件：认出错的值就地纠正。
                val vip = vipOf(player.currentMediaItem, trial)
                if (meta.hasTrack != hasTrack || meta.durationMs != duration ||
                    meta.trial != trial || meta.trialEnded != trialEnded || meta.vip != vip
                ) {
                    meta = meta.copy(
                        hasTrack = hasTrack,
                        durationMs = duration,
                        trial = trial,
                        trialEnded = trialEnded,
                        vip = vip,
                    )
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
                // 暂停时进度根本不变：4Hz 唤醒只是白烧电（写入同一个值不会触发重组，
                // 但协程照醒、Binder 照调）。降到 1Hz 够了 —— 暂停期间的 seek 由上面的
                // onPositionDiscontinuity 即时补上，不依赖轮询。
                delay(if (player.isPlaying) 250 else 1_000)
            }
        } finally {
            player.removeListener(listener)
        }
    }
    return positionMs
}
