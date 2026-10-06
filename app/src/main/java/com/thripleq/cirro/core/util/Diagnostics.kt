package com.thripleq.cirro.core.util

import android.util.Log
import com.thripleq.cirro.BuildConfig
import javax.inject.Inject

/**
 * 诊断日志出口。
 *
 * 抽出来的理由是硬性的，不是洁癖：**android.jar 里的 [Log] 是空壳**，方法体一律
 * `throw` —— 单测里碰一下就 "not mocked"。所以只要一个类直接调 [Log]，这个类
 * 在 JVM 上就永远测不了，`CollectionRefresher` 之前正是这样。
 *
 * 生产实现里用 `Log.e` 而不是 `Log.d`：vivo 屏蔽 `Log.d`（实测），真机排障靠 `Log.e`。
 */
fun interface Diagnostics {
    fun log(tag: String, msg: String)
}

/** 生产实现：只在 debug 打，走 `Log.e`（vivo 上 `Log.d` 被屏蔽）。 */
class AndroidDiagnostics @Inject constructor() : Diagnostics {
    override fun log(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.e(tag, msg)
    }
}
