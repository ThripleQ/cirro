package com.thripleq.cirro.core.playback

import android.content.Context

/** 播放相关用户偏好（SharedPreferences）。UI 与 [PlaybackUrls] 共用同一份。 */
object PlaybackPreferences {

    private const val FILE = "playback"
    private const val KEY_QUALITY = "quality"
    private const val KEY_PROGRESS_STYLE = "progress_style"
    private const val KEY_ALLOW_CONCURRENT_PLAYBACK = "allow_concurrent_playback"
    private const val KEY_SCROBBLE_ENABLED = "scrobble_enabled"

    /** 音质档位，从低到高；`exhigh`(320k) 为默认。 */
    val QUALITIES = listOf("standard", "higher", "exhigh", "lossless")

    const val DEFAULT_QUALITY = "exhigh"

    fun quality(context: Context): String =
        prefs(context).getString(KEY_QUALITY, DEFAULT_QUALITY)
            ?.takeIf { it in QUALITIES } ?: DEFAULT_QUALITY

    fun setQuality(context: Context, value: String) {
        prefs(context).edit().putString(KEY_QUALITY, value).apply()
    }

    /** 播放页进度条样式；默认胶囊（0.3.x 的观感）。 */
    fun progressBarStyle(context: Context): ProgressBarStyle =
        ProgressBarStyle.fromKey(prefs(context).getString(KEY_PROGRESS_STYLE, null))

    fun setProgressBarStyle(context: Context, style: ProgressBarStyle) {
        prefs(context).edit().putString(KEY_PROGRESS_STYLE, style.key).apply()
    }

    /**
     * 「允许与其他应用同时播放音频」。默认关 = 独占音频焦点（老行为）。
     * 只存偏好的**值**，怎么作用到播放器由 [AudioFocusPolicy] 与 `PlayerHolder` 负责。
     */
    fun allowConcurrentPlayback(context: Context): Boolean =
        prefs(context).getBoolean(
            KEY_ALLOW_CONCURRENT_PLAYBACK,
            AudioFocusPolicy.DEFAULT_ALLOW_CONCURRENT_PLAYBACK,
        )

    fun setAllowConcurrentPlayback(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_CONCURRENT_PLAYBACK, value).apply()
    }

    /**
     * 「上报播放记录到网易云」。**默认关**：开启后每首歌累计听够一半（最长 4 分钟）
     * 就向网易云写一条听歌记录 / 排行 —— 这是写账号数据的操作，且第三方上报有
     * 账号风控风险（见设置页的风险确认与 libnetease docs/RISKS.md）。
     *
     * 只存偏好的**值**；何时、怎么上报由 [ScrobbleReporter] 与 [PlayerHolder] 负责。
     */
    fun scrobbleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCROBBLE_ENABLED, false)

    fun setScrobbleEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCROBBLE_ENABLED, value).apply()
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
