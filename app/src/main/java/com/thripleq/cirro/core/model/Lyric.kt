// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/**
 * 一行歌词。[translation] 是同一时间戳的翻译（tlyric），无翻译为 null。
 * [timeMs] 用作自动滚动/高亮的锚点，也是点击跳转的定位。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/** 一首歌的歌词（可能为空 = 纯音乐/未收录）。 */
data class Lyrics(val lines: List<LyricLine>) {
    val isEmpty: Boolean get() = lines.isEmpty()
}
