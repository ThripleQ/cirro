package com.thripleq.cirro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
 * | 框高 | 30px = 10dp | 10sp 行盒 + 上下内缩 0 = 10dp |
 * | 框宽 | 61px = 20.3dp | 文本 ≈13.3dp + 左右各 3dp ≈ 19dp |
 * | 描边 | 3px = 1dp | 1dp |
 * | 圆角 | 4px ≈ 1.3dp | [CirroShape.Badge]（2dp） |
 * | 文字 | 大写字母高 17px = 5.67dp ⇒ ≈8sp 粗体 | 8sp Bold + 0.5sp 字距 |
 * | 框内留白 | 上 2.3dp / 下 2.0dp | 计算值 2.0dp / 2.3dp |
 *
 * **行盒取 10sp、上下内缩为 0**（框高仍是 10dp，与截图等高）。早先写的是「行盒 8sp +
 * 上下各 1dp」，那套数字只在**拉丁大写**上成立：8sp 的字体的自然行高约 9.3dp，行盒只给
 * 8dp 时字形装不下、会被裁掉一点 —— 而 `VIP`/`PAY` 的墨迹才 5.7dp、又没有下伸部，裁掉的
 * 正好是它没墨的 leading，所以列表行一直看着正常。换成中文「试听」（[CirroTrialBadge]）
 * 就露了：CJK 墨迹约 0.88em = 7dp 且上下都占，被裁后整枚标看着像**被压扁**。
 * 行盒给到 10sp 后字形有余量，框高不变；改成默认行高（≈1.17em）框则会长到 11.4dp、高一头。
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
        // 行盒 10sp 而不是 8sp：理由见上面 KDoc 的「行盒取 10sp」一段 —— 8sp 行盒装不下
        // 8sp 字体的自然行高（≈9.3dp），CJK 字样会被裁成「被压扁」。
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            // 竖向不许被压 —— 理由与完整说明见 [CirroTrialBadge] 同名位置：徽标行是播放
            // 卡片标题块里唯一能缩的东西，卡片档竖向只差几 dp 时，差额全压在它身上。
            .wrapContentHeight(unbounded = true)
            .border(1.dp, color, CirroShape.Badge)
            .padding(horizontal = 3.dp),
    )
}
