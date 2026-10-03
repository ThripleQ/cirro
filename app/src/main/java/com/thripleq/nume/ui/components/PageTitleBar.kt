package com.thripleq.nume.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 一级页面的大标题条 —— 取代探索 / 搜索 / 我的三份逐字节相同的标题条
 * （原 `HomeTopBar` / `SearchTitleBar`）。
 *
 * 与 [NumeScreenTopBar] 的分工：那个是**详情页**顶栏（左返回键 + `titleLarge`）；
 * 这个是**底部 tab 的一级页面**顶栏（无返回键 + `headlineSmall` 大标题），尾部可追加
 * [actions]（探索页的刷新键就走这里）。
 *
 * 规范（三页共用，改这里就是三页一起改）：
 * - 让开状态栏；
 * - 左内缩 **24dp**，比内容的 16dp 多一档 —— 大标题压在内容竖线外一点，这是站内约定
 *   （见探索页 24 / 内容 16、搜索页同）；标题 **不**为了对齐内容而缩到 16，那反而会
 *   与另外两页错开；
 * - 标题 `headlineSmall` 粗体 + `Trim.Both`：去掉行高上下多余的 leading，让字的上沿
 *   贴到状态栏（否则字上方还留着一条空隙）；
 * - 右侧 [actions] 贴右端、上下内缩 8dp。
 *
 * 调用方自己铺 `surfaceContainer` 底色（它是「状态栏那条容器色带」，内容圆角纸的顶角
 * 会把这条带子露出来），本组件只画文字与动作、**不**铺底。
 */
@Composable
fun NumePageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            // `weight(1f)` 让标题独占剩余宽度：有 actions 时标题再长也挤不走右侧动作。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
