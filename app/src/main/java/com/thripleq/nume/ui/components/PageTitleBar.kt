package com.thripleq.nume.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶部标题条的**内容高度**（不含状态栏）：站内所有「标题条 + 圆角纸」的页面共用一份。
 *
 * ## 为什么必须定死（2026-10-05 用户：「顶部标题和圆角纸要统一」）
 *
 * 标题条的高度**同时是圆角纸的顶边位置** —— 纸顶就贴在标题条下沿。原来高度由内容撑：
 * 文字只有 ~29dp，而探索页的 actions 里放了一颗 `IconButton`，它自带 **48dp 最小交互尺寸**
 * → 那两页的纸顶相差 16dp。切 tab 时标题与整张纸一起跳一下，正是用户看到的不统一。
 *
 * ## 取值：字体行高，而不是任意常量
 *
 * 取 `headlineSmall` 的 **lineHeight**（32sp → dp）：跟随系统字体缩放，用户放大字号时
 * 标题不会被裁；同时也保证三页取到**同一个数**（同一个 typography）。
 * 比"裁剪后的文字实际高度"略高 ~3dp，那点余量正好当标题的上下呼吸位。
 *
 * 详情页（歌单 / 榜单 / 专辑）的标题条是另一份实现（`TrackListTitleBar`，要交叉淡变
 * 标签与标题），它**同样引用本值**，否则详情页的纸又和三个 tab 页错开。
 */
val NumeTitleBarHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
    }

/**
 * 标题条文字的**左**内缩：比内容的 16dp 多一档 —— 大标题压在内容竖线外一点，这是站内约定
 * （见探索页 24 / 内容 16、搜索页同）；标题**不**为了对齐内容而缩到 16，那反而会与别的页错开。
 *
 * 站内两张标题条的实现（本文件的 [NumePageTitleBar]、详情页的 `TrackListTitleBar`）
 * 都引这一个值 —— 2026-10-05 用户原话「那里各种布局参数也应该一样」，所以连这个数也
 * 收成一处，免得以后只改了一边、标题又不在同一根竖线上。
 */
val TitleBarStartInset = 24.dp

/**
 * 标题条右侧的**默认**内缩：右端至多摆一颗 28dp 的小键（探索页那颗刷新键）。
 *
 * 大标题是 `weight(1f)`，所以这个值直接决定「标题最长能画到离右边缘多远」——
 * 键是 28dp、贴右端 8dp，标题最右也就停在 36dp 处，不会压到键上。
 */
val TitleBarEndInset = 8.dp

/**
 * 标题条右侧的**详情页**内缩：让开右上角那颗 36dp 浮层收起键
 * （36dp 圆 + 12dp 外边距 + 8dp 缝 = 56dp）。
 *
 * 与 [TitleBarEndInset] 是一对 —— 详情页的键从左上**翻到**右端，让位方向跟着翻，
 * 不是取消让位：键是浮层、就压在这一排上，标题（`maxLines = 1` + Ellipsis）必须收在它之前。
 *
 * **为什么两个值不该相等**：一级页面右端只有一颗 28dp 的刷新键、贴边 8dp，标题自然可以
 * 画到 8dp 处；详情页右端是 36dp 的收起键、还要离边 12dp，标题就得退到 56dp。
 * 反过来把一级页面的内缩也拉成 56dp，会把探索页那颗刷新键推到离边 56dp 的空中
 * （它和标题同在一个 `Row` 里，共用一个右内缩）。
 */
val TitleBarCloseInset = 56.dp

/**
 * 一级页面的大标题条 —— 取代探索 / 搜索 / 我的三份逐字节相同的标题条
 * （原 `HomeTopBar` / `SearchTitleBar`）。
 *
 * 与 [NumePaperPage] 的分工：那个是**详情页**的整套骨架（标题条 + 内容圆角纸 + 右上角收起键，
 * 歌手 / 播客 / 评论 / 歌单 / 榜单 / 专辑都走它）；这个是**底部 tab 的一级页面**顶栏
 * （没有收起键，尾部可追加 [actions]——探索页的刷新键就走这里）。
 *
 * 规范（三页共用，改这里就是三页一起改）：
 * - 让开状态栏；
 * - 高度锁定为 [NumeTitleBarHeight]（= `headlineSmall` 行高）—— 右侧有没有动作键都一样高，
 *   于是三页的圆角纸顶边落在同一条水平线上；[actions] 里的 `IconButton` 自带 48dp 最小
 *   交互尺寸，只在探索页有，不锁高度就会把那一页的纸整整压低 16dp；
 * - 左内缩 [TitleBarStartInset]（24dp，比内容的 16dp 多一档，详情页的标题条同引此值）；
 * - 右内缩按端上的键分档：[endInset] 默认 [TitleBarEndInset]（28dp 小键），详情页传
 *   [TitleBarCloseInset]（让开 36dp 浮层收起键）；
 * - 标题 `headlineSmall` 粗体 + `Trim.Both`：去掉行高上下多余的 leading，让字的上沿
 *   贴到状态栏（否则字上方还留着一条空隙）；
 * - 右侧 [actions] 贴右端、垂直居中。
 *
 * 调用方自己铺 `surfaceContainer` 底色（它是「状态栏那条容器色带」，内容圆角纸的顶角
 * 会把这条带子露出来），本组件只画文字与动作、**不**铺底。
 */
@Composable
fun NumePageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    /** 标题文字右侧让位宽度：见 [TitleBarEndInset] / [TitleBarCloseInset]。 */
    endInset: Dp = TitleBarEndInset,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(NumeTitleBarHeight)
            .padding(start = TitleBarStartInset, end = endInset),
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
