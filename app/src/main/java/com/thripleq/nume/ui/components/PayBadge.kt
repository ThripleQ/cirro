package com.thripleq.nume.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 歌曲行里紧跟歌手的付费标记 —— 照抄 kanade 的歌曲信息设计（2026-10-06 用户指定）。
 *
 * | 标记 | 含义 | 出现时机 |
 * |---|---|---|
 * | [VIP] | 需会员才能听 | `fee == 1` |
 * | [PAY] | 需购买（数字专辑 / 单曲） | `fee == 4` |
 *
 * **两者同时成立时优先 PAY**（用户口径）—— 所以 [payTagOf] 里 `4` 的分支写在 `1` 前面，
 * 将来若再加"会员"这一类别的独立信号源，也必须让 PAY 先判。
 */
enum class PayTag(val label: String) {
    VIP("VIP"),
    PAY("PAY"),
}

/**
 * `fee` → 徽标。判据是**实测反验过**的：用户给的 kanade 截图里
 * `Kids Return`（久石譲）标 VIP、`那些我尚未知道的美丽`（华晨宇）标 PAY，
 * 而这两首在 `/weapi/v3/song/detail` 里的 `fee` 分别是 **1** 和 **4**。
 *
 * 其余档位**不标**：
 * - `0` 免费；
 * - `8` 低音质免费（榜上占近半，且实测 `privilege.pl` 非 0、能正常播放）—— 给它加徽标
 *   会让半张榜单都挂上标，与截图里"只有个别行有标"的观感不符；
 * - 老端点缺字段 → `0`。
 *
 * ⚠️ **没有考虑 `privileges[i].payed`（已购买）**：那需要按下标去对齐另一个数组，
 * 而对齐关系没有保证；且这条信息只在"已购"语境下才改变观感，已在列表层单独处理
 * （`TrackListScreen` 对 `TrackListSource.PURCHASED` 不传标记）。
 */
fun payTagOf(fee: Int): PayTag? = when (fee) {
    4 -> PayTag.PAY
    1 -> PayTag.VIP
    else -> null
}

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
 * 颜色用 [MaterialTheme.colorScheme.onSurfaceVariant]，**与副标题同色**。截图里 kanade
 * 的徽标比它自己的正文暗约 14%（其正文本就比副标题亮），nume 的副标题已经是这一档，
 * 再压一档会看不清，故取同色、靠描边区分层级（不是靠颜色差）。
 *
 * 徽标**必须贴着「歌手 - 专辑」的前面放**（截图位置），间隔见调用方：
 * 实测框右边框到歌手首墨 9px = 3dp，其中 CJK 字形自带约 1dp 侧边距，
 * 所以间距设 2dp 才能还原这个视觉间隙。
 */
@Composable
fun NumePayBadge(tag: PayTag, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
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
