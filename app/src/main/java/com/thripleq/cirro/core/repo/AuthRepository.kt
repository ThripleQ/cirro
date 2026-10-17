package com.thripleq.cirro.core.repo

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
 * ## 登出为什么是「删文件 + 重建 jar」两步
 * jar 是 C 层**进程内只创建一次**的全局对象：`ne_set_cookie_file` 只改路径不重建，
 * 所以删文件只是让**下次冷启动**读不到，内存里那份 MUSIC_U 还在 —— 下一个请求照样是登录态。
 * 必须先删文件、再让 jar 重建（文件不存在 = 空 jar），两步缺一都不算登出。
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
     * 登出：清掉 cookie 文件并重建 jar。**只管 C 层那一半** —— 账号缓存、收藏/已购镜像
     * 由调用方一并失效（见 `ProfileViewModel.logout`），它们不是登录态，不该由这里代劳。
     */
    fun clearCookies() {
        cookies.delete()
        gateway.reloadCookies()
    }
}
