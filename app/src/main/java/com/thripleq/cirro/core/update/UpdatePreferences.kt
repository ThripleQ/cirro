package com.thripleq.cirro.core.update

import android.content.Context

/**
 * 自更新的少量持久化状态（SharedPreferences）。
 *
 * 只存两件与「打扰频率」有关的事：
 * - **上次成功检查的时间**：启动静默检查按 24h 节流。只记录**成功**的检查 ——
 *   网络失败（国内直连 GitHub 很常见）不该把用户按在「24 小时内不再试」里。
 * - **已经提示过/去过设置的版本**：同一版本只弹一次启动提示，设置页里的「可更新」
 *   状态不受影响。
 *
 * 下载进度、已下载文件都不在这里：进程重启后重新下载一次（文件在 cacheDir，系统
 * 清缓存本来就允许），省掉一套「文件还在不在/是不是上次那个包」的对账。
 */
internal class UpdatePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastSuccessAt(): Long = prefs.getLong(KEY_LAST_SUCCESS, 0L)

    fun markChecked(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
    }

    /** 这个版本是否已经被提示过（用户点了「以后再说」或「去设置」）。 */
    fun isPromptDismissed(versionName: String): Boolean =
        prefs.getString(KEY_DISMISSED, null) == versionName

    fun dismissPrompt(versionName: String) {
        prefs.edit().putString(KEY_DISMISSED, versionName).apply()
    }

    private companion object {
        const val FILE = "update"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_DISMISSED = "dismissed_version"
    }
}
