package com.thripleq.cirro.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the shipped UI through its hot paths so the release build can emit a
 * Baseline Profile (AOT-compiled methods at install time). The dominant jank
 * source here is list scrolling over Compose + image loading, so we open a chart
 * list and scroll it both ways.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "com.thripleq.cirro") {
            pressHome()
            startActivityAndWait()

            // Home is remote-data driven; wait for the chart card then open it.
            device.wait(Until.hasObject(By.text("飙升榜")), 15_000)
            device.findObject(By.text("飙升榜"))?.click() ?: device.click(216, 1416)

            // Let the list settle, then exercise compose/layout/draw of rows.
            device.waitForIdle()
            repeat(4) {
                device.swipe(540, 1850, 540, 850, 24)
                device.swipe(540, 850, 540, 1850, 24)
            }
        }
    }
}
