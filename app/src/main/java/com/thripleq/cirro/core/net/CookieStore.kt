package com.thripleq.cirro.core.net

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * libnetease 那份 cookie 文件的**唯一定义处**。
 *
 * 两处要用它，而且必须拿到**同一个路径**，否则登出就登不干净：
 * - `di/AppModule` 启动时把它交给 C 层（`ne_set_cookie_file`）—— 决定登录态从哪 reload；
 * - [com.thripleq.cirro.core.repo.AuthRepository] 登出时删它。
 *
 * 抽出来的理由不是"少写一行 `File(filesDir, …)`"，而是**两处写死同一串文件名迟早会分叉**
 * （一边改成 .txt 另一边没跟上 ⇒ 删了个不存在的文件，登出后还是登录态，而且这种 bug 只在
 * 换手机/清数据后才看得出来）。
 */
@Singleton
class CookieStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    val file: File = File(context.filesDir, FILE_NAME)

    /**
     * 删掉 cookie 文件。
     *
     * **删完必须跟着调一次 `NetEaseGateway.reloadCookies()`**：jar 是进程内**只创建一次**
     * 的（`ne_set_cookie_file` 只改路径、不重建），光删文件的话内存里那份 MUSIC_U 还在，
     * 下一个请求照样带着登录态出去。
     */
    fun delete() {
        runCatching { file.delete() }
    }

    companion object {
        private const val FILE_NAME = "netease_cookies.json"
    }
}
