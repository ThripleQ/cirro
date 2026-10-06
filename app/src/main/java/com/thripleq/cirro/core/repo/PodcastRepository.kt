package com.thripleq.cirro.core.repo

import com.thripleq.cirro.core.model.Program
import com.thripleq.cirro.core.model.RadioDetail
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播客/电台数据源。
 *
 * [NeteaseOp.RADIO_DETAIL] 电台资料；[NeteaseOp.RADIO_PROGRAMS] 分页节目。
 * byradio 要求 radioId/limit/offset 为 JSON 数字、asc 为 JSON 布尔（该约束在
 * C 侧 libnetease 内完成，Kotlin 只传字符串）。
 */
@Singleton
class PodcastRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    suspend fun radio(id: String): RadioDetail = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RADIO_DETAIL, id)
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("radio detail")
        try {
            val o = JSONObject(String(r.body, Charsets.UTF_8)).optJSONObject("djRadio")
                ?: throw RequestFailedException("radio detail")
            val dj = o.optJSONObject("dj")
            RadioDetail(
                id = o.optLong("id", 0L).toString(),
                name = o.optString("name"),
                coverUrl = httpsUrl(o.optString("picUrl")),
                desc = o.optString("desc"),
                djName = dj?.optString("nickname") ?: "",
                djAvatarUrl = httpsUrl(dj?.optString("avatarUrl")),
                programCount = o.optLong("programCount", 0L),
                playCount = o.optLong("playCount", 0L),
                subCount = o.optLong("subCount", 0L),
                category = o.optString("category"),
            )
        } catch (e: RequestFailedException) {
            throw e
        } catch (_: Exception) {
            throw RequestFailedException("radio detail parse")
        }
    }

    suspend fun programs(
        radioId: String,
        limit: Int = 30,
        offset: Int = 0,
    ): List<Program> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RADIO_PROGRAMS, radioId, limit.toString(), offset.toString(), "false")
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("radio programs")
        try {
            val arr = JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("programs")
            buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    parseProgram(arr.optJSONObject(i))?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            throw RequestFailedException("radio programs parse")
        }
    }

    private fun parseProgram(o: JSONObject?): Program? {
        if (o == null) return null
        val id = o.optLong("id", 0L)
        if (id <= 0) return null
        val ms = o.optJSONObject("mainSong")
        val album = ms?.optJSONObject("album")
        val songId = ms?.optLong("id", 0L) ?: 0L
        return Program(
            id = id.toString(),
            name = o.optString("name"),
            coverUrl = httpsUrl(o.optString("coverUrl")) ?: httpsUrl(album?.optString("picUrl")),
            durationMs = o.optLong("duration", 0L),
            songId = songId.takeIf { it > 0 }?.toString(),
            artistName = ms?.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
            listenerCount = o.optLong("listenerCount", 0L),
            createTime = o.optLong("createTime", 0L),
            commentThreadId = o.optString("commentThreadId"),
        )
    }
}
