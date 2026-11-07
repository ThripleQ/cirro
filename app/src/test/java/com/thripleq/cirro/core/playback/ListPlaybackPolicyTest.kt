package com.thripleq.cirro.core.playback

import com.google.common.truth.Truth.assertThat
import com.thripleq.cirro.core.model.Track
import kotlin.random.Random
import org.junit.Test

/**
 * 「播放全部 / 随机播放」两枚按钮的顺序语义锁：
 *   1. 眼前顺序不许被播放器随机模式改写（开着就必须关）；
 *   2. 随机播放只打乱一次 —— 队列交给播放器时随机模式已关，不会被再随机一遍；
 *   3. 两枚按钮都不增不减曲目。
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
    fun playAll_withShuffleOn_clearsShuffleAndKeepsVisibleOrder() {
        val plan = planListPlayback(tracks, ListPlaybackKind.IN_ORDER, shuffleModeEnabled = true)

        assertThat(plan.clearShuffle).isTrue()
        assertThat(plan.tracks).isEqualTo(tracks)
    }

    @Test
    fun playAll_withShuffleOff_leavesModeAlone() {
        val plan = planListPlayback(tracks, ListPlaybackKind.IN_ORDER, shuffleModeEnabled = false)

        assertThat(plan.clearShuffle).isFalse()
        assertThat(plan.tracks).isEqualTo(tracks)
    }

    @Test
    fun shuffleAll_withShuffleOn_clearsShuffleAndShufflesOnce() {
        val plan = planListPlayback(
            tracks,
            ListPlaybackKind.SHUFFLED,
            shuffleModeEnabled = true,
            random = Random(42),
        )

        assertThat(plan.clearShuffle).isTrue()
        // 打乱恰好一次：与同种子现算的一份逐项相等，不是「打乱两次」也不是原序。
        assertThat(plan.tracks).isEqualTo(tracks.shuffled(Random(42)))
        assertThat(plan.tracks).containsExactlyElementsIn(tracks)
    }

    @Test
    fun shuffleAll_withShuffleOff_shufflesWithoutTouchingMode() {
        val plan = planListPlayback(
            tracks,
            ListPlaybackKind.SHUFFLED,
            shuffleModeEnabled = false,
            random = Random(7),
        )

        assertThat(plan.clearShuffle).isFalse()
        assertThat(plan.tracks).isEqualTo(tracks.shuffled(Random(7)))
        assertThat(plan.tracks).containsExactlyElementsIn(tracks)
    }

    @Test
    fun emptyQueue_isHarmless() {
        val plan = planListPlayback(emptyList(), ListPlaybackKind.SHUFFLED, shuffleModeEnabled = true)

        assertThat(plan.tracks).isEmpty()
        assertThat(plan.clearShuffle).isTrue()
    }
}
