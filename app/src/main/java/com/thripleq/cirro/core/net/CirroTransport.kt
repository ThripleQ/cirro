package com.thripleq.cirro.core.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import android.util.Log
import com.thripleq.cirro.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * The C→Kotlin endpoint of the injected transport. libnetease calls this
 * (through the JNI shim in libnetease_jni.c) whenever it needs to send an HTTP
 * round trip; all real I/O happens here in OkHttp.
 *
 * gzip/zlib decoding and redirect following are handled by OkHttp the same way
 * libcurl did for the desktop build.
 */
object CirroTransport {

    @JvmStatic
    fun httpRequest(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        cookieHeader: String?,
        userAgent: String?,
        realIp: String?,
    ): CirroTransportOut {
        return RawHttp.send(method, url, body, contentType, cookieHeader, userAgent, realIp)
    }

    private object RawHttp {
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // 全调用硬顶：网易云偶发慢响应（风控梯度/网关抖动）会挂满 read 超时（30s），
            // 用户侧表现就是「某个内容等很久才就绪」。15s 顶格快速失败，交给上层
            // 错误态 + 重试闭环（各屏已建）——比无限期等一个可能永远不回的响应合理。
            .callTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC = method/url/status/timing; never bodies (cookies/credentials).
                    addInterceptor(
                        HttpLoggingInterceptor { Log.d("CirroHttp", it) }
                            .setLevel(HttpLoggingInterceptor.Level.BASIC),
                    )
                }
            }
            .build()

        private val formType = "application/x-www-form-urlencoded; charset=UTF-8"
            .toMediaType()

        fun send(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            cookieHeader: String?,
            userAgent: String?,
            realIp: String?,
        ): CirroTransportOut {
            val requestBody: RequestBody? =
                if (method == "POST" && body != null) {
                    (contentType?.toMediaType() ?: formType).let { body.toRequestBody(it) }
                } else {
                    null
                }

            val builder = Request.Builder()
                .url(url)
                .method(method, requestBody)

            if (!cookieHeader.isNullOrEmpty()) builder.header("Cookie", cookieHeader)
            if (!userAgent.isNullOrEmpty()) builder.header("User-Agent", userAgent)
            // libnetease's curl transport sets this unconditionally; mirror it.
            builder.header("Referer", "https://music.163.com")
            if (!realIp.isNullOrEmpty()) {
                builder.header("X-Real-IP", realIp)
                builder.header("X-Forwarded-For", realIp)
            }

            return try {
                // URL 含签名/查询参数，仅 debug 打印，避免 release 泄露。
                if (BuildConfig.DEBUG) Log.d("CirroTransport", "-> ${method} ${url}")
                client.newCall(builder.build()).execute().use { resp ->
                    val status = resp.code
                    val bytes = resp.body.bytes()
                    val cookies = resp.headers.values("Set-Cookie").takeIf { it.isNotEmpty() }
                    if (BuildConfig.DEBUG) Log.d("CirroTransport", "<- status=${status} bytes=${bytes.size}")
                    CirroTransportOut(status, null, bytes, cookies?.toTypedArray())
                }
            } catch (e: Exception) {
                Log.e("CirroTransport", "transport failed for ${url}: ${e}", e)
                CirroTransportOut(0, e.message ?: "transport error", ByteArray(0), null)
            }
        }
    }
}