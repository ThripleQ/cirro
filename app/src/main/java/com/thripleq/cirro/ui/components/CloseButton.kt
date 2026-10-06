package com.thripleq.cirro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.theme.CirroFade
import com.thripleq.cirro.ui.theme.CirroInk

/** 浮层键的直径（收起键与左上返回键同规格）。 */
val CloseButtonSize = 36.dp

/**
 * 收起键相对「壳内容顶」的**上提量**：配合 `padding(12.dp)` 一起用，把 36dp 的圆从
 * 「外边距 12dp」提到「中线与内容标题条重合」。
 *
 * ## 为什么要把键提起来（2026-10-03 用户：「有点靠下」）
 *
 * 壳内容从状态栏下沿开始。内容标题条 2026-10-03 照抄搜索页后**高度跟着文字走**
 * （headlineSmall + 行高裁剪），只剩 ~24dp 高，标题中线落在内容顶 +14dp；
 * 而键是 12dp 外边距 + 36dp 圆，圆心落在内容顶 +30dp。
 * 键于是比标题低 **16dp**，看着像从那一行「掉」了下去 —— 标题条以前是固定的
 * [com.thripleq.cirro.ui.screens.TrackListMetrics.TopBarHeight]（56dp，中线 28dp）时
 * 两者本来就对齐，行高改小后键没跟着调，才错开的。
 *
 * 上提这一档后圆心落在内容顶 +14dp，与标题中线重合。代价是圆顶探进状态栏约 4dp ——
 * 那一带是壳/本屏自己铺的容器色，黑底圆压上去不违和，比错位好。
 */
val CloseButtonRaise = 16.dp

/**
 * 浮层收起键（**右上角**，2026-10-03 起）：36dp 圆 + 黑底（[CirroFade.CONTROL_SCRIM]）+ 白色下箭头。
 *
 * 从左上挪到右上是因为壳内容的标题在左上：键在左边就得逼标题让出一档内缩，挪到右边后
 * 标题左起对齐内容缩进，让位翻到右端（用户：「标题不要给收起按钮让位，收起按钮放右上角」）。
 *
 * 取代官方壳 [ShellPanel] 与自研壳 `CoverExpandShell` 里两份相同的实现。
 * 调用方用 `modifier` 传入对齐 / `statusBarsPadding()` / 外边距，
 * 并叠一个 `offset(y = -CloseButtonRaise)` 让圆心对上内容标题条的中线（见上）。
 */
@Composable
fun CirroCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(CloseButtonSize)
            .clip(CircleShape)
            .background(CirroInk.Scrim.copy(alpha = CirroFade.CONTROL_SCRIM))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "收起",
            tint = CirroInk.OnImage,
            modifier = Modifier.size(24.dp),
        )
    }
}
