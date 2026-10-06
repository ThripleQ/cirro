package com.thripleq.nume.ui.components

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
import com.thripleq.nume.ui.theme.NumePay
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 歌曲行里紧跟歌手的付费标记 —— 照抄 kanade 的歌曲信息设计（2026-10-06 用户指定）。
 *
 * 三态，**颜色**是唯一的区别所在（框、字号、描边完全一致）：
 *
 * | 标记 | 颜色 | 含义 | 出现时机 |
 * |---|---|---|---|
 * | [VIP] | 灰（跟随副标题） | 需会员才能听 | `fee == 1` |
 * | [PAY] / [PayTag.PAY] | 红 | **需要购买，还没买** | `fee == 4` 且未购 |
 * | [PAY] / [PayTag.PAID] | 蓝 | **已经购买** | 已购（单曲或数字专辑） |
 *
 * ## 为什么「需要购买」与「已购」是两个枚举、却是同一个字符串
 *
 * 用户口径（2026-10-06）：「所有歌曲都能购买，所以用红色的 pay 标记需要购买但没有买的，
 * 用蓝色的 pay 标记已经购买的；如果是需要购买则把红色 pay 替换成蓝色的，vip 则加一个蓝色 pay」。
 *
 * 也就是说这两者是**同一枚徽标的两种状态**（文字都是 PAY），不是两种徽标 —— 拆成两个枚举
 * 只是为了拿到两种颜色，[label] 故意相同。于是组合只有四种：
 *
 * - `fee=4` 未购 → `[PAY 红]`
 * - `fee=4` 已购 → `[PAY 蓝]`（红的那枚被**替换**，不是并排）
 * - `fee=1` 未购 → `[VIP]`
 * - `fee=1` 已购 → `[VIP] [PAY 蓝]`（VIP 是"要会员"、蓝 PAY 是"我买过"，两件事都要说）
 *
 * 最后那条**不是理论情形**：实测用户的 9 首已购单曲全部 `fee=1`（VIP 歌可以单曲购买，
 * 买完 `fee` 不变、只是 `privileges[].payed` 从 0 变 3），所以它出现在真实列表里。
 */
enum class PayTag(val label: String) {
    VIP("VIP"),
    PAY("PAY"),
    PAID("PAY"),
}

/**
 * `fee` + 「买了没」→ 徽标序列（空的、一枚、或 VIP+PAY 两枚）。
 *
 * 判据是**实测反验过**的：用户给的 kanade 截图里 `Kids Return`（久石譲）标 VIP、
 * `那些我尚未知道的美丽`（华晨宇）标 PAY，而这两首在 `/weapi/v3/song/detail` 里的
 * `fee` 分别是 **1** 和 **4**。
 *
 * 其余档位**不标**：
 * - `0` 免费；
 * - `8` 低音质免费（榜上占近半，且实测 `privilege.pl` 非 0、能正常播放）—— 给它加徽标
 *   会让半张榜单都挂上标，与截图里"只有个别行有标"的观感不符；
 * - 老端点缺字段 → `0`。
 *
 * [owned] 的含义见 [com.thripleq.nume.core.repo.LibraryStateStore.isOwned]：单曲购买 ∪
 * 所属数字专辑已购。它只影响颜色、不影响「标不标」，所以**没载入完时退化成全红**，
 * 而不是让徽标整体消失（那会显得列表被改坏了）。
 */
fun payTagsOf(fee: Int, owned: Boolean): List<PayTag> = when (fee) {
    4 -> if (owned) listOf(PayTag.PAID) else listOf(PayTag.PAY)
    1 -> if (owned) listOf(PayTag.VIP, PayTag.PAID) else listOf(PayTag.VIP)
    else -> emptyList()
}

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
    PayTag.PAY -> if (onDarkSurface()) NumePay.PayDark else NumePay.PayLight
    PayTag.PAID -> if (onDarkSurface()) NumePay.OwnedDark else NumePay.OwnedLight
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
 * | 圆角 | 4px ≈ 1.3dp | [NumeShape.Badge]（2dp） |
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
fun NumePayBadge(tag: PayTag, modifier: Modifier = Modifier) {
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
            .border(1.dp, color, NumeShape.Badge)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}
