// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** 歌手主页的头部信息（weapi/v1/artist/{id} 的 artist 对象）。 */
data class ArtistProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val briefDesc: String,
    val albumSize: Long,
    val musicSize: Long,
    val aliases: List<String>,
    val followed: Boolean,
)

/** 歌手专辑（weapi/artist/albums/{id} 的 hotAlbums 条目）。 */
data class ArtistAlbum(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val artist: String,
    val size: Long,
    val publishTime: Long,
)

/** 歌手主页首屏：资料 + 热门 50 首。 */
data class ArtistPage(
    val profile: ArtistProfile,
    val hotSongs: List<Track>,
)
