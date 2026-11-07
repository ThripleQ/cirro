package com.thripleq.cirro.core.playback

import com.google.common.truth.Truth.assertThat
import com.thripleq.cirro.core.model.Track
import kotlin.random.Random
import org.junit.Test

/**
 * 「播放全部 / 随机播放」两枚按钮的顺序语义锁（2026-11-07 用户纠正后的口径）：
 *   1. **全局随机开着**：两枚按钮都不碰 `shuffleModeEnabled`、都不预先打乱队列；
 *      改由播放器持有唯一一份随机队列，起点 = 该随机队列头一首（不是列表头一首）；
 *   2. **全局随机没开**：「播放全部」给原序、「随机播放」打乱恰好一次，都从队头开始；
 *   3. 任何情况下曲目不增不减、随机队列是合法排列。
 *
 * 「不碰开关」在纯判定层的体现就是 [ListPlaybackPlan.shuffleOrder] 非 null —— 它要求
 * [PlaybackLauncher] 把这份随机序交给**已经开着随机的**播放器，而不是返回「关随机」的指令。
 */
class ListPlaybackPolicyTest {

    private val tracks = listOf(
        Track("1", "一", "甲", null, 1000L),
        Track("2", "二", "乙", null, 2000L),
        Track("3", "三", "丙", null, 3000L),
        Track("4", "四", "丁", null, 4000L),
        Track("5", "五", "戊", null, 5000L),
    )

    @Test
    fun playAll_withShuffleOn_startsAtFirstOfRandomQueue() {
        val plan = planListPlayback(
            tracks,
            ListPlaybackKind.IN_ORDER,
            shuffleModeEnabled = true,
            random = Random(42),
        )

        val order = tracks.indices.toList().shuffled(Random(42))
        assertThat(plan.shuffleOrder?.toList()).isEqualTo(order)
        assertThat(plan.startIndex).isEqualTo(order[0])
        // 队列仍是列表眼前顺序：随机序只存在于播放器那一份，不叠第二份打乱。
        assertThat(plan.tracks).isEqualTo(tracks)
    }

    @Test
    fun playAll_withShuffleOn_randomQueueIsAPermutation() {
        val plan = planListPlayback(tracks, ListPlaybackKind.IN_ORDER, shuffleModeEnabled = true)

        assertThat(plan.shuffleOrder?.toList()).containsExactlyElementsIn(tracks.indices.toList())
        assertThat(plan.startIndex).isEqualTo(plan.shuffleOrder!![0])
        assertThat(plan.startIndex).isAtLeast(0)
        assertThat(plan.startIndex).isAtMost(tracks.lastIndex)
    }

    @Test
    fun shuffleAll_withShuffleOn_doesNotShuffleTwice() {
        // 全局已经随机，这枚按钮只重新洗一份随机队列，不把列表本身再打乱一遍。
        val plan = planListPlayback(
            tracks,
            ListPlaybackKind.SHUFFLED,
            shuffleModeEnabled = true,
            random = Random(9),
        )

        val order = tracks.indices.toList().shuffled(Random(9))
        assertThat(plan.shuffleOrder?.toList()).isEqualTo(order)
        assertThat(plan.startIndex).isEqualTo(order[0])
        assertThat(plan.tracks).isEqualTo(tracks)
    }

    @Test
    fun playAll_withShuffleOff_startsAtHeadInVisibleOrder() {
        val plan = planListPlayback(tracks, ListPlaybackKind.IN_ORDER, shuffleModeEnabled = false)

        assertThat(plan.shuffleOrder?.toList()).isNull()
        assertThat(plan.startIndex).isEqualTo(0)
        assertThat(plan.tracks).isEqualTo(tracks)
    }

    @Test
    fun shuffleAll_withShuffleOff_shufflesOnceWithoutTouchingMode() {
        val plan = planListPlayback(
            tracks,
            ListPlaybackKind.SHUFFLED,
            shuffleModeEnabled = false,
            random = Random(7),
        )

        assertThat(plan.shuffleOrder?.toList()).isNull() // 没开全局随机 ⇒ 不改播放器的随机序
        assertThat(plan.startIndex).isEqualTo(0)
        assertThat(plan.tracks).isEqualTo(tracks.shuffled(Random(7)))
        assertThat(plan.tracks).containsExactlyElementsIn(tracks)
    }

    @Test
    fun emptyQueue_isHarmless() {
        val withShuffle = planListPlayback(
            emptyList(), ListPlaybackKind.IN_ORDER, shuffleModeEnabled = true,
        )
        val withoutShuffle = planListPlayback(
            emptyList(), ListPlaybackKind.SHUFFLED, shuffleModeEnabled = false,
        )

        assertThat(withShuffle.tracks).isEmpty()
        assertThat(withShuffle.startIndex).isEqualTo(0)
        assertThat(withShuffle.shuffleOrder?.toList()).isEmpty()
        assertThat(withoutShuffle.tracks).isEmpty()
        assertThat(withoutShuffle.startIndex).isEqualTo(0)
        assertThat(withoutShuffle.shuffleOrder?.toList()).isNull()
    }
}
