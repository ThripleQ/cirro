package com.thripleq.cirro.core.repo

import android.webkit.CookieManager
import com.thripleq.cirro.core.net.CookieStore
import com.thripleq.cirro.core.net.NetEaseGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录态的**唯一入口**：导入 cookie（WebView 登录）与登出。
 *
 * ## 为什么要有这一层
 * 这两件事原先散在两个地方：导入在 `ProfileViewModel` 里**直接调 `NetEaseGateway`**（全项目
 * 唯一的 `ui/` → `core/net` 反向依赖 —— 其余 `ui/` 只碰 repo），登出则压根没有。把「登录态
 * 怎么进、怎么出」收在一处，UI 就不需要知道 cookie 存在哪、jar 是进程内单例这类细节。
 *
 * ## 登出为什么要清**三处** cookie
 * 登录态在三个地方各存了一份，少清任何一处都退不干净：
 * 1. **cookie 文件**（`netease_cookies.json`）—— C 层下次冷启动的登录态来源；
 * 2. **C 层内存里的 jar** —— `ne_set_cookie_file` 只改路径不重建，jar 是进程内只创建一次
 *    的全局对象，所以删完文件必须再 `reloadCookies()` 让它重建（文件不存在 = 空 jar），
 *    否则内存里那份 MUSIC_U 还在，下一个请求照样是登录态；
 * 3. **WebView 自己的 cookie 商店** —— 登录是走 `WebLoginScreen` 里官方登录页的，官方页
 *    只认 WebView 商店里的 MUSIC_U。这份商店**跨进程重启持久化**（`app_webview/Default/
 *    Cookies`，SQLite），只清前两处的话，再进登录页会被官方页直接认成已登录，秒登回旧号
 *    —— 用户连「选账号 / 填登录表单」都看不到（2026-10-23 用户实测反馈的正是这个）。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val cookies: CookieStore,
) {

    /** 合入一份浏览器导出的 cookie 串并落盘（下次冷启动据此恢复登录态）。 */
    fun importCookies(cookieStr: String) {
        gateway.importCookies(cookieStr)
    }

    /**
     * 登出：清掉三处 cookie（文件 / C 层 jar / WebView 商店）。
     * **只管 C 层那一半** —— 账号缓存、收藏/已购镜像
     * 由调用方一并失效（见 `ProfileViewModel.logout`），它们不是登录态，不该由这里代劳。
     */
    fun clearCookies() {
        cookies.delete()
        gateway.reloadCookies()
        // WebView 商店：removeAllCookies 是异步的（内部走后台线程），这里不接回调 ——
        // 登出不阻塞等待它；WebLoginScreen 加载前还会兜底再清一次（见那里的注释）。
        CookieManager.getInstance().also {
            it.removeAllCookies(null)
            it.flush()
        }
    }
}
