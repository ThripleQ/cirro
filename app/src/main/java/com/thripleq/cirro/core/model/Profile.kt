// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

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
