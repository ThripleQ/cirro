package com.thripleq.cirro.core.repo

import android.util.Log
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一行歌词。[translation] 是同一时间戳的翻译（tlyric），无翻译为 null。
 * [timeMs] 用作自动滚动/高亮的锚点，也是点击跳转的定位。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/** 一首歌的歌词（可能为空 = 纯音乐/未收录）。 */
data class Lyrics(val lines: List<LyricLine>) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** `[mm:ss]` / `[mm:ss.xx]` / `[mm:ss.xxx]` 时间标签；一行可挂多个。 */
private val LRC_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

/**
 * 解析 LRC：逐行取行首连续的 `[mm:ss.xx]` 标签（一行可多标签），标签之后的文本即歌词。
 * 元数据行（`[ti:…]`/`[offset:…]` 等无时间标签的行）自然被跳过。结果按时间升序。
 */
private fun parseLrc(raw: String?): List<Pair<Long, String>> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    val out = ArrayList<Pair<Long, String>>()
    for (rawLine in raw.split('\n')) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        val tags = LRC_TAG.findAll(line).toList()
        if (tags.isEmpty()) continue
        val text = line.substring(tags.last().range.last + 1).trim()
        if (text.isEmpty()) continue
        for (m in tags) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val millis = when (frac.length) {
                3 -> frac.toLong()
                2 -> frac.toLong() * 10
                1 -> frac.toLong() * 100
                else -> 0L
            }
            out.add((minutes * 60_000L + seconds * 1000L + millis) to text)
        }
    }
    out.sortBy { it.first }
    return out
}

/** 合并主歌词与翻译：翻译按时间戳精确对齐，对不上的行不带翻译。 */
fun parseLyrics(mainLrc: String?, translationLrc: String?): Lyrics {
    val main = parseLrc(mainLrc)
    if (main.isEmpty()) return Lyrics(emptyList())
    val translation = parseLrc(translationLrc).toMap()
    return Lyrics(main.map { (timeMs, text) -> LyricLine(timeMs, text, translation[timeMs]) })
}

/**
 * 歌词来源：/api/song/lyric（op=15）。结果按 songId 缓存在内存里（歌词不变，
 * 且同一首翻来覆去播）。网络失败不写缓存，下次可重试。
 */
@Singleton
class LyricRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {
    private val cache = LruCache<String, Lyrics>(24)

    suspend fun lyrics(songId: String): Lyrics = withContext(Dispatchers.IO) {
        cache[songId]?.let { return@withContext it }
        val r = gateway.call(NeteaseOp.LYRIC, songId)
        if (r.err != 0) {
            Log.e("LyricRepository", "lyric failed: err=${r.err} code=${r.code} id=$songId")
            return@withContext Lyrics(emptyList())
        }
        val parsed = try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            parseLyrics(
                root.optJSONObject("lrc")?.optString("lyric"),
                root.optJSONObject("tlyric")?.optString("lyric"),
            )
        } catch (_: Exception) {
            // 解析失败返回 null（不是"无歌词"），据此不写缓存。
            null
        }
        // 只有真正解析成功才落缓存：把 JSON 抖动固化成空歌词，会让这首歌整个会话
        // 都显示"无歌词"且无法重试。纯音乐/未收录（解析成功但为空）才缓存空结果。
        if (parsed != null) cache[songId] = parsed
        parsed ?: Lyrics(emptyList())
    }
}
