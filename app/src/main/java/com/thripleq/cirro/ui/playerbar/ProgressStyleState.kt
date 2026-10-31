package com.thripleq.cirro.ui.playerbar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thripleq.cirro.core.playback.PlaybackPreferences
import com.thripleq.cirro.core.playback.ProgressBarStyle

/**
 * 进度条样式的进程内共享状态：设置页写、播放页读，改完立刻互见。
 *
 * 用 Compose 状态而不是每次读 SharedPreferences：播放页每 250ms 重组合一次，
 * 读盘虽被系统缓存，但没必要；而且暂停时没有轮询，只有状态变化才能触发重组。
 * 首次访问才从偏好里初始化。
 */
internal object ProgressStyleState {

    private var loaded = false
    private var style by mutableStateOf(ProgressBarStyle.Capsule)

    fun current(context: Context): ProgressBarStyle {
        if (!loaded) {
            style = PlaybackPreferences.progressBarStyle(context)
            loaded = true
        }
        return style
    }

    fun set(context: Context, value: ProgressBarStyle) {
        style = value
        loaded = true
        PlaybackPreferences.setProgressBarStyle(context, value)
    }
}
