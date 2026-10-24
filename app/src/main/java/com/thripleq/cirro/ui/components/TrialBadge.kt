package com.thripleq.cirro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 「试听」标识：正在播的这首是**非会员只能听到的片段**（VIP 歌，官方只给 30 秒 ——
 * 判据与实测见 `core/playback/TrialClip`）。
 *
 * ## 为什么不是复用 [CirroPayBadge]
 *
 * 形制**完全照抄**它（描边小框 + 粗体 + [CirroShape.Badge] 圆角 + 同样的 8sp/1dp 内缩，
 * 尺寸口径那套逐像素推导见 [CirroPayBadge]），只有颜色不同 —— 因为两者是**不同类别**的标：
 *
 * | | 出现在 | 语义 | 颜色 |
 * |---|---|---|---|
 * | [CirroPayBadge] VIP | 曲目列表行、**播放条 / 播放页** | 静态属性（这首歌要会员） | `onSurfaceVariant` 灰 |
 * | 本标 「试听」 | **播放条 / 播放页** | 正在发生的临时状态（你现在只能听 30 秒） | `tertiary` 暖金 |
 *
 * 播放器里两枚标**同时出现**（VIP 在左、试听在右），不是二选一：VIP 说的是「这首歌
 * 要会员」，试听说的是「你现在只拿到 30 秒」，丢掉任何一枚都少一半信息 —— 尤其
 * 试听片段本身不会告诉用户它为什么短。列表行不挂「试听」：那里没有「正在播」的
 * 语境，且试听是播放态而非曲目属性。
 *
 * 用 `tertiary` 而不是 `primary`：主色红在这套主题里已经被**红心（喜欢）**占用
 * （`CirroLightColors.primary` = 0xFFC92027，即实测的红心色 rgb(201,32,39)），
 * 一枚红色的「试听」会和「已喜欢」撞。暖金在明暗两套里分别是 0xFF604100 / 0xFFE3B56D，
 * 对各自底色的对比度都够，不需要再按底色分档（`tertiary` 本身已随主题换值）。
 */
@Composable
fun CirroTrialBadge(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.tertiary
    Text(
        text = "试听",
        color = color,
        fontSize = 8.sp,
        // 行盒 10sp（不是 8sp）：8sp 字体的自然行高约 9.3dp，行盒只给 8dp 时装不下、
        // 字形会被裁 —— 本标是**唯一的中文标**（CJK 墨迹约 0.88em 且上下都占），
        // 所以「被压扁」只出现在它身上；拉丁大写（VIP / PAY）墨迹才 5.7dp，看不出来。
        // 框高仍是 10dp：行盒 10 + 上下内缩 0 = 原先行盒 8 + 上下各 1。
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, color, CirroShape.Badge)
            .padding(horizontal = 3.dp),
    )
}
