// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/** 一条评论。 */
data class Comment(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val content: String,
    val timeMs: Long,
    val likedCount: Long,
    val liked: Boolean,
    val location: String,
    val repliedNickname: String,
    val repliedContent: String,
)

/**
 * 评论页：首页含热门评论（hot）；latest 为按时间倒序的评论（翻页累积）。
 */
data class CommentPage(
    val hot: List<Comment>,
    val hotHasMore: Boolean,
    val latest: List<Comment>,
    val total: Long,
    val hasMore: Boolean,
)
