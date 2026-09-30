package com.thripleq.nume.core.repo

import org.json.JSONArray
import org.json.JSONObject

/** A playable song. Album name is captured for the player / lyrics pages. */
data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val albumName: String = "",
)

/**
 * 网易云部分端点返回 `http://` 图片址（典型：v6 `playlist/detail` 的 `al.picUrl`，
 * 而 `coverImgUrl` 多是 https）。Android 默认禁明文 HTTP，Coil/Media3 会直接加载失败，
 * 表现就是「封面全灰」。CDN 同时支持 https，统一升级（已是 https 的不动）。
 * 另有老端点（dj/节目、部分 playlist）返回**协议相对** `//p3.music.126.net/...`——
 * 无 scheme 一样加载失败且更隐蔽，补全为 https。
 */
fun httpsUrl(url: String?): String? = url
    ?.takeIf { it.isNotBlank() }
    ?.let {
        when {
            it.startsWith("http://") -> "https://" + it.removePrefix("http://")
            it.startsWith("//") -> "https:$it"
            else -> it
        }
    }

/** Parses a standard netease song object (`songs`/`tracks` entries, ar/al shape). */
fun parseTrack(o: JSONObject?): Track? {
    if (o == null) return null
    val id = o.optLong("id", 0L)
    if (id <= 0) return null
    val al = o.optJSONObject("al")
    return Track(
        id = id.toString(),
        name = o.optString("name"),
        artist = o.optJSONArray("ar")?.optJSONObject(0)?.optString("name")
            ?: o.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
        artworkUrl = httpsUrl(al?.optString("picUrl")),
        durationMs = o.optLong("dt", 0L),
        albumName = al?.optString("name") ?: "",
    )
}

/** Parses a whole song array, skipping unparseable entries. */
fun parseTracks(arr: JSONArray?): List<Track> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            parseTrack(arr.optJSONObject(i))?.let { add(it) }
        }
    }
}
