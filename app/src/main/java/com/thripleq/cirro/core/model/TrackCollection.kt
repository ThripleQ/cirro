// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/**
 * 一个"壳子 + 列表"：榜单、歌单、专辑都是同一个结构——一段集合元数据
 * （封面/标题/播放量/收藏数/更新频率/描述/创建者）+ 曲目列表。喜欢/已购
 * 没有独立后端壳，由 ViewModel 用已有数据组装一份简化壳。
 *
 * [subscribed] 只对**歌单/榜单**有真值：`/weapi/v6/playlist/detail` 的 playlist
 * 对象自带这个布尔，所以收藏按钮的初始态跟着壳走，不需要额外请求。
 * 专辑没有对应字段（探针实测），专辑的收藏态由
 * [LibraryStateStore.subscribedAlbumIds] 提供；喜欢/已购/每日推荐这类本地
 * 组装的壳恒为 false（它们本来就不支持收藏）。
 */
data class TrackCollection(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Long,
    val updateFrequency: String,
    val description: String,
    val creator: String,
    val tracks: List<Track>,
    val subscribed: Boolean = false,
    /**
     * [playlistFingerprint] 算出的曲目指纹，空串 = 不知道（专辑、本地组装的壳、v1 迁移来的旧行）。
     *
     * 存在的理由是「判断要不要重新拉」：进页面时发一次轻量检查（`n=0`）拿新指纹，
     * 与这份比 —— 相同就说明曲目表还能用，不必拉全量。见
     * [com.thripleq.cirro.core.repo.CollectionRefresher]。
     */
    val fingerprint: String = "",
)
