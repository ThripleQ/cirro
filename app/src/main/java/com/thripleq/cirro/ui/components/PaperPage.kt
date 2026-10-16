package com.thripleq.cirro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * | 纸顶那条线（静止） | 状态栏 + [CirroTitleBarHeight]；参与收起时纸顶会从这条线**轻轻上抬一点**（见下） |
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
 * ## 「滑进半遮」（2026-10-16）
 *
 * 传了 [collapse] 就参与：内容上滚过一点之后纸顶抬起来**压住标题的下沿**（见 [CirroTitleCollapse]，
 * 行程、补间与纸沿三层柔化的参数都在那边）。
 * 三个调用方（歌手 / 播客 / 评论）各自把 [rememberCirroTitleCollapse] hoist 在 Screen 层传进来
 * ——state 不能留在这里，`AnimatedContent` 开合浮层时会重建本组件，清零了而列表还停在中间，
 * 标题会自己弹回来（见 [CirroTitleCollapse] 的文档）。
 *
 * ⚠️ 这里**不**放 [CirroMiniTitleBar]：那套「滚到底再往回翻时冒出小标题」是**一级页面**的
 * 语言（位置本来由底部导航指示，标题沉进纸里没有损失）。本组件的标题是**当前对象的名字**
 * （歌手名 / 播客名 / 评论数），不能照抄。何况标题只是下沿被压住一点（[CirroTitleCollapse.titleAlpha]
 * 恒 1，柔化也只雾化贴着纸沿的一小段），名字从来没有真正丢过。
 *
 * ## 内容的契约（改本组件时唯一要当心的地方）
 *
 * 纸是**绘制期**的裁剪边界，**不改变内容的布局位置**（见 [cirroCollapsingSheet]）。所以
 * [content] 拿到的永远是**铺满整屏**的尺寸，必须自己让开顶上的 [Dp]（= 初始纸顶）：
 *
 * - 能滚的内容 ⇒ `contentPadding = PaddingValues(top = sheetTop, …)`。**必须用内缩、不能用
 *   `Modifier.padding`**：后者会把滚动视口切在标题条之下，纸顶抬起来时那段就是空白，
 *   效果整个不成立；
 * - 不能滚的内容（骨架 / 空态 / 错误态）⇒ `Modifier.padding(top = sheetTop)` 即可，它本来
 *   就不会往上走。
 *
 * 把 `sheetTop` 直接喂给 [content] 而不是让调用方各自 [rememberCirroSheetTop] 一次，
 * 是为了让这个内缩**不可能**和纸顶写歪。
 *
 * 调用方负责 `BackHandler`（系统返回键仍要能退出本页），本组件只管画。
 */
@Composable
fun CirroPaperPage(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 本页的收起点；`null` = 不参与「滑进半遮」（纸静止）。须 hoist 到 Screen。 */
    collapse: CirroTitleCollapse? = null,
    /** 参数 = 初始纸顶（本屏坐标）：内容要让开的高度。见「内容的契约」。 */
    content: @Composable (Dp) -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val sheetTop = rememberCirroSheetTop()
    Box(
        modifier
            .fillMaxSize()
            .background(containerColor)
            // 只读不消费地接住内容侧滚动容器的滚动量（见 [CirroTitleCollapse]）。
            .cirroTitleCollapse(collapse),
    ) {
        // 绘制顺序是效果的一部分：标题条先画、纸后画 ⇒ 纸天然盖住标题。
        CirroPageTitleBar(title, endInset = TitleBarCloseInset, collapse = collapse)
        // 内容圆角纸：与探索 / 搜索 / 我的 / 详情页是**同一个形状 token**。
        // 铺满整屏（含标题条那一段）而不是 `weight(1f)`：纸顶要能向上画进标题条里去。
        Box(
            Modifier
                .fillMaxSize()
                .cirroCollapsingSheet(collapse, sheetTop),
        ) { content(sheetTop) }
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
