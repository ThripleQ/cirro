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
 * 「我收藏了什么 / 我买了什么」的进程级镜像：喜欢的歌曲 id 集合 + 已收藏专辑 id 集合
 * + 已购（单曲 / 数字专辑）id 集合。
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
 * - 已购（[isOwned]）：曲目对象上**没有任何字段**能表达「我买过这首」。唯一的
 *   权威信号是 `privileges[].payed`，而它只在部分端点出现（实测 `v6/playlist/detail`
 *   有且下标严格对齐，`/weapi/v1/album/{id}` **整个没有 privileges 数组**）——
 *   偏偏专辑详情正是最需要它的地方（进了已购的数字专辑，整张都该标「已购」）。
 *   所以走同一条路：拉两份「我买了什么」的清单，之后本地查表。
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

    private val _ownedSongIds = MutableStateFlow<Set<String>>(emptySet())

    /** 已购单曲 id（`/weapi/single/mybought/song/list`）。未加载完成时是空集。 */
    val ownedSongIds: StateFlow<Set<String>> = _ownedSongIds.asStateFlow()

    private val _ownedAlbumIds = MutableStateFlow<Set<String>>(emptySet())

    /** 已购数字专辑 id（`/weapi/digitalAlbum/purchased`）。未加载完成时是空集。 */
    val ownedAlbumIds: StateFlow<Set<String>> = _ownedAlbumIds.asStateFlow()

    private val likedMutex = Mutex()
    private val albumMutex = Mutex()
    private val ownedMutex = Mutex()

    /** 已成功载入的账号 uid；null = 还没载入过。 */
    @Volatile
    private var loadedUid: Long? = null

    @Volatile
    private var albumsLoadedUid: Long? = null

    @Volatile
    private var ownedLoadedUid: Long? = null

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

    /**
     * 确保「已购」两份清单已载入（已购单曲 + 已购数字专辑）。同一账号只真正请求一次。
     *
     * 两份都拿到才算成功：只拿到一份就置位的话，另一份对应的歌会一律画成「未购」，
     * 而那是**错的**（红 PAY 的含义是"需要买"）——宁可整体不置位，下次重试。
     */
    suspend fun ensureOwnedLoaded(uid: Long) {
        if (ownedLoadedUid == uid) return
        ownedMutex.withLock {
            if (ownedLoadedUid == uid) return
            val songs = fetchOwnedSongIds() ?: return
            val albums = fetchOwnedAlbumIds() ?: return
            _ownedSongIds.value = songs
            _ownedAlbumIds.value = albums
            ownedLoadedUid = uid
            diag("owned loaded uid=$uid songs=${songs.size} albums=${albums.size}")
        }
    }

    /**
     * 「这首歌我买了没」—— [LibraryStateStore] 的唯一查询入口，同步、不发网络。
     *
     * 判据是**并集**，两个来源各管一类购买：
     * - 单曲购买：`songId` 在已购单曲清单里。实测 VIP 歌可以单曲购买（买完 `fee`
     *   仍是 1，只是 `privileges[].payed` 从 0 变 3），所以**「VIP」与「已购」不互斥**。
     * - 数字专辑：整张购买，所以曲目只要 `albumId` 命中一张已购专辑就算。这也正是
     *   [Track.albumId] 存在的唯一理由。
     *
     * 2026-10-06 交叉验证：用户「喜欢」的 116 首里，服务端 `payed != 0` 的 12 首
     * 与本判据的结果**完全一致（0 处不一致）**——既没漏也没多。所以它等价于
     * `privileges[].payed`，但**在任何端点都可用**（专辑详情没有那个数组）。
     *
     * 未载入完成时一律返回 false（保守：画成「需要购买」），与红心未载入时是空心同理。
     */
    fun isOwned(trackId: String, albumId: String?): Boolean {
        if (trackId.isNotEmpty() && trackId in _ownedSongIds.value) return true
        return !albumId.isNullOrEmpty() && albumId in _ownedAlbumIds.value
    }

    /** 乐观更新本地红心态。网络失败后调用方要用相反的值翻回来。 */
    fun markLiked(trackId: String, liked: Boolean) {
        _likedIds.update { if (liked) it + trackId else it - trackId }
    }

    /** 乐观更新本地专辑收藏态。 */
    fun markAlbumSubscribed(albumId: String, subscribed: Boolean) {
        _subscribedAlbumIds.update { if (subscribed) it + albumId else it - albumId }
    }

    /**
     * 「这个账号的已收藏专辑」——**只在确实载入过时才返回集合**，否则返回 null。
     *
     * 调用方（专辑详情的收藏态）必须拿到 null 就**保持原样**：把「没载入」当成「没收藏」，
     * 会让每张专辑在载入完成前都画成空心，用户点一下才发现「已经在收藏列表里」。
     * 同步读、不发网络（首屏渲染路径上用，见 `ProfileRepository.cachedAlbumCollection`）。
     */
    fun albumsLoadedFor(uid: Long): Set<String>? =
        if (albumsLoadedUid == uid) _subscribedAlbumIds.value else null

    /** 同上，红心版（切歌时画红心前的同步读）。 */
    fun likedLoadedFor(uid: Long): Set<String>? =
        if (loadedUid == uid) _likedIds.value else null

    /**
     * 「下次访问重新拉一遍」。收藏是会在**别处**变的（官方 App 收藏了歌 / 专辑），
     * 而本类只按 uid 记账 —— 同一个账号一个进程内只拉一次，于是那边加了收藏这边永远看不到。
     *
     * 只清「已载入」这个记账，**不动已载入的集合本身**：清了集合，红心会在重新拉回来之前
     * 整片熄灭（页面闪一下）。保留旧值、让下一次 [ensureLikedLoaded] /
     * [ensureSubscribedAlbumsLoaded] 静默换成新的，观感才对。
     */
    fun markStale() {
        loadedUid = null
        albumsLoadedUid = null
        ownedLoadedUid = null
    }

    /** 登出时调用：把镜像清空，避免下个账号看到上个人的红心。 */
    fun invalidate() {
        loadedUid = null
        albumsLoadedUid = null
        ownedLoadedUid = null
        _likedIds.value = emptySet()
        _subscribedAlbumIds.value = emptySet()
        _ownedSongIds.value = emptySet()
        _ownedAlbumIds.value = emptySet()
    }

    private suspend fun fetchLikedIds(uid: Long): Set<String>? = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.LIKE_LIST, uid.toString())
        parseIds(r, "like/get")
    }

    private suspend fun fetchSubscribedAlbumIds(): Set<String>? = withContext(Dispatchers.IO) {
        // 分页拉到底。**只拉一页是不够的**：这是「本地查表」用的镜像，漏掉的 id 会让
        // 已经收藏的专辑画成空心（用户点一下才发现「已经在收藏列表里」）。
        // 单页给 PAGE_SIZE（上游默认只有 25）；不满一页即到底。
        val ids = mutableSetOf<String>()
        var offset = 0
        while (true) {
            val r = gateway.call(NeteaseOp.ALBUM_SUBLIST, PAGE_SIZE.toString(), offset.toString())
            val page = parseAlbumIds(r) ?: return@withContext null
            val before = ids.size
            ids += page
            // 不满一页 = 到底；一条新 id 都没进来 = 服务端没按 offset 翻页（再拉还是同一页）。
            if (page.size < PAGE_SIZE || ids.size == before) break
            offset += PAGE_SIZE
        }
        ids
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

    /**
     * 已购单曲 id 全集。分页拉到底 —— 同 [fetchSubscribedAlbumIds]，这是「本地查表」
     * 用的镜像，只拉一页会让买得多的用户漏判（那首歌明明买了却标红"需要购买"）。
     * 失败返回 null（**不置位，下次重试**）。
     */
    private suspend fun fetchOwnedSongIds(): Set<String>? = withContext(Dispatchers.IO) {
        val ids = mutableSetOf<String>()
        var offset = 0
        while (true) {
            val r = gateway.call(NeteaseOp.SONG_PURCHASED, PAGE_SIZE.toString(), offset.toString())
            val page = parseOwnedSongPage(r) ?: return@withContext null
            val before = ids.size
            ids += page
            if (page.size < PAGE_SIZE || ids.size == before) break
            offset += PAGE_SIZE
        }
        ids
    }

    /** 单页已购单曲。结构是 `{"code":200,"data":{"list":[{songId,...}]}}`（**不是** song 对象）。 */
    private fun parseOwnedSongPage(r: ApiResult): Set<String>? {
        if (r.err != 0 || r.body.isEmpty()) {
            diag("single/mybought failed code=${r.code} err=${r.err}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            if (root.optInt("code", 200) != 200) return null
            val list = root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("data")
                ?: return emptySet()
            buildSet {
                for (i in 0 until list.length()) {
                    val id = list.optJSONObject(i)?.optLong("songId", 0L) ?: 0L
                    if (id > 0) add(id.toString())
                }
            }
        } catch (e: Exception) {
            diag("single/mybought parse failed: ${e.message}")
            null
        }
    }

    /** 已购数字专辑 id 全集。结构是 `{"total":N,"paidAlbums":[{albumId,...}]}`。 */
    private suspend fun fetchOwnedAlbumIds(): Set<String>? = withContext(Dispatchers.IO) {
        val ids = mutableSetOf<String>()
        var offset = 0
        while (true) {
            val r = gateway.call(NeteaseOp.ALBUM_PURCHASED, PAGE_SIZE.toString(), offset.toString())
            val page = parseOwnedAlbumPage(r) ?: return@withContext null
            val before = ids.size
            ids += page
            if (page.size < PAGE_SIZE || ids.size == before) break
            offset += PAGE_SIZE
        }
        ids
    }

    private fun parseOwnedAlbumPage(r: ApiResult): Set<String>? {
        if (r.err != 0 || r.body.isEmpty()) {
            diag("digitalAlbum/purchased failed code=${r.code} err=${r.err}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            if (root.optInt("code", 200) != 200) return null
            val list = root.optJSONArray("paidAlbums")
                ?: root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("data")
                ?: return emptySet()
            buildSet {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val id = o.optLong("albumId", o.optLong("id", 0L))
                    if (id > 0) add(id.toString())
                }
            }
        } catch (e: Exception) {
            diag("digitalAlbum/purchased parse failed: ${e.message}")
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

    private companion object {
        /** `album/sublist` 的分页大小：镜像要的是「全量」，不是收藏管理页那种一屏。 */
        const val PAGE_SIZE = 100
    }
}
