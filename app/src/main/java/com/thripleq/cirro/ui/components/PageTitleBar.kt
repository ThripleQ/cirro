package com.thripleq.cirro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.theme.Motion

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
val CirroTitleBarHeight: Dp
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
    }

/**
 * 标题条文字的**左**内缩：比内容的 16dp 多一档 —— 大标题压在内容竖线外一点，这是站内约定
 * （见探索页 24 / 内容 16、搜索页同）；标题**不**为了对齐内容而缩到 16，那反而会与别的页错开。
 *
 * 站内两张标题条的实现（本文件的 [CirroPageTitleBar]、详情页的 `TrackListTitleBar`）
 * 都引这一个值 —— 2026-10-05 用户原话「那里各种布局参数也应该一样」，所以连这个数也
 * 收成一处，免得以后只改了一边、标题又不在同一根竖线上。
 */
val TitleBarStartInset = 24.dp

/**
 * 标题条右侧的**默认**内缩：右端至多摆一颗 28dp 的小键（「我的」右上那颗设置键）。
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
 * 与 [CirroPaperPage] 的分工：那个是**详情页**的整套骨架（标题条 + 内容圆角纸 + 右上角收起键，
 * 歌手 / 播客 / 评论 / 歌单 / 榜单 / 专辑都走它）；这个是**底部 tab 的一级页面**顶栏
 * （没有收起键，尾部可追加 [actions]——「我的」右上角的设置键就走这里；
 * 刷新这类与标题强相关的动作走 [titleAction]，贴着标题文字放）。
 *
 * 规范（三页共用，改这里就是三页一起改）：
 * - 让开状态栏；
 * - 高度锁定为 [CirroTitleBarHeight]（= `headlineSmall` 行高）—— 右侧有没有动作键都一样高，
 *   于是三页的圆角纸顶边落在同一条水平线上；[actions] 里的 `IconButton` 自带 48dp 最小
 *   交互尺寸，只在「我的」有，不锁高度就会把那一页的纸整整压低 16dp；
 * - 左内缩 [TitleBarStartInset]（24dp，比内容的 16dp 多一档，详情页的标题条同引此值）；
 * - 右内缩按端上的键分档：[endInset] 默认 [TitleBarEndInset]（28dp 小键），详情页传
 *   [TitleBarCloseInset]（让开 36dp 浮层收起键）；
 * - 标题 `headlineSmall` 粗体 + `Trim.Both`：去掉行高上下多余的 leading，让字的上沿
 *   贴到状态栏（否则字上方还留着一条空隙）；
 * - [titleAction] 紧贴标题文字之后（「探索」「我的」的刷新键），读作标题的一部分；
 * - 右侧 [actions] 贴右端、垂直居中。
 *
 * ## `collapse`：内容上滑时被下方那张纸压住下沿（2026-10-16）
 *
 * 传了 [collapse] 就参与这套效果：本组件**不吃位移**，纸顶上抬压住标题的下沿是内容侧的事
 * （[cirroCollapsingSheet]），标题在绘制顺序上本来就在纸下面。
 * 这样分工的依据是：标题一旦自己也开始动，就会出现「标题在滑、纸在追」两条时间线，
 * 手指来回抖一下就会错位。
 *
 * 切口那道硬边的柔化（墨迹渐隐带）同样在**纸那一层**做 —— 那里能精确地只雾化贴近纸沿的
 * 一小段，而按进度整体降 alpha 会把仍在纸**上方**的字一起擦掉。因此
 * [CirroTitleCollapse.titleAlpha] 现在恒为 1，下面那个 `graphicsLayer` 是空操作；
 * 留着它，只是为了让「标题要不要淡出」这个开关仍然只有一处（复活小标题时必须同时改）。
 *
 * ⚠️ 标题条里的动作键（行内刷新 / 「我的」右上设置）**也一起被纸沿的柔化雾化**，没有单独
 * 退场。它们落在右上角那 28dp 圆角缺口附近，纸顶经过时会被圆角切掉一个角。
 * 这一点**用户 2026-10-16 明确知悉并说「之后会处理」**，别擅自加特判。
 *
 * 调用方自己铺 `surfaceContainer` 底色（它是「状态栏那条容器色带」，内容圆角纸的顶角
 * 会把这条带子露出来），本组件只画文字与动作、**不**铺底。
 */
@Composable
fun CirroPageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    /** 标题文字右侧让位宽度：见 [TitleBarEndInset] / [TitleBarCloseInset]。 */
    endInset: Dp = TitleBarEndInset,
    /** 紧贴标题文字的行内动作（「探索」「我的」的刷新键）；null = 无。 */
    titleAction: (@Composable () -> Unit)? = null,
    /** 非 null 则参与「滑进半遮」，滚动时按进度淡出。 */
    collapse: CirroTitleCollapse? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TitleBarRow(
        title = title,
        modifier = modifier,
        endInset = endInset,
        titleAction = titleAction,
        collapse = collapse,
        actions = actions,
    )
}

/**
 * 一级页标题条的**触摸层**：一个和 [CirroPageTitleBar] 逐像素同布局、但**不绘制**的副本。
 *
 * ## 为什么必须有它（2026-10-24）
 *
 * 三个 tab 页的结构是 `Box { 标题条; 内容 }`—— 内容（`LazyColumn` / `verticalScroll`）
 * **铺满整屏**（含标题条那一段，见各页对 `contentPadding` 的注释），而 Compose 的命中测试
 * 只走**绘制顺序最上面的那个兄弟**：内容排在标题条之后 ⇒ 它的 `scrollable` 节点独占了
 * 整块区域的指针事件，标题条里的 `IconButton` 一个都收不到（2026-10-24 真机：
 * 设置键 / 行内刷新键按下有 `MotionEvent`、却没有回调）。
 *
 * 想靠裁剪（[cirroCollapsingSheet] 的 `clipPath`）让内容让出这一段是做不到的：
 * 裁剪只作用于**绘制**，命中测试仍按布局矩形算。
 *
 * ⇒ 在内容**之后**再叠一份同布局、不画任何东西的标题条：它是最上层兄弟，动作键照常可点。
 * 视觉由下层那份负责，这份只吃触摸。
 *
 * ## 为什么视觉那份不改成「也放最上面、再让纸盖住它」
 *
 * 纸必须盖在标题条上（半遮效果的前提），而这套效果是**绘制期**画的；把标题条挪到最上层，
 * 就得反过来再画一次纸 —— 两份纸比两份标题条更容易漂。
 *
 * ## 遮住下层那份**只能靠「不画」，不能靠 `alpha = 0`**
 *
 * 2026-10-24 真机实测：`graphicsLayer { alpha = 0f }` 会把本层连同**被它盖住的下层标题条**
 * 一起从无障碍树里抹掉（`uiautomator dump` 里连「我的」「设置」「刷新」都没有了；
 * 换成 `drawWithContent {}` 才恢复）。所以这里用「跳过绘制」而不是「画成透明」。
 * 副作用是语义只剩本层这一份（下层那份被遮挡算法裁掉），正好不用 `clearAndSetSemantics`——
 * 若哪天两份同时出现，再把语义收进本层、清掉下层那份。
 *
 * ⚠️ 内容滚上去、纸把标题压住之后，本层**仍然可点**（命中测试不看绘制）。
 * 重叠区只有纸顶那 ≈10dp，且落在纸的 28dp 圆角缺口里（那一段没有可点内容），
 * 所以没有按 `progress` 关掉它 —— 那会在补间全程重组。若以后圆角缺口里出现了
 * 可点内容，再考虑在按下那一刻读 `collapse.offsetPx` 判定，而不是组合期读。
 *
 * ⚠️ 只给「内容铺满整屏盖住标题条」的一级页用。详情页（[CirroPaperPage]）的收起键
 * 本来就在浮层里、不在这张纸后面，别顺手加。
 */
@Composable
fun CirroPageTitleBarHitLayer(
    title: String,
    modifier: Modifier = Modifier,
    endInset: Dp = TitleBarEndInset,
    titleAction: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TitleBarRow(
        title = title,
        modifier = modifier
            .drawWithContent { },
        endInset = endInset,
        titleAction = titleAction,
        actions = actions,
    )
}

@Composable
private fun TitleBarRow(
    title: String,
    modifier: Modifier = Modifier,
    endInset: Dp = TitleBarEndInset,
    titleAction: (@Composable () -> Unit)? = null,
    collapse: CirroTitleCollapse? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(CirroTitleBarHeight)
            .padding(start = TitleBarStartInset, end = endInset)
            .then(
                if (collapse == null) {
                    Modifier
                } else {
                    Modifier.graphicsLayer { alpha = collapse.titleAlpha }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
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
                // `weight(1f, fill = false)`：标题按内容宽收紧（行内动作才能贴着它），
                // 但长标题仍会被约束在这一行内、末尾省略，挤不到动作键。
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            titleAction?.let {
                Spacer(Modifier.width(2.dp))
                it()
            }
        }
        actions()
    }
}

/**
 * 标题条里的**行内**小动作键（贴在「探索」「我的」文字之后）。
 *
 * 尺寸比右上角那颗收起键小一档：20dp 图标 / 32dp 触控区。它是标题的一部分，
 * 不是页面级动作，跟着标题一起读；色调用 `onSurfaceVariant`，不与粗体标题抢。
 */
@Composable
fun CirroTitleInlineAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 小标题**开始尝试升起**的收起进度。
 *
 * 取 0.6 而不是等到大标题淡净（0.9）之后：升起的动画得跑得完。只给
 * `progress ∈ (0.9, 1]`（约 5.6dp 的回滚距离）的话，手指一划就冲过去了，小标题只会闪一下。
 * 那 0.6~0.9 这段「大标题还没淡净」怎么办 —— 靠权重交接，见 [CirroMiniTitleBar] 里那个
 * `1f - titleAlpha` 因子。
 *
 * ⚠️ [CirroMiniTitleBar] 当前整体休眠（大标题不再淡出 ⇒ 交接因子恒 0），本阈值暂时不起作用；
 * 复活条件见 [CirroTitleCollapse.titleAlpha] 的文档。
 */
private const val MiniTitleFrom = 0.6f

/**
 * **吸附小标题**：大标题被纸吞掉之后，**往回滚时**从纸底下长出来的那行小字（2026-10-16）。
 *
 * ## ⚠️ 当前处于**休眠**状态（2026-10-16 真机修正后）
 *
 * 本组件在三个一级页仍在组合树里，但**不会显示**：它的不透明度是
 * `升起 × (1 - titleAlpha)`，而 `titleAlpha` 现在恒为 1（标题不再淡出）⇒ 那个因子恒 0。
 * 原因与复活条件见 [CirroTitleCollapse.titleAlpha] 的文档：行程收到条高 1/3（≈29px）之后，
 * 「淡出 + 小标题」这套状态机根本装不进去 —— 小标题的升起窗口只有 `progress ∈ (0.6, 1]`
 * 那十几像素，一次回滚手势三两帧就穿过去了；而标题本来就只剩最下沿被压住，此时再淡出
 * 等于把仍在纸**上方**的字凭空擦掉。**别把它当成「实现漏了」而擅自去改 `titleAlpha`**。
 *
 * ## 为什么只在这一个时机出现
 *
 * 一级页的「探索 / 搜索 / 我的」本来就由底部导航指示，滚到底时标题沉进纸里没任何损失 ——
 * 所以**滚满不留**常驻标题，只有用户**往回翻**（[CirroTitleCollapse.scrollingBack]）时才
 * 冒出来提示「往回滚标题会回来」。这与详情页的情形不同：那边标题是**当前对象的名字**
 * （歌单名），丢失它用户就不知道在看什么，不能照抄这套。
 *
 * ## 出现方式仍然是「半遮」
 *
 * 初位正好**沉在纸顶**（= 标题条下沿），靠 `translationY` 往上浮到标题条位置 —— 而本组件在
 * 页面里排在纸**之前**（更下层），于是它被纸半遮着露出来，先露一截、再露全。和标题沉进去
 * 是同一个动作的反向。普通的 fade in 做不到这个。
 *
 * 顺带这也是它**必须**有足够行程的原因：它出现时要靠纸边切着才叫「半遮」，而纸边最高只能到
 * 标题条上沿（需要行程回到一整条）—— 行程不够，它就整条被压在纸底下，永远露不出来。
 *
 * ## 与真标题「交接」，不是叠加
 *
 * 两个不同字号的字叠在同一处会读成**重影**（不是交叉淡变，是脏）。所以小标题的最终不透明度
 * 不是那个动画值本身，而是它再乘上**「大标题已经让出多少」**（`1f - titleAlpha`）：
 * 大标题还亮着时小标题被压到几乎不可见、也还沉在纸里；大标题淡净的那一刻它正好全亮全升。
 * 于是中途**任何一帧都读不到两个字号的字**，只有一个连续的换位。
 *
 * 透明度归零时本组件**仍然留在组合里**：移出去会把 `animateFloatAsState` 的状态一起丢掉，
 * 淡出过程会变成硬切。代价只是一个不可见的 `Text`，可以忽略。
 *
 * ⚠️ 判定包在 `derivedStateOf` 里而不是直接读 `progress`：那个值**每帧都在变**，直接读等于
 * 让这一行在滚动全程每帧重组。包起来之后它只在**跨越 [MiniTitleFrom] 那一帧**通知一次，
 * 其余时候一次重组都不发生。
 *
 * ⚠️ 交接因子放在 `graphicsLayer` 的 lambda 里算（`reveal.value * (1f - titleAlpha)`），
 * 两者都在**绘制期**读 ⇒ 滚动全程只重绘、不重组。若提到 lambda 外面就成了组合期读值，
 * 每帧重组（而这行本来只是个普通 `Text`，不值这个代价）。
 */
@Composable
fun CirroMiniTitleBar(
    title: String,
    collapse: CirroTitleCollapse,
    modifier: Modifier = Modifier,
) {
    val show by remember(collapse) {
        derivedStateOf { collapse.scrollingBack && collapse.progress > MiniTitleFrom }
    }
    val reveal = animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(Motion.MicroMs, easing = Motion.Standard),
        label = "cirroMiniTitle",
    )
    val risePx = with(LocalDensity.current) { CirroTitleBarHeight.toPx() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(CirroTitleBarHeight)
            .padding(start = TitleBarStartInset)
            .graphicsLayer {
                val a = reveal.value * (1f - collapse.titleAlpha)
                alpha = a
                translationY = (1f - a) * risePx
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
