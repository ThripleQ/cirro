package com.thripleq.nume.core.repo

import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
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
    private val refresher: CollectionRefresher,
) {

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
     * 一个榜单的完整壳（元数据 + 曲目）。匿名 /weapi/toplist/detail 里 per-chart 的
     * `tracks` 预览是空的；榜单 id 本身就是歌单 id，所以直接走歌单详情那条路 ——
     * 连「要不要重新拉」的变更检查也是同一套（见 [CollectionRefresher]）。
     *
     * cacheKey 与 [ProfileRepository.playlistCollection] 共用 `pl:<id>`：同一个歌单无论
     * 从「榜单」还是「歌单」进来，都是同一份副本。[force] 用于错误态重试。
     */
    suspend fun chartCollection(chartId: String, force: Boolean = false): TrackCollection? =
        refresher.playlist("pl:$chartId", chartId, force)

    /** 只读 Room 副本（不发网络），进页面第一段渲染用。 */
    suspend fun cachedChartCollection(chartId: String): TrackCollection? =
        refresher.cached("pl:$chartId")
}
