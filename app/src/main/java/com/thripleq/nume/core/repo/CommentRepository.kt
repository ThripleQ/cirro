package com.thripleq.nume.core.repo

import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 一条评论。 */
data class Comment(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val content: String,
    val timeMs: Long,
    val likedCount: Long,
    val liked: Boolean,
    val location: String,
    val repliedNickname: String,
    val repliedContent: String,
)

/**
 * 评论页：首页含热门评论（hot）；latest 为按时间倒序的评论（翻页累积）。
 */
data class CommentPage(
    val hot: List<Comment>,
    val hotHasMore: Boolean,
    val latest: List<Comment>,
    val total: Long,
    val hasMore: Boolean,
)

/**
 * 评论数据源，走 [NeteaseOp.COMMENTS] / [NeteaseOp.COMMENTS_HOT]。
 *
 * threadId 是网易的资源线程序号，[CommentThread] 提供常用构造。
 * 注意：v1 端点对歌单用 `A_PL_0_`（`R_SQ_2_` 返回空），专辑用 `R_AL_3_`，
 * 单曲用 `R_SO_4_`；节目（`R_VI_62_`）服务端目前返回空。
 */
@Singleton
class CommentRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    suspend fun comments(
        threadId: String,
        limit: Int = 20,
        offset: Int = 0,
        beforeTime: Long = 0L,
    ): CommentPage = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.COMMENTS, threadId, limit.toString(), offset.toString(), beforeTime.toString())
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("comments")
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            CommentPage(
                hot = parseComments(root.optJSONArray("hotComments")),
                hotHasMore = root.optBoolean("moreHot", false),
                latest = parseComments(root.optJSONArray("comments")),
                total = root.optLong("total", 0L),
                hasMore = root.optBoolean("more", false),
            )
        } catch (_: Exception) {
            throw RequestFailedException("comments parse")
        }
    }

    suspend fun hot(
        threadId: String,
        limit: Int = 20,
        offset: Int = 0,
        beforeTime: Long = 0L,
    ): List<Comment> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.COMMENTS_HOT, threadId, limit.toString(), offset.toString(), beforeTime.toString())
        if (r.err != 0 || r.body.isEmpty()) throw RequestFailedException("hot comments")
        try {
            parseComments(JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("hotComments"))
        } catch (_: Exception) {
            throw RequestFailedException("hot comments parse")
        }
    }

    private fun parseComments(arr: org.json.JSONArray?): List<Comment> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val user = o.optJSONObject("user")
                val replied = o.optJSONArray("beReplied")?.optJSONObject(0)
                add(
                    Comment(
                        id = o.optLong("commentId", 0L).toString(),
                        nickname = user?.optString("nickname") ?: "",
                        avatarUrl = httpsUrl(user?.optString("avatarUrl")),
                        content = o.optString("content"),
                        timeMs = o.optLong("time", 0L),
                        likedCount = o.optLong("likedCount", 0L),
                        liked = o.optBoolean("liked", false),
                        location = o.optJSONObject("ipLocation")?.optString("location") ?: "",
                        repliedNickname = replied?.optJSONObject("user")?.optString("nickname") ?: "",
                        repliedContent = replied?.optString("content") ?: "",
                    ),
                )
            }
        }
    }
}

/** 网易评论线程序号构造。 */
object CommentThread {
    fun song(id: String) = "R_SO_4_$id"
    fun album(id: String) = "R_AL_3_$id"
    fun playlist(id: String) = "A_PL_0_$id"
    fun program(id: String) = "R_VI_62_$id"
}
