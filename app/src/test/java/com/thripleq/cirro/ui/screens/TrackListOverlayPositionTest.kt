package com.thripleq.cirro.ui.screens

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 覆盖层位置的锁：无穷 / NaN 绝不能进 `graphicsLayer.translationY`，
 * 页面整体位移（壳动画）也不能让按钮相对壳子跑偏。
 */
class TrackListOverlayPositionTest {

    @Test
    fun firstFrame_sentinelIsNotFedIntoMatrix() {
        assertThat(playAllOverlayOffsetY(Float.POSITIVE_INFINITY, 120f)).isEqualTo(0f)
        assertThat(playAllOverlayOffsetY(Float.NaN, 120f)).isEqualTo(0f)
    }

    @Test
    fun dockedAtTop_staysAtShellTop() {
        assertThat(playAllOverlayOffsetY(120f, 120f)).isEqualTo(0f)
    }

    @Test
    fun belowHeader_followsStickyHeaderLayoutY() {
        assertThat(playAllOverlayOffsetY(420f, 120f)).isEqualTo(300f)
    }

    @Test
    fun shellMovesWithPageAnimation_relativeOffsetIsUnchanged() {
        val docked = playAllOverlayOffsetY(120f, 120f)
        val dockedAfterShellShift = playAllOverlayOffsetY(180f, 180f)
        assertThat(dockedAfterShellShift).isEqualTo(docked)

        val below = playAllOverlayOffsetY(420f, 120f)
        val belowAfterShellShift = playAllOverlayOffsetY(480f, 180f)
        assertThat(belowAfterShellShift).isEqualTo(below)
    }
}
