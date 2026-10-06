package com.thripleq.cirro.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 页面冷却门的测试。
 *
 * 它与 `CollectionRefresher` 的 30s 是**同一个取舍的两处落点**：一个管「重复可见」，
 * 一个管「重复取数」。这扇门坏掉的表现是静默的 —— 要么来回切 tab 变成来回打网络
 * （用户明确说过不喜欢），要么退回「挂在 init 上、一辈子只刷一次」（这就是此前
 * 「有数据的地方拿新数据不及时」的根因）。
 *
 * ⚠️ 调用必须**显式传 `nowMs`**：默认参数走 `SystemClock.elapsedRealtime()`，而
 * android.jar 里它是空壳，单测一碰就抛 "not mocked"。
 */
class RefreshGateTest {

    /** 从没取过数 → 一律放行（否则首屏连第一批数据都没有）。 */
    @Test
    fun firstVisit_isAllowed() {
        assertThat(RefreshGate().allow(0L)).isTrue()
    }

    /** 冷却内拦截；刚好到点放行 —— 边界用 `>=`，差 1ms 就差一次请求。 */
    @Test
    fun cooldown_blocksUntilElapsed() {
        val gate = RefreshGate(cooldownMs = 30_000L)
        gate.mark(1_000L)
        assertThat(gate.allow(1_000L + 29_999L)).isFalse()
        assertThat(gate.allow(1_000L + 30_000L)).isTrue()
    }

    /**
     * [RefreshGate.allow] **只看时间不改状态**：放行后不调 [RefreshGate.mark]，
     * 下一次可见仍会放行 —— 这是纪律，不是缺陷（放行与「真的取了」是两件事）。
     */
    @Test
    fun allow_doesNotConsume() {
        val gate = RefreshGate(cooldownMs = 30_000L)
        gate.mark(0L)
        repeat(3) { assertThat(gate.allow(60_000L)).isTrue() }
    }

    /** 登录态变化 / 用户明确要求刷新：[RefreshGate.reset] 让下一次必定放行。 */
    @Test
    fun reset_forcesNextAllow() {
        val gate = RefreshGate(cooldownMs = 30_000L)
        gate.mark(1_000L)
        assertThat(gate.allow(2_000L)).isFalse()
        gate.reset()
        assertThat(gate.allow(2_000L)).isTrue()
    }
}
