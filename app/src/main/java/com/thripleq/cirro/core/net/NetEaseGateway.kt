package com.thripleq.cirro.core.net

import android.util.Log
import com.thripleq.cirro.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Facade over the libnetease request kernel.
 *
 * The C request layer guards its global cookie jar with an internal mutex
 * (see netease/request.h), so calls no longer need to be funneled through a
 * single serializing lock. Each [call] still hops to [io] to keep the native
 * call off the main thread, and concurrent callers now run in parallel.
 */
class NetEaseGateway(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Points the cookie jar at file and remembers it for future sessions. */
    fun configure(cookieFile: File) {
        CirroNative.setCookieFile(cookieFile.absolutePath)
    }

    /** Merges a browser-exported cookie string into the jar and persists it. */
    fun importCookies(cookieStr: String) {
        CirroNative.importCookies(cookieStr)
    }

    /** Runs one libnetease service call, blocking on [io] while it round-trips. */
    suspend fun call(op: Int, vararg args: String): ApiResult =
        withContext(io) { callBlocking(op, *args) }

    /**
     * Blocking variant for callers that are already on a background thread and
     * cannot suspend — notably [androidx.media3.datasource.ResolvingDataSource]'s
     * resolver, which runs on ExoPlayer's loading thread and must return a URL
     * synchronously. Do NOT call from the main thread.
     */
    fun callBlocking(op: Int, vararg args: String): ApiResult {
        val r = CirroNative.request(op, args)
        // args 可能是搜索词/用户 ID，body 是接口原文 —— 仅 debug 打印，避免
        // release 泄露隐私并省掉每次请求的字符串拼接开销。
        if (BuildConfig.DEBUG) {
            Log.d("NetEaseGateway", "op=$op args=${args.joinToString(",")} -> code=${r.code} err=${r.err} body=${String(r.body, Charsets.UTF_8).take(200)}")
        }
        return r
    }
}