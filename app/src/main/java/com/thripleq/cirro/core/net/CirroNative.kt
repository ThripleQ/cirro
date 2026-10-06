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

    /** Dispatches [op] (one of [NeteaseOp]) with [args]; returns the raw result. */
    external fun request(op: Int, args: Array<out String>): ApiResult
}