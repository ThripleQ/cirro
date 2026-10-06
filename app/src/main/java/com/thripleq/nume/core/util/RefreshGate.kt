package com.thripleq.nume.core.util

import android.os.SystemClock

/**
 * 「页面重新可见时，还要不要真的去取一次新数据」的冷却门。
 *
 * ## 为什么需要它
 *
 * 页面的 ViewModel 挂在导航回退栈上（tab 用 `saveState`/`restoreState` 保留），
 * **切走再切回不会重建 VM** —— 于是「刷新」只要挂在 `init` 上，就注定一辈子只跑一次，
 * 数据停在第一次进页面时那份（这就是「有数据的地方拿新数据不及时」的结构性来源）。
 *
 * 但把刷新直接挂到「每次可见」上也不对：来回切 tab 是秒级动作，会变成来回打网络，
 * 而用户明确**不喜欢频繁的重新拉取**。
 *
 * 折中就是这扇门：可见时先问一句 [allow]，冷却期内的重复可见直接放掉（零请求），
 * 冷却之外才放行一次真取数。取值的推理与 `CollectionRefresher.COOLDOWN_MS` 同源 ——
 * 「反复切 tab」是秒级动作，「去别处（官方 App）改了数据再回来看」是几十秒级动作，
 * 30s 正好把前者全挡掉、又把后者的可见延迟压在半分钟内。
 *
 * ## 纪律
 *
 * - [allow] **只看时间、不改状态**；放行后必须自己调 [mark]，否则每次可见都会放行。
 * - 首次（从没取过）一律放行 —— 但它通常会被 `init` 里那次取数 `mark` 掉，
 *   所以「进页面时 UI 回调又补一枪」这种重复不会被发出来。
 * - 时钟用 [SystemClock.elapsedRealtime]（单调，不受用户改系统时间影响）。
 * - **不是线程安全的**，只在主线程（ViewModel）里用。
 */
class RefreshGate(private val cooldownMs: Long = DEFAULT_COOLDOWN_MS) {

    /** null = 还从没取过数。 */
    private var lastAtMs: Long? = null

    /** 距上次取数已超过冷却（或从没取过）时为 true。 */
    fun allow(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        lastAtMs?.let { nowMs - it >= cooldownMs } ?: true

    /** 记下「刚刚真的取过一次」。调用方在**发起取数之后**调它。 */
    fun mark(nowMs: Long = SystemClock.elapsedRealtime()) {
        lastAtMs = nowMs
    }

    /**
     * 让下一次 [allow] 必定放行（登录态变化、用户明确要求刷新时用）。
     * 清的是时间戳，不是「已加载」这个事实 —— 与页面数据无关。
     */
    fun reset() {
        lastAtMs = null
    }

    companion object {
        /** 见类注释：秒级的反复可见全挡掉，几十秒级的真变更仍能在半分钟内看到。 */
        const val DEFAULT_COOLDOWN_MS = 30_000L
    }
}
