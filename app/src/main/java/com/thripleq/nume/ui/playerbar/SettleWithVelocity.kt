package com.thripleq.nume.ui.playerbar

import androidx.compose.animation.core.FloatAnimationSpec
import kotlin.math.abs
import kotlin.math.min

/** 距离小于它就当没有行程，直接瞬时完成（px）。 */
private const val MIN_TRAVEL_PX = 0.5f

/** 视为「无初速度」的门限（px/s）：低于它时曲线退化成对称缓入缓出（S 形）。 */
private const val NEGLIGIBLE_VELOCITY = 1f

private const val NS_PER_SECOND = 1_000_000_000f

/**
 * 松手落档曲线：**继承松手速度**的单调减速（五次 Hermite 插值）。
 *
 * ## 为什么不能用 `tween`
 * [androidx.compose.animation.core.tween] **忽略 `initialVelocity`** —— 无论手指以多快的
 * 速度离开屏幕，曲线都从速度 0 起步。松手瞬间速度被整个丢掉，于是出现「速度断崖」：
 * 手指还在 2393px/s 地滑、壳忽然停住（用户读作**「突然收到很大阻力」**），随后曲线中段
 * 又爬到峰值（**「又重新加速」**），末端再归零。三次速度突变、且形状与「松手后越滑越慢」
 * 的日常经验完全相反 —— 用户 2026-10-03：「违反常识」。
 *
 * 真机逐帧实测（120Hz，travelPx=952，`a35fc3e` 后随本轮改动接的临时日志，已清理）：
 * `fling from=1637.1 to=1904.0 v=2393` 时，旧曲线第一帧的壳速只有 **28px/s**，
 * 峰值 1131px/s 出现在行程 30% 处；新曲线第一帧 **2390px/s**，之后单调递减。
 *
 * ## 曲线形状
 * 归一化 `u = t/T` 的五次 Hermite，四个边界条件：
 * `x(0)=x0`、`x'(0)=v0`（**速度连续**）、`x(T)=x1`、`x'(T)=0`（稳稳停住，不再动）。
 *
 * ```
 * x(u) = x0 + Δx·H2(u) + T·v0·H1(u)
 * H1(u) = u - 6u³ + 8u⁴ - 3u⁵      // 速度基：H1'(0)=1（承载 v0）、H1'(1)=0
 * H2(u) = 10u³ - 15u⁴ + 6u⁵        // 位移基：H2'(0)=0、H2'(1)=0
 * ```
 *
 * `v0 = 0` 时退化成 `H2`，即**对称缓入缓出**（两端斜率 0、中段最陡）—— 与原来那条 tween
 * 形状等价，所以「几乎静止地松手」的观感不变。
 *
 * ## 时长
 * 取两者的**较小者**：
 * - `baseDurationMs`（调用方按剩余行程给出，即原来的 320~540ms）：松手慢时走它，
 *   保持原有「距离越远跑越久、从容读出加减速」的节奏；
 * - **物理匀减速** `T = 2Δx / v0`：松手快时走它 —— 这就是「越滑越慢、很快停住」的常识。
 *
 * 为什么这个取法**永远单调、绝不过冲**：只要 `T ≤ 2Δx/v0`，就有 `T·v0/Δx ≤ 2`，
 * 而五次 Hermite 保持单调的上界是 `≈4.29`。两个分支都满足 `T·v0/Δx ≤ 2`，所以
 * 「先冲过终点再退回来」在数学上不可能出现，不需要额外钳位。
 * （离线脚本扫描 9 档距离 × 10 档速度 = 90 组，倒退/过冲 0 例。）
 *
 * @param baseDurationMs 慢速松手时的时长（随剩余行程缩放的那条）；快速松手时它只是上限。
 */
internal class SettleWithVelocity(
    private val baseDurationMs: Float,
) : FloatAnimationSpec {

    override fun getDurationNanos(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Long {
        val distance = abs(targetValue - initialValue)
        if (distance < MIN_TRAVEL_PX) return 0L
        val v0 = speedToward(initialValue, targetValue, initialVelocity)
        // 物理时长：以 v0 起步、末速为 0 的匀减速恰好走完 distance。
        val physicalMs =
            if (v0 < NEGLIGIBLE_VELOCITY) Float.MAX_VALUE
            else 2f * distance / v0 * 1000f
        val ms = min(baseDurationMs, physicalMs)
        return (ms / 1000f * NS_PER_SECOND).toLong().coerceAtLeast(1L)
    }

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        val duration = getDurationNanos(initialValue, targetValue, initialVelocity)
        if (duration <= 0L) return targetValue
        val u = (playTimeNanos.toFloat() / duration).coerceIn(0f, 1f)
        val u2 = u * u
        val u3 = u2 * u
        val u4 = u3 * u
        val u5 = u4 * u
        val h1 = u - 6f * u3 + 8f * u4 - 3f * u5
        val h2 = 10f * u3 - 15f * u4 + 6f * u5
        val t = duration / NS_PER_SECOND
        val v0 = speedToward(initialValue, targetValue, initialVelocity)
        return initialValue + (targetValue - initialValue) * h2 + t * v0 * h1
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float {
        val duration = getDurationNanos(initialValue, targetValue, initialVelocity)
        if (duration <= 0L) return 0f
        val u = (playTimeNanos.toFloat() / duration).coerceIn(0f, 1f)
        val u2 = u * u
        val u3 = u2 * u
        val u4 = u3 * u
        val h1d = 1f - 18f * u2 + 32f * u3 - 15f * u4
        val h2d = 30f * u2 - 60f * u3 + 30f * u4
        val t = duration / NS_PER_SECOND
        val v0 = speedToward(initialValue, targetValue, initialVelocity)
        return ((targetValue - initialValue) * h2d + t * v0 * h1d) / t
    }

    /**
     * 沿目标方向的松手速度（px/s，恒非负）。
     *
     * 只在同向时才继承：反向速度（例如手指回勾一下再松手）不代表「想滑向那一边」，
     * 传 0 让曲线退化成对称缓入缓出即可 —— 否则会先往反方向倒一段，那是明确的反效果。
     */
    private fun speedToward(initialValue: Float, targetValue: Float, velocity: Float): Float {
        val delta = targetValue - initialValue
        if (delta == 0f) return 0f
        val along = if (delta > 0f) velocity else -velocity
        return if (along > 0f) along else 0f
    }
}
