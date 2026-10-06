package com.thripleq.cirro.core.repo

import com.thripleq.cirro.core.model.ArtistAlbum
import com.thripleq.cirro.core.model.ArtistPage
import com.thripleq.cirro.core.model.ArtistProfile
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌手主页数据源。
 *
 * 走 libnetease 的 artist 家族：
 * [NeteaseOp.ARTIST_DETAIL] 一次拿到资料 + hotSongs；
 * [NeteaseOp.ARTIST_SONGS] 分页全部歌曲（order hot/time）；
 * [NeteaseOp.ARTIST_ALBUMS] 专辑列表；[NeteaseOp.ARTIST_DESC] 详细简介。
 */
@Singleton
class ArtistRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    suspend fun page(id: String): ArtistPage = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ARTIST_DETAIL, id)
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("artist detail")
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val a = root.optJSONObject("artist") ?: throw RequestFailedException("artist detail")
            ArtistPage(
                profile = ArtistProfile(
                    id = a.optLong("id", 0L).toString(),
                    name = a.optString("name"),
                    avatarUrl = httpsUrl(a.optString("picUrl")),
                    briefDesc = a.optString("briefDesc"),
                    albumSize = a.optLong("albumSize", 0L),
                    musicSize = a.optLong("musicSize", 0L),
                    aliases = a.optJSONArray("alias")?.let { arr ->
                        buildList {
                            for (i in 0 until arr.length()) {
                                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    } ?: emptyList(),
                    followed = a.optBoolean("followed", false),
                ),
                hotSongs = parseTracks(root.optJSONArray("hotSongs")),
            )
        } catch (e: RequestFailedException) {
            throw e
        } catch (_: Exception) {
            throw RequestFailedException("artist detail parse")
        }
    }

    suspend fun songs(
        id: String,
        order: String = "hot",
        offset: Int = 0,
        limit: Int = 50,
    ): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ARTIST_SONGS, id, offset.toString(), limit.toString(), order)
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("artist songs")
        try {
            parseTracks(JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("songs"))
        } catch (_: Exception) {
            throw RequestFailedException("artist songs parse")
        }
    }

    suspend fun albums(
        id: String,
        offset: Int = 0,
        limit: Int = 50,
    ): List<ArtistAlbum> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ARTIST_ALBUMS, id, limit.toString(), offset.toString())
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("artist albums")
        try {
            val arr = JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("hotAlbums")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val aid = o.optLong("id", 0L)
                    if (aid <= 0) continue
                    add(
                        ArtistAlbum(
                            id = aid.toString(),
                            name = o.optString("name"),
                            coverUrl = httpsUrl(o.optString("picUrl")),
                            artist = o.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
                            size = o.optLong("size", 0L),
                            publishTime = o.optLong("publishTime", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            throw RequestFailedException("artist albums parse")
        }
    }

    /** 详细简介：introduction 各段 txt 拼接；为空时回退 briefDesc。 */
    suspend fun desc(id: String): String = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ARTIST_DESC, id)
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("artist desc")
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val intro = root.optJSONArray("introduction")
            val sb = StringBuilder()
            if (intro != null) for (i in 0 until intro.length()) {
                val o = intro.optJSONObject(i) ?: continue
                val txt = o.optString("txt")
                if (txt.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(txt)
                }
            }
            if (sb.isEmpty()) root.optString("briefDesc") else sb.toString()
        } catch (_: Exception) {
            throw RequestFailedException("artist desc parse")
        }
    }
}
