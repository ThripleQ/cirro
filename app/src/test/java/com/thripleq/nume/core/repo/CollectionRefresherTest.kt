package com.thripleq.nume.core.repo

import com.google.common.truth.Truth.assertThat
import com.thripleq.nume.core.net.ApiResult
import com.thripleq.nume.core.util.Clock
import com.thripleq.nume.core.util.Diagnostics
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * 「Room 先出 → 冷却 → 检查 → 变了才拉全量」这条编排的测试。
 *
 * ## 为什么现在才测得动
 *
 * [CollectionRefresher] 过去直接依赖 JNI 网关、Room、[System.currentTimeMillis]，
 * 三样在 JVM 上都碰不得（[android.util.Log] 还是空壳，调就抛），所以这段逻辑一条
 * 断言都没有 —— 而它恰恰是「用户不喜欢频繁重拉」与「数据要新」这对矛盾的落点，
 * 改错的后果是**静默**的：不是崩，是「每次都偷偷全量拉」或「永远不更新」。
 * 现在三个依赖都走接口了（[CollectionRemote] / [CollectionStore] / [Clock]）。
 *
 * ## 每条断言的出处
 *
 * 判据不是凭空设计的，是 2026-10-06 探针实测的：
 * - `n=0` 回的 `trackIds` **与全量完全一致**（2213 首也给 2213 个）⇒ 指纹比对成立；
 * - `playlist.trackUpdateTime` **每次调用都变**（服务端现算）⇒ 绝不能当判据，所以用指纹；
 * - 无 ETag / Last-Modified（`no-cache, no-store`）⇒ 条件请求不可用，只能自己比对。
 */
class CollectionRefresherTest {

    // ---------------------------------------------------------- 冷却：省请求

    /**
     * 冷却内**一个请求都不发**。
     *
     * 第一次调用发了一次检查并记下时刻；第二次只过了 1s（< 30s）→ 直接吃 Room。
     * 这是「反复进出同一列表 / 来回切 tab」这条秒级路径的落点。
     */
    @Test
    fun playlist_withinCooldown_sendsNothing() {
        val store = FakeStore(collectionOf(listOf(1, 2)))
        val remote = FakeRemote(onPlaylist = { _, _ -> ok(playlistBody("名", listOf(1, 2))) })
        val clock = FakeClock()
        val r = refresher(remote, store, clock)

        runBlocking {
            r.playlist("k", "123")
            val before = remote.calls.size
            val second = r.playlist("k", "123")
            assertThat(remote.calls).hasSize(before)
            assertThat(second).isNotNull()
        }
    }

    /** `force = true` 时跳过冷却（用户明确下拉刷新）—— 但**检查该发还是发**。 */
    @Test
    fun playlist_forceSkipsCooldown() {
        val store = FakeStore(collectionOf(listOf(1, 2)))
        val remote = FakeRemote(onPlaylist = { _, _ -> ok(playlistBody("名", listOf(1, 2))) })
        val clock = FakeClock()
        val r = refresher(remote, store, clock)

        runBlocking {
            r.playlist("k", "123")
            remote.calls.clear()
            r.playlist("k", "123", force = true)
            assertThat(remote.calls).containsExactly("playlist:123:n=0")
        }
    }

    // ------------------------------------------------------- 检查：指纹比对

    /**
     * 指纹相同 → **只写元数据，曲目表一行都不重写**，也不拉全量。
     *
     * 对 2000 首的歌单，重写一次曲目表就是 2000 行的删 + 插；而名称/封面/收藏数
     * 这些元数据本来就在检查请求里回来了，顺手写回即可。
     */
    @Test
    fun playlist_unchangedFingerprint_writesMetaOnly() {
        val cached = collectionOf(listOf(1, 2), name = "旧名")
        val store = FakeStore(cached)
        val remote = FakeRemote(onPlaylist = { _, n ->
            // 名字变了、曲目没变：正是「元数据要新、曲目表不动」的那一类。
            ok(playlistBody("新名", listOf(1, 2)))
                .also { assertThat(n).isEqualTo("0") }
        })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.playlist("k", "123")!!
            assertThat(remote.calls).containsExactly("playlist:123:n=0")
            assertThat(store.puts).isEqualTo(0)
            assertThat(store.metas).isEqualTo(1)
            assertThat(out.name).isEqualTo("新名")
            // 曲目沿用 Room 里那份（检查请求没有 tracks，若丢了这个就是整列表变空）。
            assertThat(out.tracks.map { it.id }).containsExactly("1", "2").inOrder()
        }
    }

    /** 指纹不同 → 检查之后**再拉一次全量**，并整壳写回。 */
    @Test
    fun playlist_changedFingerprint_fetchesFull() {
        val store = FakeStore(collectionOf(listOf(1, 2)))
        val remote = FakeRemote(onPlaylist = { _, n ->
            if (n == "0") ok(playlistBody("名", listOf(1, 2, 3))) else ok(playlistBody("名", listOf(1, 2, 3), listOf(song(1), song(2), song(3))))
        })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.playlist("k", "123")!!
            assertThat(remote.calls).containsExactly("playlist:123:n=0", "playlist:123:n=").inOrder()
            assertThat(store.puts).isEqualTo(1)
            assertThat(store.metas).isEqualTo(0)
            assertThat(out.tracks.map { it.id }).containsExactly("1", "2", "3").inOrder()
        }
    }

    /**
     * 缓存里的指纹是空串（v1 迁移来的旧行、没写指纹的老副本）→ **视为变了**，走全量。
     *
     * 空串的定义是「不知道」，不是「没变」—— 后者会让这批旧副本永远不刷新。
     */
    @Test
    fun playlist_unknownFingerprint_fetchesFull() {
        val store = FakeStore(collectionOf(listOf(1, 2)).copy(fingerprint = ""))
        val remote = FakeRemote(onPlaylist = { _, n ->
            if (n == "0") ok(playlistBody("名", listOf(1, 2))) else ok(playlistBody("名", listOf(1, 2), listOf(song(1), song(2))))
        })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            r.playlist("k", "123")
            assertThat(remote.calls).containsExactly("playlist:123:n=0", "playlist:123:n=").inOrder()
        }
    }

    // ------------------------------------------------------------ 失败：保持

    /**
     * 检查请求本身失败（传输错）→ **保持 Room 内容，不白拉全量**。
     *
     * 「检查失败」最常见的成因是断网，此时再发一次全量只会更慢且同样失败。
     */
    @Test
    fun playlist_checkFailed_keepsRoom() {
        val cached = collectionOf(listOf(1, 2))
        val store = FakeStore(cached)
        val remote = FakeRemote(onPlaylist = { _, _ -> ApiResult(520, 1, ByteArray(0)) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.playlist("k", "123")
            assertThat(remote.calls).containsExactly("playlist:123:n=0")
            assertThat(out).isSameInstanceAs(cached)
            assertThat(store.puts).isEqualTo(0)
            assertThat(store.metas).isEqualTo(0)
        }
    }

    /** 传输是通的、但业务码不是 200（下架 / 无权限）→ 同样保持 Room。 */
    @Test
    fun playlist_checkNonOkCode_keepsRoom() {
        val cached = collectionOf(listOf(1, 2))
        val store = FakeStore(cached)
        val remote = FakeRemote(onPlaylist = { _, _ -> ApiResult(404, 0, ByteArray(0)) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            assertThat(r.playlist("k", "123")).isSameInstanceAs(cached)
        }
    }

    /** 全量那次失败 → 回退 Room 旧内容（宁可旧，不要空屏）。 */
    @Test
    fun playlist_fullFetchFailed_fallsBackToRoom() {
        val cached = collectionOf(listOf(1, 2))
        val store = FakeStore(cached)
        val remote = FakeRemote(onPlaylist = { _, n ->
            if (n == "0") ok(playlistBody("名", listOf(1, 2, 3))) else ApiResult(520, 1, ByteArray(0))
        })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            assertThat(r.playlist("k", "123")).isSameInstanceAs(cached)
            assertThat(store.puts).isEqualTo(0)
        }
    }

    // ---------------------------------------------------------- 无缓存：直拉

    /** Room 里没有副本 → 检查无从谈起（没有可比的对象），直接拉全量。 */
    @Test
    fun playlist_noCache_fetchesFullImmediately() {
        val store = FakeStore(null)
        val remote = FakeRemote(onPlaylist = { _, _ -> ok(playlistBody("名", listOf(7), listOf(song(7)))) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.playlist("k", "123")!!
            assertThat(remote.calls).containsExactly("playlist:123:n=")
            assertThat(out.tracks.map { it.id }).containsExactly("7")
            assertThat(store.puts).isEqualTo(1)
        }
    }

    /**
     * 大歌单：`tracks[]` 被服务端截断（只回预览），按 `trackIds` 走 `song/detail` 补，
     * **顺序以 trackIds 为准**。
     *
     * 实测：2213 首的歌单 `tracks[]` 只给前一批；`SONG_DETAIL` 单批上限 1000。
     */
    @Test
    fun playlist_previewTruncated_completesViaSongDetail() {
        val store = FakeStore(null)
        val remote = FakeRemote(
            onPlaylist = { _, _ ->
                // trackIds 三首，tracks 只给了中间那首 → 缺 1、3 需要补。
                ok(playlistBody("名", listOf(1, 2, 3), listOf(song(2))))
            },
            onSong = { ids -> ok(songsBody(ids.split(",").map { song(it.toLong()) })) },
        )
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.playlist("k", "123")!!
            assertThat(remote.calls).containsExactly("playlist:123:n=", "song:1,3").inOrder()
            assertThat(out.tracks.map { it.id }).containsExactly("1", "2", "3").inOrder()
        }
    }

    // --------------------------------------------------------------- 专辑分支

    /**
     * 专辑没有轻量检查形态（`/weapi/v1/album/{id}` 无 updateTime、无 trackIds 形态返回，
     * 实测），所以冷却之外直接拉全量 —— 它只有几十首，代价可接受。
     */
    @Test
    fun album_outsideCooldown_fetchesAndStores() {
        val store = FakeStore(null)
        val remote = FakeRemote(onAlbum = { ok(albumBody(listOf(albumSong(1), albumSong(2)))) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            val out = r.album("k", "9")!!
            assertThat(remote.calls).containsExactly("album:9")
            assertThat(out.tracks.map { it.id }).containsExactly("1", "2")
            assertThat(store.puts).isEqualTo(1)
        }
    }

    @Test
    fun album_withinCooldown_sendsNothing() {
        val store = FakeStore(null)
        val remote = FakeRemote(onAlbum = { ok(albumBody(listOf(albumSong(1)))) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            r.album("k", "9")
            remote.calls.clear()
            assertThat(r.album("k", "9")).isNotNull()
            assertThat(remote.calls).isEmpty()
        }
    }

    /** 专辑拉失败（有缓存时）→ 回退 Room，不写空壳。 */
    @Test
    fun album_failed_fallsBackToRoom() {
        val cached = collectionOf(listOf(1, 2))
        val store = FakeStore(cached)
        val remote = FakeRemote(onAlbum = { ApiResult(520, 1, ByteArray(0)) })
        val r = refresher(remote, store, FakeClock())

        runBlocking {
            assertThat(r.album("k", "9")).isSameInstanceAs(cached)
            assertThat(store.puts).isEqualTo(0)
        }
    }

    // ------------------------------------------------------------------ 辅助

    private fun refresher(remote: CollectionRemote, store: CollectionStore, clock: Clock) =
        CollectionRefresher(remote, store, clock, Diagnostics { _, _ -> })

    private class FakeClock(var now: Long = 1_000L) : Clock {
        override fun nowMs(): Long = now
    }

    private class FakeRemote(
        private val onPlaylist: suspend (String, String) -> ApiResult = { _, _ -> unexpected("playlistDetail") },
        private val onAlbum: suspend (String) -> ApiResult = { unexpected("albumDetail") },
        private val onSong: suspend (String) -> ApiResult = { unexpected("songDetail") },
    ) : CollectionRemote {
        val calls = mutableListOf<String>()

        override suspend fun playlistDetail(id: String, n: String): ApiResult {
            calls += "playlist:$id:n=$n"
            return onPlaylist(id, n)
        }

        override suspend fun albumDetail(id: String): ApiResult {
            calls += "album:$id"
            return onAlbum(id)
        }

        override suspend fun songDetail(ids: String): ApiResult {
            calls += "song:$ids"
            return onSong(ids)
        }
    }

    /** 内存版 Room：除了存值，还记下「整壳写了几次 / 只写元数据几次」—— 那正是要断言的。 */
    private class FakeStore(initial: TrackCollection?) : CollectionStore {
        var value: TrackCollection? = initial
        var puts = 0
        var metas = 0

        override suspend fun get(key: String): TrackCollection? = value

        override suspend fun put(key: String, collection: TrackCollection) {
            puts++
            value = collection
        }

        override suspend fun putMeta(key: String, collection: TrackCollection) {
            metas++
            value = collection
        }

        override suspend fun putSubscribed(key: String, subscribed: Boolean, subscribedCount: Long) = Unit

        override suspend fun clearAll() {
            value = null
        }
    }

    private fun ok(body: ByteArray) = ApiResult(200, 0, body)

    /**
     * 造一份「Room 里已有的副本」。
     *
     * ⚠️ 必须取 `playlist` 子对象：`parsePlaylistObject` 收的是 playlist 本身，
     * 直接喂 root（只有一层 `{"playlist":…}`）会解析出**空指纹**，于是编排把它
     * 当成「不知道」而直接拉全量 —— 测试看上去就像「冷却/检查没生效」。
     */
    private fun collectionOf(ids: List<Long>, name: String = "旧名"): TrackCollection =
        parsePlaylistObject(playlistObject(name, ids, ids.map { song(it) }))

    private fun playlistObject(name: String, ids: List<Long>, tracks: List<JSONObject>) =
        JSONObject(String(playlistBody(name, ids, tracks))).optJSONObject("playlist")!!

    /** 一个最小可用的 playlist 响应壳：`tracks` 给不给由调用方决定（n=0 时服务端就不给）。 */
    private fun playlistBody(name: String, ids: List<Long>, tracks: List<JSONObject> = emptyList()): ByteArray {
        val p = JSONObject()
            .put("id", 123L)
            .put("name", name)
            .put("trackCount", ids.size)
        val idArr = JSONArray()
        ids.forEach { idArr.put(JSONObject().put("id", it)) }
        p.put("trackIds", idArr)
        val tArr = JSONArray()
        tracks.forEach { tArr.put(it) }
        p.put("tracks", tArr)
        return JSONObject().put("playlist", p).toString().toByteArray(Charsets.UTF_8)
    }

    private fun song(id: Long) = JSONObject().put("id", id).put("name", "s$id")

    private fun songsBody(songs: List<JSONObject>): ByteArray {
        val arr = JSONArray()
        songs.forEach { arr.put(it) }
        return JSONObject().put("songs", arr).toString().toByteArray(Charsets.UTF_8)
    }

    /** 专辑响应顶层只有 `songs`（实测），专辑元数据从首曲的 `al` / `ar` 推断。 */
    private fun albumBody(songs: List<JSONObject>): ByteArray {
        val arr = JSONArray()
        songs.forEach { arr.put(it) }
        return JSONObject().put("songs", arr).toString().toByteArray(Charsets.UTF_8)
    }

    private fun albumSong(id: Long) = song(id)
        .put("al", JSONObject().put("id", 9L).put("name", "专辑名").put("picUrl", "http://p/1.jpg"))
        .put("ar", JSONArray().put(JSONObject().put("name", "歌手")))

    private companion object {
        fun unexpected(which: String): Nothing =
            throw AssertionError("测试没预设 $which 的响应 —— 说明编排多发了预期外的请求")
    }
}
