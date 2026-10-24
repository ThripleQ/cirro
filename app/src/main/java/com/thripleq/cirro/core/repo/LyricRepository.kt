package com.thripleq.cirro.core.repo

import com.thripleq.cirro.core.model.LyricLine
import com.thripleq.cirro.core.model.LyricWord
import com.thripleq.cirro.core.model.Lyrics
import android.util.Log
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** `[mm:ss]` / `[mm:ss.xx]` / `[mm:ss.xxx]` 时间标签；一行可挂多个。 */
private val LRC_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

/**
 * yrc 行头 `[行起ms,行时长ms]`。**必须锚定行首**：行体里的 `(字起,字时长,类型)`
 * 是另一个括号格式，锚定后两者不会互相误吃。
 */
private val YRC_LINE = Regex("^\\[(\\d+),(\\d+)]")

/** yrc 片段 `(字起ms,字时长ms,类型)`。类型实测恒为 0，忽略。 */
private val YRC_WORD = Regex("\\((\\d+),(\\d+),\\d+\\)")

/**
 * 翻译与主行允许的最大时间差。同一首歌的 lrc/tlyric（或 yrc/ytlrc）绝大多数时间戳
 * **完全相等**；但不同来源偶尔差几十毫秒 —— 原先的精确匹配会让这些行整句丢翻译。
 * 200ms 远小于行距（通常 ≥1s），不会串行。
 */
private const val TRANSLATION_TOLERANCE_MS = 200L

/**
 * 解析 LRC：逐行取行首连续的 `[mm:ss.xx]` 标签（一行可多标签），标签之后的文本即歌词。
 * 元数据行（`[ti:…]`/`[offset:…]` 等无时间标签的行）自然被跳过。结果按时间升序。
 *
 * 分行用 [String.lineSequence]（LF / CRLF / CR 都认），并去掉行首 BOM —— 原先只
 * `split('\n')`，整份文件若用 CR 分行会把所有行并成一行、只剩最后一个标签的文本。
 * 没有文本的定时行（纯间隔）不进列表：它在 UI 里就是空行，跳过不会少任何可见内容。
 */
private fun parseLrc(raw: String?): List<Pair<Long, String>> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    val out = ArrayList<Pair<Long, String>>()
    for (rawLine in raw.lineSequence()) {
        val line = rawLine.trim().removePrefix("\uFEFF")
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

/**
 * 解析 yrc（逐字歌词）：每行 `[行起,行时长](字起,字时长,0)字…`。
 *
 * **片段文本 = 本标记之后、下一个标记之前的全部字符**，空格原样保留：网易云把
 * 英文词间空格挂在**前一个片段**末尾（`(0,400,0)We (400,600,0)were`），所以所有
 * 片段首尾相接就是这一行的官方文本。中文/日文通常一字一段，英文整词一段 ——
 * 不需要再猜分词规则，直接照标记的边界切。
 *
 * 行时间取行头（比行级 LRC 更准，实测常早几十到几百毫秒）；行首/行尾空白会从
 * 首末片段里剪掉（居中排版不留偏移），行内空格保留。没有片段的行走 null。
 */
internal fun parseYrc(raw: String?): List<LyricLine> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    val out = ArrayList<LyricLine>()
    for (rawLine in raw.lineSequence()) {
        if (rawLine.isBlank()) continue
        val line = rawLine.removePrefix("\uFEFF")
        val head = YRC_LINE.find(line) ?: continue
        val body = line.substring(head.range.last + 1)
        val marks = YRC_WORD.findAll(body).toList()
        if (marks.isEmpty()) continue
        val words = ArrayList<LyricWord>(marks.size)
        marks.forEachIndexed { i, m ->
            val from = m.range.last + 1
            val to = if (i + 1 < marks.size) marks[i + 1].range.first else body.length
            if (to <= from) return@forEachIndexed
            words += LyricWord(
                timeMs = m.groupValues[1].toLong(),
                durationMs = m.groupValues[2].toLong(),
                text = body.substring(from, to),
            )
        }
        if (words.isEmpty()) continue
        words[0] = words[0].copy(text = words[0].text.trimStart())
        words[words.size - 1] =
            words[words.size - 1].copy(text = words[words.size - 1].text.trimEnd())
        val text = words.joinToString(separator = "") { it.text }
        if (text.isBlank()) continue
        out += LyricLine(
            timeMs = head.groupValues[1].toLong(),
            text = text,
            words = words,
        )
    }
    out.sortBy { it.timeMs }
    return out
}

/** 插入点左侧/右侧最近的两条翻译里取更近的一条，超出容差返回 null。 */
private fun nearestTranslation(translations: List<Pair<Long, String>>, timeMs: Long): String? {
    var lo = 0
    var hi = translations.size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (translations[mid].first < timeMs) lo = mid + 1 else hi = mid
    }
    var best: String? = null
    var bestDelta = Long.MAX_VALUE
    for (i in intArrayOf(lo - 1, lo)) {
        val candidate = translations.getOrNull(i) ?: continue
        val delta = abs(candidate.first - timeMs)
        if (delta < bestDelta) {
            bestDelta = delta
            best = candidate.second
        }
    }
    return if (bestDelta <= TRANSLATION_TOLERANCE_MS) best else null
}

/** 给主歌词按时间挂翻译：先精确匹配，再按 [TRANSLATION_TOLERANCE_MS] 找最近行。 */
private fun attachTranslations(
    lines: List<LyricLine>,
    translations: List<Pair<Long, String>>,
): List<LyricLine> {
    if (translations.isEmpty()) return lines
    val exact = HashMap<Long, String>(translations.size)
    translations.forEach { (timeMs, text) -> exact[timeMs] = text }
    return lines.map { line ->
        val text = exact[line.timeMs] ?: nearestTranslation(translations, line.timeMs)
        if (text.isNullOrBlank()) line else line.copy(translation = text)
    }
}

/**
 * 合并歌词：**有 yrc 就用 yrc**（行时间与字时间都更准，且行边界就是官方逐字文本
 * 的边界），没有才退回 LRC。翻译优先用 `ytlrc`（与 yrc 对齐），再退 `tlyric`。
 */
fun parseLyrics(
    mainLrc: String?,
    translationLrc: String?,
    wordLrc: String? = null,
    wordTranslationLrc: String? = null,
): Lyrics {
    val wordLines = parseYrc(wordLrc)
    val lines = if (wordLines.isNotEmpty()) {
        wordLines
    } else {
        parseLrc(mainLrc).map { (timeMs, text) -> LyricLine(timeMs, text) }
    }
    if (lines.isEmpty()) return Lyrics(emptyList())
    val translations = if (wordLines.isNotEmpty() && !wordTranslationLrc.isNullOrBlank()) {
        parseLrc(wordTranslationLrc)
    } else {
        parseLrc(translationLrc)
    }
    return Lyrics(attachTranslations(lines, translations))
}

/**
 * 歌词来源：/api/song/lyric（op=15）。结果按 songId 缓存在内存里（歌词不变，
 * 且同一首翻来覆去播）。网络失败不写缓存，下次可重试。
 *
 * 2026-10 起请求带 `yv/ytv/yrv`，返回多出 `yrc`（逐字主歌词）、`ytlrc`（逐字翻译，
 * 行级 LRC）、`yromalrc`（罗马音）。**逐字歌词不是每首歌都有**：没有 `yrc` 时
 * [parseYrc] 返回空，自动退回 `lrc` 的整行高亮。
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
                mainLrc = root.optJSONObject("lrc")?.optString("lyric"),
                translationLrc = root.optJSONObject("tlyric")?.optString("lyric"),
                wordLrc = root.optJSONObject("yrc")?.optString("lyric"),
                wordTranslationLrc = root.optJSONObject("ytlrc")?.optString("lyric"),
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
