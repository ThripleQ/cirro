package com.thripleq.nume.core.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.thripleq.nume.MainActivity

/**
 * Keeps playback alive in the background and drives the system media notification.
 *
 * 为什么不用 Media3 默认通知提供者：本 App 的播放器由 UI 直接持有（共享 ExoPlayer），
 * 没有 MediaController 连接，Media3 的 `DefaultMediaNotificationProvider` 在这个组合下
 * 不会自动投递通知，导致 startForegroundService() 在 5s 内没人 startForeground 而被系统杀。
 * 因此这里**自行管理前台通知**：onCreate 立刻 startForeground（永不超时），此后随播放器
 * 事件更新标题/艺人/播放态。会话仍用 `MediaStyle` 绑定，锁屏/车机能拿到媒体控制。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()

        val player = PlayerHolder.get(this)
        val launchIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.takeIf { it.action == Intent.ACTION_MAIN }
            ?: Intent(this, MainActivity::class.java)
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .build()
        mediaSession = session

        // 立即进入前台：满足 startForegroundService() 的 5s 窗口，避免慢加载被杀。
        startForeground(
            NOTIF_ID,
            buildNotification(player, session, activityIntent),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        // 随播放器事件刷新通知（标题/艺人/播放暂停）。只 notify，不再重复 startForeground。
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                notificationManager?.notify(NOTIF_ID, buildNotification(player, session, activityIntent))
            }
        }
        playerListener = listener
        player.addListener(listener)
    }

    /**
     * 空实现：通知由本类自己维护（见 [buildNotification]），不交给 Media3 默认提供者，
     * 否则可能与其内部前台逻辑重复/冲突。
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) = Unit

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_PLAY) {
            val player = PlayerHolder.get(this)
            if (player.isPlaying) player.pause() else PlayerHolder.togglePlay(player)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildNotification(
        player: Player,
        session: MediaSession,
        activityIntent: PendingIntent,
    ): Notification {
        val meta = player.mediaMetadata
        val title = meta.title?.toString().orEmpty().ifBlank { "nume" }
        val artist = meta.artist?.toString().orEmpty()
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionIcon =
            if (player.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        val actionLabel = if (player.isPlaying) "暂停" else "播放"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(activityIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .addAction(actionIcon, actionLabel, toggleIntent)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        playerListener?.let { PlayerHolder.get(this).removeListener(it) }
        playerListener = null
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
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 11
        private const val ACTION_TOGGLE_PLAY = "com.thripleq.nume.action.TOGGLE_PLAY"
    }
}
