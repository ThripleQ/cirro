package com.thripleq.cirro.core.playback

import android.content.Context
import com.thripleq.cirro.core.repo.Track
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

    fun save(context: Context, snapshot: Snapshot) {
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

    fun load(context: Context): Snapshot? {
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
