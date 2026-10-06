package com.thripleq.cirro.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thripleq.cirro.ui.profile.ProfileViewModel

/**
 * 网页登录：用 WebView 打开网易云官方登录页（Kanade 同款方案）。
 *
 * 用户在官方页面完成登录（手机号+短信 / 扫码 / 密码，含官方人机验证），
 * 登录成功跳转到 y.music.163.com/m 后，自动抓取官方签发的 cookie 合并进
 * libnetease cookie jar——完全绕开逆向登录 API 的验证码/云盾风控。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: ProfileViewModel,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val finished = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (error != null) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = false

                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (finished.value) return
                                    // 登录成功判定不依赖具体跳转 URL：直接检查 cookie 里是否
                                    // 已出现登录态 MUSIC_U（网易可能跳到 y.music.163.com 或
                                    // music.163.com 任一子域，URL 检测不可靠）。
                                    val host = url?.let { Uri.parse(it).host } ?: ""
                                    val cookie = CookieManager.getInstance()
                                        .getCookie("https://$host") ?: ""
                                    Log.d("WebLogin", "pageFinished url=$url host=$host cookieLen=${cookie.length} hasMUSIC_U=${cookie.contains("MUSIC_U=")}")
                                    if (cookie.contains("MUSIC_U=")) {
                                        finished.value = true
                                        vm.webLoginCookies(cookie) { ok, msg ->
                                            if (ok) {
                                                onDone()
                                            } else {
                                                error = msg.ifBlank { "登录态无效，请重试" }
                                                finished.value = false
                                            }
                                        }
                                    }
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            loadUrl("https://music.163.com/m/login")
                        }
                    },
                )
            }
        }
    }
}
