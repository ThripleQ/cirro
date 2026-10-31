package com.thripleq.cirro.core.playback

/**
 * 播放页进度条的视觉样式。`key` 落盘，只追加、不改旧值。
 *
 * 顺序即设置页展示顺序；默认 [Capsule] 与 0.3.x 的观感一致。
 * 样式只影响绘制，拖动/seek 仍走同一条 M3 Slider 手势。
 */
enum class ProgressBarStyle(val key: String) {
    Capsule("capsule"),
    Hairline("hairline"),
    Dot("dot"),
    Solid("solid"),
    TimeChip("time_chip"),
    Waveform("waveform"),
    ;

    companion object {
        fun fromKey(key: String?): ProgressBarStyle =
            entries.firstOrNull { it.key == key } ?: Capsule
    }
}
