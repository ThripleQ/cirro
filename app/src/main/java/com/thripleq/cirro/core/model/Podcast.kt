// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** 播客/电台详情（weapi/djradio/get 的 djRadio 对象）。 */
data class RadioDetail(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val desc: String,
    val djName: String,
    val djAvatarUrl: String?,
    val programCount: Long,
    val playCount: Long,
    val subCount: Long,
    val category: String,
)

/**
 * 电台的一期节目（weapi/dj/program/byradio 的 programs 条目）。
 *
 * [songId] 是 mainSong.id —— 播放走歌曲通道；[coverUrl] 优先用节目自身
 * coverUrl，缺失时退回 mainSong.album.picUrl。
 */
data class Program(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val durationMs: Long,
    val songId: String?,
    val artistName: String,
    val listenerCount: Long,
    val createTime: Long,
    val commentThreadId: String,
) {
    /** 转成可播放 [Track]（无 mainSong.id 的节目不可播，返回 null）。 */
    fun toTrack(): Track? = songId?.let {
        Track(
            id = it,
            name = name,
            artist = artistName,
            artworkUrl = coverUrl,
            durationMs = durationMs,
        )
    }
}
