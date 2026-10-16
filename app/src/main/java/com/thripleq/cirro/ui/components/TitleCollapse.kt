package com.thripleq.cirro.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 一级页大标题的「滑进半遮」：内容上滑时，下方那张圆角纸的顶边跟着抬起来，
 * 从下往上**把标题吞掉** —— 中途必然出现「标题只剩上半截露着」的那一帧。
 *
 * ## 为什么是「吞掉」而不是「滑走」
 *
 * 标题**不位移、不缩放、不逃**。它只是被纸盖住。于是「半遮」不是一个单独做的动画，
 * 而是几何上必然出现的中间态：纸顶抬到标题中线时，标题正好露出上半截。手滑多快、
 * 往回翻多少，这一帧都自动成立，不存在「动画跑到一半被打断」的错位。
 *
 * ## 唯一的状态就是一个像素数
 *
 * [offsetPx] 只有它一个自由度，由滚动量累积而来（见 [connection]），夹在 `0..maxPx`。
 * 所有视觉量（纸顶位置、标题透明度）都由它算出来，**没有第二条时间线**，所以：
 * - 滚回顶部必然精确复原（不像「播一段动画」那样会被中途打断）；
 * - 切 tab 后滚回来，只要拿着同一个 state，收起状态就跟着滚动位置一起回来。
 *
 * ⚠️ 也正因为是**累积**而不是「从滚动容器的位置反推」，state 必须 hoist 到 Screen 层
 * （与 `LazyListState` 同级）：`AnimatedContent` 开合面板时会重组网格，留在内层会被清零，
 * 而列表还停在中间 —— 标题会自己弹回来。
 *
 * ## 上限 = 标题条高度
 *
 * 抬到上限时纸顶正好落在**状态栏下沿**，于是状态栏那一条始终是容器色（图标一直有底，
 * 不会糊在纸上）。这也意味着这套效果最多让出 [CirroTitleBarHeight] 的视野。
 */
@Stable
class CirroTitleCollapse internal constructor(private val maxPx: Float) {

    private var offset by mutableFloatStateOf(0f)

    /** 收起进度 `0..1`。0 = 完全展开（标题完整），1 = 纸顶抵住状态栏下沿。 */
    val progress: Float
        get() = if (maxPx <= 0f) 0f else (offset / maxPx).coerceIn(0f, 1f)

    /**
     * 是否正在**往回滚**（内容往下走、手指下滑）。
     *
     * 只服务吸附小标题（[CirroMiniTitleBar]）：滚到底后大标题已经沉进纸里了，往回翻时给
     * 它一个名字 —— 提示「往回滚标题会回来」，而不是让人对着一整张纸发呆。
     *
     * 只在方向**翻转**那一帧写，滚动途中不产生重组。
     */
    var scrollingBack: Boolean by mutableStateOf(false)
        private set

    /** 纸顶相对初始位置已上移的距离（px）。绘制期读它，不触发重组。 */
    internal val offsetPx: Float get() = offset

    /**
     * 标题的不透明度：**恒为 1**。
     *
     * ## 为什么不再淡出（2026-10-16 真机修正）
     *
     * 原来是「先遮、后淡」（0.5 起淡、0.9 淡净），前提是纸能把标题**整个**盖住。行程减半后
     * （见 [rememberCirroTitleCollapse]）纸顶只到标题**中线**，淡净就变成了**凭空消失** ——
     * 实测限位处标题带里 0 行墨迹，用户看到的仍是他抱怨的那个「全遮住」。
     *
     * 用户的话是这个效果的判据：「上滑的时候**滑进半遮**」「就是被纸**吞掉一半**」
     * ⇒ 终态就应该是**上半截留着**。所以淡出整个去掉，标题保持全不透明，由纸**切**掉下半截
     * —— 那道切口就是「半遮」本身，不是瑕疵。
     *
     * ## ⚠️ 这条与 [CirroMiniTitleBar] 是互斥的，改之前先读
     *
     * 小标题的可见性 = `升起 × (1 - titleAlpha)`（见该组件）。本值恒 1 ⇒ 那个因子恒 0
     * ⇒ **小标题当前处于休眠状态**。这不是漏掉，是行程减半后**两条路只能选一条**：
     * - 行程 44px 里，「淡出 + 小标题」的状态机挤在 offset 22…44 这 22px 内（小标题可见窗口
     *   仅约 18px），一次真实回滚手势三两帧就穿过 —— 等于仍然看不见；
     * - 而「标题上半截留着」在**任何**行程下都自洽。
     *
     * ⇒ 要复活小标题：把行程改回一整条（[rememberCirroTitleCollapse]）并把本值改回
     * `1f - smoothstep(0.5f, 0.9f, progress)`（0.5 = 纸顶压到标题中线、半遮最好看的那一帧，
     * 淡出若提前开始「半遮」就没得看；0.9 = 纸顶越过标题上沿，必须淡净，否则被纸切出硬边）
     * —— **两者必须同时改**，否则小标题永远不出现、或两个不同字号的字叠在同一处读成**重影**
     * （它们位置完全相同）。
     */
    val titleAlpha: Float get() = 1f

    /**
     * 只**读**滚动量、不消费。
     *
     * 挂在外层（`Box`）上接住内部滚动容器的量 —— 三个 tab 页的滚动容器各不相同
     * （`LazyColumn` / `verticalScroll` / 各自滚的落地页与结果页），这样谁都不用改。
     *
     * 取 [NestedScrollConnection.onPostScroll] 的 `consumed` 而不是 `onPreScroll` 的
     * `available`：前者是「子级真的滚掉了多少」，后者只是「手指移动了多少」——
     * 用后者会在列表已经到顶（或到底）时继续累积，标题收起了却回不来。
     *
     * ## ⚠️ `y` 的符号（写反了效果就整个反过来）
     *
     * **手指上滑（内容往上走、看后面的内容）时 `consumed.y` 是负的。**
     *
     * 依据不是记忆，是反编译出来的：Material3 的 `EnterAlwaysScrollBehavior` 在
     * `onPreScroll` 里就是 `heightOffset += available.y`，而它的 `heightOffset`
     * 取值是 `[-高度, 0]`、负得越多顶栏越收起 —— 所以「收起」必须由**负**的 y 驱动。
     * 因此这里用**减**：向上滑 y 为负 ⇒ `offset` 增大 ⇒ 纸顶上抬。
     *
     * ⚠️ 这条只能靠真机确认（JVM 单测跑不了嵌套滚动）。若上机发现「下滑时标题才收」，
     * 就是这个符号，不要改别的。
     */
    internal val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (consumed.y != 0f) {
                offset = (offset - consumed.y).coerceIn(0f, maxPx)
                // 同一套符号：往回滚（看前面的内容）时 y 为正。
                val back = consumed.y > 0f
                if (scrollingBack != back) scrollingBack = back
            }
            // 返回 Zero：本连接纯粹是观察者，一像素都不截留，滚动全给内容。
            return Offset.Zero
        }
    }
}

// 淡出的两个阈值（0.5 起淡 / 0.9 淡净）与 `smoothstep` 随 2026-10-16 的行程减半一并删除 ——
// 纸只到标题中线、盖不满标题，没有淡出的场合。公式与恢复条件见 [CirroTitleCollapse.titleAlpha]。

/** 建立一个与页面同生命周期的收起状态（须 hoist 到 Screen，见 [CirroTitleCollapse]）。 */
@Composable
fun rememberCirroTitleCollapse(): CirroTitleCollapse {
    // ⚠️ 行程取**半个**标题条高，不是一整条 —— 纸顶停在标题条**中线**，让出上半条。
    //
    // 2026-10-16 真机反馈修正（用户原话：「没有半遮住这个限位，会全遮住」）。小米 K40 实测：
    // 状态栏 80px、标题条 88px、sheetTop 168px、标题字形框 80…168（**中线 124**）。
    // 原来 maxPx = 88 ⇒ 纸顶停在 y=80，正好是标题**上沿** ⇒ 整条被吃光；取一半则停在 124，
    // 标题正好只剩上半截 —— 这才是用户要的「半遮」。
    //
    // 第二件事（同一根因）：半遮也决定了**小标题有没有地方露脸**。`CirroMiniTitleBar` 在
    // `progress > 0.6` 才出现，即 offset > maxPx×0.6。行程 88 时那个门槛是 52.8 ⇒ 纸顶最高
    // 只能退到 y=115，而小标题的字占到 104 —— 只剩 ~11px 上沿能露出来，等于永远看不见
    // （用户反馈的「恢复露出才发生」）。行程减半后门槛降到 26.4，小标题有完整的露出行程。
    //
    // ⇒ 以后**别**把 maxPx 当「标题条高」用；它是「半遮位」。要改先回真机量上面四个数。
    val maxPx = with(LocalDensity.current) { (CirroTitleBarHeight / 2).toPx() }
    return remember(maxPx) { CirroTitleCollapse(maxPx) }
}

/**
 * 接住本页内部滚动容器的滚动量，驱动 [CirroTitleCollapse]。挂在最外层容器上。
 */
fun Modifier.cirroTitleCollapse(collapse: CirroTitleCollapse): Modifier =
    nestedScroll(collapse.connection)

/**
 * **初始纸顶**（相对本屏顶）：状态栏让位 + 标题条高度 = 标题条下沿。
 *
 * 读的是**当前上下文里尚未被消费**的 `statusBars` —— 这正是 [CirroPageTitleBar] 里
 * `statusBarsPadding()` 会拿到的同一个值，所以两者必然一致，不会一个让位一个不让：
 * - **壳路径**（三个 tab 页）：壳已经替内容让开过状态栏，这里读出来是 0 ⇒ 纸顶 = 标题条高；
 * - **非壳路径**：读出来是真实状态栏高 ⇒ 纸顶 = 状态栏 + 标题条高。
 *
 * 不在这里改用平台 root insets（`rememberStatusBarTop`）：那个是给「壳盖住了本屏顶部、
 * 要把色条补画到屏幕顶」用的，量的是屏幕绝对坐标；本值要的是**本屏坐标系**里的位置，
 * 两者在壳路径下差一个状态栏高，混用会把纸顶顶到屏幕外。
 */
@Composable
fun rememberCirroSheetTop(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheet = statusBar + CirroTitleBarHeight
    return sheet
}

/**
 * 「当前纸顶」相对本屏顶的位置（px）提供器 —— 喂给 [SharedSourceGuard] 的 `viewportModifier`。
 *
 * 纸顶会随 [collapse] 上抬，共享元素的可见性判定必须跟着它走，否则滚进标题条那一段的
 * 卡片会被当成「完整可见」，点开就从纸底下飞出一张封面。不参与该效果的页面不传即可。
 *
 * 读 [CirroTitleCollapse.offsetPx] 的动作发生在**点击那一刻**（`isFullyVisible` 内），
 * 不会因为滚动而触发重组。
 */
@Composable
fun rememberSheetViewportTop(collapse: CirroTitleCollapse, sheetTop: Dp): () -> Float {
    val topPx = with(LocalDensity.current) { sheetTop.toPx() }
    return remember(collapse, topPx) { { topPx - collapse.offsetPx } }
}

/**
 * 内容圆角纸：**顶边可随 [CirroTitleCollapse] 上抬**的那张纸（纸色 + 28dp 顶角 + 裁剪）。
 *
 * ## 为什么是一个绘制期的 Modifier，而不是「把纸摆到新位置」
 *
 * 纸顶要用作**内容的裁剪边界**，而不是一个会移动的容器。若改成把纸（连同里面的内容）
 * 整体上移，内容就会被多推一份 —— 手指滑 32dp，内容会走 64dp，手感立刻不对。
 * 所以这里**只画**：纸色从 `顶边` 铺到底，内容裁在这个形状内。内容的布局位置一像素不动，
 * 上移纯属绘制。
 *
 * 顺带这也让它天然正确：纸顶抬起让出的那一段，滚上来的内容会自己填进去（因为内容确实
 * 在往上滚），不会留白。
 *
 * ## 读 state 的位置
 *
 * `offsetPx` 只在 `drawWithContent` 里读 ⇒ 滚动时**只重绘、不重组、不重新布局**。
 * 这是详情页 `washExit` / `headerScrollPx`（`TrackListScreen`）已经跑通的同一条路子。
 * [Path] 提到外面复用，避免每帧新建。
 *
 * ## 圆角为什么不用收平
 *
 * 纸的 28dp 圆角只吃最外两侧各 28dp，而标题左内缩 [TitleBarStartInset] = 24dp ——
 * 在 x=24 那一处，圆弧只切进约 **0.3dp**，擦不到标题。硬把圆角收平反而丢掉纸感。
 *
 * @param sheetTop 初始纸顶（本屏坐标），取 [rememberCirroSheetTop]。
 */
@Composable
fun Modifier.cirroCollapsingSheet(
    collapse: CirroTitleCollapse,
    sheetTop: Dp = rememberCirroSheetTop(),
): Modifier {
    val paper = MaterialTheme.colorScheme.surface
    val radiusPx = with(LocalDensity.current) { CirroShape.SheetRadius.toPx() }
    val topPx = with(LocalDensity.current) { sheetTop.toPx() }
    val path = remember { Path() }
    return drawWithContent {
        val paperTop = topPx - collapse.offsetPx
        val corner = CornerRadius(radiusPx, radiusPx)
        path.rewind()
        path.addRoundRect(
            RoundRect(
                rect = Rect(0f, paperTop, size.width, size.height),
                topLeft = corner,
                topRight = corner,
            ),
        )
        clipPath(path) {
            drawRect(
                color = paper,
                topLeft = Offset(0f, paperTop),
                size = Size(size.width, size.height - paperTop),
            )
            this@drawWithContent.drawContent()
        }
    }
}
