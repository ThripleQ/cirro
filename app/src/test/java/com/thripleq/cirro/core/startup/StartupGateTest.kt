package com.thripleq.cirro.core.startup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 开屏放行判据的测试。
 *
 * 锁的是两条**口径**，不是实现细节：
 * 1. 「App 就绪」是三条信号**全到齐**，少一条都不放行 —— 少放行一次的表现是开屏退场后
 *    页面又自己变一下（缺 `Session` 长出「每日推荐」、缺 `Playback` 弹出底部迷你条），
 *    这正是开屏存在的理由，判据被悄悄砍成一条时**没有任何编译错误**会提醒；
 * 2. 演出名额**一个进程只发一个** —— 用 `savedInstanceState == null` 判冷启动会在
 *    「Activity 被系统回收后重建」时漏判（Compose 没有 View 状态要存，Bundle 可能是 null），
 *    于是「没重启却演了一遍开屏」。
 *
 * `StartupGate` 是纯 Kotlin（不碰 `SystemClock`/`Context`），所以这里不需要 Robolectric。
 */
class StartupGateTest {

    /** 一条信号都没到 → 不放行。开屏全靠这条撑着「缓冲」的意义。 */
    @Test
    fun nothingSignalled_isNotReady() {
        assertThat(StartupGate().ready.value).isFalse()
    }

    /** 少一条就不放行：逐条试，别让「凑够两条也算」溜进来。 */
    @Test
    fun partialSignals_areNotReady() {
        StartupGate.Signal.values().forEach { missing ->
            val gate = StartupGate()
            StartupGate.Signal.values().filter { it != missing }.forEach(gate::signal)
            assertThat(gate.ready.value).isFalse()
            assertThat(gate.pendingSignals).containsExactly(missing)
        }
    }

    /** 三条全到齐才放行。 */
    @Test
    fun allSignals_isReady() {
        val gate = StartupGate()
        StartupGate.Signal.values().forEach(gate::signal)
        assertThat(gate.ready.value).isTrue()
        assertThat(gate.pendingSignals).isEmpty()
    }

    /** 同一条信号重复上报不扣分（首页逐块发布，[StartupGate.Signal.Content] 会被报很多次）。 */
    @Test
    fun repeatedSignal_isIdempotent() {
        val gate = StartupGate()
        repeat(6) { gate.signal(StartupGate.Signal.Content) }
        gate.signal(StartupGate.Signal.Session)
        gate.signal(StartupGate.Signal.Playback)
        assertThat(gate.ready.value).isTrue()
    }

    /** 顺序无关：谁先到都行（首页那几块本来就是并发回填的）。 */
    @Test
    fun orderDoesNotMatter() {
        val gate = StartupGate()
        gate.signal(StartupGate.Signal.Playback)
        gate.signal(StartupGate.Signal.Content)
        assertThat(gate.ready.value).isFalse()
        gate.signal(StartupGate.Signal.Session)
        assertThat(gate.ready.value).isTrue()
    }

    /** 演出名额：一个 gate（= 一个进程）只发一次，后面全是 false。 */
    @Test
    fun acquire_isOncePerProcess() {
        val gate = StartupGate()
        assertThat(gate.acquire()).isTrue()
        assertThat(gate.acquire()).isFalse()
        assertThat(gate.acquire()).isFalse()
    }

    /**
     * 名额是**冷启动**的同义词：页面重建（旋转 / 切主题 / 回前台）拿不到第二个名额，
     * 而它们恰恰是「不该再演一遍」的场合。
     */
    @Test
    fun acquire_isNotRefilledAfterReady() {
        val gate = StartupGate()
        gate.acquire()
        StartupGate.Signal.values().forEach(gate::signal)
        assertThat(gate.acquire()).isFalse()
    }
}
