package com.thripleq.cirro.core.playback

import android.content.Context
import android.net.Uri
import com.thripleq.cirro.core.net.ApiResult
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 该曲确实拿不到可播 URL（无版权 / VIP 未登录无权益）——不可靠重试恢复，应跳过。 */
class NoPlayableUrlException(songId: String) : IOException("no playable url for song $songId")

/**
 * 歌曲 id → 可播音频 URL 的解析与缓存，作为 Media3 [androidx.media3.datasource.ResolvingDataSource]
 * 的解析器：ExoPlayer 真正打开某首歌的字节流那一刻才解析签名 URL（惰性）。
 *
 * 这样队列里只放**稳定的合成 URI**（[uriFor]），于是：
 * - 整单可以一次性入队，shuffle / 循环 / 连播全部交给 ExoPlayer，无需增量补队列；
 * - SimpleCache 的缓存键是合成 URI，与带时效的签名 URL 解耦 —— URL 轮换不会让已下载的音频失效；
 * - 不再有"点 VIP 歌要线性扫描整单找可播曲"的无上限请求。
 *
 * [resolve] 运行在 ExoPlayer 的加载线程（非主线程），因此走 [NetEaseGateway.callBlocking]。
 */
@Singleton
class PlaybackUrls @Inject constructor(
    private val gateway: NetEaseGateway,
    @ApplicationContext private val context: Context,
) : PlaybackUrlSource {
    private data class CachedUrl(val url: String?, val at: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    /**
     * 解析歌曲的音频 URL；解析失败（无版权 / 未登录无权益 / 风控 / 网络）返回 null，
     * 由 ExoPlayer 的加载错误触发 [PlayerHolder] 的"自动跳下一首"。
     */
    override fun resolve(id: String): String? {
        val now = System.currentTimeMillis()
        urlCache[id]?.let { hit ->
            // 成功结果按 CACHE_TTL_MS 复用；失败（null）只短缓存：网络抖动 / VIP 判定
            // 这类失败不该让该曲在 6 小时内被永久跳过。
            val ttl = if (hit.url != null) CACHE_TTL_MS else FAILURE_TTL_MS
            if (now - hit.at < ttl) return hit.url
        }
        val url = query(id)
        urlCache[id] = CachedUrl(url, now)
        return url
    }

    /** 签名 URL 过期（播放中遇 CDN 403）时由 [PlayerHolder] 调用，下次解析重新请求。 */
    override fun invalidate(id: String) {
        urlCache.remove(id)
    }

    private fun query(id: String): String? {
        // 用户选定的音质档取不到时逐级向下回退（最终兜底 standard），与上游一致。
        val order = PlaybackPreferences.qualityFallbackOrder(
            PlaybackPreferences.quality(context),
        )
        for (quality in order) {
            val url = parseUrl(gateway.callBlocking(NeteaseOp.SONG_URL_V1, id, quality))
            if (url != null) return url
        }
        return null
    }

    private fun parseUrl(result: ApiResult): String? {
        if (result.err != 0) return null
        return try {
            val root = JSONObject(String(result.body, Charsets.UTF_8))
            root.getJSONArray("data")
                .getJSONObject(0)
                .optString("url")
                // 陷阱：不可播时接口返回 `"url": null`，org.json 的 optString 会得到
                // **字面量 "null"**（不是空串），必须显式过滤，否则会被当成有效 URL，
                // 既不回退 standard 档、也把 "null" 交给 ExoPlayer 当地址去请求。
                .takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
                ?.toHttps()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** 合成 URI 的 scheme/host；队列里的 MediaItem 用它，缓存也以它为键。 */
        private const val SCHEME = "cirro"
        private const val HOST = "song"

        /** 队列中每首歌的稳定 URI：`cirro://song/<id>`（不含任何时效性签名）。 */
        fun uriFor(songId: String): String = "$SCHEME://$HOST/$songId"

        /** 从合成 URI 取回歌曲 id；不是本 scheme 则返回 null。 */
        fun songId(uri: Uri): String? =
            if (uri.scheme == SCHEME && uri.host == HOST) uri.lastPathSegment else null

        /** 网易云 CDN 音频 URL 常回 `http://`，Android 禁明文；CDN 同样支持 https。 */
        internal fun String.toHttps(): String =
            if (startsWith("http://", ignoreCase = true)) "https://" + substring(7) else this

        // 音频 URL 有效期数小时，缓存 6h 后重新解析。
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        // 解析失败短缓存，尽快允许重试。
        private const val FAILURE_TTL_MS = 60 * 1000L
    }
}
