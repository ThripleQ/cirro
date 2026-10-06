package com.thripleq.nume.core.repo

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * `TrackParser` 的判据测试。
 *
 * ## 为什么先测这里
 *
 * 这批判据过去**每一条都是探针实测出来的**，但只写在注释和记忆里 —— 没人能验证它
 * 们还在生效。而 `parseTrack` 出错的表现**不是崩溃**：封面变灰、专辑名消失、徽标
 * 不画，全是静默的。这类错误只有真机截图才看得出来，成本极高。
 *
 * ## 每条断言的来源
 *
 * 都标注了实测端点与日期。**改断言前先去实测一遍**，别为了让测试变绿而改断言 ——
 * 那等于把实测过的口径换成猜测。
 */
class TrackParserTest {

    // ---------------------------------------------------------------- 新格式

    /**
     * 新格式（`al` / `ar` / `dt`）：`v6/playlist/detail`、`v1/album`、
     * `recommend/songs`、`artist/hotSongs` 都是这一套。
     *
     * 取值取自 2026-10-06 探针实测（`v6/playlist/detail`，fee=4 = 需购买）。
     */
    @Test
    fun newFormat_readsAlbumArtistDurationFeeAndAlbumId() {
        val t = parseTrack(
            JSONObject(
                """
                {
                  "id": 1974443814,
                  "name": "那些我尚未知道的美丽",
                  "al": { "id": 258512180, "name": "量变临界点", "picUrl": "http://p3.music.126.net/a.jpg" },
                  "ar": [ { "name": "夏日入侵企划" } ],
                  "dt": 245000,
                  "fee": 4
                }
                """.trimIndent(),
            ),
        )!!

        assertThat(t.id).isEqualTo("1974443814")
        assertThat(t.name).isEqualTo("那些我尚未知道的美丽")
        assertThat(t.artist).isEqualTo("夏日入侵企划")
        assertThat(t.albumName).isEqualTo("量变临界点")
        assertThat(t.albumId).isEqualTo("258512180")
        assertThat(t.durationMs).isEqualTo(245_000L)
        assertThat(t.fee).isEqualTo(4)
    }

    /**
     * 封面统一升 https。实测 v6 `playlist/detail` 的 `al.picUrl` 就是 `http://`
     * （而 `coverImgUrl` 多是 https），Android 默认禁明文 → 不升级就是整批封面灰掉。
     */
    @Test
    fun newFormat_upgradesInsecureCoverToHttps() {
        val t = parseTrack(
            JSONObject("""{"id":1,"al":{"picUrl":"http://p3.music.126.net/a.jpg"}}"""),
        )!!
        assertThat(t.artworkUrl).isEqualTo("https://p3.music.126.net/a.jpg")
    }

    // ---------------------------------------------------------------- 老格式

    /**
     * 老格式（`album` / `artists` / `duration`）：`/api/v1/radio/get` 与部分 dj 端点。
     *
     * **这是修过的 bug，别再退回去**（2026-10-05）：只认新格式时，私人漫游整批歌
     * `artworkUrl = null`、`albumName = ""`、`durationMs = 0` —— 表现为「点漫游卡片
     * 直接播放，播放页没封面、副标题少专辑」。
     */
    @Test
    fun oldFormat_stillYieldsCoverAlbumAndDuration() {
        val t = parseTrack(
            JSONObject(
                """
                {
                  "id": 5156018,
                  "name": "老格式曲",
                  "album": { "id": 35061, "name": "老专辑", "picUrl": "https://p4.music.126.net/b.jpg" },
                  "artists": [ { "name": "老歌手" } ],
                  "duration": 180000
                }
                """.trimIndent(),
            ),
        )!!

        assertThat(t.artworkUrl).isEqualTo("https://p4.music.126.net/b.jpg")
        assertThat(t.albumName).isEqualTo("老专辑")
        assertThat(t.albumId).isEqualTo("35061")
        assertThat(t.durationMs).isEqualTo(180_000L)
    }

    /** 老格式没有 `fee` ⇒ 0（「不画徽标」），与「免费」同形。别把它当 1 或 4。 */
    @Test
    fun oldFormat_hasNoFee_soFeeIsZero() {
        val t = parseTrack(JSONObject("""{"id":1,"name":"x","album":{},"artists":[]}"""))!!
        assertThat(t.fee).isEqualTo(0)
    }

    /**
     * `dt` 为 0 时才回落到 `duration`：老端点会给 `dt: 0`（不是缺字段）。
     * 反过来 `dt` 有值就不能让 `duration` 顶掉。
     */
    @Test
    fun durationFallsBackOnlyWhenDtIsZero() {
        val zeroDt = parseTrack(JSONObject("""{"id":1,"dt":0,"duration":123000}"""))!!
        assertThat(zeroDt.durationMs).isEqualTo(123_000L)

        val hasDt = parseTrack(JSONObject("""{"id":1,"dt":245000,"duration":123000}"""))!!
        assertThat(hasDt.durationMs).isEqualTo(245_000L)
    }

    // ---------------------------------------------------------------- 坏数据

    /** id 缺失 / 为 0 ⇒ 整条丢弃（`parseTracks` 会跳过，不产生空白行）。 */
    @Test
    fun unparseableEntriesAreDropped() {
        assertThat(parseTrack(null)).isNull()
        assertThat(parseTrack(JSONObject("""{"name":"无 id"}"""))).isNull()
        assertThat(parseTrack(JSONObject("""{"id":0}"""))).isNull()
        assertThat(parseTrack(JSONObject("""{"id":-1}"""))).isNull()
    }

    @Test
    fun parseTracks_skipsBadEntriesAndKeepsOrder() {
        val list = parseTracks(
            org.json.JSONArray(
                """
                [
                  {"id":1,"name":"a"},
                  {"id":0,"name":"bad"},
                  {},
                  {"id":2,"name":"b"}
                ]
                """.trimIndent(),
            ),
        )
        assertThat(list.map { it.id }).containsExactly("1", "2").inOrder()
    }

    @Test
    fun parseTracks_nullOrEmptyYieldsEmpty() {
        assertThat(parseTracks(null)).isEmpty()
        assertThat(parseTracks(org.json.JSONArray())).isEmpty()
    }

    // ---------------------------------------------------------------- httpsUrl

    /** 三种形态都要处理；已 https 的不动；空白与 null 归 null。 */
    @Test
    fun httpsUrl_handlesAllThreeShapes() {
        assertThat(httpsUrl("http://p3.music.126.net/a.jpg"))
            .isEqualTo("https://p3.music.126.net/a.jpg")
        assertThat(httpsUrl("//p3.music.126.net/a.jpg"))
            .isEqualTo("https://p3.music.126.net/a.jpg")
        assertThat(httpsUrl("https://p3.music.126.net/a.jpg"))
            .isEqualTo("https://p3.music.126.net/a.jpg")
        assertThat(httpsUrl("")).isNull()
        assertThat(httpsUrl("   ")).isNull()
        assertThat(httpsUrl(null)).isNull()
    }
}
