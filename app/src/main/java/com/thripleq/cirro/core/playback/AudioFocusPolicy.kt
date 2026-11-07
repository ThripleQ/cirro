package com.thripleq.cirro.core.playback

/**
 * 「允许与其他应用同时播放」→ ExoPlayer 是否接管音频焦点，**唯一**映射处。
 *
 * Media3 的 `handleAudioFocus = true` 会申请 `AUDIOFOCUS_GAIN`：别的 App 一出声
 * Cirro 就被暂停/压低，反过来 Cirro 也会让别人停下来。不接管焦点（`false`）
 * 时两路声音才能并存，代价是别的 App、来电、导航播报都不会让 Cirro 自动暂停。
 *
 * 抽成纯函数由单测锁定：这里写反，设置页的开关就会变成「反的」。
 */
object AudioFocusPolicy {

    /** 默认保持老行为：独占音频焦点，不与其他应用混音。 */
    const val DEFAULT_ALLOW_CONCURRENT_PLAYBACK = false

    fun handlesAudioFocus(allowConcurrentPlayback: Boolean): Boolean =
        !allowConcurrentPlayback
}
