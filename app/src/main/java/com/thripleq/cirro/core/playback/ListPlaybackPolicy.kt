package com.thripleq.cirro.core.playback

import com.thripleq.cirro.core.model.Track
import kotlin.random.Random

/**
 * 曲目列表页两枚「整单播放」按钮各自要的顺序。
 *
 * 两枚按钮共用 [PlaybackLauncher.playList] 这一条路，差别只在**没开全局随机时**队列怎么排。
 */
enum class ListPlaybackKind {
    /** 「播放全部」：严格按列表**眼前这份顺序**（含用户选的排序）。 */
    IN_ORDER,

    /** 「随机播放」：把眼前这份顺序**打乱一次**，作为队列顺序（随机播一次，不改全局开关）。 */
    SHUFFLED,
}

/**
 * 一次整单播放的决定。
 *
 * @property tracks 实际交给 `setMediaItems` 的那一份顺序。
 * @property startIndex 从 [tracks] 的第几首开播（0 = 队头）。
 * @property shuffleOrder 非 null 表示**全局随机开着**：这是播放器该采用的随机队列，
 *   数组按「播放顺序 → [tracks] 里的下标」排列，是一个 0..lastIndex 的排列。
 *   [PlaybackLauncher] 会把它装成 Media3 的 `DefaultShuffleOrder` 交给播放器。
 *   null = 播放器没开随机，队列顺序自己就是播放顺序，不用动播放器的随机序。
 */
data class ListPlaybackPlan(
    val tracks: List<Track>,
    val startIndex: Int,
    val shuffleOrder: IntArray?,
)

/**
 * 把「点了哪枚按钮 + 播放器当前什么模式」算成一份 [ListPlaybackPlan]。
 *
 * ## 随机是全局的，按钮不许接管
 *
 * `shuffleModeEnabled` 是播放器**全局**属性，切队列不会重置它 —— 这是设计，不是 bug。
 * 2026-11-07 用户明确纠正过：不要为了「播放全部」把它关掉，那等于替用户改播放设置。
 * 上一版实现（入队前 `shuffleModeEnabled = false`）在这条上做错了，已废弃。
 *
 * ## 随机开着时：从随机队列的第一首起步
 *
 * 用户要的是「点播放全部不该从列表第一首开始，而是从**打乱过的随机队列**的第一首开始」。
 * Media3 在 shuffle 开着时，`setMediaItems(items, startIndex, 0)` 里的 startIndex 仍是
 * **原始下标**；旧实现固定传 0，于是第一首永远是列表第一首，之后才按随机走 —— 这就是
 * 违和感的来源。
 *
 * 这里改为自己生成一份随机队列 [ListPlaybackPlan.shuffleOrder]，把
 * [ListPlaybackPlan.startIndex] 定成它的第 0 项，再由 [PlaybackLauncher] 通过
 * `setShuffleOrder` 把**同一份**随机队列交给播放器。于是：
 *
 * - 第一首 = 随机队列头一首（不再是列表头一首）；
 * - 之后的每一首都按这份随机队列走，全局随机照常生效；
 * - 队列本身保持原序（播放页 `getMediaItemAt` 读到的就是列表顺序），**只有一份**随机序，
 *   不存在「预先打乱的队列又被播放器随机一次」的双重随机。
 *
 * 两枚按钮此时合流：全局已经随机了，「随机播放」再自己打乱一遍只会叠出第二份随机序，
 * 所以同样只是重新生成一份随机队列、从它的头一首开始 —— 每次点都是一次新的洗牌。
 *
 * ## 随机没开时：按钮自己决定队列顺序
 *
 * - [ListPlaybackKind.IN_ORDER]：原序、`startIndex = 0`；
 * - [ListPlaybackKind.SHUFFLED]：打乱一次、`startIndex = 0`。这枚按钮是「随机播一次」，
 *   **不打开**全局 shuffle（那也是替用户改设置），播完这一单模式仍在列表循环。
 *
 * 两种情况下 [ListPlaybackPlan.shuffleOrder] 都是 null，播放器随机模式一个字节都不动。
 *
 * ## 为什么抽成纯函数
 *
 * 判定不依赖 Android / ExoPlayer，可以单测锁住语义（见 `ListPlaybackPolicyTest`）。
 * [random] 可注入，好让「洗牌恰好一次」「起始下标 == 随机队列头一首」可复现。
 */
internal fun planListPlayback(
    tracks: List<Track>,
    kind: ListPlaybackKind,
    shuffleModeEnabled: Boolean,
    random: Random = Random,
): ListPlaybackPlan {
    if (shuffleModeEnabled) {
        // 全局随机开着：不碰开关、不预先打乱队列，只给出唯一一份随机队列和它的头一首。
        val order = tracks.indices.toList().shuffled(random).toIntArray()
        return ListPlaybackPlan(
            tracks = tracks,
            startIndex = if (order.isEmpty()) 0 else order[0],
            shuffleOrder = order,
        )
    }
    return ListPlaybackPlan(
        tracks = when (kind) {
            ListPlaybackKind.IN_ORDER -> tracks
            ListPlaybackKind.SHUFFLED -> tracks.shuffled(random)
        },
        startIndex = 0,
        shuffleOrder = null,
    )
}
