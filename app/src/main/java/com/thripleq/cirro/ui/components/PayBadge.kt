package com.thripleq.cirro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thripleq.cirro.ui.theme.CirroPay
import com.thripleq.cirro.ui.theme.CirroShape


/**
 * 徽标墨色。
 *
 * VIP 走 [MaterialTheme.colorScheme]，红/蓝**按主题底色的明暗各取一档**：这两个颜色要
 * 在一行小字里被一眼分辨出来，就得让它们与所在底色保持足够对比 —— 一个色值包打两种
 * 主题是做不到的（挑中间亮度则明底偏淡、暗底偏闷，两头都吃亏）。判据取 `surface` 的
 * 亮度而不是 `isSystemInDarkTheme()`：主题是可以被覆盖的，而**能不能看清只取决于底色**。
 */
@Composable
private fun payBadgeColor(tag: PayTag): Color = when (tag) {
    PayTag.VIP -> MaterialTheme.colorScheme.onSurfaceVariant
    PayTag.PAY -> if (onDarkSurface()) CirroPay.PayDark else CirroPay.PayLight
    PayTag.PAID -> if (onDarkSurface()) CirroPay.OwnedDark else CirroPay.OwnedLight
}

@Composable
private fun onDarkSurface(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * 徽标本体：**描边小框 + 大写字母**，无填充。尺寸照 kanade 截图逐像素量出来的
 * （1080 宽、密度 3，故 px÷3 = dp）：
 *
 * | 量 | 截图实测 | 本实现 |
 * |---|---|---|
 * | 框高 | 30px = 10dp | 8sp 行高 + 上下各 1dp 内缩 = 10dp |
 * | 框宽 | 61px = 20.3dp | 文本 ≈13.3dp + 左右各 3dp ≈ 19dp |
 * | 描边 | 3px = 1dp | 1dp |
 * | 圆角 | 4px ≈ 1.3dp | [CirroShape.Badge]（2dp） |
 * | 文字 | 大写字母高 17px = 5.67dp ⇒ ≈8sp 粗体 | 8sp Bold + 0.5sp 字距 |
 * | 框内留白 | 上 2.3dp / 下 2.0dp | 计算值 2.0dp / 2.3dp |
 *
 * `lineHeight` 与字号同为 8sp 是**故意的**：这样文本盒子就是 8dp，加 2dp 内缩正好 10dp，
 * 与截图等高；大写字母没有下伸部，墨迹（约 5.7dp）稳稳落在框内。改成默认行高
 * （≈1.17em）框就会长到 11.4dp，比截图高一头。
 *
 * 徽标**必须贴着「歌手 - 专辑」的前面放**（截图位置），间隔见调用方：
 * 实测框右边框到歌手首墨 9px = 3dp，其中 CJK 字形自带约 1dp 侧边距，
 * 所以间距设 2dp 才能还原这个视觉间隙；两枚并排时（VIP + 蓝 PAY）同样用 2dp。
 */
@Composable
fun CirroPayBadge(tag: PayTag, modifier: Modifier = Modifier) {
    val color = payBadgeColor(tag)
    Text(
        text = tag.label,
        color = color,
        fontSize = 8.sp,
        lineHeight = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, color, CirroShape.Badge)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}
