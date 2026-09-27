package com.thripleq.nume.core.repo

import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 搜索结果里的一个歌单（cloudsearch type=1000）。 */
data class SearchPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val creator: String,
    val trackCount: Long,
    val playCount: Long,
)

/** 搜索结果里的一个专辑（cloudsearch type=10）。 */
data class SearchAlbum(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val artist: String,
    val size: Long,
    val publishTime: Long,
)

/** 搜索结果里的一个歌手（cloudsearch type=100）。 */
data class SearchArtist(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val albumSize: Long,
    val musicSize: Long,
)

/** 搜索结果里的一个播客/电台（cloudsearch type=1009）。 */
data class SearchRadio(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val djName: String,
    val programCount: Long,
    val playCount: Long,
)

/**
 * 搜索数据源：单曲 / 歌单 / 专辑 / 歌手 / 播客（电台）。
 *
 * 全部走一个 [NeteaseOp.SEARCH] 服务，靠 type 区分：
 * 1=单曲, 1000=歌单, 10=专辑, 100=歌手, 1009=播客/电台。
 * 匿名可搜；失败一律返回空表（不抛），由 UI 展示空态。
 */
@Singleton
class SearchRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    /** 单曲搜索。 */
    suspend fun songs(keyword: String, limit: Int = 30, offset: Int = 0): List<Track> =
        search(keyword, TYPE_SONG, limit, offset) { root ->
            parseTracks(root.optJSONObject("result")?.optJSONArray("songs"))
        }

    /** 歌单搜索。 */
    suspend fun playlists(keyword: String, limit: Int = 30, offset: Int = 0): List<SearchPlaylist> =
        search(keyword, TYPE_PLAYLIST, limit, offset) { root ->
            val arr = root.optJSONObject("result")?.optJSONArray("playlists")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        SearchPlaylist(
                            id = id.toString(),
                            name = o.str("name"),
                            coverUrl = httpsUrl(o.str("coverImgUrl")),
                            creator = o.optJSONObject("creator")?.str("nickname") ?: "",
                            trackCount = o.optLong("trackCount", 0L),
                            playCount = o.optLong("playCount", 0L),
                        ),
                    )
                }
            }
        }

    /** 专辑搜索。 */
    suspend fun albums(keyword: String, limit: Int = 30, offset: Int = 0): List<SearchAlbum> =
        search(keyword, TYPE_ALBUM, limit, offset) { root ->
            val arr = root.optJSONObject("result")?.optJSONArray("albums")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    val artist = o.optJSONObject("artist")?.str("name")
                        ?: o.optJSONArray("artists")?.optJSONObject(0)?.str("name")
                        ?: ""
                    add(
                        SearchAlbum(
                            id = id.toString(),
                            name = o.str("name"),
                            coverUrl = httpsUrl(o.str("picUrl")),
                            artist = artist,
                            size = o.optLong("size", 0L),
                            publishTime = o.optLong("publishTime", 0L),
                        ),
                    )
                }
            }
        }

    /** 歌手搜索。 */
    suspend fun artists(keyword: String, limit: Int = 30, offset: Int = 0): List<SearchArtist> =
        search(keyword, TYPE_ARTIST, limit, offset) { root ->
            val arr = root.optJSONObject("result")?.optJSONArray("artists")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        SearchArtist(
                            id = id.toString(),
                            name = o.str("name"),
                            avatarUrl = httpsUrl(o.str("picUrl")),
                            albumSize = o.optLong("albumSize", 0L),
                            musicSize = o.optLong("musicSize", 0L),
                        ),
                    )
                }
            }
        }

    /** 播客/电台搜索。 */
    suspend fun radios(keyword: String, limit: Int = 30, offset: Int = 0): List<SearchRadio> =
        search(keyword, TYPE_RADIO, limit, offset) { root ->
            val arr = root.optJSONObject("result")?.optJSONArray("djRadios")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        SearchRadio(
                            id = id.toString(),
                            name = o.str("name"),
                            coverUrl = httpsUrl(o.str("picUrl")),
                            djName = o.optJSONObject("dj")?.str("nickname") ?: "",
                            programCount = o.optLong("programCount", 0L),
                            playCount = o.optLong("playCount", 0L),
                        ),
                    )
                }
            }
        }

    private suspend fun <T> search(
        keyword: String,
        type: String,
        limit: Int,
        offset: Int,
        parse: (JSONObject) -> List<T>,
    ): List<T> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val r = gateway.call(NeteaseOp.SEARCH, keyword, type, limit.toString(), offset.toString())
        // 传输失败 / 空响应 / 非法 JSON 一律抛错：UI 据此提示"搜索失败"并允许重试，
        // 而不是把一次偶发失败缓存成"无结果"（与真正的空结果区分开）。
        if (r.err != 0 || r.body.isEmpty()) throw SearchFailedException()
        try {
            parse(JSONObject(String(r.body, Charsets.UTF_8)))
        } catch (_: Exception) {
            throw SearchFailedException()
        }
    }

    private companion object {
        const val TYPE_SONG = "1"
        const val TYPE_ALBUM = "10"
        const val TYPE_ARTIST = "100"
        const val TYPE_PLAYLIST = "1000"
        const val TYPE_RADIO = "1009"
    }
}

/** 搜索请求失败（传输错误 / 空响应 / 非法 JSON）。UI 可据此重试。 */
class SearchFailedException : Exception("search request failed")

/** 同 [parseTrack] 的清洗：org.json 把缺失/null 串成字面量 "null"，统一去掉。 */
private fun JSONObject.str(key: String): String =
    optString(key).takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: ""
