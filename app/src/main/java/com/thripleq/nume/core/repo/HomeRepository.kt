package com.thripleq.nume.core.repo

import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A recommended playlist card (personalized/playlist, served anonymously). */
data class PlaylistCard(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val trackCount: Long,
)

/**
 * Explore (Home) content source. 推荐歌单 / 雷达歌单 / 排行榜匿名可拉；每日推荐歌曲
 * 需要登录（否则接口返回 301 / 空）。所有拉取容错返回空列表，不抛。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val profileRepo: ProfileRepository,
) {

    /** 是否已登录（复用 Profile 的 account 查询，code 301 = 未登录）。 */
    suspend fun loggedIn(): Boolean = profileRepo.account() != null

    /** 个性化推荐歌单（匿名可用）。 */
    suspend fun recommendPlaylists(limit: String = "12"): List<PlaylistCard> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.RECOMMEND_PLAYLISTS, limit)
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val arr = root.optJSONArray("result")
                    ?: root.optJSONArray("recommend")
                    ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optLong("id", 0L)
                        if (id <= 0) continue
                        add(
                            PlaylistCard(
                                id = id.toString(),
                                name = o.optString("name"),
                                coverUrl = httpsUrl(o.optString("picUrl")),
                                playCount = o.optLong("playCount", o.optLong("playcount", 0L)),
                                trackCount = o.optLong("trackCount", 0L),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 每日推荐歌曲（需登录）。 */
    suspend fun dailySongs(): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECOMMEND_SONGS)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val data = root.optJSONObject("data")
            parseTracks(data?.optJSONArray("dailySongs") ?: data?.optJSONArray("recommend"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 个性化推荐资源（`/weapi/personalized/playlist`，匿名可用）。
     *
     * 与 [recommendPlaylists] 的分工（布局参照 kanade 主页，2026-10-04）：
     * kanade 的「雷达歌单」区显示 私人雷达/新歌雷达/时光雷达 三张卡 —— 这类"雷达"
     * 歌单恰好是 RECOMMEND_RESOURCE 返回里的常客（实测首条即「私人雷达」），而
     * RECOMMEND_PLAYLISTS 偏向大众化歌单，作「场景音乐」区的数据源。
     */
    suspend fun radarPlaylists(): List<PlaylistCard> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECOMMEND_RESOURCE)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val arr = root.optJSONArray("result")
                ?: root.optJSONArray("recommend")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        PlaylistCard(
                            id = id.toString(),
                            name = o.optString("name"),
                            coverUrl = httpsUrl(o.optString("picUrl")),
                            playCount = o.optLong("playCount", o.optLong("playcount", 0L)),
                            trackCount = o.optLong("trackCount", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
