package com.thripleq.cirro.core.repo

import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.db.CollectionCache
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class Chart(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracks: List<Track>,
)

/**
 * No-login content source. /weapi/toplist/detail is served anonymously, so the
 * public charts are a safe first source until login + personalised lists land.
 */
@Singleton
class ChartRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val collectionCache: CollectionCache,
) {
    // 集合（榜单详情）内存缓存：列表页返回再进不重拉 JSON。Room 为二级（离线）缓存。
    private val collectionMemory = LruCache<String, TrackCollection>(16)

    suspend fun charts(): List<Chart> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.TOPLIST_DETAIL)
        if (r.err != 0) {
            // 响应体预览只在 debug 打：release 下既不刷屏，也不把接口内容写进 logcat。
            val detail = if (BuildConfig.DEBUG) {
                " body=${String(r.body, 0, minOf(200, r.body.size), Charsets.UTF_8)}"
            } else {
                ""
            }
            Log.e("ChartRepository", "toplist failed: err=${r.err} code=${r.code}$detail")
            return@withContext emptyList()
        }
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            if (BuildConfig.DEBUG) {
                Log.d("ChartRepository", "toplist ok, root keys=${root.length()}, has list=${root.has("list")}")
            }
            val list = root.optJSONArray("list") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        Chart(
                            id = id.toString(),
                            name = obj.optString("name"),
                            coverUrl = httpsUrl(obj.optString("coverImgUrl")),
                            tracks = parseTracks(obj.optJSONArray("tracks")),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 一个榜单的完整壳（元数据 + 曲目）。匿名 /weapi/toplist/detail 里
     * per-chart 的 `tracks` 预览是空的；榜单 id 本身就是歌单 id，改从
     * /api/v6/playlist/detail 拉完整集合。网络成功写内存 + Room；网络失败
     * 回退 Room 里的离线副本（断网仍可看打开过的榜单）。
     */
    suspend fun chartCollection(chartId: String): TrackCollection? = withContext(Dispatchers.IO) {
        // 榜单 id 即歌单 id，与 ProfileRepository.playlistCollection 共用同一 cacheKey。
        val key = "pl:$chartId"
        collectionMemory[key]?.let { return@withContext it }
        val fresh = fetchCollection(chartId)
        if (fresh != null) {
            collectionMemory[key] = fresh
            collectionCache.put(key, fresh)
            return@withContext fresh
        }
        collectionCache.get(key)?.also { collectionMemory[key] = it }
    }

    /** 拉一个榜单/歌单的完整集合；失败返回 null。 */
    private suspend fun fetchCollection(chartId: String): TrackCollection? {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, chartId, "0")
        if (r.err != 0) {
            Log.e("ChartRepository", "playlist detail failed: err=${r.err} code=${r.code}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val playlist = root.optJSONObject("playlist") ?: return null
            val base = parsePlaylistObject(playlist)
            base.copy(tracks = completePlaylistTracks(gateway, playlist, base.tracks))
        } catch (_: Exception) {
            null
        }
    }
}