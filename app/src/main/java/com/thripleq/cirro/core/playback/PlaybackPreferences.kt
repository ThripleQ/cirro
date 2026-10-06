package com.thripleq.cirro.core.playback

import android.content.Context

/** 播放相关用户偏好（SharedPreferences）。UI 与 [PlaybackUrls] 共用同一份。 */
object PlaybackPreferences {

    private const val FILE = "playback"
    private const val KEY_QUALITY = "quality"

    /** 音质档位，从低到高；`exhigh`(320k) 为默认。 */
    val QUALITIES = listOf("standard", "higher", "exhigh", "lossless")

    const val DEFAULT_QUALITY = "exhigh"

    fun quality(context: Context): String =
        prefs(context).getString(KEY_QUALITY, DEFAULT_QUALITY)
            ?.takeIf { it in QUALITIES } ?: DEFAULT_QUALITY

    fun setQuality(context: Context, value: String) {
        prefs(context).edit().putString(KEY_QUALITY, value).apply()
    }

    /** 选定的音质档取不到时，逐级向下回退，最终兜底 standard。 */
    fun qualityFallbackOrder(quality: String): List<String> = when (quality) {
        "lossless" -> listOf("lossless", "exhigh", "standard")
        "exhigh" -> listOf("exhigh", "standard")
        "higher" -> listOf("higher", "standard")
        else -> listOf("standard")
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
