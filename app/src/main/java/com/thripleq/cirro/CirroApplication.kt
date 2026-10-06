package com.thripleq.cirro

import android.app.Application
import android.os.Build
import android.os.StrictMode
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.thripleq.cirro.core.playback.PlaybackUrls
import com.thripleq.cirro.core.playback.PlayerHolder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** App entry point; Hilt generates the dependency graph rooted here. */
@HiltAndroidApp
class CirroApplication : Application() {

    // 播放器的惰性 URL 解析器（ResolvingDataSource 在 ExoPlayer 加载线程上回调）。
    // 必须在任何 PlayerHolder.get() 之前安装好，Application.onCreate 是所有
    // Activity/Service 之前唯一确定的时机。
    @Inject
    lateinit var playbackUrls: PlaybackUrls

    override fun onCreate() {
        super.onCreate()
        PlayerHolder.installUrlSource(playbackUrls)
        if (BuildConfig.DEBUG) enableStrictMode()
        // 封面加载策略对齐成熟 Compose 播放器（InnerTune/ViMusic）的验证过路径：
        // 不预载、不限制并发（Coil 默认线程池 + LRU 已是千万设备验证过的行为）、磁盘缓存兜底。
        //
        // - 不预载：进列表不预拉一堆图（之前并发预载会一波占满 IO 线程，叠加滚动造成掉帧）。
        // - 磁盘缓存：封面持久化，首次冷缓存需下载，之后进入/重启命中磁盘，不再重复下载解码
        //   （实测"只有第一次卡"，正是磁盘缓存命中与否的差别）。
        // - crossfade(true)：加载完成淡入，观感顺滑（100ms 默认，不增 jank）。
        // - respectCacheHeaders(false)：网易云 CDN 若带 Cache-Control/Expires，默认 true 会跳过
        //   磁盘缓存每次重新下载 → 缓存看似"不生效"。关掉后永远命中磁盘缓存。
        // - allowHardware(true)：硬件位图，GPU 直接渲染纹理，减少 CPU 上传位图开销（CPU 是瓶颈）。
        val imageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    /** Debug-only: surface main-thread disk/network and leaks in logcat. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}