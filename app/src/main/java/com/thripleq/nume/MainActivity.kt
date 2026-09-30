package com.thripleq.nume

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
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.JankStats
import com.thripleq.nume.ui.theme.NumeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var jankStats: JankStats? = null

    // Android 13+ 通知需要运行时授权；不请求的话前台播放服务的媒体通知不会显示
    // （权限默认 denied，通知被系统静默丢弃）。
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞播放 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): keeps the system splash up until the
        // first Compose frame, then hands off to Theme.Nume (postSplashScreenTheme).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 系统栏完全透明，底色交给应用自己画（dock 的 surfaceContainer / 页面 surface 会
        // 一直铺到导航栏后面）。默认的 edge-to-edge 会给导航栏蒙一层半透明 scrim —— 三键
        // 导航下就是那条和界面不同色的"灰条"；Play 商店那类做法是让应用背景直接透上来。
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 关掉系统给导航栏加的对比度蒙层（部分 OEM 仍会在透明底色上再压一层）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        installJankStats()
        requestNotificationPermissionIfNeeded()
        setContent {
            // 禁用 Material You 动态取色: 在某些设备/壁纸下 dynamicDarkColorScheme
            // 派生的 onBackground/onSurface 偏深, 导致未指定 color 的 Text 在深色主题
            // 下显示成接近背景的深色, 看不见. 用我们验证过的 DarkColorScheme.
            NumeTheme(dynamicColor = false) {
                NumeApp()
            }
        }
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
