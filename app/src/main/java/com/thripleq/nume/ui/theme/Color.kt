package com.thripleq.nume.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 手写语义色层 —— 调色板本身**不在这里**（那是 [Palette.kt] 机器生成的），
 * 这里只放「调色板表达不了」的一类颜色：**压在不受控内容（封面图）之上的墨色**，
 * 以及散落的半透明常量。
 *
 * ## 为什么 on-image 墨色刻意不走 colorScheme
 * 封面是不受控的位图，深浅主题色压上去都可能不可读。可读性由**图上的暗色渐变遮罩**
 * 保证，而不是由主题保证 —— 所以这套墨色在明暗两种主题下**都是同一组白色**，
 * 故意与 `colorScheme` 解耦。若把它们改成 `onSurface`，暗色主题没问题，
 * 浅色主题下 `onSurface` 是深字，压在中亮度封面上直接看不清。
 *
 * ## 为什么这些数字要从调用点收上来
 * 之前 `Color.White.copy(alpha = 0.88f)`、`Color.Black.copy(alpha = 0.38f)` 这类
 * 魔数散在组件里：同一个「图上次要文字」在两个组件里可能一个是 0.88 一个是 0.8，
 * 而且改不动。收成一个有名字的常量后，全局一处可调。
 *
 * 透明度 → ARGB 前缀换算：`round(a × 255)` 取十六进制（0.88→E0、0.28→47）。
 */
object NumeInk {
    /** 图上主文字 / 图标（收起按钮图标、封面名）。 */
    val OnImage = Color(0xFFFFFFFF)

    /** 图上次要文字（白 @88%，如封面上的曲目数）。原 `Color.White.copy(alpha=0.88f)`。 */
    val OnImageMuted = Color(0xE0FFFFFF)

    /** 图上内容属性水印（白 @28%）。原 `Color.White.copy(alpha=0.28f)`。 */
    val Watermark = Color(0x47FFFFFF)

    /**
     * 通用黑遮罩色基。用于「压暗不受控背景 / 浮层控件圆底」——
     * 与 [NumeFade.SHELL_SCRIM] / [NumeFade.CONTROL_SCRIM] / [NumeFade.IMAGE_SCRIM]
     * 组合出具体不透明度，避免在调用点裸写 `Color.Black`。
     */
    val Scrim = Color.Black
}

/** 需要「跟随主题色再乘透明度」的场景：只能给 alpha，不能给固化 Color。 */
object NumeFade {
    /** 封面底部文字托底渐变的最暗端。原 `BigCoverVisual.scrimAlpha` 默认值。 */
    const val IMAGE_SCRIM: Float = 0.66f

    /** 缺封面兜底块上的水印图标不透明度（压在 `secondaryContainer` 上）。 */
    const val WATERMARK_ON_CONTAINER: Float = 0.75f

    /** banner / hero 大封面承载多行文字时的渐变遮罩：起始位置与底部黑度。 */
    const val BANNER_SCRIM_TOP: Float = 0.35f
    const val BANNER_SCRIM: Float = 0.85f

    /**
     * 伸展壳展开时「壳以外」区域退暗的最大不透明度。
     * 原 `ExpandableShell` 私有常量 `SHELL_SCRIM_ALPHA`：提到这里是为了和
     * [CONTROL_SCRIM] 同族可调（两者都是「壳/浮层压暗背景建立 modal 焦点」的同一语义）。
     */
    const val SHELL_SCRIM: Float = 0.32f

    /** 浮层控件圆底（收起按钮）的黑底不透明度。 */
    const val CONTROL_SCRIM: Float = 0.38f

    /**
     * 歌单页面板上沿的投影：头部（封面糊底）与面板（不透明面）交界处压一条暗带。
     * 官方页面实测交界面两侧差 ~11/255，正是这条投影；没有它，上下两块面会读成一块。
     */
    const val SHEET_SHADOW: Float = 0.16f

    // ── 播放页「功能性控件」的弱化不透明度（原散落的裸 alpha）─────────────

    /** 顶部拉手 / 播放页次要图标的弱化。 */
    const val HANDLE: Float = 0.4f

    /** 进度条 / 滑块未填充轨道底的弱化。 */
    const val TRACK: Float = 0.12f

    // PROGRESS_SLOT 已删（2026-10-03）：它只服务于播放页「歌手下边那条 1dp 分割线」，
    // 用户要求删线，常量随之失去唯一用处。

    /** 无封面兜底里的音符图标弱化。 */
    const val ART_PLACEHOLDER: Float = 0.35f

    /** 行卡尾部箭头（chevron）的弱化。原 ProfileCards 裸写 0.5f。 */
    const val ARROW_MUTED: Float = 0.5f

    /** 彩色容器底（primaryContainer 等）上副文字的弱化。原裸写 0.72f。 */
    const val ON_CONTAINER_BODY: Float = 0.72f

    /** 歌词非当前行的弱化（当前行插值到 1）。 */
    const val LYRIC: Float = 0.55f
}
