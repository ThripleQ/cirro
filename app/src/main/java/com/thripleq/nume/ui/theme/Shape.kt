package com.thripleq.nume.ui.theme

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
 *
 * 使用约定：新代码禁止再写裸 `RoundedCornerShape(<magic>.dp)`（动态插值与
 * 仅顶/底角场景除外），一律引用本 token，保证全局可一处调形。
 */
object NumeShape {
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
}

/** M3 组件默认 shape 对齐本体系（Card/Button/TextField 等未显式指定 shape 时生效）。 */
val NumeShapes = Shapes(
    extraSmall = NumeShape.Track,
    small = NumeShape.Chip,
    medium = NumeShape.CardSmall,
    large = NumeShape.Card,
    extraLarge = NumeShape.Capsule,
)
