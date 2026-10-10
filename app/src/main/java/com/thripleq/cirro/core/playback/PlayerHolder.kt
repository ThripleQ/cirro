package com.thripleq.cirro.core.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.thripleq.cirro.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 惰性音频 URL 的来源：由 [PlaybackUrls] 实现，[PlayerHolder] 在 ExoPlayer 打开
 * 字节流时用它解析签名 URL，并在签名过期（CDN 403）时把它失效以触发重解析。
 */
interface PlaybackUrlSource {
    /** 解析 song id 的签名音频 URL；不可播返回 null。运行在加载线程，需阻塞。 */
    fun resolve(songId: String): String?

    /** 丢弃缓存的 URL，下次 [resolve] 重新请求（用于签名过期）。 */
    fun invalidate(songId: String)
}

/**
 * Process-scoped [ExoPlayer]. Built once with the byte-cache wired into its
 * media-source factory so every stream flows through [PlaybackCache].
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    // 由 CirroApplication 在启动时安装（见 installUrlSource）。在 ExoPlayer 加载
    // 线程上被调用，实现必须阻塞且线程安全。
    @Volatile
    private var urlSource: PlaybackUrlSource? = null

    // 已为某首歌重试过一次的标记（签名过期原地重试，最多一次，避免死循环）。
    @Volatile
    private var retriedItemId: String? = null

    // 当前队列（Track 原始对象）：MediaItem 只带 id+元数据，落盘/恢复需要完整 Track
    // （时长、专辑名等），故由 PlaybackLauncher 在入队时登记。
    @Volatile
    private var currentQueue: List<Track> = emptyList()

    // 是否已尝试过恢复上次播放状态（进程内只恢复一次）。
    @Volatile
    private var restored = false

    // 前台播放服务是否已拉起。通知/后台保活都依赖它，任何播放路径都要覆盖
    // （含恢复后的"点播放"，那条路径不经过 PlaybackLauncher）。
    @Volatile
    private var foregroundServiceStarted = false

    /** Installs the lazy URL source used by the [ResolvingDataSource]. Call once at startup. */
    fun installUrlSource(source: PlaybackUrlSource) {
        urlSource = source
    }

    /** 登记当前队列（供状态持久化落盘）。 */
    fun rememberQueue(tracks: List<Track>) {
        currentQueue = tracks
    }

    /**
     * 把 [track] 插到当前曲目的下一首位置（「下一首播放」）。
     *
     * ## 为什么必须同时改两份队列
     *
     * 播放器的队列（[MediaItem]）与 [currentQueue]（[Track]）是**按下标一一对应**的两份：
     * 落盘时用 `currentQueue[p.currentMediaItemIndex]` 取当前曲目（见 [persist]）。
     * 只往播放器里插、不动 [currentQueue]，两边下标就错开一格 —— 此后每次落盘都写错歌，
     * 下次冷启动恢复出来的是**另一首**。所以插入是「两份一起插同一个位置」的原子动作。
     *
     * 两份长度本来就对不上（例如恢复失败、或外部改过播放器队列）时**只插播放器**：
     * 这种情况下 [currentQueue] 已经不是权威映射，硬插只会把错位固定下来。等下一次
     * `setMediaItems`（PlaybackLauncher.play）重建两边的一致性。
     *
     * 必须在主线程调用（ExoPlayer 非线程安全）。
     */
    fun insertNext(context: Context, track: Track) {
        val p = get(context)
        if (p.mediaItemCount == 0) return
        // 入队**前**的长度，用来判断两份是否一致（add 之后就都是加过的了）。
        val inSync = currentQueue.size == p.mediaItemCount
        val at = (p.currentMediaItemIndex + 1).coerceIn(0, p.mediaItemCount)
        p.addMediaItem(at, trackMediaItem(track))
        if (inSync) {
            currentQueue = currentQueue.toMutableList().apply { add(at.coerceIn(0, size), track) }
        }
    }

    /**
     * 前台服务销毁时回调（用户划掉通知 / 系统回收服务）：复位标记。
     * 否则标记仍为 true，之后的播放路径会跳过 [ensureForegroundService]，
     * 导致没有媒体通知、进程在后台也更易被系统回收。
     */
    fun onServiceDestroyed() {
        foregroundServiceStarted = false
    }

    // 错误恢复用的协程作用域。object 单例的普通属性在类初始化时就求值；
    // 用 lazy 推迟到首次真正需要时再取 Main dispatcher，避免在非 UI 线程
    // （如无 Looper 的工作线程）首次触碰对象导致 Main.immediate 初始化失败。
    private val recoveryScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var recoveryJob: Job? = null

    // 状态持久化用的作用域（与 recoveryScope 同理，lazy 推迟 Main dispatcher 初始化）。
    private val persistScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    fun get(context: Context): ExoPlayer = player ?: synchronized(this) {
        player ?: build(context).also {
            it.addErrorRecovery()
            it.attachStatePersistence(context.applicationContext)
            player = it
            restore(it, context.applicationContext)
        }
    }

    /**
     * 播放失败的处理，分两种：
     * - **可恢复的 IO 错误**（CDN 403 签名过期、网络抖动）：失效该曲 URL 缓存、
     *   原地 `prepare()` 重解析一次，而不是直接跳歌（成熟播放器的做法）。
     * - **不可恢复**（无版权/VIP、确实拿不到 URL）：跳到下一首，别让队列卡死在
     *   source error 上。队列已整单入队，通常下一首就在手边。
     */
    private fun ExoPlayer.addErrorRecovery() {
        addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // 切到新曲目后清掉重试标记，让新曲目也有一次重试机会。
                retriedItemId = null
            }

            override fun onPlayerError(error: PlaybackException) {
                if (mediaItemCount == 0) return
                val id = currentMediaItem?.mediaId

                if (id != null && id != retriedItemId && error.isRetryableIo()) {
                    retriedItemId = id
                    urlSource?.invalidate(id)
                    // error 状态停在 STATE_IDLE：需要显式 prepare() 才会重新解析并加载。
                    prepare()
                    play()
                    return
                }

                if (nextMediaItemIndex != C.INDEX_UNSET) {
                    recover()
                    return
                }
                // 队列末尾失败：轮询等一会儿（万一是并发补队列的竞态），超时放弃。
                recoveryJob?.cancel()
                recoveryJob = recoveryScope.launch {
                    var attempts = 0
                    while (attempts < RECOVERY_WAIT_ATTEMPTS) {
                        delay(RECOVERY_POLL_MS)
                        attempts++
                        if (nextMediaItemIndex != C.INDEX_UNSET && mediaItemCount > 0) {
                            recover()
                            return@launch
                        }
                        if (playbackState != Player.STATE_IDLE) return@launch
                    }
                }
            }
        })
    }

    /** 该错误是否值得"失效 URL + 原地重试"（IO/HTTP/网络类），排除"无 URL 可播"。 */
    private fun PlaybackException.isRetryableIo(): Boolean {
        var t: Throwable? = this
        while (t != null) {
            if (t is NoPlayableUrlException) return false
            t = t.cause
        }
        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> true
            else -> false
        }
    }

    private fun ExoPlayer.recover() {
        seekToNextMediaItem()
        // error 状态 player 停在 STATE_IDLE：seekToNextMediaItem 只切换 index，
        // 不会自动开始缓冲；必须显式 prepare() 才会重新加载下一首的音频。
        prepare()
        play()
    }

    /**
     * 播放状态持久化：切歌/播放暂停/随机/循环/进入 READY 时落盘，另每
     * [POSITION_SAVE_INTERVAL_MS] 落一次进度（只靠事件的话，进程被杀时进度会停在
     * 最后一次事件那一刻，比如刚切歌的 0:00）。
     */
    private fun ExoPlayer.attachStatePersistence(context: Context) {
        val p = this
        addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // play() 一旦被调用就拉起前台服务（含恢复后点播放这条不经 Launcher 的路径），
                // 否则无通知、且退到后台可能被系统杀进程。
                if (playWhenReady) ensureForegroundService(context)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persist(context, p)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persist(context, p)
                if (isPlaying) ensureForegroundService(context)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                persist(context, p)
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                persist(context, p)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    persist(context, p)
                }
            }
        })
        persistScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (p.isPlaying) persist(context, p)
            }
        }
    }

    private fun persist(context: Context, p: ExoPlayer) {
        val queue = currentQueue
        if (queue.isEmpty() || p.mediaItemCount == 0) return
        PlaybackStateStore.save(
            context,
            PlaybackStateStore.Snapshot(
                queue = queue,
                index = p.currentMediaItemIndex.coerceIn(0, queue.lastIndex),
                positionMs = p.currentPosition.coerceAtLeast(0L),
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
            ),
        )
    }

    /** 幂等拉起前台播放服务；后台启动被系统拒绝时复位标记，下次再试。 */
    private fun ensureForegroundService(context: Context) {
        if (foregroundServiceStarted) return
        foregroundServiceStarted = true
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java),
            )
        } catch (_: Exception) {
            foregroundServiceStarted = false
        }
    }

    /** 启动时恢复上次的队列/进度/随机/循环；**恢复为暂停态**，等用户点播放。 */
    private fun restore(p: ExoPlayer, context: Context) {
        if (restored) return
        restored = true
        val snap = PlaybackStateStore.load(context) ?: return
        currentQueue = snap.queue
        val idx = snap.index.coerceIn(0, snap.queue.lastIndex)
        p.setMediaItems(snap.queue.map(::trackMediaItem), idx, snap.positionMs.coerceAtLeast(0L))
        p.shuffleModeEnabled = snap.shuffle
        p.repeatMode = snap.repeatMode
        // 不 prepare()/play()：避免未点播放就发起网络请求；点播放时 togglePlay 会
        // 检测到 STATE_IDLE 自动 prepare。
    }

    /** error 或已恢复但尚未加载（STATE_IDLE）时都需 prepare，play() 才会真正出声。 */
    private fun Player.ensurePrepared() {
        if (playerError != null || playbackState == Player.STATE_IDLE) prepare()
    }

    /** 统一播放/暂停：error（或恢复后的 idle）状态下 play() 需先 prepare 才会加载。 */
    fun togglePlay(player: Player) {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.ensurePrepared()
            player.play()
        }
    }

    /** 统一下一首：error/idle 状态下 seekToNext 后必须 prepare+play 才会加载新曲目。 */
    fun skipNext(player: Player) {
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.seekToNextMediaItem()
        }
    }

    /** 统一上一首：同上，error/idle 后需要显式 prepare 才能恢复加载。 */
    fun skipPrevious(player: Player) {
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            player.seekToPreviousMediaItem()
            player.prepare()
            player.play()
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    /** 统一 seek：error/idle 状态下拖动进度条同样需要先 prepare 恢复。 */
    fun seekTo(player: Player, positionMs: Long) {
        player.ensurePrepared()
        player.seekTo(positionMs)
    }

    /** 随机播放开关（直接映射 ExoPlayer.shuffleModeEnabled）。 */
    fun toggleShuffle(player: Player) {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    /** 循环模式轮换：关 → 列表循环 → 单曲循环 → 关。 */
    fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun build(context: Context): ExoPlayer {
        // Upstream HTTP (the audio CDN). Accept protocol redirects and keep a
        // UA so netease's CDN doesn't 4xx on us.
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        val base = DefaultDataSource.Factory(context, http)

        // 惰性解析：队列里放的是合成 URI `cirro://song/<id>`，真正打开某首歌的字节流
        // 时才把 URI 换成签名 URL。放在 CacheDataSource 的**上游**，于是缓存键取合成
        // URI（稳定）：命中缓存根本不触发解析，签名 URL 轮换也不会让已缓存音频失效。
        val resolving = ResolvingDataSource.Factory(base) { dataSpec ->
            val id = PlaybackUrls.songId(dataSpec.uri)
            if (id == null) {
                dataSpec
            } else {
                val url = urlSource?.resolve(id) ?: throw NoPlayableUrlException(id)
                dataSpec.withUri(Uri.parse(url))
            }
        }

        // Byte-cache front: cache hit → local read; miss → resolve URL + range request.
        val cacheFactory = CacheDataSource.Factory()
            .setCache(PlaybackCache.get(context))
            .setUpstreamDataSourceFactory(resolving)

        val mediaFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            // 流媒体用 NETWORK 唤醒锁（LOCAL 只持 CPU 锁，息屏后 WiFi 可能休眠，
            // 弱网/长缓冲时断流）。成熟播放器（Media3 示例 / ViMusic）均用 NETWORK。
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // 拔耳机/蓝牙断开自动暂停：handleAudioFocus 只处理 AudioFocus，
            // 不覆盖 AUDIO_BECOMING_NOISY；不开会突然外放。
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    const val USER_AGENT = "cirro/0.1 (Android)"

    // 错误恢复轮询：间隔与次数共同决定最长等待(3s)。后台补队列可能还没就绪，
    // 等一小段让它补上；超时则放弃这首，避免播放长时间卡死在错误态。
    private const val RECOVERY_POLL_MS = 250L
    private const val RECOVERY_WAIT_ATTEMPTS = 12

    // 播放中进度落盘间隔：只靠事件落盘的话，进程被杀时进度最多回退到上次事件那一刻。
    private const val POSITION_SAVE_INTERVAL_MS = 5_000L
}