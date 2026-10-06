package com.thripleq.cirro.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.cirro.ui.components.CirroTitleBarHeight
import com.thripleq.cirro.ui.components.coverSizedUrl
import kotlin.math.roundToInt

/**
 * 状态栏高度。
 *
 * 读的是**平台**的根 insets（[ViewCompat.getRootWindowInsets]），不是 Compose 的
 * `WindowInsets.statusBars`：壳路径里内容已被壳用 `statusBarsPadding()` 整体让开过，
 * Compose 的 insets 在内容这一层读出来是 0（那份已被消费），拿不到真实高度。
 */
@Composable
internal fun rememberStatusBarTop(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val px = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        with(density) { px.toDp() }
    }
}

/**
 * 壳路径：把本屏的容器色条**往上补到屏幕顶**（补 [heightPx] 那一条，色 = [color]）。
 *
 * 壳（`ShellPanel` / `ExpandableShell`）会用 `statusBarsPadding()` / `shellTopInset` 把内容整体
 * 推到状态栏之下，于是本屏的顶边就在状态栏下沿 —— 而屏顶那一条由壳画成它的 `containerColor`
 * （`surface`，近黑），与本屏在标题条铺的 `surfaceContainer` 不同色：不补就是「黑 + 灰」两段、
 * 中间一道横贯全屏的硬分界。搜索页没有壳、自己不躲状态栏，所以那边天然是一条连续容器色。
 *
 * 做法是**画到自己边界之外**（`topLeft.y` 取负）：壳给的是 padding、不是 `clip`，所以这截画得
 * 出来 —— 与本屏早先糊底用 `offset(y = -statusBarTop)` 补状态栏是同一套办法。代价是上游一旦
 * 改成 `clip`，这截会被裁掉（届时改为把色条交给壳来画）。
 */
internal fun Modifier.shellStatusBarBand(color: Color, heightPx: Float): Modifier = drawBehind {
    if (heightPx <= 0f) return@drawBehind
    drawRect(
        color = color,
        topLeft = Offset(0f, -heightPx),
        size = Size(size.width, heightPx),
    )
}

/**
 * 页底：封面柔焦放大 + 主题色蒙版。
 *
 * 复用人物 hero 那套（放大 + `blur` + 同色渐隐）——同一份「不受控位图当背景」的解法不必写两遍。
 * 低清解码（[BackdropRequestSize]）再放大本身就糊，`blur` 在 API<31 是 no-op 也退化成低清放大，
 * 两档都不崩。
 *
 * **放大走布局**（格子比可视带大一个 [BackdropScale]、上下各顶出去 [BackdropBleed]），不走
 * `graphicsLayer` 的 `scale`：图层放大不改变布局边界，多出来的那截等于「画到自己格子之外」，
 * 而蒙版只按格子铺 —— 溢出的上沿一旦落在可视区里（壳路径把内容整体让开状态栏，正是如此）
 * 就是一条没过蒙版的亮色。改成布局边界后位图与蒙版范围严格同一，谁也不越界。
 *
 * 蒙版用 `surface` 而非固定黑：明暗两套各自把封面压到「透出一点封面色」的程度，
 * 头部文字因此始终用主题墨色（onSurface / onSurfaceVariant），不必为浅色主题另备一套白字。
 *
 * ## 裁在面板上沿（2026-10-03）
 *
 * 糊底不是铺满整屏的：它到**面板上沿 + 一个纸角半径**（「播放全部」行的顶边再往下让一个
 * 圆角，见 `panelTopInRoot`）为止。这不是省事，是那一行四个圆角能成立的前提 —— 面板行是
 * 吸顶的、直接压在这块糊底上，糊底一天铺到面板这一带，行上切掉的圆角就一天透出封面
 * （用户：「没有补色露的是封面背景，把背景也削掉」）。
 *
 * 让出这一个半径是为了上边两角：那一对角就是面板这张纸的顶角，圆角之外该是头部的封面空气，
 * 且空气正好绕到弧的终点为止 —— 那一处凹口宽度收到 0，**裁切线本身不会露出来**。
 * 再往下是面板内部，那一行**下边两角**切开的凹口落在裁切线之下，露出壳子的 `surface`（纸面）。
 *
 * 裁切线末端 [BackdropFade] 那一段把封面空气化进纸面，接上「面板以下是不透明面」这条规矩。
 */
@Composable
internal fun TrackListBackdrop(
    coverUrl: String?,
    exit: State<Float>,
    /** 头部当前滚出的像素量（0..头部高度）。退场位移按它以 [BackdropParallax] 折算。 */
    parallaxPx: () -> Float,
    panelTopInRoot: () -> Float,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    // 退场后化入的底色：顶部那条容器色（见下面那条底盒）。蒙版颜色也从它插值而来。
    val container = MaterialTheme.colorScheme.surfaceContainer
    // 退场位移：糊底不只淡出，还往上让开一段 —— 读起来是「封面色跟着头部走掉」，
    // 而不是眼看着内容从一块不动的色块上滑过去。
    //
    // 2026-10-03：让开的量改成**视差**（跟头部实际滚出量成正比、取其一半），不再是固定终值
    // —— 见 [BackdropParallax]。
    val blurPx = with(LocalDensity.current) { BackdropBlur.toPx() }
    val blurGrowPx = with(LocalDensity.current) { BackdropBlurGrow.toPx() }
    val fadePx = with(LocalDensity.current) { BackdropFade.toPx() }
    // 裁切线比面板上沿再往下让一个纸角半径：面板行的**上边两角**就是面板这张纸的顶角，
    // 圆角之外本来就该是头部的封面空气 —— 让这一个半径，空气正好绕到弧的终点为止
    // （那一处凹口宽度收到 0），裁切线于是**不可能露出来**；再往下才是面板内部，
    // 那一行**下边两角**切开的凹口落在裁切线之下，露出的就是壳子的 `surface`（纸面）。
    //
    // 一个半径够不够，取决于行高 > 2×半径（实测 68dp 内容 ≫ 2×28dp），正常取不到边界。
    val cornerPx = with(LocalDensity.current) { TrackListMetrics.SheetCorner.toPx() }
    // 自己在根坐标系里的 y：把回传的「面板上沿」换算成本地裁切高度。
    // 走布局回调、draw 阶段读，滚动时每帧只引发重绘。
    var selfTopInRootPx by remember { mutableFloatStateOf(0f) }
    // 位图与蒙版共用同一套「格子」：都要盖住放大后的整块范围。
    val band = Modifier
        .fillMaxWidth()
        .height(BackdropHeight + BackdropBleed * 2)
        .offset(y = -BackdropBleed)
        // 两个 offset 叠加：固定让开（上一条）+ 滚动视差（这条）。视差量读在 layout 阶段，
        // 滚动时每帧只重排这一层。
        .offset { IntOffset(0, -(parallaxPx() * BackdropParallax).roundToInt()) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { selfTopInRootPx = it.positionInRoot().y }
            // `drawWithContent` 在 `background` **之前**：底色也得待在裁切线以内，
            // 线以下交给壳子的 `surface`（同色，无缝）。
            .drawWithContent {
                val bottom = panelTopInRoot() - selfTopInRootPx + cornerPx
                if (bottom >= size.height) {
                    // 还没量到（首帧给的正无穷）或面板整个在壳子之外：整块照画。
                    this@drawWithContent.drawContent()
                } else {
                    val cut = bottom.coerceAtLeast(0f)
                    clipRect(right = size.width, bottom = cut) {
                        // `clipRect` 的接收者是 DrawScope，`drawContent` 挂在 ContentDrawScope 上，
                        // 这里必须显式指明外层的接收者。
                        this@drawWithContent.drawContent()
                        if (cut > 0f) {
                            // 收口：把封面空气在裁切线之前化进纸面，线本身于是不留硬边。
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, surface),
                                    startY = cut - fadePx,
                                    endY = cut,
                                ),
                                topLeft = Offset(0f, cut - fadePx),
                                size = Size(size.width, fadePx),
                            )
                        }
                    }
                }
            }
            .background(surface),
    ) {
        // 糊底自己的退场底色：透明度跟着糊底退场进度走（`exit`），到 1 时整块化进
        // `surfaceContainer`（= 顶部那条容器色，收起后不露白）。
        //
        // 2026-10-03 勘误：这条原先写着「= 圆角纸两角外露出、吸顶后 = 状态栏那一带」——
        // 那是糊底还铺在纸**外面**那套的说法。糊底挪进壳子（纸的里面）之后，那两个位置都
        // 不在这里了：纸角由壳子的 `clip(paperShape)` 裁掉，状态栏那一条由本屏自己往上补
        // （见 [shellStatusBarBand]）。
        //
        // 顺序要紧：`graphicsLayer`（alpha）必须在 `background` **之前** —— 它是外层图层，
        // 写到后面背景就画在图层之外、alpha 完全不生效（这条底盒刚写时正是这么错的，
        // 底色同为 `surface` 才没露馅）。
        //
        // 底色走 `container` 而不是 `surface`：它只在蒙版顶部那 14% 的余量里透出来，与蒙版
        // 同色即可 —— 两层同源，退场过程中这一带的色相才不会分叉。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = exit.value }
                .background(container),
        )
        if (coverUrl != null) {
            val context = LocalContext.current
            val model = remember(coverUrl) {
                val sized = coverSizedUrl(coverUrl, BackdropRequestSize) ?: coverUrl
                ImageRequest.Builder(context).data(sized).size(BackdropRequestSize).build()
            }
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 无色（2026-10-03 用户：「歌单列表的那个模糊效果蒙版弄成无色的」）：
                // 只取封面的**明暗**、丢掉色相。糊底是大面积底层，之前它把封面的色相
                // 一路铺到状态栏一带，整块头图都跟着封面变色；去掉色相后它就是一团中性
                // 的灰雾，封面色只留在圆钮身后那一小团（见 [rememberCoverAccent]）。
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                // 化开：头部滚走时**越走越模糊 + 略微放大**（2026-10-03「糊底随滚动化开」）。
                // 两者都在绘制层做，不改布局尺寸。
                //
                // 用 graphicsLayer 的 renderEffect 而不是 `Modifier.blur(r)`：后者是 modifier
                // 参数，改它会重建整条链、每帧重组这个 Image；前者在绘制阶段读值，只重绘。
                // API < 31 没有 RenderEffect，优雅降级为不模糊 —— 与 Compose 的 `Modifier.blur`
                // 在低版本上的表现一致。
                modifier = band.graphicsLayer {
                    val e = exit.value
                    alpha = 1f - e
                    if (Build.VERSION.SDK_INT >= 31) {
                        val r = blurPx + blurGrowPx * e
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    }
                    val s = 1f + BackdropSpread * e
                    scaleX = s
                    scaleY = s
                },
            )
        }
        // 蒙版：把封面压到「当前底色」。**它不能跟着图像一起淡出。**
        //
        // 2026-10-03 修：上一版给这条也套了 `alpha = 1f - exit.value`，于是图像与蒙版共用同一个
        // 淡出系数，封面的可见份额变成
        //
        //     (1-e) × [1 − 0.86(1-e)]
        //
        // —— 一条关于 e **开口向下的抛物线**：静止时 0.14，滚到 e≈0.44 反涨到 0.29（两倍多），
        // 然后再归零。真机上读到的就是「先亮后暗」：封面色先漾出来、再整体淡掉。
        // 那个 0.14 是「顶部蒙版 0.86 让出的余量」，它只该随**图像**的淡出而缩小；蒙版自己也
        // 衰减一遍，等于把余量放大 —— 两块衰减相乘，中段必然反涨。
        //
        // 正解：**只让图像退场**，蒙版始终满强度，退场改由它的**颜色**承接 —— 底色从 `surface`
        // 插值到 `container`（与下面那条底盒同源）。可见份额于是回到 0.14 × (1-e)，严格单调。
        // 两个端点与改前一致：静止 = 0.86 surface + 0.14 封面；滚完 = 容器色。
        //
        // 色值在 draw 阶段读，滚动只重绘这一层、不重组。所以用 `drawBehind` 而不是
        // `background(brush)`：后者在组合期构造 brush，读 `exit.value` 就成了每帧重组。
        Box(
            band.drawBehind {
                val paper = lerp(surface, container, exit.value)
                drawRect(
                    brush = Brush.verticalGradient(
                        // 顶部压得比中段重：状态栏图标与返回键都落在这一段，且封面可能是纯亮图
                        // （白封面时 0.78 会让白图标糊在浅灰上）；中段留住封面色相即可。
                        0f to paper.copy(alpha = 0.86f),
                        0.55f to paper.copy(alpha = 0.90f),
                        1f to paper,
                    ),
                )
            },
        )
    }
}

/* ── 顶部标题条 ────────────────────────────────────────────────── */

/**
 * 顶部标题条：**铺在容器色上的一条透明带**，不画底、不切圆角 —— 与搜索页「搜索」大标题
 * 同一种做法（那边也是标题直接铺 `surfaceContainer`，圆角只由下面那张纸给）。
 *
 * 圆角纸的顶盖在**面板首行**（「播放全部」）那一档，不在这一条。早先这条也切了同半径的圆角、
 * 还自带 `surface` 底，于是屏上同时立着两个圆角顶（标题条一个、纸一个），中间夹出一道束腰，
 * 且这条的 `surface` 与圆角外露出的 `surfaceContainer` 还对不上色。现在底交给
 * [TrackListBackdrop] 的容器色条、圆角交给纸，站内三张纸的「容器色条 + 圆角纸」才真正一致。
 *
 * [label]（类型：歌单 / 榜单 / 专辑 / 喜欢 / 已购）与 [title]（实际标题）**叠在同一格**交叉淡变：
 * 换字进度由 [reveal]（糊底退场进度，头部一滚走就是 1）重映射，两个 alpha 都读在 draw 阶段，
 * 滚动只重画不重组。长标题单行截断（`maxLines = 1` + Ellipsis）。
 *
 * 左右内缩由调用方给（[textStart] / [textEnd]）：浮层控件在哪一侧，文字就让开哪一侧 ——
 * 壳路径的收起键在**右上角**，nav 路径的返回键在左上，同一条标题条两处反过来用。
 */
@Composable
internal fun TrackListTitleBar(
    label: String,
    title: String,
    reveal: State<Float>,
    /** 文字左内缩：让开左上角的浮层键（nav 路径），或直接对齐壳内内容（壳路径）。 */
    textStart: Dp,
    /** 文字右内缩：让开右上角的浮层键（壳路径），或直接对齐壳内内容（nav 路径）。 */
    textEnd: Dp,
    modifier: Modifier = Modifier,
) {
    // 头部退到 35% 才开始换字、到 70% 换完：太早「歌单」一闪而过，太晚标题迟迟不出现。
    val morph = remember(reveal) {
        derivedStateOf { ((reveal.value - 0.35f) / 0.35f).coerceIn(0f, 1f) }
    }
    // 2026-10-03 照抄 SearchTitleBar 的写法：headlineSmall + 行高居中裁剪
    // （LineHeightStyle Center / Trim.Both）+ 粗体。
    // 2026-10-05：高度不再"由文字撑"，改为共用 [CirroTitleBarHeight]（= headlineSmall 行高）
    // —— 与探索 / 搜索 / 我的三页的标题条同一份规格，详情页的圆角纸顶边因此与三个 tab 页
    // 落在同一条线上；取值仍是字体行高，所以系统字体放大时标题不会被裁。
    // 两条 Text 的 style 同源，交叉淡变时行盒不变、不跳。
    val barStyle = MaterialTheme.typography.headlineSmall.copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(CirroTitleBarHeight)
            .padding(start = textStart, end = textEnd),
    ) {
        Text(
            text = label,
            style = barStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = 1f - morph.value },
        )
        Text(
            text = title,
            style = barStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = morph.value },
        )
    }
}
