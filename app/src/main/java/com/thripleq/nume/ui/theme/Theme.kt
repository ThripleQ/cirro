package com.thripleq.nume.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 把生成好的整套角色映射进 M3 `ColorScheme`。
 *
 * **必须逐个显式传入，一个都不能省。** 之前这里只传了 20 个角色，
 * `surfaceContainer / surfaceContainerLow / High / Highest / surfaceDim /
 * surfaceBright / outlineVariant / error*` 全部没传 —— 于是它们**回落到
 * M3 内置默认值（带紫调的中性）**，而 App 最显眼的表面（dock 浮岛、伸展壳、
 * scrim）用的恰恰就是这几个角色。结果：品牌是红的，主角表面却是紫灰的 ——
 * 这就是"颜色不协调"的真正来源，不是散落的硬编码（全 app 的硬编码本就集中在
 * 这一个文件里）。
 */
private fun NumeColorRoles.toColorScheme(): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = error, onError = onError,
    errorContainer = errorContainer, onErrorContainer = onErrorContainer,
    background = surface, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    // ↓ 这几项正是过去缺失、导致回落 M3 紫灰的角色
    surfaceDim = surfaceDim, surfaceBright = surfaceBright,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline, outlineVariant = outlineVariant,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    surfaceTint = surfaceTint,
)

/**
 * 深色方案必须走 `darkColorScheme`：它与 `lightColorScheme` 的差别不只是色值，
 * 还有若干角色的默认回退值不同（如 scrim）。少写一个就会把浅色的默认值漏进暗色。
 */
private fun NumeColorRoles.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = error, onError = onError,
    errorContainer = errorContainer, onErrorContainer = onErrorContainer,
    background = surface, onBackground = onSurface,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceDim = surfaceDim, surfaceBright = surfaceBright,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline, outlineVariant = outlineVariant,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    surfaceTint = surfaceTint,
)

@Composable
fun NumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * 动态取色（Material You / 壁纸取色），Android 12+ 生效。
     *
     * 置 true 会用壁纸生成的整套色**顶掉** [Palette.kt]：品牌红没了，[NumeInk] /
     * [NumeFade] 这些刻意的常量也失去意义，界面长相变成「用户壁纸的函数」。
     * 所以 `MainActivity` 显式传 false —— 这是有意的取舍，不是没接。
     * 想换成跟随壁纸：把那一行改成 `dynamicColor = true` 即可（深色下若出现
     * `onBackground` 偏深看不清的情况，优先怀疑这里）。
     */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> NumeDarkColors.toDarkColorScheme()
        else -> NumeLightColors.toColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NumeShapes,
        content = content,
    )
}
