package com.thripleq.cirro.core.playback

import android.content.Context
import android.util.Log
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.net.CookieStore
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 「上报播放记录到网易云」的实现端：接住 [PlayerHolder] 的播放判据，把
 * `clientlog` 的那条写请求发出去。
 *
 * 三道闸，缺一不可（写的是用户账号里的数据）：
 * 1. 设置页开关（[PlaybackPreferences.scrobbleEnabled]，默认关）；
 * 2. 已登录 —— 没有导入过 cookie 就无从认证，直接跳过；
 * 3. 网络失败只吞掉：听歌不该因为上报失败而中断，也不重试（避免重复计数）。
 *
 * `source` 固定 `list`、`sourceId` 固定 0：播放器队列没有携带「从哪个歌单/
 * 专辑点的」，而排行只认歌曲 id + 时长。后台发出，不占主线程。
 */
@Singleton
class ScrobbleReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: NetEaseGateway,
    private val cookies: CookieStore,
) : ScrobbleSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScrobble(songId: String, listenedSeconds: Long) {
        if (!PlaybackPreferences.scrobbleEnabled(context)) return
        if (!cookies.file.exists()) return
        scope.launch {
            val result = runCatching {
                gateway.callBlocking(
                    NeteaseOp.SCROBBLE,
                    songId,
                    "0",
                    listenedSeconds.toString(),
                    "list",
                )
            }.getOrNull() ?: return@launch
            if (BuildConfig.DEBUG) {
                Log.d(
                    "ScrobbleReporter",
                    "song=$songId time=${listenedSeconds}s -> code=${result.code} err=${result.err}",
                )
            }
        }
    }
}