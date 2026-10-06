package com.thripleq.nume.core.repo

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
    )
}

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
