package com.thripleq.cirro.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * **机器生成，请勿手改** —— 由 `tools/gen_palette.py` 从单一 seed 推导。
 * 换品牌色：改脚本里 `SEED` 一行 + 重跑，本文件与所有调用方零改动。
 *
 * seed = #C92027（品牌红，浅色 primary 逐位用它本色）
 * ｜secondary = 冷蓝灰 258°｜tertiary = 暖金 78°｜中性面 = 微冷 260°
 * ｜暗色 surface 锚 tone 19
 *
 * 色相与明度音阶取自 OKLCH（感知均匀，tone = L×100）；与 Material Theme Builder 的
 * HCT 产出不逐位相同，但**所有 on_* 正文色对都按 WCAG 相对亮度验证过 ≥4.5:1**
 * （次要文字 ≥3.0:1），且主次容器、主次文字均强制可区分。
 *
 * 三条刻意的设计选择（详见脚本文件头）：
 * 1. 浅色 primary 用品牌本色而非 M3 惯例 tone 40 —— 白字压品牌红实测 5.6:1，
 *    本就达 AA，压暗反而是去品牌化。
 * 2. 中性面**不与品牌红同色相**：改成近乎无彩的微冷灰（彩度 0.006）。旧版把表面染成
 *    品牌红色相的暖调，成片铺开时读作「发粉、发脏」（暗色尤甚）；冷暖对照也让品牌红更跳。
 * 3. on_* 先试 M3 规范 tone，不达标才修正 —— 保证次要文字不与主文字同色。
 *
 * 音阶留档（tone: RRGGBB）：
 *   primary          10:0D0000 20:320002 30:5C0007 40:8B000F 50:BB071B 60:DE3C3B 70:FF645D 80:FFA097 90:FFD2CC 99:FFFBFA
 *   secondary        10:00030D 20:0D1624 30:242E3E 40:3D4859 50:586476 60:758194 70:929FB3 80:B1BFD3 90:D1DFF4 99:FAFCFF
 *   tertiary         10:060300 20:201300 30:3F2900 40:604100 50:835A00 60:A2782D 70:C2964D 80:E3B56D 90:FFD79A 99:FFFBF6
 *   neutral          10:030305 20:141619 30:2C2E31 40:46484B 50:616367 60:7E8084 70:9C9EA2 80:BBBEC2 90:DCDEE2 99:FAFCFF
 *   neutral_variant  10:020306 20:13161B 30:2B2E33 40:44484E 50:60636A 60:7C8087 70:9A9FA5 80:BABEC5 90:DADEE5 99:FAFCFF
 *   error            10:0C0000 20:300300 30:5A0900 40:871300 50:B61E00 60:DA452C 70:FE674C 80:FFA18F 90:FFD2C9 99:FFFBFA
 */

/** 一整套 M3 颜色角色；浅色 / 深色各一份实例，由 [CirroTheme] 映射进 colorScheme。 */
data class CirroColorRoles(
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color,
    val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color,
    val errorContainer: Color, val onErrorContainer: Color,
    val surface: Color, val onSurface: Color,
    val surfaceDim: Color, val surfaceBright: Color,
    val surfaceContainerLowest: Color, val surfaceContainerLow: Color,
    val surfaceContainer: Color, val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color,
    val outline: Color, val outlineVariant: Color,
    val inverseSurface: Color, val inverseOnSurface: Color,
    val inversePrimary: Color, val surfaceTint: Color,
)

// ── 浅色 ────────────────────────────────────────────────────────
val CirroLightColors = CirroColorRoles(
    primary = Color(0xFFC92027),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD2CC),
    onPrimaryContainer = Color(0xFF5C0007),
    secondary = Color(0xFF3D4859),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1DFF4),
    onSecondaryContainer = Color(0xFF242E3E),
    tertiary = Color(0xFF604100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD79A),
    onTertiaryContainer = Color(0xFF3F2900),
    error = Color(0xFF871300),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD2C9),
    onErrorContainer = Color(0xFF5A0900),
    surface = Color(0xFFF6F9FD),
    onSurface = Color(0xFF030305),
    // 2026-10-03 用户：「亮色主题要亮一点」——**容器这一族整体上抬约 7 个亮度**。
    // 原来 `surfaceContainer`(#E9EBEF) 与 `surface`(#F6F9FD) 差 14，落在页面上就是
    // 「灰标题条 + 白纸 + 灰卡片」三块分明；抬到 #F0F2F7 后差值收到 7，颜色关系还在，
    // 但整页不再是灰压白。
    //
    // 只动容器这一族（Low 以上）：`surface` / `surfaceBright` / `surfaceContainerLowest`
    // 是「亮端」，本就接近纸白，不动；`surfaceDim` 在暗端，跟着抬以免梯度断掉。
    surfaceDim = Color(0xFFDBDDE1),
    surfaceBright = Color(0xFFFAFCFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6FA),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFE9ECF1),
    surfaceContainerHighest = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFFE2E5EC),
    onSurfaceVariant = Color(0xFF52555C),
    outline = Color(0xFF60636A),
    outlineVariant = Color(0xFFBABEC5),
    inverseSurface = Color(0xFF141619),
    inverseOnSurface = Color(0xFFECEFF3),
    inversePrimary = Color(0xFFFFA097),
    surfaceTint = Color(0xFFC92027),
)

// ── 深色 ────────────────────────────────────────────────────────
val CirroDarkColors = CirroColorRoles(
    primary = Color(0xFFFF645D),
    onPrimary = Color(0xFF320002),
    primaryContainer = Color(0xFF8B000F),
    onPrimaryContainer = Color(0xFFFFD2CC),
    secondary = Color(0xFFB1BFD3),
    onSecondary = Color(0xFF0D1624),
    secondaryContainer = Color(0xFF242E3E),
    onSecondaryContainer = Color(0xFFD1DFF4),
    tertiary = Color(0xFFE3B56D),
    onTertiary = Color(0xFF201300),
    tertiaryContainer = Color(0xFF3F2900),
    onTertiaryContainer = Color(0xFFFFD79A),
    error = Color(0xFFFFA18F),
    onError = Color(0xFF300300),
    errorContainer = Color(0xFF5A0900),
    onErrorContainer = Color(0xFFFFD2C9),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFDCDEE2),
    surfaceDim = Color(0xFF121417),
    surfaceBright = Color(0xFF2F3033),
    surfaceContainerLowest = Color(0xFF06070A),
    surfaceContainerLow = Color(0xFF0C0D10),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF222427),
    surfaceContainerHighest = Color(0xFF2C2E31),
    surfaceVariant = Color(0xFF23272C),
    onSurfaceVariant = Color(0xFFBABEC5),
    outline = Color(0xFF7C8087),
    outlineVariant = Color(0xFF2D3036),
    inverseSurface = Color(0xFFDCDEE2),
    inverseOnSurface = Color(0xFF141619),
    inversePrimary = Color(0xFF8B000F),
    surfaceTint = Color(0xFFFF645D),
)
