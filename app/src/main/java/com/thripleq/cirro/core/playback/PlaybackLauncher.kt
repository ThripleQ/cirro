@file:OptIn(UnstableApi::class)

package com.thripleq.cirro.core.playback

import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.thripleq.cirro.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 [Track] 列表交给共享播放器：**整单一次性入队**。
 *
 * 队列里每个 [MediaItem] 用稳定的合成 URI（`cirro://song/<id>`），真正的签名音频
 * URL 由 [PlayerHolder] 里的 `ResolvingDataSource` 在播放那一刻惰性解析
 * （见 [PlaybackUrls]）。因此这里不再需要占位 item、增量补队列、或"扫描整单找
 * 可播曲"：shuffle / 循环 / 连播 / 自动跳过不可播曲全部交给 ExoPlayer 原生处理。
 *
 * 必须在主线程调用（ExoPlayer 非线程安全）；ViewModels 用 `viewModelScope` 满足。
 */
@Singleton
class PlaybackLauncher @Inject constructor() {

    /** Plays a single track. */
    fun play(context: Context, track: Track) = play(context, listOf(track), 0)

    /**
     * 「下一首播放」：把 [track] 插到当前曲目**之后**，不打断正在播的这首。
     *
     * 队列还是空的（进程刚起、从没播过东西）就直接播它 —— 插到一个不存在的队列里
     * 等于什么都没发生，用户读到的是「点了没反应」。
     *
     * ⚠️ 已知限制：开着随机播放时 ExoPlayer 会重算随机序，插入的这首**不保证**真的
     * 接着播（`addMediaItem(index)` 的 index 是未随机序里的下标）。要彻底解决得先关掉
     * 随机再插，那是在替用户改设置；这里选择保留用户设置、接受这个不精确。
     * 该限制同样适用于曲序（插入位置以后仍按未随机序保存）。
     */
    fun playNext(context: Context, track: Track) {
        val player = PlayerHolder.get(context)
        if (player.mediaItemCount == 0) {
            play(context, track)
            return
        }
        PlayerHolder.insertNext(context, track)
    }

    /**
     * 列表页「播放全部 / 随机播放」专用：按 [kind] 与播放器**当前的随机模式**定好队列和起点，
     * 再整单播放。
     *
     * 与 [play] 的差别只有两处，都由 [planListPlayback] 决定（理由见 `ListPlaybackPolicy.kt`）：
     *   1. 起点不一定是队头 —— 随机模式开着时是随机队列的头一首；
     *   2. 随机模式开着时，把那一份随机队列通过 `setShuffleOrder` 交给播放器。
     *
     * **绝不改 `shuffleModeEnabled`**：随机是全局设置，按钮无权接管（2026-11-07 用户明确纠正）。
     * 其余（落盘队列、URL 惰性解析、试听身份、前台服务）全复用同一份入队实现，不另写第二份。
     *
     * ⚠️ 先 [PlayerHolder.get] 拿播放器再读 `shuffleModeEnabled`：冷启动那次 `restore()`
     * 会在此刻把上次的 shuffle 态写回来，之后读到的才是真实当前值。
     *
     * 线程要求同 [play]：主线程（ViewModels 用 `viewModelScope` 满足）。
     */
    fun playList(context: Context, tracks: List<Track>, kind: ListPlaybackKind) {
        if (tracks.isEmpty()) return
        val player = PlayerHolder.get(context)
        val plan = planListPlayback(tracks, kind, player.shuffleModeEnabled)
        playInternal(context, plan.tracks, plan.startIndex, plan.shuffleOrder)
    }

    /** Plays [tracks] as the queue, starting at [index]. 单曲点播 / 「下一首播放」走这条。 */
    fun play(context: Context, tracks: List<Track>, index: Int) =
        playInternal(context, tracks, index, shuffleOrder = null)

    /**
     * 唯一的入队实现。[shuffleOrder] 非 null 时把它装成 Media3 的随机队列交给播放器。
     *
     * ⚠️ `setShuffleOrder` 必须排在 `setMediaItems` **之后**：`ExoPlayerImpl` 会校验
     * `order.length == mediaItemCount`（队列还没建好就抛出 `IllegalArgumentException`），
     * 而且换队列本身会重建一次默认随机序，先设会被覆盖掉。设上去之后 Media3 只重建
     * timeline、保留当前曲目与进度（`maskTimelineAndPosition` 用的是当前 index/position），
     * 于是 `startIndex`（= 随机队列头一首）仍是实际开播的那一首。
     */
    private fun playInternal(
        context: Context,
        tracks: List<Track>,
        index: Int,
        shuffleOrder: IntArray?,
    ) {
        if (tracks.isEmpty()) return
        val player = PlayerHolder.get(context)

        // 先登记队列：状态持久化（PlaybackStateStore）据此落盘整单，切歌/退出后能原样恢复。
        PlayerHolder.rememberQueue(tracks)

        val idx = index.coerceIn(0, tracks.lastIndex)
        player.setMediaItems(tracks.map(::trackMediaItem), idx, 0L)
        if (shuffleOrder != null) {
            // 第二个参数只是后续 cloneAndInsert（「下一首播放」）重新洗牌用的种子，
            // 本单的播放顺序完全由传入的排列决定。
            player.setShuffleOrder(DefaultShuffleOrder(shuffleOrder, System.nanoTime()))
        }
        // 换队列后按新的当前曲重算试听身份。onMediaItemTransition 在 setMediaItems 时的触发
        // 走的是 Media3 的 PLAYLIST_CHANGED 分支，这里不赌那条语义 —— 它是**上一首试听留下的
        // `pauseAtEndOfMediaItems` 是否会误作用到这一首**的唯一闸门（见 PlayerHolder.refreshTrial）。
        PlayerHolder.refreshTrial(player)
        player.prepare()
        player.play()

        // Start the foreground service AFTER playback has begun. Media3's
        // MediaSessionService calls startForeground() off the first player-state
        // change; kicking the foreground timer first when the audio fetch is slow
        // blows Android's 5s window and crashes (ForegroundServiceDidNotStart…).
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java),
        )
    }

}

/**
 * [Track] → 队列 [MediaItem]：稳定合成 URI + 元数据。URL 惰性解析，故这里只放
 * `cirro://song/<id>`。同时供 [PlayerHolder] 恢复上次队列时复用。
 *
 * 元数据里**声明时长**：恢复态停在 STATE_IDLE（不 prepare、不联网），此时
 * `player.duration` 为 0，UI 靠这个声明值把进度条/总时长显示出来（见 rememberPlayerState）。
 *
 * 元数据 extras 里带 `fee`：播放页/迷你条要显示 VIP 标，而 [MediaItem] 本身不带
 * `Track` 的字段。恢复队列时 `trackMediaItem` 用 [PlaybackStateStore] 里存下的
 * `fee` 重建，所以冷启动也能显示。
 */
internal fun trackMediaItem(track: Track): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(PlaybackUrls.uriFor(track.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.name)
                .setArtist(track.artist)
                .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                .setExtras(Bundle().apply { putInt(MEDIA_EXTRA_FEE, track.fee) })
                .apply { if (track.durationMs > 0L) setDurationMs(track.durationMs) }
                .build(),
        )
        .build()

/** MediaItem 元数据里携带的 `Track.fee`（判据口径见 `payTagsOf`）。 */
internal const val MEDIA_EXTRA_FEE = "cirro.track.fee"
