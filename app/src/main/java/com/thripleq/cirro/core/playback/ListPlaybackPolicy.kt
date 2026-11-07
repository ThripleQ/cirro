package com.thripleq.cirro.core.playback

import com.thripleq.cirro.core.model.Track
import kotlin.random.Random

/**
 * 曲目列表页两枚「整单播放」按钮各自要的顺序。
 *
 * 两枚按钮共用 [PlaybackLauncher.playList] 这一条路，差别只在队列怎么排。
 */
enum class ListPlaybackKind {
    /** 「播放全部」：严格按列表**眼前这份顺序**（含用户选的排序）。 */
    IN_ORDER,

    /** 「随机播放」：把眼前这份顺序**打乱一次**，作为队列顺序。 */
    SHUFFLED,
}

/**
 * 一次整单播放的决定：入队用的曲目顺序 + 要不要先关掉播放器的随机模式。
 *
 * [tracks] 就是实际交给 `setMediaItems` 的那一份，播放页队列看到的与听到的是同一个顺序。
 */
data class ListPlaybackPlan(
    val tracks: List<Track>,
    /** 为 true 时 [PlaybackLauncher.playList] 会在入队前把 `shuffleModeEnabled` 置 false。 */
    val clearShuffle: Boolean,
)

/**
 * 把「点了哪枚按钮 + 播放器当前什么模式」算成一份 [ListPlaybackPlan]。
 *
 * ## 为什么随机模式开着就一定要关
 *
 * `shuffleModeEnabled` 是播放器**全局**属性，`setMediaItems` 换队列不会重置它。它开着时，
 * 真正播放顺序由 Media3 自己的 ShuffleOrder 决定，传入的 [tracks] 只是「未随机序」：
 *
 * - 点「播放全部」→ 名义从第 1 首开始，之后却按随机序走，与「按眼前这份清单播整单」相反；
 * - 点「随机播放」→ 队列已经被 [ListPlaybackKind.SHUFFLED] 打乱一次，播放器再随机一次，
 *   成了双重随机；而且播放页队列画的顺序与耳朵听到的顺序对不上。
 *
 * 于是两枚按钮都在入队前关掉随机，让**队列顺序成为唯一真相**：顺序按钮给原序，随机按钮
 * 给打乱一次的那份。这正是 2026-11-07 用户要的「理顺随机与顺序」。
 *
 * ## 关掉的是当前播放模式，界面会如实反映
 *
 * 播放顺序是三档轮换（列表循环 → 单曲循环 → 随机，见 [PlayerHolder.cyclePlayOrder]），
 * 随机态固定 `repeatMode = REPEAT_MODE_ALL`，所以只把 shuffle 关掉正好落到「列表循环」，
 * 整单播完从头绕圈，符合「播完整张列表」。播放页那枚模式按钮读的就是
 * `player.shuffleModeEnabled`（`PlayerState.kt`），会如实显示成列表循环，不会嘴上说随机、
 * 实际按顺序；想再随机，点一下模式按钮即可。这个状态会随队列一起落盘
 * （`PlaybackStateStore.shuffle`），不是一次性的临时绕开 —— 点整单播放本就是一次明确的
 * 「按这份清单播」指令，接管当前模式是它的语义。
 *
 * 旧实现刻意「不碰用户的 shuffle 开关」，代价就是上面那两种失序；这条约定在此被取代，
 * 别再照旧注释把随机模式留着。
 *
 * ## 为什么抽成纯函数
 *
 * 判定不依赖 Android / ExoPlayer，可以单测锁住语义（见 `ListPlaybackPolicyTest`）。
 * [random] 可注入，好让「只打乱一次」的断言可复现。
 */
internal fun planListPlayback(
    tracks: List<Track>,
    kind: ListPlaybackKind,
    shuffleModeEnabled: Boolean,
    random: Random = Random,
): ListPlaybackPlan = ListPlaybackPlan(
    tracks = when (kind) {
        ListPlaybackKind.IN_ORDER -> tracks
        ListPlaybackKind.SHUFFLED -> tracks.shuffled(random)
    },
    // 原本就没开随机 ⇒ 什么也不用动；开了才需要接管。
    clearShuffle = shuffleModeEnabled,
)
