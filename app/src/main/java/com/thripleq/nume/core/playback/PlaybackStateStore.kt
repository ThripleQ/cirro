package com.thripleq.nume.core.playback

import android.content.Context
import com.thripleq.nume.core.repo.Track
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

    fun save(context: Context, snapshot: Snapshot) {
        val root = JSONObject().apply {
            put("index", snapshot.index)
            put("position", snapshot.positionMs)
            put("shuffle", snapshot.shuffle)
            put("repeat", snapshot.repeatMode)
            put(
                "queue",
                JSONArray().apply {
                    snapshot.queue.forEach { t ->
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
                },
            )
        }
        prefs(context).edit().putString(KEY_SNAPSHOT, root.toString()).apply()
    }

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
