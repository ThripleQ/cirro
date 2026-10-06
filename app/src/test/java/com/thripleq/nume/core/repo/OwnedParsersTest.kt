package com.thripleq.nume.core.repo

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * 「我买过 / 收藏过 / 点过红心」的解析判据测试。
 *
 * 这些 id 集合是**镜像**：拉一次全量、之后本地查表。解析错一个 id 的后果是静默的 ——
 * 买过的歌被标成红色「需要购买」、红心不亮、收藏画空心 —— 不崩不报错，只有用户自己
 * 知道不对。所以这里逐条钉死。
 *
 * 结构全部来自 2026-10-06 探针实测。最关键的一条：**`null`（没拉到）与 `emptySet()`
 * （拉到了但是空）必须分开** —— 前者会被调用方当成"下次重试"，固化成后者就让一次
 * 网络抖动永久生效。
 */
class OwnedParsersTest {

    // --------------------------------------------------------------- 已购单曲

    /** `{"code":200,"data":{"list":[{songId,...}]}}` —— 注意**不是** song 对象。 */
    @Test
    fun ownedSongs_readsSongIdFromDataList() {
        val root = JSONObject(
            """{"code":200,"data":{"list":[{"songId":18122,"name":"Kids Return"},{"songId":1974443814}]}}""",
        )
        assertThat(parseOwnedSongIds(root)).containsExactly("18122", "1974443814")
    }

    /** 壳子也可能是扁平的 `data` 数组（端点的两种形态都见过）。 */
    @Test
    fun ownedSongs_acceptsFlatDataArray() {
        val root = JSONObject("""{"code":200,"data":[{"songId":7}]}""")
        assertThat(parseOwnedSongIds(root)).containsExactly("7")
    }

    /** 业务码非 200 ⇒「没拉到」，不能当成"买了 0 首"固化下来。 */
    @Test
    fun ownedSongs_nonOkCodeIsFailure() {
        assertThat(parseOwnedSongIds(JSONObject("""{"code":301,"data":{"list":[]}}"""))).isNull()
        assertThat(parseOwnedSongIds(JSONObject("""{"code":520}"""))).isNull()
    }

    /** `data` 整个没有 ⇒ 真的没有已购（空集，不是失败）。 */
    @Test
    fun ownedSongs_missingDataMeansEmptyNotFailure() {
        assertThat(parseOwnedSongIds(JSONObject("""{"code":200}"""))).isEmpty()
    }

    @Test
    fun ownedSongs_skipsEntriesWithoutSongId() {
        val root = JSONObject("""{"code":200,"data":{"list":[{"songId":0},{"name":"无 id"},{"songId":9}]}}""")
        assertThat(parseOwnedSongIds(root)).containsExactly("9")
    }

    // ----------------------------------------------------------- 已购数字专辑

    /** 整张购买，所以「属于已购专辑」=「这首买了」（`Track.albumId` 存在的理由）。 */
    @Test
    fun ownedAlbums_readsPaidAlbums() {
        val root = JSONObject(
            """{"code":200,"paidAlbums":[{"albumId":156507145,"albumName":"希忘Hope"},{"albumId":83848829}]}""",
        )
        assertThat(parseOwnedAlbumIds(root)).containsExactly("156507145", "83848829")
    }

    /** 键可能是 `id` 而不是 `albumId`。 */
    @Test
    fun ownedAlbums_fallsBackToIdKey() {
        assertThat(parseOwnedAlbumIds(JSONObject("""{"code":200,"paidAlbums":[{"id":42}]}""")))
            .containsExactly("42")
    }

    @Test
    fun ownedAlbums_nonOkCodeIsFailure() {
        assertThat(parseOwnedAlbumIds(JSONObject("""{"code":520,"paidAlbums":[]}"""))).isNull()
    }

    @Test
    fun ownedAlbums_nothingMeansEmptyNotFailure() {
        assertThat(parseOwnedAlbumIds(JSONObject("""{"code":200,"total":0}"""))).isEmpty()
    }

    // ------------------------------------------------------------- 已收藏专辑

    @Test
    fun subscribedAlbums_readsDataArray() {
        val root = JSONObject("""{"code":200,"data":[{"id":1},{"id":2,"name":"x"}]}""")
        assertThat(parseSubscribedAlbumIds(root)).containsExactly("1", "2")
    }

    /** 未登录时这条回 301 且没有 data —— 一律当「没拉到」，绝不置位。 */
    @Test
    fun subscribedAlbums_unloggedInIsFailure() {
        assertThat(parseSubscribedAlbumIds(JSONObject("""{"code":301}"""))).isNull()
        assertThat(parseSubscribedAlbumIds(JSONObject("""{"code":200}"""))).isNull()
    }

    // ------------------------------------------------------------------ 红心

    @Test
    fun likedIds_readsPlainIdArray() {
        assertThat(parseLikedIds(JSONObject("""{"ids":[1,2,3],"code":200}""")))
            .containsExactly("1", "2", "3")
    }

    @Test
    fun likedIds_nonOkOrMissingIsFailure() {
        assertThat(parseLikedIds(JSONObject("""{"code":301,"ids":[1]}"""))).isNull()
        assertThat(parseLikedIds(JSONObject("""{"code":200}"""))).isNull()
    }

    @Test
    fun likedIds_skipsZero() {
        assertThat(parseLikedIds(JSONObject("""{"ids":[0,5],"code":200}"""))).containsExactly("5")
    }
}
