package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
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
    /**
     * 已购单曲本体。**「我的」页内联展开（已购 → 单曲）直接用这份**，不再打网络；
     * [loadProfile] 本来就 await 了它，只是以前只取 size，列表本身白白扔掉。
     */
    val purchasedSongs: List<Track>,
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
    private val refresher: CollectionRefresher,
    private val library: LibraryStateStore,
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

    /** 登录/登出后调用：丢弃账号短缓存，并清掉集合副本（收藏态与收藏数都是随账号变的）。 */
    suspend fun invalidateAccount() {
        cachedAccount = null
        purchasedSongsCache = null
        purchasedAlbumsCache = null
        refresher.clear()
    }

    /**
     * 已购（单曲 / 专辑）缓存作废，下次 [purchasedSongs] / [purchasedAlbums] 重拉。
     *
     * 这两份原来只在登录态变化时才清 —— 而「在官方 App 里刚买了一首歌」既不换账号、
     * 也没有任何回调，于是永远看不到。「我的」页重新可见时调用（见 `ProfileViewModel.onEnterVisible`）。
     */
    fun invalidatePurchased() {
        purchasedSongsCache = null
        purchasedAlbumsCache = null
    }

    /**
     * 已缓存的账号，**纯同步、绝不发网络**。首屏渲染路径（[cachedAlbumCollection]）上
     * 用来取 uid，不值得为它挂一次 `/account` 往返 —— 拿不到就先不补收藏态。
     */
    fun cachedAccountOrNull(): Account? = cachedAccount?.first

    /**
     * 红心在别处被改过（[InteractionRepository.setSongLiked]）后调用。
     *
     * 只清「喜欢」那一份缓存，**不动 [cachedProfile]**：清了它，「我的」页会退回
     * 骨架屏重来一次，而用户刚从播放页回来、期望的是原来的版面。留着旧数据、
     * 让下次静默刷新把计数改过来，观感才是对的；曲目列表本身下次进「喜欢的音乐」
     * 也会因为缓存被清而重拉。
     */
    fun invalidateLikedCache() {
        likedCache.clear()
    }

    /**
     * 歌单收藏态在别处被改过（[InteractionRepository.setPlaylistSubscribed]）后调用，
     * 把本地那份壳同步掉 —— 否则退出列表页再进来，收藏按钮会退回旧状态。
     *
     * **现在会落进 Room**：`collection` 表已有 `subscribed` 列，而 Room 是列表页的
     * 常态读路径 —— 只改内存的话，冷却期内再进页面就会把旧值读回来。
     */
    suspend fun cacheSubscribed(playlistId: String, subscribed: Boolean, subscribedCount: Long) {
        refresher.noteSubscribed("pl:$playlistId", subscribed, subscribedCount)
    }

    private companion object {
        // 账号态短缓存有效期：覆盖 tab 切换/VM 重建的峰值调用，又不至于让
        // 登录态过期太久（5s 内手滑切 tab 仍复用，陈旧影响可忽略）。
        const val ACCOUNT_REFRESH_MS = 5_000L
        // 已购曲目/专辑分页大小：单页 100，随 offset 一直拉直到某页不满为止。
        const val PAGE_SIZE = 100
        /**
         * [songDetails] 单批 id 数。上限来自 C 层的 `char ids[8192]`（约 680 条，见该函数注释），
         * 这里取 200 留足余量 —— 而且这一屏的 id 是拼接后一次性发出去的，越小越不容易踩到
         * 服务端对请求体长度的限制。
         */
        const val SONG_DETAIL_BATCH = 200
    }

    /** 已解析"喜欢"曲目缓存（uid → tracks）：Profile 页计数与列表页共用，避免重复全量拉取。 */
    private val likedCache = LruCache<String, List<Track>>(2)

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
            purchasedSongs = purchasedSongList,
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

    /**
     * 已购单曲。分页拉全，避免上限 100。结果内存缓存。
     *
     * ## 为什么要多打一次 `v3/song/detail`
     *
     * 这个端点回的是**购买记录**、不是 song 对象（字段表见 [fetchPurchasedSongPage]），
     * 里面**没有 `fee`** —— 而徽标的判据只有 `fee`。它确实有个 `vip` 字段（实测用户这
     * 9 首全是 `true`），但：① 已购样本**全部**是 VIP 歌（fee=1），`vip=false` 的情形
     * 一个样本都没有，**无法证伪**；② `v3/song/detail` 的 song 对象里**根本没有** `vip`
     * 这个键，所以拿不到大样本去对照；③ 上游 module 只是转发、没定义这个字段。
     * 猜错的代价是把免费歌标成 VIP，而用户对徽标的要求就是「准确」，于是不猜 ——
     * 按 songId 另发一次 [songDetails] 取权威档位（9 首一个请求，成本可忽略）。
     *
     * **只补 `fee` / `durationMs`，不替换整条**：detail 查不到的歌（已下架）必须留在
     * 列表里、只退化成不画徽标；而 `artistName` 这类字段购买记录给得更全（它是拼接的
     * 完整歌手串，song 对象的 `ar[]` 只取第一个）。
     */
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
        var detailed = 0
        val merged = if (completed && all.isNotEmpty()) {
            val feeById = songDetails(all.map { it.id }).associateBy { it.id }
            detailed = feeById.size
            if (feeById.isEmpty()) {
                // 一条都没补上 ⇒ 这次等于没拿到档位。**不固化**，否则下次进来徽标仍然缺，
                // 而用户没有任何线索知道是「拉失败了」而不是「本来就该没有」。
                completed = false
                all
            } else {
                all.map { t ->
                    feeById[t.id]?.let { d ->
                        t.copy(
                            fee = d.fee,
                            // detail 老端点可能给 0（没时长），那时保留原值而不是抹掉。
                            durationMs = d.durationMs.takeIf { it > 0L } ?: t.durationMs,
                        )
                    } ?: t
                }
            }
        } else {
            all
        }
        // 只有整轮分页（含补档位）都成功才落缓存（含"确实没有已购"的空结果）：请求失败若被
        // 固化，会让用户看到"无已购"；而空结果不入缓存，已购为 0 的人每次进"我的"都要重拉。
        if (completed) {
            diag("purchasedSongs complete offset=$offset total=${all.size} detailed=$detailed")
            purchasedSongsCache = merged
        }
        merged
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
                            albumId = o.optLong("albumId", 0L).takeIf { it > 0L }?.toString() ?: "",
                            // 档位**不在这里取**：这一项没有 `fee`（它是购买记录、不是 song 对象），
                            // 留 0 占位，由 [purchasedSongs] 拉完全部后按 id 统一补。
                            // 别改用同项的 `vip` 字段 —— 理由见 [purchasedSongs] 的注释。
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

    /**
     * 歌单完整壳（元数据 + 曲目）；榜单 id 即歌单 id。
     *
     * 流程是「**Room 先出 → 检查 → 指纹变了才拉全量**」，全在 [CollectionRefresher] 里；
     * 这里只负责 cacheKey 的拼法（`pl:<id>`，与榜单共用）。
     * [force] 用于错误态重试：跳过冷却、无条件真拉一次。
     */
    suspend fun playlistCollection(playlistId: String, force: Boolean = false): TrackCollection? =
        refresher.playlist("pl:$playlistId", playlistId, force)

    /**
     * 已购专辑完整壳（/weapi/v1/album/{id}，无播放量等）。同 [playlistCollection]，但不做变更检查。
     *
     * **收藏态在出口处补上**：专辑详情**没有** `subscribed` 字段（实测 album 对象 29 个键里没有），
     * 拿到的壳里它恒为 false —— 于是「已经收藏过的专辑」按钮画成空心，点一下才拿到服务端的
     * 「该专辑已经在用户收藏列表中」。收藏态只能查 [LibraryStateStore] 那份「我收藏的专辑」镜像
     * （歌单不同：`v6/playlist/detail` 自带这个字段，跟着 [TrackCollection.subscribed] 走）。
     *
     * 放在出口处而不是写进缓存：Room 里那一行的该列是解析结果的死值（恒 false），
     * 每次读都得就着镜像现覆写 —— 缓存本身**不参与**收藏态的判断。
     */
    suspend fun albumCollection(albumId: String, force: Boolean = false): TrackCollection? {
        val base = refresher.album("al:$albumId", albumId, force) ?: return null
        val subscribed = albumSubscribed(albumId) ?: return base
        return base.copy(subscribed = subscribed)
    }

    /**
     * 这张专辑是否已被当前账号收藏。镜像拉不到就返回 null —— 调用方**保持原值**，
     * 绝不把「没拉到」当成「没收藏」（那会让每张专辑先画空心）。
     */
    private suspend fun albumSubscribed(albumId: String): Boolean? {
        val uid = account()?.uid ?: return null
        library.ensureSubscribedAlbumsLoaded(uid)
        return library.albumsLoadedFor(uid)?.contains(albumId)
    }

    /**
     * 只读 Room 副本（不发网络）—— 进列表页时**第一段**先用它渲染，避免骨架屏，
     * 也让 Room 成为常态读路径而不是「只有离线才用得上」的兜底。
     */
    suspend fun cachedPlaylistCollection(playlistId: String): TrackCollection? =
        refresher.cached("pl:$playlistId")

    /**
     * 同上，专辑版。收藏态这里也要补 —— 否则第一段先画空心、第二段才亮，按钮会闪一下。
     * 镜像还没载入（或还不知道 uid）就先不补，等第二段（[albumCollection]）修正；
     * 这条路径上**只读已缓存的账号**，不为了取 uid 去打网络（首屏要快）。
     */
    suspend fun cachedAlbumCollection(albumId: String): TrackCollection? {
        val base = refresher.cached("al:$albumId") ?: return null
        val uid = cachedAccountOrNull()?.uid ?: return base
        val ids = library.albumsLoadedFor(uid) ?: return base
        return base.copy(subscribed = albumId in ids)
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

    /**
     * `/weapi/v3/song/detail` 批量取曲目详情（说话人：「喜欢」列表与「已购单曲」补档位都走这里）。
     *
     * ## 为什么必须分批
     *
     * C 层的 `ne_song_detail` 把逗号串原样拼进 `char ids[8192]`（`snprintf`），每条约
     * 12 字节（最长 11 位 id + 逗号）⇒ **约 680 条是硬上限**；超了会被静默截断。而同一请求里
     * 那个 `c` 字段是 `sb` 动态拼的、不受限 —— 一旦截断，两个参数描述的就是不同的 id 集合，
     * 服务端行为不可预期。`chunked` 后每批 200 条，留足余量。
     *
     * 保序性**已实测**（把 9 个 id 逆序传入 → 逆序返回，不是按 id 排序），所以按批
     * `flatMap` 出来的顺序仍是传入顺序，分页/懒加载的调用方可以依赖它。
     *
     * ## 失败语义
     *
     * 任一批失败即返回**空表**，不返回「已拿到的那部分」：调用方（[likedTracks] /
     * [purchasedSongs]）把空理解为「这次没拿到档位」，而部分结果会被当成完整结果
     * 固化进缓存 —— 那会变成「红心/徽标少几首」且看不出是缺的。
     */
    private suspend fun songDetails(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<Track>()
        for (batch in ids.chunked(SONG_DETAIL_BATCH)) {
            val r = gateway.call(NeteaseOp.SONG_DETAIL, batch.joinToString(","))
            // create_weapi 路径：body 无顶层 code 时库 fallback code=200/err=0；
            // 旧版严格路径的 code=0 已不复现。以 err+body 判定，code 仅作诊断。
            if (r.err != 0 || r.body.isEmpty()) {
                diag("songDetails batch=${batch.size} err=${r.err} code=${r.code} -> abort")
                return emptyList()
            }
            val parsed = try {
                parseTracks(JSONObject(String(r.body, Charsets.UTF_8)).optJSONArray("songs"))
            } catch (e: Exception) {
                diag("songDetails parse failed: ${e.message}")
                return emptyList()
            }
            out += parsed
        }
        diag("songDetails ids=${ids.size} parsed=${out.size} batches=${(ids.size + SONG_DETAIL_BATCH - 1) / SONG_DETAIL_BATCH}")
        return out
    }

    /** 调试诊断日志，仅在 debug 构建输出（vivo 等机型 Log.d 被屏蔽，故用 Log.e）。 */
    private fun diag(msg: String) {
        if (BuildConfig.DEBUG) Log.e("ProfileDiag", msg)
    }
}
