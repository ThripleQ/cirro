// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** A recommended playlist card (personalized/playlist, served anonymously). */
data class PlaylistCard(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val trackCount: Long,
)

/** 一位相似歌手（`/api/discovery/simiArtist` 的一项）。 */
data class SimiArtist(
    val id: String,
    val name: String,
    val picUrl: String?,
)

/**
 * 首页 block 流一次请求的解析结果（见 [HomeRepository.homePage]）。
 *
 * [guessPages] 是**分页**的：每一页 3 首，对应 kanade 主页那一栏「一屏 3 行、
 * 左右翻页」的结构（`showType = HOMEPAGE_SLIDE_SONGLIST_ALIGN`）。不要把它拍平 ——
 * 拍平就退化成一条纵向长列表，与 kanade 不像。
 */
data class HomePageBlocks(
    val radar: List<PlaylistCard> = emptyList(),
    /**
     * 雷达 block 的服务端栏目标题。**必须用服务端给的，不要写死「雷达歌单」**：
     * 同一个 block 在未登录时给「网易云音乐的雷达歌单」、登录后给「<昵称>的雷达歌单」
     * （2026-10-05 实测两态返回），写死就少了一截信息。
     */
    val radarTitle: String? = null,
    /** 「猜你喜欢的「XX」好歌」的完整标题（风格词由服务端给）。 */
    val guessTitle: String? = null,
    val guessPages: List<List<Track>> = emptyList(),
    /**
     * 「猜你喜欢」第一首歌的**首位歌手 id** —— 顺路从 block 里带出来，给「相似艺人」
     * 功能卡当种子（拿相似歌手头像做卡面）。
     *
     * 这一层 data class 里塞一个歌手 id 看着越界，但它省掉一次 `/song/detail`：
     * block 的 `resourceExtInfo.artists[0].id` 本来就在手上（实测 18122 这种真值），
     * 调 simiArtist 前没必要再回去查一遍歌曲详情。null = block 没给或未登录。
     */
    val seedArtistId: String? = null,
) {
    /** 拍平后的全部单曲（播放入口用）。 */
    val guessSongs: List<Track> get() = guessPages.flatten()
}

/** 「发现」页一个圆形入口。 */
data class DragonBallEntry(
    val name: String,
    val iconUrl: String?,
    val targetType: String,
    val targetId: String,
)

/** 一个曲风标签（`/api/tag/list/get`）。 */
data class StyleTag(val id: String, val name: String)
