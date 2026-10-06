package com.thripleq.nume.core.repo

import org.json.JSONObject

/**
 * 「我买过 / 收藏过 / 点过红心」的解析：JSON → id 集合，全是纯函数。
 *
 * ## 为什么单独成一个文件
 *
 * 与 `HomeParsers.kt` 同理：这些判据原先是 `LibraryStateStore` 的 private 成员，
 * 而那个类带 `Log`（诊断）与 `NetEaseGateway`（网络），JVM 单测里碰不得。抽出来之后
 * 这份文件**零 Android 依赖**，可以脱离设备断言。
 *
 * ## 这些 id 是「镜像」的一部分
 *
 * 购买 / 收藏 / 红心都没有便宜的按需查询，所以是「拉一次全量 + 本地查表」
 * （见 `LibraryStateStore`）。**解析错一个 id，后果是静默的**：明明买过的歌被标成
 * 红色「需要购买」，或者红心不亮 —— 不崩、不报错，只有用户自己知道不对。
 *
 * ## 返回值的约定
 *
 * `null` 表示「**没拉到**」，与「拉到了但为空」（`emptySet()`）是两件事：
 * 前者不能被固化成"没有"，否则一次网络抖动就让镜像永远错下去。
 */
internal fun parseOwnedSongIds(root: JSONObject): Set<String>? {
    if (root.optInt("code", 200) != 200) return null
    val list = root.optJSONObject("data")?.optJSONArray("list")
        ?: root.optJSONArray("data")
        // data 整个没有 ⇒ 真的没有已购（不是失败）
        ?: return emptySet()
    return buildSet {
        for (i in 0 until list.length()) {
            val id = list.optJSONObject(i)?.optLong("songId", 0L) ?: 0L
            if (id > 0) add(id.toString())
        }
    }
}

/**
 * 已购数字专辑 `{"total":N,"paidAlbums":[{albumId,...}]}`。
 *
 * 整张购买 ⇒ 「这首歌属于一张已购专辑」就等于「这首歌我买了」，这是 `Track.albumId`
 * 存在的唯一理由（`LibraryStateStore.isOwned` 靠它做归属判定）。
 */
internal fun parseOwnedAlbumIds(root: JSONObject): Set<String>? {
    if (root.optInt("code", 200) != 200) return null
    val list = root.optJSONArray("paidAlbums")
        ?: root.optJSONObject("data")?.optJSONArray("list")
        ?: root.optJSONArray("data")
        ?: return emptySet()
    return buildSet {
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val id = o.optLong("albumId", o.optLong("id", 0L))
            if (id > 0) add(id.toString())
        }
    }
}

/**
 * 已收藏专辑 `/weapi/album/sublist` 的 `{"data":[<专辑对象>...]}`。
 *
 * 未登录时这条回 **301** 且没有 `data` —— 一律当成「没拉到」（null），
 * 不能当成「收藏了 0 张」，否则登录后第一次看到的收藏态是错的。
 */
internal fun parseSubscribedAlbumIds(root: JSONObject): Set<String>? {
    if (root.has("code") && root.optInt("code", 200) != 200) return null
    val arr = root.optJSONArray("data") ?: return null
    return buildSet {
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optLong("id", 0L) ?: 0L
            if (id > 0) add(id.toString())
        }
    }
}

/**
 * 红心 `/weapi/song/like/get` 的 `{"ids":[...],"code":200}`。
 *
 * 实测 115 首的响应只有 1KB 出头 —— 一次拉全量、本地查表，比每首歌单独查一次便宜得多。
 */
internal fun parseLikedIds(root: JSONObject): Set<String>? {
    if (root.optInt("code", 200) != 200) return null
    val arr = root.optJSONArray("ids") ?: return null
    return buildSet {
        for (i in 0 until arr.length()) {
            val id = arr.optLong(i, 0L)
            if (id > 0) add(id.toString())
        }
    }
}
