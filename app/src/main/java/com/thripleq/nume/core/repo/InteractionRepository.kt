package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.net.ApiResult
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次写操作的结果。
 *
 * [ok] 为假时 [message] 一定是能给用户看的一句话 —— 写接口的失败几乎全是
 * **HTTP 200 + 业务错码**：服务端只会回 `{"code":401,"message":"下架歌曲无法收藏"}`。
 * 所以这里不允许出现「失败了但没话说」的情况，[fail] 家族都在兜底文案里。
 */
data class ActionResult(val ok: Boolean, val message: String = "") {
    companion object {
        val Ok = ActionResult(true)
        fun fail(message: String) = ActionResult(false, message)
    }
}

/**
 * 收藏 / 点赞这批**写**操作。
 *
 * ## 为什么要有这一层
 *
 * 这批端点的契约和读接口完全不同，混在各自的 Domain Repository 里很容易被
 * 当成读接口写错：
 *
 * - **失败藏在 message 里**。下架歌曲点赞回 HTTP 200 + `code=401`，
 *   评论点赞回 `{"code":200}`（成功）。只看 [ApiResult.err] / 非 200 业务码
 *   判断不出任何东西 —— 必须把 body 里的 `message`/`msg` 抽出来。
 * - **都是「设成目标态」而不是「切换」**。重复调幂等，所以调用方可以放心
 *   乐观更新 + 失败回滚，不必先读后写。
 * - **未登录时服务端回 301/250 而不是拒绝连接**，文案同样在 message 里。
 *
 * 因此这里统一返回 [ActionResult]，由 UI 决定怎么呈现（Toast / Snackbar）。
 */
@Singleton
class InteractionRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    /**
     * 红心 / 取消红心一首歌（`/weapi/song/like`）。
     * 成功返 `{"playlistId": <"我喜欢的音乐"歌单id>,"code":200}` —— 那个
     * playlistId ≠ uid，别拿去当「喜欢」歌单的 id 用。
     */
    suspend fun setSongLiked(trackId: String, liked: Boolean): ActionResult =
        write(NeteaseOp.SONG_LIKE, trackId, if (liked) "true" else "false")

    /** 点赞 / 取消点赞一条评论（`/weapi/v1/comment/{like,unlike}`）。 */
    suspend fun setCommentLiked(
        threadId: String,
        commentId: String,
        liked: Boolean,
    ): ActionResult = write(NeteaseOp.COMMENT_LIKE, threadId, commentId, if (liked) "1" else "0")

    /** 收藏 / 取消收藏一个歌单（榜单 id 也是歌单 id，同一条路径）。 */
    suspend fun setPlaylistSubscribed(playlistId: String, subscribed: Boolean): ActionResult =
        write(NeteaseOp.PLAYLIST_SUBSCRIBE, playlistId, if (subscribed) "1" else "0")

    /** 收藏 / 取消收藏一张专辑（专辑详情里没有 subscribed 字段，初始态见 [LibraryStateStore]）。 */
    suspend fun setAlbumSubscribed(albumId: String, subscribed: Boolean): ActionResult =
        write(NeteaseOp.ALBUM_SUBSCRIBE, albumId, if (subscribed) "1" else "0")

    /** 跑一次写接口并把 HTTP 200 里的业务错码翻译成 [ActionResult]。 */
    private suspend fun write(op: Int, vararg args: String): ActionResult =
        withContext(Dispatchers.IO) {
            val r = gateway.call(op, *args)
            val result = interpret(r)
            if (BuildConfig.DEBUG) {
                Log.e("NumeAction", "op=$op args=${args.joinToString(",")} -> ${result.ok} ${result.message}")
            }
            result
        }

    private fun interpret(r: ApiResult): ActionResult {
        // 传输层失败：真没连上（err != 0）或拿回空 body。
        if (r.err != 0 || r.body.isEmpty()) return ActionResult.fail(NETWORK_FAIL)
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            // code 缺省按成功算：create_weapi 路径下成功响应有时不带 code。
            val code = if (root.has("code")) root.optInt("code", 200) else 200
            if (code == 200) ActionResult.Ok else ActionResult.fail(messageOf(root, code))
        } catch (_: Exception) {
            // body 不是 JSON —— 多半是网关的错误页。
            ActionResult.fail(NETWORK_FAIL)
        }
    }

    /**
     * 业务错码 → 人话。服务端的 `message` 已经很贴近用户（「下架歌曲无法收藏」），
     * 优先原样透出；它为空时才退回按 code 给的兜底文案。
     */
    private fun messageOf(root: JSONObject, code: Int): String {
        val server = root.optString("message").ifBlank { root.optString("msg") }
        if (server.isNotBlank() && server != "null") return server
        return when (code) {
            301, 250, 401 -> "登录后才能收藏"
            else -> "操作失败（$code）"
        }
    }

    private companion object {
        const val NETWORK_FAIL = "网络不给力，稍后再试"
    }
}
