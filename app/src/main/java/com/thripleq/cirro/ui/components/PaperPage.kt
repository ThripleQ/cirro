package com.thripleq.cirro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * **详情页的骨架**：顶上一段容器色条 + 大标题，下面一张内容圆角纸，右上角一颗浮层收起键
 * —— 站内「探索 / 搜索 / 我的 / 歌单 / 榜单 / 专辑 / 歌手 / 播客 / 评论」全部长得这一份。
 *
 * ## 为什么抽成一个组件（2026-10-05 用户：「改成一模一样的，返回按钮也是一模一样」）
 *
 * 这几页原先各写各的：三个 tab 页与曲目页走「容器色条 + 圆角纸」，而歌手 / 播客 / 评论
 * 三页走的是另一套老顶栏（`CirroScreenTopBar`：**左上角返回箭头** + 小一号的 `titleLarge`
 * 标题，且**没有圆角纸**）——并排看就是标题不在同一根竖线上、键在左右两边、纸有无各半。
 * 现在这三页也换成本组件，键统一挪到右上角（同一颗 [CirroCloseButton]），于是：
 *
 * | 项 | 全站统一值 |
 * |---|---|
 * | 纸顶那条线 | 状态栏 + [CirroTitleBarHeight] |
 * | 标题 | `headlineSmall` 粗体 + 行高裁剪，左起 **24dp** |
 * | 标题右内缩 | [TitleBarCloseInset]（让开那颗 36dp 键） |
 * | 纸 | [CirroShape.SheetTop]（28dp 上两角）+ `surface` |
 * | 收起键 | [CirroCloseButton]，右上角、12dp 外边距、上提一档压标题条中线 |
 *
 * ## 两个必须照抄的结构细节
 *
 * 1. **键不能塞进标题条那层**：36dp 的圆比标题条（[CirroTitleBarHeight]）高，塞进去会把
 *    那层撑高、**下方圆角纸整体下移**，纸顶就又和别的页错开了。它是浮层，必须是这一层的
 *    兄弟节点（`align(TopEnd)` 不参与 `Column` 测量）。这个坑在 `TrackListScreen` 里
 *    踩过一次。
 * 2. **容器色铺在根节点上**（不是铺在标题条上）：状态栏那一条也要是这个色，纸的顶角才
 *    把它露出来；标题条自己带 `statusBarsPadding()`，内容从状态栏下沿起。
 *
 * 调用方负责 `BackHandler`（系统返回键仍要能退出本页），本组件只管画。
 */
@Composable
fun CirroPaperPage(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier
            .fillMaxSize()
            .background(containerColor),
    ) {
        Column(Modifier.fillMaxSize()) {
            CirroPageTitleBar(title, endInset = TitleBarCloseInset)
            // 内容圆角纸：与探索 / 搜索 / 我的 / 详情页是**同一个形状 token**。
            // weight(1f) 而不是 fillMaxSize：纸的顶边只能由标题条决定，不能反过来撑标题条。
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(CirroShape.SheetTop)
                    .background(MaterialTheme.colorScheme.surface),
            ) { content() }
        }
        CirroCloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                // 上提一档让圆心压到标题条中线上（详见 [CloseButtonRaise]）——与壳里那颗、
                // 与 TrackListScreen nav 路径那颗是**同一条 modifier 链**，三处不许各写各的。
                .offset(y = -CloseButtonRaise)
                .padding(12.dp),
        )
    }
}
