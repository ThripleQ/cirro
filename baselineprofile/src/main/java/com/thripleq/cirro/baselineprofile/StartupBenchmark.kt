package com.thripleq.cirro.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **冷启动耗时基准** —— 把启动拆成可归因的三段读数，而不是一个笼统的「慢」。
 *
 * 关注 [StartupTimingMetric] 的三个指标：
 * - `timeToInitialDisplayMs`：进程起来到首帧（**splash / ContentProvider / Application.onCreate**
 *   的开销全在这里，本项目 `CirroApplication.onCreate` 会建 Coil ImageLoader + 磁盘缓存，
 *   JNI 库 `System.loadLibrary` 也算在内）。
 * - `timeToFullDisplayMs`：到报告「绘制完成」的时刻（**首页远端数据到位**会推后它，
 *   所以这个数大不等于启动代码慢，要先看它和 initial 的差值落在哪）。
 *
 * ## 怎么跑
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.thripleq.cirro.baselineprofile.StartupBenchmark
 * ```
 *
 * ## 归因建议
 * 想知道 baseline profile 到底赚了多少，把同一份测试跑两遍：一次默认（含已安装的 profile），
 * 一次 [CompilationMode] 显式换成 `CompilationMode.NONE`（纯解释/JIT）。差值就是 AOT 的收益，
 * 也直接告诉你值不值得继续投入启动优化。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** 冷启动：每次迭代都会先杀进程，测的是真实首启路径（含 Coil/JNI 初始化）。 */
    @Test
    fun coldStartup() {
        rule.measureRepeated(
            packageName = APP_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            measureBlock = {
                // 只做启动：测量由 metrics 抓取，这里不额外触发交互，
                // 免得把列表组合的耗时混进启动数里。
                pressHome()
                startActivityAndWait()
            },
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.thripleq.cirro"
        const val ITERATIONS = 5
    }
}
