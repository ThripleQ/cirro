package com.thripleq.cirro.core.playback

import android.content.Context
import com.thripleq.cirro.core.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * 播放状态持久化（SharedPreferences + JSON）：进程被杀 / 用户退出后，下次启动恢复
 * **上次的队列、当前曲目、进度、随机/循环模式**（恢复为暂停态，等用户点播放）。
 *
 * 只存最小必要字段，逐项存 [Track]（id/名称/艺人/封面/时长/专辑）。恢复时据此重建
 * MediaItem —— 音频 URL 依旧由 [PlaybackUrls] 惰性解析，**不落盘任何带签名的 URL**
 * （签名有时效，存了也会过期，且属于敏感数据）。
 */
internal object PlaybackStateStore {

    private const val FILE = "playback_state"
    private const val KEY_SNAPSHOT = "snapshot"

    data class Snapshot(
        val queue: List<Track>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    // 队列序列化缓存。save() 由切歌/播放/暂停/READY 事件与 5s 进度轮询共同驱动，
    // 但**队列本身**只在换歌单/增删队列时变 —— 其余时刻复用上次的 JSON 片段，
    // 省掉每次 save 重建几百个 JSONObject 的开销。用内容比较而非引用：调用方
    // 每次可能重新 map 出新 List，引用比较会一路失效。
    private val cacheLock = Any()
    private var cachedQueue: List<Track>? = null
    private var cachedQueueJson: String? = null

    // 预热结果（见 [prewarm]）。写盘即失效：预热缓存的是「启动那一刻」的磁盘内容，
    // 一旦 save 过就必须回落到真读。
    private val warmLock = Any()
    private var warmDone = false
    private var warmSnapshot: Snapshot? = null

    fun save(context: Context, snapshot: Snapshot) {
        synchronized(warmLock) {
            warmDone = false
            warmSnapshot = null
        }
        val queueJson = synchronized(cacheLock) {
            if (cachedQueueJson == null || cachedQueue != snapshot.queue) {
                cachedQueue = snapshot.queue
                cachedQueueJson = queueJsonOf(snapshot.queue)
            }
            cachedQueueJson!!
        }
        // 手写拼接而不是再建一整棵 JSONObject：index/position/shuffle/repeat 都是
        // 数值与布尔，直接内联没有转义风险；queue 那段用上面缓存好的字符串。
        val json = "{\"index\":${snapshot.index}" +
            ",\"position\":${snapshot.positionMs}" +
            ",\"shuffle\":${snapshot.shuffle}" +
            ",\"repeat\":${snapshot.repeatMode}" +
            ",\"queue\":$queueJson}"
        prefs(context).edit().putString(KEY_SNAPSHOT, json).apply()
    }

    private fun queueJsonOf(queue: List<Track>): String =
        JSONArray().apply {
            queue.forEach { t ->
                put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("name", t.name)
                        put("artist", t.artist)
                        put("art", t.artworkUrl ?: JSONObject.NULL)
                        put("dur", t.durationMs)
                        put("album", t.albumName)
                    },
                )
            }
        }.toString()

    /**
     * 启动预热：在**后台线程**把「读盘 + XML 解析 + JSON 解析」这一整套做掉，主线程首帧
     * 只取缓存。
     *
     * 为什么要它：[load] 的唯一调用方是 `PlayerHolder.restore()`，而 `restore()` 跑在
     * `PlayerHolder.get()` 里 —— 那是在**首帧组合期的主线程**上。队列几百首时，
     * 首次 `getSharedPreferences` 是同步读盘 + XML 解析（10~50ms），紧接着几百次
     * `optString/optLong` 又是十几毫秒，全压在首帧上。
     *
     * 由 [com.thripleq.cirro.CirroApplication.onCreate] 在 IO 线程调一次即可。
     */
    fun prewarm(context: Context) {
        val snap = loadUncached(context)
        synchronized(warmLock) {
            warmDone = true
            warmSnapshot = snap
        }
    }

    fun load(context: Context): Snapshot? {
        synchronized(warmLock) { if (warmDone) return warmSnapshot }
        return loadUncached(context)
    }

    private fun loadUncached(context: Context): Snapshot? {
        val raw = prefs(context).getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("queue") ?: return null
            val queue = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    add(
                        Track(
                            id = id,
                            name = o.optString("name"),
                            artist = o.optString("artist"),
                            // 同 TrackParser：JSON null 经 optString 会得到字面量 "null"。
                            artworkUrl = o.optString("art")
                                .takeIf { it.isNotBlank() && it != "null" },
                            durationMs = o.optLong("dur"),
                            albumName = o.optString("album"),
                        ),
                    )
                }
            }
            if (queue.isEmpty()) return null
            Snapshot(
                queue = queue,
                index = root.optInt("index"),
                positionMs = root.optLong("position"),
                shuffle = root.optBoolean("shuffle"),
                repeatMode = root.optInt("repeat"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
