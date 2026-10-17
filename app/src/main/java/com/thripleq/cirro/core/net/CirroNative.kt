package com.thripleq.cirro.core.net

/**
 * JNI declarations for the libnetease bridge (bound in libnetease_jni.c).
 *
 * [request] runs a service call on the current thread, blocking while the
 * injected transport performs the HTTP round trip. The request layer is
 * process-global and single-threaded, so callers MUST serialize every [request]
 * (see NetEaseGateway).
 */
object CirroNative {
    init {
        System.loadLibrary("cirro_jni")
    }

    /** Points libnetease's cookie jar at a file and reloads it. */
    external fun setCookieFile(path: String)

    /** Overrides the API base URL (defaults to https://music.163.com). */
    external fun setApiBase(base: String)

    /**
     * Merges a browser-exported cookie string ("MUSIC_U=…; __csrf=…; …") into the
     * jar and persists it, so a later process start reloads the login state.
     */
    external fun importCookies(cookieStr: String)

    /**
     * 丢掉 jar 里**全部** cookie 并按 cookie 文件重建 —— 登出用。
     *
     * 调用方必须先删掉那个文件：`ne_jar_reload()` 把「文件不存在」当作空 jar（并重新播一个
     * sDeviceId），于是内存里那份 MUSIC_U 也随之消失。jar 对宿主只暴露 merge，没有
     * 「移除某一个」—— 而登出要的是**全部清掉**，留一个都还是登录态。
     */
    external fun reloadCookies()

    /** Dispatches [op] (one of [NeteaseOp]) with [args]; returns the raw result. */
    external fun request(op: Int, args: Array<out String>): ApiResult
}