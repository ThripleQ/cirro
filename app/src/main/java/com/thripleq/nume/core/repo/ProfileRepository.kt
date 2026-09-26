package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.db.CollectionCache
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Logged-in user summary (from /weapi/nuser/account/get). */
data class Account(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Long,
)

/** A purchased digital album. */
data class Album(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val artist: String,
)

/** A playlist entry in the user's "my playlists" (created or subscribed). */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Long,
    val subscribed: Boolean,
)

/** Everything the Profile tab shows once logged in. */
data class ProfileData(
    val account: Account,
    val likedCount: Int,
    /** 喜欢的音乐代表封面 = 首首喜欢曲目的专辑封面（与面板 banner 同源，展开时封面不换图）。 */
    val likedCoverUrl: String?,
    val purchasedSongCount: Int,
    /** 已购代表封面 = 首首已购曲目的专辑封面（与面板 banner 同源）。 */
    val purchasedCoverUrl: String?,
    val purchasedAlbums: List<Album>,
    val subscribedPlaylists: List<PlaylistSummary>,
    val createdPlaylists: List<PlaylistSummary>,
)

/**
 * Account-scoped content source (Profile tab): login state, liked tracks,
 * purchases and the user's playlists. Every call goes through the single
 * serialized [NetEaseGateway]; `code 301` means "not logged in".
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val collectionCache: CollectionCache,
) {

    /**
     * 最近一次成功拉取的完整"我的"数据。ViewModel 重建（tab 切换 saveState/restoreState
     * 会销毁重建 entry 的 ViewModel）时先读这里，避免每次进"我的"都闪加载动画；
     * 展示后由后台静默刷新保持新鲜。登录/登出时失效。
     */
    @Volatile
    var cachedProfile: ProfileData? = null
        private set

    /** 最近一次成功/未登录的账号态短缓存。tab 频繁切换重建 ViewModel 时，
     *  account() 不必每次都打一次 /account 往返；5s 内复用并保留旧值保证不闪。 */
    private var cachedAccount: Pair<Account?, Long>? = null

    /** 已购单曲/专辑缓存：面板重开、Profile 静默刷新不再重复分页拉取。登录态变化时失效。 */
    private var purchasedSongsCache: List<Track>? = null
    private var purchasedAlbumsCache: List<Album>? = null

    /** 登录/登出后调用，让 [account] 立刻反映最新登录态，不读短缓存。 */
    fun invalidateAccount() {
        cachedAccount = null
        purchasedSongsCache = null
        purchasedAlbumsCache = null
    }

    private companion object {
        // 账号态短缓存有效期：覆盖 tab 切换/VM 重建的峰值调用，又不至于让
        // 登录态过期太久（5s 内手滑切 tab 仍复用，陈旧影响可忽略）。
        const val ACCOUNT_REFRESH_MS = 5_000L
        // 已购曲目/专辑分页大小：单页 100，随 offset 一直拉直到某页不满为止。
        const val PAGE_SIZE = 100
    }

    /** 已解析"喜欢"曲目缓存（uid → tracks）：Profile 页计数与列表页共用，避免重复全量拉取。 */
    private val likedCache = LruCache<String, List<Track>>(2)

    /** 歌单/专辑壳内存缓存：重进列表页不重拉 JSON。Room 为二级（离线）缓存。 */
    private val collectionMemory = LruCache<String, TrackCollection>(32)

    /** Current account, or null when not logged in (code 301) / on error. */
    suspend fun account(): Account? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedAccount?.let { (a, at) ->
            if (now - at < ACCOUNT_REFRESH_MS) return@withContext a
        }
        val result = fetchAccount()
        cachedAccount = result to now
        result
    }

    private suspend fun fetchAccount(): Account? {
        val r = gateway.call(NeteaseOp.USER_ACCOUNT)
        // 明确未登录（301）时清缓存，让 UI 回落到未登录；网络错误保留旧缓存。
        if (r.code == 301) {
            cachedProfile = null
            purchasedSongsCache = null
            purchasedAlbumsCache = null
        }
        if (r.err != 0 || r.code != 200) return null
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val account = root.optJSONObject("account") ?: return null
            val profile = root.optJSONObject("profile")
            Account(
                uid = account.optLong("id", 0L),
                nickname = profile?.optString("nickname")
                    ?: account.optString("userName"),
                avatarUrl = httpsUrl(profile?.optString("avatarUrl")),
                vipType = profile?.optLong("vipType", 0L) ?: 0L,
            )
        } catch (e: Exception) {
            Log.e("ProfileRepository", "account parse failed: ${e.message}")
            null
        }
    }

    /** 拉取完整 Profile 数据并写入 [cachedProfile]；失败返回 null 且保留旧缓存。 */
    suspend fun loadProfile(account: Account): ProfileData? = coroutineScope {
        val liked = async { likedTracks(account.uid) }
        val purchasedSongs = async { purchasedSongs() }
        val purchasedAlbums = async { purchasedAlbums() }
        val playlists = async { playlists(account.uid) }
        val (subscribedPlaylists, createdPlaylists) = playlists.await()
        // 代表封面取首曲专辑封面：网易云合集（喜欢/已购）的 coverUrl 就是它，
        // 这样「我的」大卡与展开面板的 banner 是同一张图，hero 交接时封面不换图。
        val likedList = liked.await()
        val purchasedSongList = purchasedSongs.await()
        val data = ProfileData(
            account = account,
            likedCount = likedList.size,
            likedCoverUrl = likedList.firstOrNull()?.artworkUrl,
            purchasedSongCount = purchasedSongList.size,
            purchasedCoverUrl = purchasedSongList.firstOrNull()?.artworkUrl,
            purchasedAlbums = purchasedAlbums.await(),
            subscribedPlaylists = subscribedPlaylists,
            createdPlaylists = createdPlaylists,
        )
        cachedProfile = data
        data
    }

    /** Liked tracks of [uid]: ids from /weapi/song/like/get → details. 结果内存缓存。 */
    suspend fun likedTracks(uid: Long): List<Track> = withContext(Dispatchers.IO) {
        likedCache[uid.toString()]?.let { return@withContext it }
        val ids = rawIds(NeteaseOp.LIKE_LIST, uid.toString())
        if (ids.isNullOrEmpty()) return@withContext emptyList()
        songDetails(ids).also { likedCache[uid.toString()] = it }
    }

    /** Purchased single tracks (/api/single/mybought/song/list). 分页拉全，避免上限 100。结果内存缓存。 */
    suspend fun purchasedSongs(): List<Track> = withContext(Dispatchers.IO) {
        purchasedSongsCache?.let { return@withContext it }
        val all = mutableListOf<Track>()
        var offset = 0
        var completed = true
        while (true) {
            val page = fetchPurchasedSongPage(offset) ?: run { completed = false; break }
            all += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        // 只有整轮分页都成功才落缓存（含"确实没有已购"的空结果）：请求失败若被固化，
        // 会让用户看到"无已购"；而空结果不入缓存，已购为 0 的人每次进"我的"都要重拉。
        if (completed) {
            diag("purchasedSongs complete offset=$offset total=${all.size}")
            purchasedSongsCache = all
        }
        all
    }

    /** 单页已购单曲。返回 null 表示请求/解析失败（终止分页）；否则返回该页列表。 */
    private suspend fun fetchPurchasedSongPage(offset: Int): List<Track>? {
        val r = gateway.call(NeteaseOp.SONG_PURCHASED, PAGE_SIZE.toString(), offset.toString())
        if (r.err != 0 || r.body.isEmpty()) {
            diag("purchasedSongs page offset=$offset err=${r.err} code=${r.code}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("data")
                ?: return emptyList()
            // 返回结构为 {songId,name,picUrl,artistName,albumName,...},
            // 不是标准 song 对象, 需按字段映射
            buildList {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val id = o.optLong("songId", 0L)
                    if (id <= 0) continue
                    add(
                        Track(
                            id = id.toString(),
                            name = o.optString("name"),
                            artist = o.optString("artistName"),
                            artworkUrl = httpsUrl(o.optString("picUrl")),
                            durationMs = 0L,
                            albumName = o.optString("albumName"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "purchased songs parse failed: ${e.message}")
            null
        }
    }

    /** Purchased digital albums (/api/digitalAlbum/purchased). 分页拉全，避免上限 100。结果内存缓存。 */
    suspend fun purchasedAlbums(): List<Album> = withContext(Dispatchers.IO) {
        purchasedAlbumsCache?.let { return@withContext it }
        val all = mutableListOf<Album>()
        var offset = 0
        var completed = true
        while (true) {
            val page = fetchPurchasedAlbumPage(offset) ?: run { completed = false; break }
            all += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        // 同上：整轮成功才落缓存，失败不固化。
        if (completed) {
            diag("purchasedAlbums complete offset=$offset total=${all.size}")
            purchasedAlbumsCache = all
        }
        all
    }

    /** 单页已购专辑。返回 null 表示请求/解析失败（终止分页）；否则返回该页列表。 */
    private suspend fun fetchPurchasedAlbumPage(offset: Int): List<Album>? {
        val r = gateway.call(NeteaseOp.ALBUM_PURCHASED, PAGE_SIZE.toString(), offset.toString())
        if (r.err != 0 || r.body.isEmpty()) {
            diag("purchasedAlbums page offset=$offset err=${r.err} code=${r.code}")
            return null
        }
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONArray("paidAlbums")
                ?: root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("data")
                ?: return emptyList()
            // 返回结构为 {albumId,cover,albumName,artist:{name},...},
            // 不是标准 album 对象, 需按字段映射
            buildList {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val id = o.optLong("albumId", o.optLong("id", 0L))
                    if (id <= 0) continue
                    add(
                        Album(
                            id = id.toString(),
                            name = o.optString("albumName", o.optString("name")),
                            coverUrl = httpsUrl(o.optString("cover", o.optString("picUrl"))),
                            artist = o.optJSONObject("artist")?.optString("name") ?: "",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "purchased albums parse failed: ${e.message}")
            null
        }
    }

    /** The user's playlists, split into subscribed (collected) and created. */
    suspend fun playlists(uid: Long): Pair<List<PlaylistSummary>, List<PlaylistSummary>> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.USER_PLAYLIST, uid.toString(), "100", "0")
            if (r.err != 0 || r.code != 200) return@withContext Pair(emptyList(), emptyList())
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val arr = root.optJSONArray("playlist") ?: return@withContext Pair(emptyList(), emptyList())
                val subscribed = mutableListOf<PlaylistSummary>()
                val created = mutableListOf<PlaylistSummary>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    // 剔除"我喜欢的音乐"（specialType==5）：它在"我的"页有独立
                    // 区块（likedTracks），不列入"创建的歌单"。
                    if (o.optLong("specialType", 0L) == 5L) continue
                    if (o.optString("name") == "我喜欢的音乐") continue
                    val p = PlaylistSummary(
                        id = id.toString(),
                        name = o.optString("name"),
                        coverUrl = httpsUrl(o.optString("coverImgUrl")),
                        trackCount = o.optLong("trackCount", 0L),
                        subscribed = o.optBoolean("subscribed", false),
                    )
                    if (p.subscribed) subscribed.add(p) else created.add(p)
                }
                Pair(subscribed, created)
            } catch (e: Exception) {
                Log.e("ProfileRepository", "playlists parse failed: ${e.message}")
                Pair(emptyList(), emptyList())
            }
        }

    /** 歌单完整壳（元数据 + 曲目），榜单 id 即歌单 id。网络成功写内存 + Room，失败回退离线副本。 */
    suspend fun playlistCollection(playlistId: String): TrackCollection? = withContext(Dispatchers.IO) {
        val key = "pl:$playlistId"
        collectionMemory[key]?.let { return@withContext it }
        val fresh = fetchPlaylistCollection(playlistId)
        if (fresh != null) {
            collectionMemory[key] = fresh
            collectionCache.put(key, fresh)
            return@withContext fresh
        }
        collectionCache.get(key)?.also { collectionMemory[key] = it }
    }

    /** 拉一个歌单的完整壳；请求/解析失败返回 null。 */
    private suspend fun fetchPlaylistCollection(playlistId: String): TrackCollection? {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, playlistId, "0")
        if (r.err != 0 || r.code != 200) return null
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val playlist = root.optJSONObject("playlist") ?: return null
            val base = parsePlaylistObject(playlist)
            base.copy(tracks = completePlaylistTracks(gateway, playlist, base.tracks))
        } catch (_: Exception) {
            null
        }
    }

    /** 已购专辑完整壳（/weapi/v1/album/{id}，无播放量等）。网络成功写内存 + Room，失败回退离线副本。 */
    suspend fun albumCollection(albumId: String): TrackCollection? = withContext(Dispatchers.IO) {
        val key = "al:$albumId"
        collectionMemory[key]?.let { return@withContext it }
        val fresh = fetchAlbumCollection(albumId)
        if (fresh != null) {
            collectionMemory[key] = fresh
            collectionCache.put(key, fresh)
            return@withContext fresh
        }
        collectionCache.get(key)?.also { collectionMemory[key] = it }
    }

    /** 拉一个已购专辑的完整壳；请求/解析失败返回 null。 */
    private suspend fun fetchAlbumCollection(albumId: String): TrackCollection? {
        val r = gateway.call(NeteaseOp.ALBUM_DETAIL, albumId)
        diag("albumCollection op=${NeteaseOp.ALBUM_DETAIL} id=$albumId code=${r.code} err=${r.err} body=${String(r.body, Charsets.UTF_8).take(300)}")
        // 同 songDetails：以 err+body 判定，code 仅作诊断
        if (r.err != 0 || r.body.isEmpty()) return null
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            // 顶层无 album 对象, songs 直接在根; 元数据从首曲推断
            parseAlbumObject(root)
        } catch (_: Exception) {
            null
        }
    }

    // ── helpers ────────────────────────────────────────────

    private suspend fun rawIds(op: Int, arg: String): List<String>? {
        val r = gateway.call(op, arg)
        if (r.err != 0 || r.code != 200) {
            diag("rawIds op=$op code=${r.code} err=${r.err} -> null")
            return null
        }
        return try {
            val body = String(r.body, Charsets.UTF_8)
            val root = JSONObject(body)
            val arr = root.optJSONArray("ids")
            // 用 Log.e 保证 vivo 上可见 (Log.d 被屏蔽)
            diag("rawIds op=$op bodyLen=${body.length} hasIds=${arr != null} idsLen=${arr?.length() ?: 0} body=${body.take(400)}")
            arr ?: return null
            buildList { for (i in 0 until arr.length()) add(arr.optLong(i, 0L).toString()) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun songDetails(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        // /weapi/v3/song/detail accepts up to ~1000 ids in one call.
        val r = gateway.call(NeteaseOp.SONG_DETAIL, ids.joinToString(","))
        diag("songDetails op=${NeteaseOp.SONG_DETAIL} ids=${ids.size} code=${r.code} err=${r.err} body=${String(r.body, Charsets.UTF_8).take(300)}")
        // create_weapi 路径：body 无顶层 code 时库 fallback code=200/err=0；
        // 旧版严格路径的 code=0 已不复现。以 err+body 判定，code 仅作诊断。
        if (r.err != 0 || r.body.isEmpty()) return emptyList()
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val tracks = parseTracks(root.optJSONArray("songs"))
            diag("songDetails parsed=${tracks.size} hasSongs=${root.has("songs")}")
            tracks
        } catch (e: Exception) {
            diag("songDetails parse failed: ${e.message}")
            emptyList()
        }
    }

    /** 调试诊断日志，仅在 debug 构建输出（vivo 等机型 Log.d 被屏蔽，故用 Log.e）。 */
    private fun diag(msg: String) {
        if (BuildConfig.DEBUG) Log.e("ProfileDiag", msg)
    }
}
