// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** 搜索结果里的一个歌单（cloudsearch type=1000）。 */
data class SearchPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val creator: String,
    val trackCount: Long,
    val playCount: Long,
)

/** 搜索结果里的一个专辑（cloudsearch type=10）。 */
data class SearchAlbum(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val artist: String,
    val size: Long,
    val publishTime: Long,
)

/** 搜索结果里的一个歌手（cloudsearch type=100）。 */
data class SearchArtist(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val albumSize: Long,
    val musicSize: Long,
)

/** 搜索结果里的一个播客/电台（cloudsearch type=1009）。 */
data class SearchRadio(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val djName: String,
    val programCount: Long,
    val playCount: Long,
)
