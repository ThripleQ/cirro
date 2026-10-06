package com.thripleq.cirro.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **曲目列表滚动的帧时间基准** —— 把「卡不卡」从主观观感变成可比对的数字。
 *
 * ## 为什么先要这个
 * 本项目性能优化的历史教训是：动画与列表的每处看似冗余（250ms/500ms 轮询兜底、
 * 骨架→列表的不透明底板、hero 铺底）都是真机录屏逐帧验证后**刻意留下**的。
 * 没有测量就改性能，最容易把「为消闪黑付出的代价」当浪费删掉，闪黑立刻回归。
 * 所以本文件只提供读数，不改产品代码。
 *
 * ## 关注哪两个数
 * - `frameDurationCpuMs`：单帧 CPU 耗时。**p95 > 16ms**（60Hz）即说明主线程在丢帧。
 * - `frameOverrunMs`：超出 vsync 配额的部分。**p95 > 0** 就是掉帧，比均值更能暴露尖峰。
 *   均值好看但 p99 很差 ⇒ 典型的「偶发卡顿」，正是滚动时那种一下一下的顿挫。
 *
 * ## 怎么跑
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.thripleq.cirro.baselineprofile.ScrollFrameBenchmark
 * ```
 * 需连真机/模拟器（建议真机 + 开发者选项关掉「动画时长缩放」以外的干预）。
 *
 * ## 与 Baseline Profile 的关系
 * 默认 [CompilationMode] 由 rule 决定（含已安装的 baseline profile），即**贴近线上真实表现**。
 * 若要专门复现 debug 下的卡顿差异，给 [MacrobenchmarkRule] 显式传
 * `compilationMode = CompilationMode.DEBUG`，同一份代码跑两遍即可量化 AOT 的收益。
 *
 * 复用 [BaselineProfileGenerator] 已验证可用的入口与滑动坐标，避免另造一条不稳定的路径。
 */
@RunWith(AndroidJUnit4::class)
class ScrollFrameBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * 曲目列表来回滚动时的帧耗时。列表 = LazyColumn + 每行 AsyncImage 封面，
     * 是本 App 最重的主线程负载（也是 BaselineProfileGenerator 注释里点名的
     * 「dominant jank source」）。
     */
    @Test
    fun scrollTrackList() {
        rule.measureRepeated(
            packageName = APP_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                // 首页是远端数据驱动：等榜单卡出现再进列表；拿不到就按已知坐标兜底
                // （与 BaselineProfileGenerator 同一策略，避免 CI 上因网络抖动直接失败）。
                device.wait(Until.hasObject(By.text(CHART_ENTRY)), ENTRY_TIMEOUT_MS)
                device.findObject(By.text(CHART_ENTRY))?.click() ?: device.click(216, 1416)
                device.waitForIdle()
            },
            measureBlock = {
                // 下滚 + 回滚成对进行：单向只测到「新行组合」，回滚才测到
                // 回收复用与图片内存命中这条路径，两者都要看。
                repeat(REPEAT_SWIPES) {
                    device.swipe(SWIPE_X, SWIPE_DOWN_FROM, SWIPE_X, SWIPE_DOWN_TO, SWIPE_STEPS)
                    device.swipe(SWIPE_X, SWIPE_DOWN_TO, SWIPE_X, SWIPE_DOWN_FROM, SWIPE_STEPS)
                }
            },
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.thripleq.cirro"
        const val CHART_ENTRY = "飙升榜"
        const val ENTRY_TIMEOUT_MS = 15_000L
        const val REPEAT_SWIPES = 4
        const val ITERATIONS = 5
        const val SWIPE_X = 540
        const val SWIPE_DOWN_FROM = 1850
        const val SWIPE_DOWN_TO = 850
        const val SWIPE_STEPS = 24
    }
}
