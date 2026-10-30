@file:OptIn(UnstableApi::class)

package com.thripleq.cirro.core.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.thripleq.cirro.MainActivity
import com.thripleq.cirro.R
import com.thripleq.cirro.core.repo.LibraryStateStore
import com.thripleq.cirro.core.repo.SongLikeAction
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps playback alive in the background and drives the system media notification.
 *
 * 为什么不用 Media3 默认通知提供者：本 App 的播放器由 UI 直接持有（共享 ExoPlayer），
 * 没有 MediaController 连接，Media3 的 `DefaultMediaNotificationProvider` 在这个组合下
 * 不会自动投递通知，导致 startForegroundService() 在 5s 内没人 startForeground 而被系统杀。
 * 因此这里**自行管理前台通知**：onCreate 立刻 startForeground（永不超时），此后随播放器
 * 事件更新标题/艺人/播放态。会话仍用 `MediaStyle` 绑定，锁屏/车机能拿到媒体控制。
 *
 * ## 通知长什么样（2026-10-30 按网易云那套重排）
 *
 * 五个动作固定成一条线：**上一首 / 播放暂停 / 下一首 / 喜欢 / 播放顺序**。
 * 收起态只摆前三枚（[setShowActionsInCompactView]），展开后五枚全出 —— 与用户给的
 * 参考截图一致。封面异步加载进 largeIcon；没加载完先出门无图版，加载完再刷一张。
 *
 * **通知常驻**：服务活着通知就一直在，`setOngoing(true)` 让它连划都划不掉 —— 暂停后
 * 不该消失（用户 2026-10-30：「通知应该是常驻的，不是播放时候才有」）。冷启动恢复出
 * 暂停态的队列时也会把服务拉起来（见 `PlayerHolder.ensureNotification`），通知从打开
 * App 那一刻就在，不必等按下播放。
 *
 * **进度条不归我们画**：Android 13+ 的系统媒体卡片本会从 MediaSession 读位置与时长，
 * 播放中在卡片底部画一条可拖的进度线（原生系统如此）。但 **vivo OriginOS 把通知中心
 * 媒体卡片的进度条砍掉了**（锁屏/状态栏的播放控件还有；原系统用户在社区里一直在问
 * 「更新后进度条去哪了」）。2026-10-30 本机实测：Cirro 的 session 正常播放中
 * （dumpsys 里 state=3、position 递增、actions 含 SEEK_TO），同一块通知中心里网易云、
 * B 站、Kanade 的卡片也都没有进度条 —— 不是我们少给了什么，是这块 ROM 不给画。
 * 位置/时长我们照常发布，原生 13+ 会自动画；自己用 RemoteViews 画一条就得放弃系统
 * 媒体卡片（连同锁屏/车机控制），不值当。13 以下本来就没有这个控件。
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    /** 红心动作与「我的」页、播放页共用同一份实现（见 [SongLikeAction]）。 */
    @Inject
    lateinit var likeAction: SongLikeAction

    /** 只用来订阅喜欢集合：别处点了红心，通知这颗心也要跟着换。 */
    @Inject
    lateinit var library: LibraryStateStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var player: Player
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private var notificationManager: NotificationManager? = null
    private var activityIntent: PendingIntent? = null

    /** 上次投递通知时的内容指纹，见 [notifKey]；只在主线程读写。 */
    private var lastNotifKey: String? = null

    /** 封面：当前曲目的 artworkUri 与它对应的位图（异步加载，未完成时 null）。 */
    private var artworkUrl: String? = null
    private var artworkBitmap: Bitmap? = null
    private var artworkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()

        player = PlayerHolder.get(this)
        val launchIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.takeIf { it.action == Intent.ACTION_MAIN }
            ?: Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        activityIntent = pending
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .build()
        mediaSession = session

        // 立即进入前台：满足 startForegroundService() 的 5s 窗口，避免慢加载被杀。
        //
        // 三参那个 `startForeground(id, notification, type)` 是 **API 29** 才有的，
        // 而 minSdk = 26 —— 在 Android 8/9 上调它直接 `NoSuchMethodError`（lint 的
        // NewApi 抓到的就是这条）。type 只是给系统声明「这是放音乐的」，低版本没有
        // 这个概念，走两参重载即可。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(session),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, buildNotification(session))
        }
        lastNotifKey = notifKey()
        syncArtwork()

        // 随播放器事件刷新通知（标题/艺人/播放暂停/播放顺序）。只 notify，不再重复
        // startForeground。先比内容指纹：onEvents 覆盖 buffering / 状态迁移等一大票事件，
        // 无差别重建会每次都新建 PendingIntent + Builder + MediaStyle（还带一次跨进程通知
        // 投递）—— 指纹没变就直接返回。
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncArtwork()
                refresh()
            }
        }
        playerListener = listener
        player.addListener(listener)

        // 红心可能在播放页/「我的」那边被改：订阅喜欢集合，通知那颗心立刻跟上。
        scope.launch { library.likedIds.collect { refresh() } }
    }

    /**
     * 空实现：通知由本类自己维护（见 [buildNotification]），不交给 Media3 默认提供者，
     * 否则可能与其内部前台逻辑重复/冲突。
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) = Unit

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> {
                if (player.isPlaying) player.pause() else PlayerHolder.togglePlay(player)
            }
            ACTION_PREVIOUS -> PlayerHolder.skipPrevious(player)
            ACTION_NEXT -> PlayerHolder.skipNext(player)
            ACTION_LIKE -> toggleLikeFromNotification()
            ACTION_PLAY_ORDER -> {
                PlayerHolder.cyclePlayOrder(player)
                // 三档共用一枚键，图标差别不足以说明档位，补一句 Toast。
                Toast.makeText(this, playOrderLabel(), Toast.LENGTH_SHORT).show()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun toggleLikeFromNotification() {
        val id = player.currentMediaItem?.mediaId ?: return
        scope.launch {
            likeAction.toggle(id)?.let {
                Toast.makeText(this@PlaybackService, it, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    /**
     * 通知内容指纹。**只放真正影响这张通知长相的字段**：少一个会漏更新（`isPlaying`
     * 决定播放/暂停那枚图标；红心与播放顺序各决定一枚图标），多一个（如进度）会让
     * 去重直接失效。标题/艺人在 [buildNotification] 里取自 mediaMetadata，所以必须
     * 参与指纹。
     */
    private fun notifKey(): String {
        val meta = player.mediaMetadata
        return buildString {
            append(player.currentMediaItemIndex)
            append('|').append(player.isPlaying)
            append('|').append(meta.title)
            append('|').append(meta.artist)
            append('|').append(likeAction.isLiked(player.currentMediaItem?.mediaId))
            append('|').append(player.shuffleModeEnabled)
            append('|').append(player.repeatMode)
            // 封面是异步来的：加载完成那一下也要换一张通知。
            append('|').append(artworkBitmap != null)
        }
    }

    /** 指纹变了才重投；所有「状态可能变了」的地方都走这里。 */
    private fun refresh() {
        val key = notifKey()
        if (key == lastNotifKey) return
        lastNotifKey = key
        val session = mediaSession ?: return
        notificationManager?.notify(NOTIF_ID, buildNotification(session))
    }

    /**
     * 切歌时把封面换成当前曲目的。[artworkUrl] 是「这张位图属于哪首歌」的账本：
     * 加载完成时如果人已经切走，就丢弃那份结果（见 [artworkJob] 里的比对）。
     */
    private fun syncArtwork() {
        val url = player.mediaMetadata.artworkUri?.toString().orEmpty()
        if (url == artworkUrl) return
        artworkUrl = url
        artworkBitmap = null
        artworkJob?.cancel()
        if (url.isBlank()) return
        artworkJob = scope.launch {
            // 走 Coil 的内存/磁盘缓存：同一首歌来回切不会重复下载。
            val request = ImageRequest.Builder(this@PlaybackService)
                .data(url)
                .size(512, 512)
                .allowHardware(false)
                .build()
            val drawable = (runCatching { imageLoader.execute(request) }.getOrNull()
                as? SuccessResult)?.drawable
            if (artworkUrl != url) return@launch
            artworkBitmap = drawable?.toBitmap()
            refresh()
        }
    }

    private fun buildNotification(session: MediaSession): Notification {
        val meta = player.mediaMetadata
        val title = meta.title?.toString().orEmpty().ifBlank { "cirro" }
        val artist = meta.artist?.toString().orEmpty()
        val playing = player.isPlaying
        val liked = likeAction.isLiked(player.currentMediaItem?.mediaId)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(activityIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            // 常驻：暂停也不消失、划也划不掉（见类注释「通知常驻」）。
            .setOngoing(true)
            .addAction(R.drawable.ic_notif_prev, "上一首", serviceAction(2, ACTION_PREVIOUS))
            .addAction(
                if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (playing) "暂停" else "播放",
                serviceAction(1, ACTION_TOGGLE_PLAY),
            )
            .addAction(R.drawable.ic_notif_next, "下一首", serviceAction(3, ACTION_NEXT))
            .addAction(
                if (liked) R.drawable.ic_notif_heart else R.drawable.ic_notif_heart_outline,
                if (liked) "取消喜欢" else "喜欢",
                serviceAction(4, ACTION_LIKE),
            )
            .addAction(playOrderIcon(), playOrderLabel(), serviceAction(5, ACTION_PLAY_ORDER))
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    // 收起态只摆这三枚：与参考截图一致；展开后五枚全出。
                    .setShowActionsInCompactView(0, 1, 2),
            )
        artworkBitmap?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun serviceAction(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun playOrderIcon(): Int = when {
        player.shuffleModeEnabled -> R.drawable.ic_notif_shuffle
        player.repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
        else -> R.drawable.ic_notif_repeat
    }

    /** 档位文案，与播放页那枚键同一套说法（见 `PlayerTransportSection`）。 */
    private fun playOrderLabel(): String = when {
        player.shuffleModeEnabled -> "随机播放"
        player.repeatMode == Player.REPEAT_MODE_ONE -> "单曲循环"
        else -> "列表循环"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        playerListener?.let { player.removeListener(it) }
        playerListener = null
        artworkJob?.cancel()
        scope.cancel()
        // 复位前台标记：服务结束后下一次播放必须能重新拉起，否则通知不再出现。
        PlayerHolder.onServiceDestroyed()
        mediaSession?.release()
        mediaSession = null
        // Do NOT release the player here. PlayerHolder is a process-scoped
        // singleton the UI retains; the service stopping (e.g. user dismisses the
        // media notification) must not tear down an instance still in use.
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playback/player alive; playback continues until the user pauses or
        // the OS reclaims the process.
        super.onTaskRemoved(rootIntent)
    }

    private fun createChannel() {
        // 同名 id 再建一次会更新名称/描述（重要性创建后不可改），所以旧设备上也一并变中文。
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "播放控制",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示当前曲目，并提供播放、切歌、喜欢与播放顺序按钮。"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 11
        private const val ACTION_TOGGLE_PLAY = "com.thripleq.cirro.action.TOGGLE_PLAY"
        private const val ACTION_PREVIOUS = "com.thripleq.cirro.action.PREVIOUS"
        private const val ACTION_NEXT = "com.thripleq.cirro.action.NEXT"
        private const val ACTION_LIKE = "com.thripleq.cirro.action.LIKE"
        private const val ACTION_PLAY_ORDER = "com.thripleq.cirro.action.PLAY_ORDER"
    }
}
