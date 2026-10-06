package com.thripleq.cirro.core.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * App-wide playback byte-cache. Format-agnostic: Media3's [SimpleCache] proxies
 * every read through the [CacheDataSource] so ExoPlayer gets random access to
 * whatever the source serves (mp3/aac/flac/wav alike).
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