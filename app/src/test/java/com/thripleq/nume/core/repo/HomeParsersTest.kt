package com.thripleq.nume.core.repo

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * 首页解析（`HomeParsers.kt`）的判据测试。
 *
 * 这批端点的返回壳**没有公开文档**，键名是探针实测猜出来的；漏读一个字段不会崩，
 * 只会变成「封面全灰」「徽标不画」这类**静默**错误 —— 只有真机截图才看得出来。
 *
 * 其中 `parseBlockSongs` 那两条是 2026-10-06 刚修的：`resourceExtInfo.song` 里的
 * `fee` / `al.id` 之前根本没读，等于首页的歌永远是「免费 + 无归属」，猜你喜欢里
 * 一枚徽标都画不出来。**别让它们退回去。**
 */
class HomeParsersTest {

    // ------------------------------------------------------------ parseBlockSongs

    private fun blockSong(
        resourceId: Long = 1974443814L,
        title: String = "那些我尚未知道的美丽",
        imageUrl: String = "http://p3.music.126.net/cover.jpg",
        artistName: String = "夏日入侵企划",
        dt: Long = 245_000L,
        fee: Int = 1,
        albumId: Long = 258512180L,
        albumName: String = "量变临界点",
    ) = JSONObject().apply {
        put("resourceId", resourceId)
        put(
            "uiElement",
            JSONObject()
                .put("mainTitle", JSONObject().put("title", title))
                .put("image", JSONObject().put("imageUrl", imageUrl)),
        )
        put(
            "resourceExtInfo",
            JSONObject()
                .put("artists", JSONArray().put(JSONObject().put("name", artistName)))
                .put(
                    "song",
                    JSONObject()
                        .put("dt", dt)
                        .put("fee", fee)
                        .put(
                            "al",
                            JSONObject()
                                .put("id", albumId)
                                .put("name", albumName),
                        ),
                ),
        )
    }

    /**
     * `fee` **必须**从 `resourceExtInfo.song` 读 —— 这是付费徽标在「猜你喜欢」里能
     * 画出来的唯一来源（block 的顶层没有 fee）。
     */
    @Test
    fun blockSongs_readsFeeFromEmbeddedSongObject() {
        val t = parseBlockSongs(JSONArray().put(blockSong(fee = 4))).single()
        assertThat(t.fee).isEqualTo(4)
    }

    /** `al.id` 同样在内嵌 song 上 —— 它是「这首歌属于哪张已购专辑」的唯一线索。 */
    @Test
    fun blockSongs_readsAlbumIdFromEmbeddedSongObject() {
        val t = parseBlockSongs(JSONArray().put(blockSong(albumId = 156507145L))).single()
        assertThat(t.albumId).isEqualTo("156507145")
        assertThat(t.albumName).isEqualTo("量变临界点")
    }

    @Test
    fun blockSongs_mapsIdTitleArtistCoverAndDuration() {
        val t = parseBlockSongs(JSONArray().put(blockSong())).single()
        assertThat(t.id).isEqualTo("1974443814")
        assertThat(t.name).isEqualTo("那些我尚未知道的美丽")
        assertThat(t.artist).isEqualTo("夏日入侵企划")
        // block 给的是明文 http，Android 默认禁明文 → 必须升级，否则封面全灰
        assertThat(t.artworkUrl).isEqualTo("https://p3.music.126.net/cover.jpg")
        assertThat(t.durationMs).isEqualTo(245_000L)
    }

    /** 缺 `resourceId` 或标题为空白 ⇒ 整条丢弃（block 里确实会有占位资源）。 */
    @Test
    fun blockSongs_dropsEntriesWithoutIdOrTitle() {
        val arr = JSONArray()
            .put(blockSong(resourceId = 0L))
            .put(blockSong(title = ""))
            .put(blockSong(resourceId = 99L, title = "有效"))
        assertThat(parseBlockSongs(arr).map { it.id }).containsExactly("99")
    }

    /** 内嵌 song 缺失时退化成 0 / 空，不能抛（老版本 block 的 shape 不一样）。 */
    @Test
    fun blockSongs_withoutEmbeddedSongStillParses() {
        val o = JSONObject()
            .put("resourceId", 123L)
            .put("uiElement", JSONObject().put("mainTitle", JSONObject().put("title", "无名")))
        val t = parseBlockSongs(JSONArray().put(o)).single()
        assertThat(t.fee).isEqualTo(0)
        assertThat(t.albumId).isEmpty()
        assertThat(t.durationMs).isEqualTo(0L)
    }

    @Test
    fun blockSongs_nullAndEmptyYieldEmpty() {
        assertThat(parseBlockSongs(null)).isEmpty()
        assertThat(parseBlockSongs(JSONArray())).isEmpty()
    }

    // ------------------------------------------------------------------ parseCards

    /** `/weapi/playlist/list`（场景音乐）走 `playlists` 键。 */
    @Test
    fun parseCards_readsPlaylistsKey() {
        val root = JSONObject().put(
            "playlists",
            JSONArray().put(
                JSONObject()
                    .put("id", 1L)
                    .put("name", "清晨")
                    .put("coverImgUrl", "https://p1/1.jpg")
                    .put("playCount", 12345L)
                    .put("trackCount", 30L),
            ),
        )
        val c = parseCards(root).single()
        assertThat(c.id).isEqualTo("1")
        assertThat(c.name).isEqualTo("清晨")
        assertThat(c.coverUrl).isEqualTo("https://p1/1.jpg")
        assertThat(c.playCount).isEqualTo(12345L)
        assertThat(c.trackCount).isEqualTo(30L)
    }

    /** 推荐歌单走 `result` 键；同一个函数要两种壳都吃得下。 */
    @Test
    fun parseCards_readsResultKey() {
        val root = JSONObject().put(
            "result",
            JSONArray().put(JSONObject().put("id", 2L).put("name", "推荐")),
        )
        assertThat(parseCards(root).map { it.id }).containsExactly("2")
    }

    /**
     * 封面键三个端点三种写法（picUrl / coverImgUrl / cover）—— 谁非空用谁。
     * 只认一种的代价是「某些区的卡面整片空白」。
     */
    @Test
    fun parseCard_triesThreeCoverKeys() {
        fun coverOf(o: JSONObject) = parseCard(o)!!.coverUrl
        assertThat(coverOf(JSONObject().put("id", 1L).put("picUrl", "https://a")))
            .isEqualTo("https://a")
        assertThat(coverOf(JSONObject().put("id", 1L).put("coverImgUrl", "https://b")))
            .isEqualTo("https://b")
        assertThat(coverOf(JSONObject().put("id", 1L).put("cover", "https://c")))
            .isEqualTo("https://c")
    }

    /** 曲目数：有 `trackCount` 用它，没有才回落到 `songCount`。 */
    @Test
    fun parseCard_fallsBackToSongCount() {
        assertThat(parseCard(JSONObject().put("id", 1L).put("trackCount", 12L))!!.trackCount)
            .isEqualTo(12L)
        assertThat(parseCard(JSONObject().put("id", 1L).put("songCount", 7L))!!.trackCount)
            .isEqualTo(7L)
    }

    @Test
    fun parseCards_skipsEntriesWithoutId() {
        val root = JSONObject().put(
            "playlists",
            JSONArray()
                .put(JSONObject().put("id", 0L))
                .put(JSONObject().put("name", "无 id"))
                .put(JSONObject().put("id", 5L).put("name", "有")),
        )
        assertThat(parseCards(root).map { it.id }).containsExactly("5")
    }

    @Test
    fun parseCards_missingArrayYieldsEmpty() {
        assertThat(parseCards(JSONObject())).isEmpty()
    }

    // ------------------------------------------------------------- parseRadarCards

    /**
     * 雷达卡标题形如「华晨宇的歌,总令人心动|华语私人雷达」—— 竖线前是推荐理由，
     * 卡面只显示后半段（146dp 的卡塞不下长理由）。
     */
    @Test
    fun radarCards_takeTitleAfterLastPipe() {
        val c = parseRadarCards(
            JSONArray().put(
                JSONObject()
                    .put("creativeId", "111")
                    .put(
                        "uiElement",
                        JSONObject()
                            .put("mainTitle", JSONObject().put("title", "华晨宇的歌,总令人心动|华语私人雷达"))
                            .put("image", JSONObject().put("imageUrl", "http://p/r.jpg")),
                    ),
            ),
        ).single()
        assertThat(c.id).isEqualTo("111")
        assertThat(c.name).isEqualTo("华语私人雷达")
        assertThat(c.coverUrl).isEqualTo("https://p/r.jpg")
    }

    /** 没有竖线就整名照用，别截成空串。 */
    @Test
    fun radarCards_withoutPipeKeepsWholeTitle() {
        val c = parseRadarCards(
            JSONArray().put(
                JSONObject()
                    .put("creativeId", "222")
                    .put("uiElement", JSONObject().put("mainTitle", JSONObject().put("title", "私人雷达"))),
            ),
        ).single()
        assertThat(c.name).isEqualTo("私人雷达")
    }

    /** creativeId 为空 ⇒ 丢弃（没有 id 的卡点了也没法跳）。 */
    @Test
    fun radarCards_requiresCreativeId() {
        val arr = JSONArray()
            .put(JSONObject().put("creativeId", ""))
            .put(JSONObject().put("creativeId", "9").put("uiElement", JSONObject()))
        assertThat(parseRadarCards(arr).map { it.id }).containsExactly("9")
    }

    // ------------------------------------------------------------------ firstArray

    @Test
    fun firstArray_skipsEmptyArraysAndKeepsLooking() {
        val flat = JSONObject()
            .put("result", JSONArray())
            .put("playlists", JSONArray().put(1))
        assertThat(firstArray(flat, "result", "playlists")).isNotNull()
    }

    /**
     * 候选键的值是对象时，往**该对象内部**按同一份候选键再找一层（`data.playlists` 这种壳）。
     */
    @Test
    fun firstArray_descendsIntoOneNestedObject() {
        val nested = JSONObject().put("data", JSONObject().put("playlists", JSONArray().put(1)))
        assertThat(firstArray(nested, "data", "playlists")).isNotNull()
    }

    /**
     * **只下钻候选键自己那一层**：`data` 不在候选表里时，`data.playlists` 找不到。
     *
     * 这是有意的约束（不无限递归，避免把不相干的深层数组也捞出来）—— 别当 bug 修。
     */
    @Test
    fun firstArray_doesNotDescendIntoKeysOutsideCandidates() {
        val nested = JSONObject().put("data", JSONObject().put("playlists", JSONArray().put(1)))
        assertThat(firstArray(nested, "result", "playlists")).isNull()
    }

    @Test
    fun firstArray_noCandidateYieldsNull() {
        assertThat(firstArray(JSONObject(), "result")).isNull()
    }

    // --------------------------------------------------------------- findTrackArray

    /**
     * 端点常同时带 playlists / artists 等大数组。`findTrackArray` 要挑出**歌曲**那个，
     * 而不是第一个数组 —— 挑错就是整块内容为空。
     */
    @Test
    fun findTrackArray_prefersTheArrayThatLooksLikeSongs() {
        val root = JSONObject()
            .put("playlists", JSONArray().put(JSONObject().put("id", 1L).put("name", "歌单")))
            .put("songs", JSONArray().put(JSONObject().put("id", 2L).put("name", "歌")))
        val arr = findTrackArray(root)!!
        assertThat(arr.optJSONObject(0).optString("name")).isEqualTo("歌")
    }

    @Test
    fun findTrackArray_noSongArrayYieldsNull() {
        assertThat(findTrackArray(JSONObject())).isNull()
    }
}
