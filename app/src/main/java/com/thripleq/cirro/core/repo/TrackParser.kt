package com.thripleq.cirro.core.repo

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
    /**
     * 所属专辑 id（`al.id` / 老格式 `album.id`）。**只为「已购」判定而留** ——
     * 数字专辑是整张购买的，所以「这首歌属于一张我已购专辑」等价于「这首歌我买了」，
     * 而曲目对象上并没有别的字段能表达这层归属（见 `LibraryStateStore.isOwned`）。
     * 老端点缺这个字段 → 空串（只看单曲购买记录，退化为保守的「未购」）。
     */
    val albumId: String = "",
    /**
     * 付费档位。接口的 `fee` 原样存下，**不在解析层翻译成「标哪个徽标」** ——
     * 那是展示决策，映射见 `ui/components/PayBadge.kt` 的 `payTagsOf`。
     *
     * 探针实测（2026-10-06）：`playlist/detail`、`v1/album`、`recommend/songs`、
     * `artist/hotSongs` 返回的 song 对象**每个都带 `fee`**，且与同下标
     * `privileges[i].fee` 完全一致 —— 所以只读 song 上这一份就够，不必去对齐
     * privileges 数组（那个数组的下标对齐关系没有保证，不值得依赖）。
     * 例外是 `song/like/get`（只回 id 列表）：那种来源拿不到档位，`0` 即「不画徽标」。
     */
    val fee: Int = 0,
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

/**
 * Parses a netease song object. **两套字段都要认**：
 *
 * | 用途 | 新格式（v6/web、`songs`/`tracks`） | 老格式（`/api/v1/radio/get`、部分 dj 端点） |
 * |---|---|---|
 * | 专辑 | `al`（`al.picUrl` / `al.name`） | `album` |
 * | 歌手 | `ar` | `artists` |
 * | 时长 | `dt` | `duration` |
 *
 * 只认新格式的代价是**整批歌静默丢封面**：私人漫游（`/api/v1/radio/get`）返回的就是
 * 老格式，`al` 不存在 → `artworkUrl = null`、`albumName = ""`、`durationMs = 0`。
 * 表现正是「点漫游这一类直接播放的卡片，播放时没有封面，副标题也少了专辑」
 * （2026-10-05 探针实测确认：radio 的歌曲顶层键是 `album`/`artists`/`duration`）。
 *
 * 歌手那一行本来就兼容了 `ar`/`artists`，另外两项当时漏了。
 */
fun parseTrack(o: JSONObject?): Track? {
    if (o == null) return null
    val id = o.optLong("id", 0L)
    if (id <= 0) return null
    val al = o.optJSONObject("al") ?: o.optJSONObject("album")
    return Track(
        id = id.toString(),
        name = o.optString("name"),
        artist = o.optJSONArray("ar")?.optJSONObject(0)?.optString("name")
            ?: o.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
        artworkUrl = httpsUrl(al?.optString("picUrl")),
        // dt 有时给 0（老端点），那种情况下才看 duration。
        durationMs = o.optLong("dt", 0L).takeIf { it > 0L } ?: o.optLong("duration", 0L),
        albumName = al?.optString("name") ?: "",
        albumId = al?.optLong("id", 0L)?.takeIf { it > 0L }?.toString() ?: "",
        // 老端点没有这个字段 → 0（不画徽标），与「免费」同形，可接受。
        fee = o.optInt("fee", 0),
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
