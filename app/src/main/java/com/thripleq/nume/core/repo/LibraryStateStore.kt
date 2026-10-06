package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.net.ApiResult
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「我收藏了什么」的进程级镜像：喜欢的歌曲 id 集合 + 已收藏专辑 id 集合。
 *
 * ## 为什么需要它
 *
 * 红心和收藏都是**开关**，要先把「当前是什么态」画出来，用户才知道点了会变成
 * 什么。而这两个状态都没有便宜的按需查询：
 *
 * - 红心态：`/weapi/song/like/get` 一次就把全部喜欢 id 给回来（实测 115 首
 *   的响应只有 1KB 出头）。**每首歌单独查一次是贵的做法**，一次拉全量、之后
 *   本地查表才是对的。也正因为如此，切歌时不需要任何网络请求。
 * - 专辑收藏态：专辑详情里**根本没有** subscribed 字段（探针实测 29 个键里没有），
 *   只能查 `/weapi/album/sublist`。同样是「拉一次列表、本地查表」。
 *
 * 歌单不一样：`/weapi/v6/playlist/detail` 的 playlist 对象自带 `subscribed`，
 * 所以歌单收藏态跟着 [TrackCollection] 走，不进这里 —— 见 [TrackCollection.subscribed]。
 *
 * ## 纪律
 *
 * - **加载失败不置位**（[loadedUid] 不写），下次调用会重试；一次网络抖动不该
 *   让整个进程的红心态永远错下去。
 * - **乐观更新**由调用方驱动：[markLiked] / [markAlbumSubscribed] 是本地翻转，
 *   失败要自己翻回来（见 `PlayerActionsViewModel.toggleLike`）。
 * - 登录态按 uid 记账：换账号后第一次访问会自动重拉，不需要谁主动失效。
 */
@Singleton
class LibraryStateStore @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())

    /** 当前账号喜欢的歌曲 id。**未加载完成时是空集** —— 红心会显示成未点亮。 */
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _subscribedAlbumIds = MutableStateFlow<Set<String>>(emptySet())

    /** 当前账号已收藏的专辑 id。未加载完成时是空集。 */
    val subscribedAlbumIds: StateFlow<Set<String>> = _subscribedAlbumIds.asStateFlow()

    private val likedMutex = Mutex()
    private val albumMutex = Mutex()

    /** 已成功载入的账号 uid；null = 还没载入过。 */
    @Volatile
    private var loadedUid: Long? = null

    @Volatile
    private var albumsLoadedUid: Long? = null

    /**
     * 确保「喜欢的歌曲」已载入。同一账号只会真正请求一次；
     * 换账号（uid 变了）会重新载入。
     */
    suspend fun ensureLikedLoaded(uid: Long) {
        if (loadedUid == uid) return
        likedMutex.withLock {
            if (loadedUid == uid) return
            val ids = fetchLikedIds(uid) ?: return
            _likedIds.value = ids
            loadedUid = uid
            diag("liked loaded uid=$uid size=${ids.size}")
        }
    }

    /** 确保「已收藏专辑」已载入。 */
    suspend fun ensureSubscribedAlbumsLoaded(uid: Long) {
        if (albumsLoadedUid == uid) return
        albumMutex.withLock {
            if (albumsLoadedUid == uid) return
            val ids = fetchSubscribedAlbumIds() ?: return
            _subscribedAlbumIds.value = ids
            albumsLoadedUid = uid
            diag("albums loaded uid=$uid size=${ids.size}")
        }
    }

    /** 乐观更新本地红心态。网络失败后调用方要用相反的值翻回来。 */
    fun markLiked(trackId: String, liked: Boolean) {
        _likedIds.update { if (liked) it + trackId else it - trackId }
    }

    /** 乐观更新本地专辑收藏态。 */
    fun markAlbumSubscribed(albumId: String, subscribed: Boolean) {
        _subscribedAlbumIds.update { if (subscribed) it + albumId else it - albumId }
    }

    /** 登出时调用：把镜像清空，避免下个账号看到上个人的红心。 */
    fun invalidate() {
        loadedUid = null
        albumsLoadedUid = null
        _likedIds.value = emptySet()
        _subscribedAlbumIds.value = emptySet()
    }

    private suspend fun fetchLikedIds(uid: Long): Set<String>? = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.LIKE_LIST, uid.toString())
        parseIds(r, "like/get")
    }

    private suspend fun fetchSubscribedAlbumIds(): Set<String>? = withContext(Dispatchers.IO) {
        // limit 100：这是「本地查表」用的，不是收藏管理页。收藏专辑超过 100 张的
        // 账号在这一档会漏判（服务端 limit 上限外的部分看不到）—— 真要支持，
        // 得加分页累积；现阶段先按单页，注释留在这儿免得后人以为它拉全了。
        val r = gateway.call(NeteaseOp.ALBUM_SUBLIST, "100", "0")
        parseAlbumIds(r)
    }

    /** 解析 `{"ids":[...],"code":200}`；失败/未登录返回 null（**不置位，下次重试**）。 */
    private fun parseIds(r: ApiResult, what: String): Set<String>? {
        if (r.err != 0 || r.code != 200 || r.body.isEmpty()) {
            diag("$what failed code=${r.code} err=${r.err}")
            return null
        }
        return try {
            val arr = JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("ids") ?: return null
            buildSet { for (i in 0 until arr.length()) add(arr.optLong(i, 0L).toString()) }
        } catch (e: Exception) {
            diag("$what parse failed: ${e.message}")
            null
        }
    }

    /** 解析 `/weapi/album/sublist` 的 `{"data":[<专辑对象>...]}`。 */
    private fun parseAlbumIds(r: ApiResult): Set<String>? {
        if (r.err != 0 || r.body.isEmpty()) {
            diag("album/sublist failed code=${r.code} err=${r.err}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            // 未登录时这条回 301 且没有 data —— 当成"拉不到"，不置位。
            if (root.has("code") && root.optInt("code", 200) != 200) return null
            val arr = root.optJSONArray("data") ?: return null
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optLong("id", 0L) ?: 0L
                    if (id > 0) add(id.toString())
                }
            }
        } catch (e: Exception) {
            diag("album/sublist parse failed: ${e.message}")
            null
        }
    }

    /** 调试诊断（vivo 上 Log.d 被屏蔽，故用 Log.e）。 */
    private fun diag(msg: String) {
        if (BuildConfig.DEBUG) Log.e("NumeLibrary", msg)
    }
}
