package com.thripleq.cirro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 胶囊壳设计语言 —— 形状令牌。
 *
 * 设计理念：整个 app 的容器都从「底部胶囊浮岛」这个母题衍生 ——
 * dock 是 22dp 胶囊；展开壳顶角 26dp（壳从胶囊长成全屏，顶角即胶囊的"上半"）；
 * 内容卡 16dp 是胶囊的次级尺度；再递减到 Chip 8dp。
 * 功能性细条（进度条/滑块轨道）用 2dp，不参与装饰体系。
 *
 * | Token     | 值  | 用途                                    |
 * |-----------|-----|-----------------------------------------|
 * | Capsule   | 22  | dock 浮岛、全屏大封面 —— 品牌圆角       |
 * | Card      | 16  | 大卡、banner/hero 封面、列表卡          |
 * | CardSmall | 12  | 次级卡、行级 ripple 收敛、骨架箱        |
 * | Chip      | 8   | 小按钮、骨架线                          |
 * | Track     | 2   | 进度/滑块等功能性细圆角                 |
 * | SheetTop  | 28  | 页面内容圆角纸的顶角（上两角）           |
 *
 * 使用约定：新代码禁止再写裸 `RoundedCornerShape(<magic>.dp)`（动态插值与
 * 仅顶/底角场景除外），一律引用本 token，保证全局可一处调形。
 */
object CirroShape {
    val Capsule = RoundedCornerShape(22.dp)
    val Card = RoundedCornerShape(16.dp)
    val CardSmall = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(8.dp)
    val Track = RoundedCornerShape(2.dp)

    /**
     * 全圆角胶囊：半径恒为高的一半（`percent = 50`）。
     *
     * 与 [Capsule] 的区别：`Capsule` 是固定 22dp 的品牌圆角；`Pill` 用于**任意高度**都要
     * 两端成半圆的按钮 / 导航 pill / 骨架胶囊，半径随高度自适应。原 `RoundedCornerShape(50)`
     * 与 `RoundedCornerShape(percent = 50)` 两种裸写法散落各处，统一到这里。
     */
    val Pill = RoundedCornerShape(percent = 50)

    /** 展开壳顶角基准（dock 顶角动画终值同源；壳动画内部动态插值，不直接引用）。 */
    val ShellTop = 26.dp

    /**
     * 付费 / VIP 徽标外框的圆角 = **2dp**。
     *
     * 值来自实测：kanade 截图（1080 宽、密度 3）里徽标框高 30px、圆角约 4px ⇒ 1.3dp；
     * 10dp 高的框上 1.3 与 2 肉眼无差，取整到 [Track] 同档，不再养第三个细圆角数。
     * 徽标是**描边**（不是填充）的细框，圆角一放大就会显得像按钮，故与 [Chip] 差三档。
     */
    val Badge = RoundedCornerShape(2.dp)

    /**
     * 内容圆角纸的顶角半径 = **28dp**。
     *
     * 站内所有「容器色标题条 + 下方圆角纸」的页面共用这一个值：探索 / 搜索 / 我的三个
     * tab 页，以及详情页（歌单 / 榜单 / 专辑，见 `TrackListMetrics.SheetCorner`）。
     * 以前它叫 `HomeSheetRadius` 且定义在 `HomeContent.kt` 里，另外三处 import 过来用 ——
     * 名字写着"探索页"，实际是全站规格，放错了地方（2026-10-05 迁到这里）。
     *
     * 三张纸的圆角差一档并排看就歪，故禁止再各写一份 `RoundedCornerShape(28.dp)`。
     */
    val SheetRadius = 28.dp

    /** 内容圆角纸的**顶角**形状（只有上两角圆，下沿接屏底）。 */
    val SheetTop = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius)
}

/** M3 组件默认 shape 对齐本体系（Card/Button/TextField 等未显式指定 shape 时生效）。 */
val CirroShapes = Shapes(
    extraSmall = CirroShape.Track,
    small = CirroShape.Chip,
    medium = CirroShape.CardSmall,
    large = CirroShape.Card,
    extraLarge = CirroShape.Capsule,
)
