package com.thripleq.nume.core.repo

import java.security.MessageDigest
import org.json.JSONObject

/** 线程安全 LRU 缓存：按最近使用排序，容量溢出淘汰最久未用。 */
class LruCache<K, V>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(max, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }

    operator fun get(key: K): V? = synchronized(map) { map[key] }
    operator fun set(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }
    fun clear() = synchronized(map) { map.clear() }
}

/**
 * 一个"壳子 + 列表"：榜单、歌单、专辑都是同一个结构——一段集合元数据
 * （封面/标题/播放量/收藏数/更新频率/描述/创建者）+ 曲目列表。喜欢/已购
 * 没有独立后端壳，由 ViewModel 用已有数据组装一份简化壳。
 *
 * [subscribed] 只对**歌单/榜单**有真值：`/weapi/v6/playlist/detail` 的 playlist
 * 对象自带这个布尔，所以收藏按钮的初始态跟着壳走，不需要额外请求。
 * 专辑没有对应字段（探针实测），专辑的收藏态由
 * [LibraryStateStore.subscribedAlbumIds] 提供；喜欢/已购/每日推荐这类本地
 * 组装的壳恒为 false（它们本来就不支持收藏）。
 */
data class TrackCollection(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Long,
    val updateFrequency: String,
    val description: String,
    val creator: String,
    val tracks: List<Track>,
    val subscribed: Boolean = false,
    /**
     * [playlistFingerprint] 算出的曲目指纹，空串 = 不知道（专辑、本地组装的壳、v1 迁移来的旧行）。
     *
     * 存在的理由是「判断要不要重新拉」：进页面时发一次轻量检查（`n=0`）拿新指纹，
     * 与这份比 —— 相同就说明曲目表还能用，不必拉全量。见
     * [com.thripleq.nume.core.repo.CollectionRefresher]。
     */
    val fingerprint: String = "",
)

/** 接口常把缺失字段返回为 JSON null，org.json 的 optString 会得到字面量 "null"；
 *  统一清洗掉 "null"/"undefined"，避免直接显示在界面上。 */
private fun JSONObject.strOrEmpty(key: String): String =
    optString(key).takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: ""

/**
 * 从 /api/v6/playlist/detail 返回的 playlist 对象解析壳元数据 + 曲目。
 * 榜单 id 就是歌单 id，两者共用此解析；曲目用共享的 [parseTracks]。
 */
fun parsePlaylistObject(obj: JSONObject): TrackCollection {
    val id = obj.optLong("id", 0L)
    val creator = obj.optJSONObject("creator")?.strOrEmpty("nickname") ?: ""
    return TrackCollection(
        id = id.toString(),
        name = obj.strOrEmpty("name"),
        coverUrl = httpsUrl(obj.strOrEmpty("coverImgUrl")),
        playCount = obj.optLong("playCount", 0L),
        subscribedCount = obj.optLong("subscribedCount", 0L),
        trackCount = obj.optLong("trackCount", 0L),
        updateFrequency = obj.strOrEmpty("updateFrequency"),
        description = obj.strOrEmpty("description"),
        creator = creator,
        tracks = parseTracks(obj.optJSONArray("tracks")),
        // 收藏按钮的初始态。未登录时该字段为 false —— 与"没收藏"同形，
        // 点了会拿到服务端的 301 文案，可接受（比停在加载态强）。
        subscribed = obj.optBoolean("subscribed", false),
        fingerprint = playlistFingerprint(obj),
    )
}

/**
 * 曲目 id 序列的指纹 —— 判「这个歌单的曲目有没有变」用。
 *
 * 用它的前提是探针实测过（2026-10-06）：**`trackIds` 不受 `n` 影响**。`n=0`（只要元数据）
 * 与 `n=100000`（全量）两次调用返回的 trackIds 完全一致，2213 首的歌单也全给 2213 个
 * —— `n` 只截断 `tracks[]` 的歌曲详情。于是「先发一个只要 id 的便宜请求、比对指纹，
 * 变了才拉全量」成立。
 *
 * ⚠️ **别改用 `playlist.trackUpdateTime`**：服务端按请求现算，同一个歌单连打 4 次
 * 每次都 +≈200ms（实测），拿它当判据会「每次都判定有更新」。`updateTime`（歌单元数据）
 * 倒是稳定的，但它不反映曲目增删。
 *
 * 逐 id 拼串再散列：2213 首也就 24KB 上下的中间串，一次 MD5 是微秒级；id 序列本身
 * 按顺序参与，所以「同数量换歌」和「重排」都能抓到。
 */
fun playlistFingerprint(obj: JSONObject): String {
    val arr = obj.optJSONArray("trackIds") ?: return ""
    val sb = StringBuilder(arr.length() * 12)
    for (i in 0 until arr.length()) {
        // 线上形态是对象 {id,v,at,alg,uid}；宽容一点，裸数字也认。
        val id = arr.optJSONObject(i)?.optLong("id", 0L) ?: (arr.opt(i) as? Number)?.toLong() ?: 0L
        if (i > 0) sb.append(',')
        sb.append(id)
    }
    return md5Hex(sb.toString())
}

private fun md5Hex(s: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return out.toString()
}

private const val HEX = "0123456789abcdef"

/** 从 /weapi/v1/album/{id} 的响应解析专辑壳。该接口顶层只有 songs，没有
 *  album 对象；专辑元数据（名称/封面/歌手/曲目数）从首曲的 al/ar 推断。 */
fun parseAlbumObject(root: JSONObject): TrackCollection {
    val songsArr = root.optJSONArray("songs")
    val tracks = parseTracks(songsArr)
    val first = songsArr?.optJSONObject(0)
    val al = first?.optJSONObject("al")
    val ar = first?.optJSONArray("ar")?.optJSONObject(0)
    return TrackCollection(
        id = al?.optLong("id", 0L)?.toString() ?: "",
        name = al?.optString("name") ?: "",
        coverUrl = httpsUrl(al?.optString("picUrl")),
        playCount = 0L,
        subscribedCount = 0L,
        trackCount = tracks.size.toLong(),
        updateFrequency = "",
        description = "",
        creator = ar?.optString("name") ?: "",
        tracks = tracks,
    )
}
