package com.thripleq.cirro.core.playback

import android.content.Context
import android.net.Uri
import com.thripleq.cirro.core.net.ApiResult
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 解析的产物是 [PlayableUrl] 而不只是 URL —— 它同时带出响应里的 `freeTrialInfo`
 * （非会员只能听到的**试听片段**，见 [TrialClip]）。这一项必须在这里取：响应的 body
 * 只在这一层出现过，播放器与 UI 都拿不到它。判断口径与实测依据全在 [PlayableUrl] 的注释里。
 *
 * ## 缓存命中时不会走到这里
 *
 * [PlaybackCache] 在解析器的**下游**（见 `PlayerHolder.build`）：字节缓存命中时
 * ExoPlayer 根本不打开 URL，于是本类既查不到、也学不到试听身份。而试听身份不能从
 * `fee` 推（已购单曲 fee=1 却是整曲），所以必须由切歌路径主动补一次解析 ——
 * 见 `PlayerHolder.learnTrial`。**别把这条当成可选优化删掉**：删了以后「重启后重播
 * 缓存过的试听曲」会退回「无试听标记 + 播完自动跳下一首」。
 *
 * [resolve] 运行在 ExoPlayer 的加载线程（非主线程），因此走 [NetEaseGateway.callBlocking]。
 */
@Singleton
class PlaybackUrls @Inject constructor(
    private val gateway: NetEaseGateway,
    @ApplicationContext private val context: Context,
) : PlaybackUrlSource {
    private data class Cached(val result: PlayableUrl?, val at: Long)

    private val urlCache = ConcurrentHashMap<String, Cached>()

    // 同一首歌的并发解析合并成一次。两条路径会同时要它：ExoPlayer 打开字节流（加载线程）
    // 与切歌时的试听补登记（PlayerHolder.learnTrial，IO 线程）。没有这个锁，
    // 缓存未命中的新歌会连发两次 URL 请求。
    private val resolveLocks = ConcurrentHashMap<String, Any>()

    /**
     * 解析歌曲的音频 URL；解析失败（无版权 / 未登录无权益 / 风控 / 网络）返回 null，
     * 由 ExoPlayer 的加载错误触发 [PlayerHolder] 的"自动跳下一首"。
     */
    override fun resolve(songId: String): PlayableUrl? {
        val lock = resolveLocks.getOrPut(songId) { Any() }
        try {
            synchronized(lock) { return resolveLocked(songId) }
        } finally {
            resolveLocks.remove(songId, lock)
        }
    }

    private fun resolveLocked(songId: String): PlayableUrl? {
        val now = System.currentTimeMillis()
        urlCache[songId]?.let { hit ->
            // 成功结果按 CACHE_TTL_MS 复用；失败（null）只短缓存：网络抖动 / VIP 判定
            // 这类失败不该让该曲在 6 小时内被永久跳过。
            val ttl = if (hit.result != null) CACHE_TTL_MS else FAILURE_TTL_MS
            if (now - hit.at < ttl) return hit.result
        }
        val result = query(songId)
        urlCache[songId] = Cached(result, now)
        return result
    }

    /** 签名 URL 过期（播放中遇 CDN 403）时由 [PlayerHolder] 调用，下次解析重新请求。 */
    override fun invalidate(songId: String) {
        urlCache.remove(songId)
    }

    /**
     * 该曲**已知**的试听信息；缓存里没有就返回 null，**绝不发请求**。
     *
     * 调用方是 [PlayerHolder] 的 `onMediaItemTransition`（主线程）——切歌那一刻要立刻决定
     * 「这首要不要在片段末尾停住」。所以这里只能在缓存里查：真正解析发生在加载线程，
     * 切歌时未必已经回来；解析回来那一路会自己补登记（见 PlayerHolder 的解析回调）。
     *
     * TTL 与 [resolve] 同源：过期的条目不再代表当下的权益（用户可能刚买了这首歌，
     * 也可能刚掉会员），宁可返回 null 让它退化成「按整曲播」，也不要拿旧判据去叫停。
     */
    override fun trialOf(songId: String): TrialClip? {
        val hit = urlCache[songId] ?: return null
        if (System.currentTimeMillis() - hit.at >= CACHE_TTL_MS) return null
        return hit.result?.trial
    }

    /**
     * 该曲的试听身份是否**已知**（已知「不是试听」也算已知，与 [trialOf] 返回 null 的
     * 「不知道」区分开）。切歌时据此决定要不要补一次解析，见 [PlayerHolder.learnTrial]。
     */
    override fun isTrialKnown(songId: String): Boolean {
        val hit = urlCache[songId] ?: return false
        return hit.result != null && System.currentTimeMillis() - hit.at < CACHE_TTL_MS
    }

    private fun query(songId: String): PlayableUrl? {
        // 用户选定的音质档取不到时逐级向下回退（最终兜底 standard），与上游一致。
        //
        // 对 VIP 歌这条回退**实际上不会走满**：实测非会员请求 lossless/exhigh，服务端都直接
        // 返回 standard 档的试听片段（`level` 被钳到 standard、br=128012），第一档就有 url。
        val order = PlaybackPreferences.qualityFallbackOrder(
            PlaybackPreferences.quality(context),
        )
        for (quality in order) {
            val result = parseResult(gateway.callBlocking(NeteaseOp.SONG_URL_V1, songId, quality))
            if (result != null) return result
        }
        return null
    }

    private fun parseResult(result: ApiResult): PlayableUrl? {
        if (result.err != 0) return null
        return parseSongUrlResponse(String(result.body, Charsets.UTF_8))
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

        // 音频 URL 有效期数小时，缓存 6h 后重新解析。
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        // 解析失败短缓存，尽快允许重试。
        private const val FAILURE_TTL_MS = 60 * 1000L
    }
}
