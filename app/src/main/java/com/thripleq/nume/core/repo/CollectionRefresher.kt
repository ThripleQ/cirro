package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.BuildConfig
import com.thripleq.nume.core.db.CollectionCache
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 歌单 / 榜单 / 专辑壳的取数：**Room 先出 → 检查 → 变了才拉全量**。
 *
 * 为什么不是「每次进页面都重拉」：用户明确「不喜欢频繁的重新拉取」；而「命中即返回」
 * 又会让数据永远不更新。所以中间加一层**便宜的变更检查**：
 *
 * 1. 先把 Room 里的副本交出去渲染（`CollectionCache.get`）—— Room 是**常态读路径**，
 *    「离线可读」只是它的副产品；
 * 2. 距上次真正打过网络不足 [COOLDOWN_MS] 就直接返回缓存，**一个请求都不发**；
 * 3. 超过冷却才发一次 `n=0` 检查：只回元数据 + **完整 trackIds**（实测约全量的 7~15%，
 *    且无需解析歌曲、无需写库）。把它算出的指纹与缓存里的比：
 *    - **相同** → 曲目表仍然有效，只把最新元数据写回（`putMeta`，不动曲目表）；
 *    - **不同** → 拉全量、写 Room、返回新的。
 * 4. 检查失败（多半断网）→ 保持 Room 内容，**不白拉全量**；全量失败 → 回退 Room 旧内容。
 *
 * 于是原来那份内存 LruCache 的两个职责被拆开了：省请求交给「冷却 + 指纹」，离线和首屏
 * 交给 Room。它本来是同一个对象承担两件事，才导致「返回再进不重拉」与「数据永远不新」
 * 这对矛盾。
 *
 * 专辑走另一条（[album]）：`/weapi/v1/album/{id}` 的 album 对象里没有 updateTime，也
 * 没有 trackIds 形态的轻量返回，本来就没法检查；但它只有几十首、20KB 上下，直接拉全量
 * 即可，同样受冷却保护。榜单 id 就是歌单 id（[playlist]），自动受益。
 */
@Singleton
class CollectionRefresher @Inject constructor(
    private val gateway: NetEaseGateway,
    private val collectionCache: CollectionCache,
) {

    /** key → 上次**真正打过网络**的时刻（检查与全量都算一次）。见 [COOLDOWN_MS]。 */
    private val lastNetworkAt = ConcurrentHashMap<String, Long>()

    /** 只读 Room，不发网络。进页面第一段渲染用。 */
    suspend fun cached(key: String): TrackCollection? = collectionCache.get(key)

    /**
     * 歌单/榜单：先出缓存 → 冷却内直接返回 → 检查 → 变了才拉全量。
     * 返回 null 只发生在「没有缓存且这次也没拉到」。
     */
    suspend fun playlist(key: String, id: String, force: Boolean = false): TrackCollection? =
        withContext(Dispatchers.IO) {
            val cached = collectionCache.get(key)
            // 没有副本可比 → 检查无从谈起，直接拉。
            if (cached == null) return@withContext fetchPlaylist(key, id)

            if (!force && cooling(key)) {
                diag("playlist $id cache-only (cooldown)")
                return@withContext cached
            }

            val meta = fetchPlaylistMeta(id)
            if (meta == null) {
                // 检查都没成，说明多半没网：保持 Room 内容。
                diag("playlist $id check failed -> keep cache")
                return@withContext cached
            }

            val known = cached.fingerprint.isNotEmpty()
            if (known && meta.fingerprint == cached.fingerprint) {
                // 曲目没变：曲目表原样复用，只更新元数据（名称/封面/收藏数/播放数会自己变新）。
                val merged = meta.copy(tracks = cached.tracks)
                collectionCache.putMeta(key, merged)
                note(key)
                diag("playlist $id unchanged -> meta only, tracks=${merged.tracks.size}")
                return@withContext merged
            }

            diag("playlist $id changed (known=$known) -> full fetch")
            fetchPlaylist(key, id) ?: cached
        }

    /**
     * 专辑：没有可靠的变更判据，冷却之外直接重拉（体量小），失败回退 Room。
     * 曲目为空时保留缓存（`get` 返回的旧壳仍可渲染）。
     */
    suspend fun album(key: String, id: String, force: Boolean = false): TrackCollection? =
        withContext(Dispatchers.IO) {
            val cached = collectionCache.get(key)
            if (cached != null && !force && cooling(key)) {
                diag("album $id cache-only (cooldown)")
                return@withContext cached
            }
            val r = gateway.call(NeteaseOp.ALBUM_DETAIL, id)
            diag("album op=${NeteaseOp.ALBUM_DETAIL} id=$id code=${r.code} err=${r.err}")
            if (r.err != 0 || r.body.isEmpty()) return@withContext cached
            val parsed = try {
                parseAlbumObject(JSONObject(String(r.body, Charsets.UTF_8)))
            } catch (_: Exception) {
                null
            } ?: return@withContext cached
            collectionCache.put(key, parsed)
            note(key)
            parsed
        }

    /** 收藏态变了（乐观更新）后落库：下次进页面 Room 才不会再交回旧值。 */
    suspend fun noteSubscribed(key: String, subscribed: Boolean, subscribedCount: Long) {
        collectionCache.putSubscribed(key, subscribed, subscribedCount)
    }

    /** 账号切换：清掉所有副本，并让冷启动式的「第一次进入必检查」重新生效。 */
    suspend fun clear() {
        collectionCache.clearAll()
        lastNetworkAt.clear()
    }

    /**
     * 轻量检查：`n=0` 只回元数据 + **完整** trackIds。
     * 返回 null = 这次检查没成功（网络/解析），调用方应保持 Room 内容。
     */
    private suspend fun fetchPlaylistMeta(id: String): TrackCollection? {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, id, "0", CHECK_N)
        if (r.err != 0 || r.code != 200) {
            diag("playlist check id=$id code=${r.code} err=${r.err}")
            return null
        }
        return try {
            val playlist = JSONObject(String(r.body, Charsets.UTF_8))
                .optJSONObject("playlist") ?: return null
            // n=0 ⇒ tracks 为空，这里取到的是纯元数据 + 指纹（parsePlaylistObject 一并算好）。
            parsePlaylistObject(playlist)
        } catch (_: Exception) {
            null
        }
    }

    /** 拉全量（`n` 空 = 库内回落到上游默认 100000）。成功写 Room；失败回退 Room 旧内容。 */
    private suspend fun fetchPlaylist(key: String, id: String): TrackCollection? {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, id, "0", FULL_N)
        if (r.err != 0 || r.code != 200) {
            diag("playlist full id=$id code=${r.code} err=${r.err}")
            return collectionCache.get(key)
        }
        val playlist = try {
            JSONObject(String(r.body, Charsets.UTF_8)).optJSONObject("playlist")
        } catch (_: Exception) {
            null
        } ?: return collectionCache.get(key)

        val base = parsePlaylistObject(playlist)
        // 超过 1000 首时 tracks[] 被服务端截断，按 trackIds 分批补（小歌单零额外请求）。
        val full = base.copy(tracks = completePlaylistTracks(gateway, playlist, base.tracks))
        collectionCache.put(key, full)
        note(key)
        diag("playlist $id full ok tracks=${full.tracks.size} count=${full.trackCount}")
        return full
    }

    /** 冷却期内直接复用 Room；表是 ConcurrentHashMap —— 各仓库都从 IO 线程调它。 */
    private fun cooling(key: String): Boolean {
        val at = lastNetworkAt[key] ?: return false
        return System.currentTimeMillis() - at < COOLDOWN_MS
    }

    private fun note(key: String) {
        lastNetworkAt[key] = System.currentTimeMillis()
    }

    /** 诊断日志只在 debug 打（vivo 上 Log.d 被屏蔽，故用 Log.e）。 */
    private fun diag(msg: String) {
        if (BuildConfig.DEBUG) Log.e(TAG, msg)
    }

    companion object {
        private const val TAG = "NumeCollection"

        /**
         * 「检查」冷却：距上次真正打过网络不足这么久，就直接用 Room、不发请求。
         *
         * 取值是 30s 的取舍：反复进出同一列表的间隔是**秒级**（展开壳来回切、tab 切换），
         * 而「去官方 App 改了歌单再回来看」是**几十秒级**的动作 —— 30s 把前者全部挡掉，
         * 又把后者的可见延迟压在半分钟内。（进程重启后这张表是空的，所以冷启动后每个
         * 集合第一次进入仍会检查一次。）
         */
        const val COOLDOWN_MS = 30_000L

        /** `n=0`：只要元数据 + 完整 trackIds 的轻量检查形态（见 libnetease services.h）。 */
        const val CHECK_N = "0"

        /** 空串 = 库里回落到上游默认的全量，避免把 100000 这个数字写死两份。 */
        const val FULL_N = ""
    }
}
