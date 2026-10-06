package com.thripleq.nume.core.util

import javax.inject.Inject

/**
 * 墙上时钟。
 *
 * 抽出来的理由只有一个：**冷却逻辑要能被断言**。「30 秒内不发请求」这种判据如果
 * 直接读 [System.currentTimeMillis]，JVM 单测就只能靠 `sleep`（慢且脆）或者干脆
 * 不测 —— 而它恰恰是「用户不喜欢频繁重拉」这条要求的落点，不测就 regressions 无声。
 *
 * 注意 [com.thripleq.nume.core.util.RefreshGate] 用的是**单调**时钟
 * （`SystemClock.elapsedRealtime`，不受改系统时间影响），这里给的是墙上时钟，
 * 两者不要混用。
 */
fun interface Clock {
    fun nowMs(): Long
}

/** 生产实现：墙上时钟。按进程存活计时，够用于「距上次打网络多久了」。 */
class WallClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
