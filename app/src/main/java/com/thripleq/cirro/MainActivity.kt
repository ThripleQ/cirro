package com.thripleq.cirro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.JankStats
import com.thripleq.cirro.core.startup.StartupGate
import com.thripleq.cirro.ui.splash.SplashOverlay
import com.thripleq.cirro.ui.theme.CirroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 开屏退场的判据（三条就绪信号）与演出名额（见 [StartupGate]）。 */
    @Inject lateinit var startupGate: StartupGate

    private var jankStats: JankStats? = null

    /**
     * 开屏只在**进程冷启动**演一次：旋转屏幕、切深色模式、从后台回来、从通知栏另开一个
     * Activity 都是同一个进程里的事，再演一遍只会让人以为 App 又重启了一次。
     *
     * 判据是 [StartupGate.acquire] 的一次性名额（名额与进程同生共死），**不是**
     * `savedInstanceState == null` —— 后者在「Activity 被系统回收后重建」时也可能是 null
     * （Compose 没有 View 层状态要存），那样就会在没重启的情况下白演一遍。
     */
    private var splashVisible by mutableStateOf(false)

    // Android 13+ 通知需要运行时授权；不请求的话前台播放服务的媒体通知不会显示
    // （权限默认 denied，通知被系统静默丢弃）。
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): keeps the system splash up until the
        // first Compose frame, then hands off to Theme.Cirro (postSplashScreenTheme).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // savedInstanceState != null 一定不是冷启动（Activity 是从回收里恢复的），短路掉 ——
        // 别在这种时候把名额白白用掉。
        splashVisible = savedInstanceState == null && startupGate.acquire()
        // 系统栏完全透明，底色交给应用自己画（dock 的 surfaceContainer / 页面 surface 会
        // 一直铺到导航栏后面）。默认的 edge-to-edge 会给导航栏蒙一层半透明 scrim —— 三键
        // 导航下就是那条和界面不同色的"灰条"；Play 商店那类做法是让应用背景直接透上来。
        // 开屏期间例外：整屏是品牌红，图标必须反白（见 [applySystemBars]）。
        applySystemBars(forSplash = splashVisible)
        // 关掉系统给导航栏加的对比度蒙层（部分 OEM 仍会在透明底色上再压一层）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        installJankStats()
        requestNotificationPermissionIfNeeded()
        setContent {
            // 不走 Material You 动态取色（壁纸取色）: 那会用壁纸色整套顶掉 Palette.kt,
            // 品牌红消失; 且部分机型/壁纸下 dynamicDarkColorScheme 派生的 onSurface 偏深,
            // 深色下未指定 color 的 Text 会看不见. 改用我们逐对验证过对比度的调色板.
            // 想跟随壁纸就把这里改成 dynamicColor = true.
            CirroTheme(dynamicColor = false) {
                // 开屏**盖在**内容之上、而不是替代内容：它压着的那几百毫秒里首页照常
                // 组合、照常取数（这就是「准备缓冲」），退场时下面已经是有内容的页，
                // 不会退场后先闪一屏骨架。
                Box(Modifier.fillMaxSize()) {
                    CirroApp()
                    if (splashVisible) {
                        SplashOverlay(startupGate) {
                            splashVisible = false
                            applySystemBars(forSplash = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * 系统栏：常规情况下应用自己画底色（透明 + 图标色跟随主题）；**开屏期间**整屏是品牌
     * 红，图标必须反白 —— 否则浅色主题下的深色图标压在红底上几乎看不见。
     *
     * 开屏退场后立刻还给默认（[forSplash] = false），别把它留在「反白」那一档。
     */
    private fun applySystemBars(forSplash: Boolean) {
        val style = if (forSplash) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** Android 13+ 首次进入时请求通知权限（用于前台播放的媒体通知）。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Frame-jank telemetry via JankStats. In debug it logs every janky frame to
     * logcat; the listener is the single hook to forward to a backend in release.
     * Tracking is enabled only while the activity is resumed to avoid foreground
     * work when the UI is not visible.
     */
    private fun installJankStats() {
        val stats = JankStats.createAndTrack(window) { frame ->
            if (BuildConfig.DEBUG && frame.isJank) {
                Log.w("JankStats", "jank ${frame.frameDurationUiNanos / 1_000_000}ms")
            }
        }
        jankStats = stats
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                stats.isTrackingEnabled = true
            }

            override fun onPause(owner: LifecycleOwner) {
                stats.isTrackingEnabled = false
            }
        })
    }
}
