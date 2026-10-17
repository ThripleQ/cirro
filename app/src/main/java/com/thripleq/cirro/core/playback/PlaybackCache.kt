@file:OptIn(UnstableApi::class)

package com.thripleq.cirro.core.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * App-wide playback byte-cache. Format-agnostic: Media3's [SimpleCache] proxies
 * every read through the [CacheDataSource] so ExoPlayer gets random access to
 * whatever the source serves (mp3/aac/flac/wav alike).
 *
 * ## 文件头那行 `@file:OptIn(UnstableApi::class)` 的由来（全项目 9 个文件都有）
 * Media3 把 `SimpleCache` / `CacheDataSource` 与一大票 ExoPlayer API 标成了 `@UnstableApi`
 * —— 它们的实际形态在 Media3 1.x 里长期没变，但**编译期不报错**，只有 lint 的
 * `UnsafeOptInUsageError` 会以 **Error 级**逐个文件查（漏一个就挡住 CI）。
 *
 * 之所以用**文件级** `@file:OptIn` 而不是往每个类/函数上挂注解：同一个文件里往往有多处
 * 用到（伴生对象、私有扩展函数各来一发），类级注解盖不全，逐处标则每次改这个文件都要
 * 记得再标一遍。文件级一行全覆盖，缺的代价也只是 lint 再红一次 —— 而这正是 CI 门禁的用途。
 *
 * 新增一个用到 Media3 这些 API 的文件时：**照抄那一行 + 那条 import**。
 */
object PlaybackCache {

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MiB
}