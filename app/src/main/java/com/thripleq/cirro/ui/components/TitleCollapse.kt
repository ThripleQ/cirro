package com.thripleq.cirro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.theme.CirroShape
import com.thripleq.cirro.ui.theme.Motion

/**
 * 标题条被下方那张圆角纸**压住下沿**的收起效果，全站七个页面共用一份：
 * 探索 / 搜索 / 我的（自建骨架）+ 歌手 / 播客 / 评论（走 [CirroPaperPage]）。
 * **歌单 / 榜单 / 专辑不参与**，理由见 [CirroPaperPage] 的文档。
 *
 * ## 2026-10-16 两次真机修正（改这一块之前先读完这两段）
 *
 * **第一次：限位。** 原来行程取一整条标题条高 ⇒ 纸顶停在标题**上沿**，整条被吃光
 * （用户原话「没有半遮住这个限位，会全遮住」）。改成半个 ⇒ 纸顶停在字形**中线**。
 *
 * **第二次：观感与遮挡量（当前这版）。** 用户随即反馈「这个设计观感并不好」
 * 「遮挡现在遮挡太多了，少一点，让字容易读出来」。原因不是参数没调好，是**思路错了**：
 * 停在字形中线意味着 **50% 的墨迹被切掉**，眼睛会把它读成「渲染被裁了」，而不是设计；
 * 而「跟手位移」又把手指的每一次加速与抖动原样长在纸边上，甩一下就是「啪」地到底。
 * 所以这一版同时改了三件事：
 * 1. **行程收到条高的 1/3**（≈29px）⇒ 纸顶停在标题字形**下沿附近**，约 78% 的墨迹完整可读；
 * 2. **纸不再跟手** —— 两个目标态 + 一条 480ms 补间（见 [CirroTitleCollapse]）；
 * 3. **纸沿那道硬边由三层柔化吃掉**（见 [cirroCollapsingSheet]）。
 *
 * ## 纸顶位置的真机量值（小米 K40，1080×2400）
 *
 * | 量 | px |
 * |---|---|
 * | 状态栏 | 80 |
 * | 标题条 | 88（= `headlineSmall` 行高 32dp） |
 * | 初始纸顶 `sheetTop` | 168 |
 * | 标题**墨迹**实际占 | **98…149** |
 *
 * ⚠️ 最后一行的 98…149 是**逐行扫非底色像素**量出来的，不是字形框（字形框是 80…168）。
 * 「标题还看得见多少」必须按墨迹算 —— 字形框上下那点 leading 里本来就没有墨。
 * ⇒ 纸顶停 139（= 168 − 29）时，墨迹只剩最下 10px 被压住。
 *
 * ⚠️ 别把 `maxPx` 当「标题条高」用。这个数改过两轮（88 → 44 → 29），
 * 每次都是真机量完之后改的；下次要改，先回真机把上表四个数重量一遍。
 */
@Stable
class CirroTitleCollapse internal constructor(
    private val maxPx: Float,
    private val triggerPx: Float,
) {

    /**
     * 纸顶位移的**动画值**（px）。由 [rememberCirroTitleCollapse] 里的补间追
     * [collapsed] 这个目标态。
     *
     * ⚠️ 它**不是**滚动量的函数 —— 这是用户「不要跟手、要柔和」的落点。滚动只负责
     * 「把目标态翻过来」，位置由补间自己走完。
     */
    internal val anim = Animatable(0f)

    private var collapsedState by mutableStateOf(false)

    /**
     * **目标态**：`true` = 收起（纸抬到半遮位）。
     *
     * 只在三个时机翻转：内容上滚过 [triggerPx]、真正回到顶部、初始状态。**往回滚不改它** ——
     * 用户原话「往下滑遮住、往上滑不露（除非到顶）」，就是这条。
     */
    internal val collapsed: Boolean get() = collapsedState

    /** 只喂判定用的上滚累积量（px）。`0` = 起点。 */
    private var scrolled = 0f

    /** 收起进度 `0..1`。0 = 完全展开（标题完整），1 = 纸顶抬到半遮位。 */
    val progress: Float
        get() = if (maxPx <= 0f) 0f else (anim.value / maxPx).coerceIn(0f, 1f)

    /**
     * 是否正在**往回滚**（内容往下走、手指下滑）。
     *
     * 只服务吸附小标题（[CirroMiniTitleBar]，当前休眠）：滚到底后大标题已经沉进纸里了，
     * 往回翻时给它一个名字。只在方向**翻转**那一帧写，滚动途中不产生重组。
     */
    var scrollingBack: Boolean by mutableStateOf(false)
        private set

    /** 纸顶相对初始位置已上移的距离（px）。绘制期读它，不触发重组。 */
    internal val offsetPx: Float get() = anim.value

    /**
     * 标题的不透明度：**恒为 1**。
     *
     * ## 为什么标题自己不淡出
     *
     * 两轮修正之后，纸顶只到标题字形**下沿附近**（约 78% 的墨迹完整露着）—— 这时候
     * 再淡出就是把仍在纸**上方**的字凭空擦掉，比不淡更怪。切口的柔化交给**纸那一层**做
     * （[cirroCollapsingSheet] 的墨迹渐隐带），那里能精确地只雾化贴近纸沿的一小段，
     * 而按进度整体降 alpha 做不到这件事。
     *
     * ## ⚠️ 这条与 [CirroMiniTitleBar] 是互斥的，改之前先读
     *
     * 小标题的可见性 = `升起 × (1 - titleAlpha)`（见该组件）。本值恒 1 ⇒ 那个因子恒 0
     * ⇒ **小标题当前处于休眠状态**。这不是漏掉：本值恒 1 就没有「大标题让出了多少」，
     * 交接因子无从算起，两个不同字号的字叠在同一处会读成**重影**。
     *
     * ⇒ 要复活小标题：把 [titleAlpha] 改回`1f - smoothstep(0.5f, 0.9f, progress)`，
     * 同时把行程调回足够长（小标题的升起窗口 = `progress ∈ (0.6, 1]`，
     * 得留出能看清的位移）—— **两者必须同时改**。
     */
    val titleAlpha: Float get() = 1f

    /**
     * 只**读**滚动量、不消费。挂在外层（`Box`）上接住内部滚动容器的量 ——
     * 各页的滚动容器各不相同（`LazyColumn` / `verticalScroll` / 各自滚的落地页与结果页），
     * 这样谁都不用改。
     *
     * 取 [NestedScrollConnection.onPostScroll] 的 `consumed` 而不是 `onPreScroll` 的
     * `available`：前者是「子级真的滚掉了多少」，后者只是「手指移动了多少」——
     * 用后者会在列表已经到顶（或到底）时继续累积，判定就不准了。
     *
     * ## ⚠️ `y` 的符号（写反了效果就整个反过来）
     *
     * **手指上滑（内容往上走、看后面的内容）时 `consumed.y` 是负的。**
     *
     * 依据不是记忆，是反编译出来的：Material3 的 `EnterAlwaysScrollBehavior` 在
     * `onPreScroll` 里就是 `heightOffset += available.y`，而它的 `heightOffset`
     * 取值是 `[-高度, 0]`、负得越多顶栏越收起 —— 所以「收起」必须由**负**的 y 驱动。
     *
     * ⚠️ 这条只能靠真机确认（JVM 单测跑不了嵌套滚动）。若上机发现「下滑时标题才收」，
     * 就是这个符号，不要改别的。
     *
     * ## 「真正到顶」怎么判
     *
     * 用 `available.y > 0f && consumed.y == 0f` —— 手指还在往下拉，但内容一像素都不动了。
     *
     * ⚠️ 两个条件缺一不可。只看 `consumed.y == 0` 会在**手指停在原地**（既不滑也不动）时
     * 误判成到顶；而 `available.y > 0f` 保证是「正在往下拽」，配合 `consumed.y == 0`
     * 才唯一地指向「已经在顶部、拉不动了」。
     */
    internal val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val dy = consumed.y
            if (dy < 0f) {
                // 上滚：累积（收起判定用）。
                scrolled -= dy
            } else if (dy > 0f) {
                // 往回滚：累积量退回，但**目标态不动** —— 「往上滑不露」。
                scrolled = (scrolled - dy).coerceAtLeast(0f)
            }
            if (dy != 0f) {
                // 同一套符号：往回滚时 y 为正。
                val back = dy > 0f
                if (scrollingBack != back) scrollingBack = back
            }

            val want = when {
                // 到顶（拉不动了）⇒ 复位。这是本效果唯一的「展开」入口。
                available.y > 0f && dy == 0f -> false
                scrolled >= triggerPx -> true
                // 其余情况（含往回滚到半路）保持目标态不变。
                else -> collapsedState
            }
            if (collapsedState != want) collapsedState = want

            // 返回 Zero：本连接纯粹是观察者，一像素都不截留，滚动全给内容。
            return Offset.Zero
        }
    }
}

/** 行程 = 标题条高 ÷ 这个值。3 ⇒ 纸顶停在标题字形下沿附近（真机量值见 [CirroTitleCollapse]）。 */
private const val TravelRatio = 3f

/** 内容上滚这么多（dp）才把目标态翻成「收起」。约半行文字，够从容、又不迟钝。 */
private val CollapseTrigger = 16.dp

/**
 * 收起 / 展开的补间时长。
 *
 * 480ms 是「柔和」的来源：比 [Motion.MicroMs]（200）长一倍多，纸才不是「一下切过去」，
 * 而是一次看得清的呼吸。进出同一条曲线、同一个时长 —— 对称才不显得谁在抢。
 */
private const val CollapseMs = 480

/** 建立一个与页面同生命周期的收起状态（须 hoist 到 Screen，见 [CirroTitleCollapse]）。 */
@Composable
fun rememberCirroTitleCollapse(): CirroTitleCollapse {
    val density = LocalDensity.current
    val maxPx = with(density) { (CirroTitleBarHeight / TravelRatio).toPx() }
    val triggerPx = with(density) { CollapseTrigger.toPx() }
    val collapse = remember(maxPx, triggerPx) { CirroTitleCollapse(maxPx, triggerPx) }

    // 目标态 → 补间。目标态翻转时才重启协程；被打断就停在当前值、从那里继续，
    // 所以来回快速滑不会跳。首次组合以 0 → 0 跑一次，无副作用。
    val collapsed = collapse.collapsed
    LaunchedEffect(collapsed) {
        collapse.anim.animateTo(
            targetValue = if (collapsed) maxPx else 0f,
            animationSpec = tween(CollapseMs, easing = Motion.Standard),
        )
    }
    return collapse
}

/**
 * 接住本页内部滚动容器的滚动量，驱动 [CirroTitleCollapse]。挂在最外层容器上。
 *
 * [collapse] 为 `null` = **本页不参与**这套效果（原样返回，什么都不挂）。收成可空是为了让
 * 调用方（如 [CirroPaperPage]）不必自己写 `if` 分支 —— 「参不参与」就是传不传这个 state。
 */
fun Modifier.cirroTitleCollapse(collapse: CirroTitleCollapse?): Modifier =
    if (collapse == null) this else nestedScroll(collapse.connection)

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
 * 墨迹渐隐带：纸沿**往上**这么宽，纸色由全透明渐到实处。
 *
 * 纸沿经过的地方从此是**已经淡掉的墨**，而不是一个被切开的实心字 —— 这是消灭
 * 「渲染被裁了」观感的关键那一层。取 5dp（K40 上 ≈14px）：够眼睛读成渐变，又短到
 * 不啃掉上方那些本该完整的笔画。
 */
private val FadeBand = 5.dp

/**
 * 内容浮出带：纸沿**往下**这么宽，纸色由实处渐到全透明。
 *
 * 内容从纸底下**浮**出来，而不是在纸沿那一行「啪」地出现/消失。代价是内容顶部让出
 * 这 6dp 换那条线不再是刀口。
 */
private val SoftBand = 6.dp

/** 纸沿投影带：纸沿**往上**这么宽的一道极淡黑。 */
private val ShadowBand = 8.dp

/**
 * 纸沿投影的浓度。5% 是量出来的：再深纸沿就成了「一条灰线」（那还是线，只是换了颜色）；
 * 再浅看不出纸有厚度。它把「一块色块的边界」读成「一张压在上面的纸」。
 */
private const val ShadowAlpha = 0.05f

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
 * ## 纸沿那三层柔化（2026-10-16 用户：「交界处要有过渡效果」）
 *
 * 三层都**只吃纸顶那一条带子**，标题条高度、纸顶落点、`maxPx` 全都不动 —— 几何一像素不改，
 * 改的全是那条缝。三层都乘 `progress`（= 纸抬起的程度）⇒ **静止时三层都关着**，
 * 展开态的观感与改造前逐像素相同。
 *
 * 1. **墨迹渐隐带**（纸沿往上 [FadeBand]）：被纸沿经过的墨是已淡掉的墨，不是被切断的字；
 * 2. **纸沿投影**（纸沿往上 [ShadowBand]，黑 [ShadowAlpha]）：把色块边界读成一张纸的厚度；
 * 3. **内容浮出带**（纸沿往下 [SoftBand]）：内容从纸底下浮出来，不是在纸沿处硬出现。
 *
 * ## ⚠️ 三条带子都必须**跟着纸的轮廓走**，不能是横条
 *
 * 2026-10-16 用户：「你加的效果是一条直线，跟我们的圆角纸不匹配」。28dp 圆角吃掉左右
 * **各 76px**（K40 实测：x=2 处纸缘比中间低 60px、x=40 处低 10px、x=76 起才回到纸顶）。
 * 而横条的满强度下沿停在 `paperTop` —— 圆角缺口里那一行往下还全是容器色、纸缘的弧线在
 * 更下面，于是缺口里浮出一条直线段，一眼就看出「线和纸不是一伙的」。三条各自的做法：
 *
 * - ①②（纸沿**之上**）：把纸的轮廓**整体上抬一个带宽**再原样填满。落在纸下面那一大块
 *   （x 整宽、y 到 `height - band`）会被紧接着画的不透明纸（`surface`）盖掉 —— 抬高后的
 *   轮廓在纸沿以下处处比纸**窄**（圆弧往内收），漏不出来。⇒ 真正可见的只剩贴边那一圈弯带：
 *   中间是直的一段，两端顺着圆弧弯下去，与纸的圆角**严格同形**。不需要布尔运算，也不必用
 *   `ClipOp.Difference`（那个要 API 26+）；
 * - ③（纸沿**之下**）：本来就必须画在 `clipPath(path)` 里 —— 它是盖在内容上的一层雾，
 *   出了裁剪就会糊到纸的圆角外面去。
 *
 * ⇒ 绘制顺序：①② 画在纸**之前**（靠后画的不透明纸替它们盖掉多余部分），③ 画在内容**之后**。
 * **别把 ①② 改回带 clip 的横条** —— 那正是这次被用户看出来的毛病。
 *
 * ## 读 state 的位置
 *
 * `offsetPx` / `progress` 只在 `drawWithContent` 里读 ⇒ 滚动与补间期间
 * **只重绘、不重组、不重新布局**。这是详情页 `washExit` / `headerScrollPx`
 * （`TrackListScreen`）已经跑通的同一条路子。[Path] 提到外面复用，避免每帧新建。
 *
 * ## 圆角为什么不用收平
 *
 * 纸的 28dp 圆角只吃最外两侧各 28dp，而标题左内缩 [TitleBarStartInset] = 24dp ——
 * 在 x=24dp（K40 上 66px）那一处，圆弧只切进约 **2px**（真机实测：x=2 处低 60px、
 * x=20 处低 26px、x=60 处低 2px），擦不到标题。硬把圆角收平反而丢掉纸感。
 * ⇒ 圆角一律留着，柔化层**跟着它弯**（见上一节）。
 *
 * ## [collapse] 为 null
 *
 * 纸静止在初始纸顶 —— 就是「不参与滑进半遮」的那张纸。与直接 `clip + background` 的画法
 * 逐像素等价，但**内容的契约完全一致**（照样铺满整屏、自己按 `sheetTop` 让位），
 * 所以调用方两种情况下写同一份内容代码。
 *
 * @param collapse 本页的收起点；`null` = 不参与（纸不动）。须 hoist 到 Screen，见 [CirroTitleCollapse]。
 * @param sheetTop 初始纸顶（本屏坐标），取 [rememberCirroSheetTop]。
 */
@Composable
fun Modifier.cirroCollapsingSheet(
    collapse: CirroTitleCollapse?,
    sheetTop: Dp = rememberCirroSheetTop(),
): Modifier {
    val paper = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val radiusPx = with(density) { CirroShape.SheetRadius.toPx() }
    val topPx = with(density) { sheetTop.toPx() }
    val fadePx = with(density) { FadeBand.toPx() }
    val softPx = with(density) { SoftBand.toPx() }
    val shadowPx = with(density) { ShadowBand.toPx() }
    val transparentPaper = paper.copy(alpha = 0f)
    val path = remember { Path() }
    val lifted = remember { Path() }
    return drawWithContent {
        val paperTop = topPx - (collapse?.offsetPx ?: 0f)
        val k = collapse?.progress ?: 0f
        val corner = CornerRadius(radiusPx, radiusPx)

        // ① 墨迹渐隐带 + ② 纸沿投影：都在纸沿**之上**，画在纸与内容之前。
        //
        // ⚠️ 这两条**不是横贯屏幕的直条**，而是「把整张纸的轮廓上抬一个带宽」之后的**那一圈
        // 贴边弯带**。2026-10-16 用户：「你加的效果是一条直线，跟我们的圆角纸不匹配」——
        // 直条在两端圆角处会**悬空**：真机量到 28dp 圆角吃掉左右各 76px，x=2 处纸缘比中间
        // 低 60px，而直条的满强度下沿还停在 `paperTop`，于是圆角缺口里浮出一条直线段
        // （缺口下方只剩容器色，纸缘的弧线才在更下面），一眼就是「线和纸不是一伙的」。
        //
        // 做法：把纸的轮廓整体上抬 band 后**原样填满**。它落在纸**下面**的那一大块（x 方向
        // 整宽、y 方向一直到 `height - band`）会被紧接着画的不透明纸（`surface`）完全盖掉 ——
        // 因为抬高后的轮廓在纸沿以下处处**比纸窄**（圆弧往内收），漏不出来。于是真正可见的
        // 只剩贴边那一圈：中间是直的一段，两端顺着圆弧弯下去，和纸的圆角严格同形。
        // 这样不需要布尔运算，也不用 `ClipOp.Difference`（那个要 API 26+）。
        if (k > 0f) {
            lifted.rewind()
            lifted.addRoundRect(
                RoundRect(
                    rect = Rect(0f, paperTop - fadePx, size.width, size.height - fadePx),
                    topLeft = corner,
                    topRight = corner,
                ),
            )
            drawPath(
                path = lifted,
                brush = Brush.verticalGradient(
                    colors = listOf(transparentPaper, paper.copy(alpha = k)),
                    startY = paperTop - fadePx,
                    endY = paperTop,
                ),
            )
            lifted.rewind()
            lifted.addRoundRect(
                RoundRect(
                    rect = Rect(0f, paperTop - shadowPx, size.width, size.height - shadowPx),
                    topLeft = corner,
                    topRight = corner,
                ),
            )
            drawPath(
                path = lifted,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0f),
                        Color.Black.copy(alpha = ShadowAlpha * k),
                    ),
                    startY = paperTop - shadowPx,
                    endY = paperTop,
                ),
            )
        }

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

            // ③ 内容浮出带：纸沿往下由纸色渐到全透明，盖在内容上。
            if (k > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(paper.copy(alpha = k), transparentPaper),
                        startY = paperTop,
                        endY = paperTop + softPx,
                    ),
                    topLeft = Offset(0f, paperTop),
                    size = Size(size.width, softPx),
                )
            }
        }
    }
}
