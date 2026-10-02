package com.thripleq.nume.core.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.thripleq.nume.core.repo.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 [Track] 列表交给共享播放器：**整单一次性入队**。
 *
 * 队列里每个 [MediaItem] 用稳定的合成 URI（`nume://song/<id>`），真正的签名音频
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

    /** Plays [tracks] as the queue, starting at [index]. */
    fun play(context: Context, tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val player = PlayerHolder.get(context)

        // 先登记队列：状态持久化（PlaybackStateStore）据此落盘整单，切歌/退出后能原样恢复。
        PlayerHolder.rememberQueue(tracks)

        val idx = index.coerceIn(0, tracks.lastIndex)
        player.setMediaItems(tracks.map(::trackMediaItem), idx, 0L)
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
 * `nume://song/<id>`。同时供 [PlayerHolder] 恢复上次队列时复用。
 *
 * 元数据里**声明时长**：恢复态停在 STATE_IDLE（不 prepare、不联网），此时
 * `player.duration` 为 0，UI 靠这个声明值把进度条/总时长显示出来（见 rememberPlayerState）。
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
                .apply { if (track.durationMs > 0L) setDurationMs(track.durationMs) }
                .build(),
        )
        .build()
