// 领域模型：**接口返回的东西的形状**。不带 Android / Compose / Dagger 依赖，也不做网络；
// 取数与判断留在 `core/repo` —— 那边的文件只该回答「怎么取、怎么判」。
// 控制流类型（ActionResult / RequestFailedException）不属于这里，留在产生它们的 repo。

package com.thripleq.cirro.core.model

/**
 * 逐字歌词里的一个片段。[timeMs] 是起唱时刻（绝对毫秒），[durationMs] 是它自己的
 * 时长；0 时长的是标点这类不发声片段。中文/日文通常**一字一段**，英文**整词一段**
 * （词间空格挂在前一段的 [text] 末尾）。
 *
 * 时间与 [LyricLine.timeMs] 同一基准；[text] 原样保留空格 —— 一行所有片段的
 * [text] 直接首尾相接就是该行文本。
 */
data class LyricWord(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
)

/**
 * 一行歌词。[translation] 是同一时间戳的翻译（tlyric），无翻译为 null。
 * [timeMs] 用作自动滚动/高亮的锚点，也是点击跳转的定位。
 *
 * [words] 非空表示这首歌有逐字时间轴（网易云 yrc）：行文本由 [words] 拼接而来，
 * 渲染时可逐字点亮；为空则退回整行高亮（普通 LRC）。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList(),
)

/** 一首歌的歌词（可能为空 = 纯音乐/未收录）。 */
data class Lyrics(val lines: List<LyricLine>) {
    val isEmpty: Boolean get() = lines.isEmpty()
}
