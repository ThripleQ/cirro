package com.thripleq.cirro.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.thripleq.cirro.ui.theme.Motion
import kotlin.math.min

/**
 * 通用「从点击处浮现」转场（origin-reveal）。
 *
 * 把一个**任意被点对象**的窗口矩形 [fromRect] 当作起点：内容页（全屏排版）在起点处
 * 以窗口态浮现，随 [progress] 0→1 长到全屏、边界收敛为硬边、合焦归位。返回时同一
 * [progress] 反向即可沿原路收回。
 *
 * ## 起点必须「挨着」被点对象（曾经翻车的地方）
 * 旧实现用**等比例缩放 + 中心对齐 + 缩放起点钳到 [0.3, 0.85]**，于是 t=0 的窗口是
 * 一个「屏幕宽高比的方框」，既不是那个又宽又扁的列表行、也不是那个方形按钮——**首帧
 * 与父内容之间留一条缝**，看着就是「凭空冒出来」而非「从它这儿长出来」。
 *
 * 现在改为 **把窗口矩形逐帧精确插值** `lerp(fromRect, 全屏, t)`，并让内容做**非等比**
 * 缩放去严丝合缝地铺满该矩形（`transformOrigin = 左上角`、无钳制）。于是 t=0 时窗口
 * 与 [fromRect] **逐像素重合**：列表行就是「纵向展开」、卡片就是「放大」，首帧就贴在
 * 被点对象上。返回的最后一帧同理，收回即没入对象，无跳变。
 *
 * ## 分层（避免仓库已踩过的坑）
 * 三段 `graphicsLayer`/绘制都读 [progress]，动画期间零重组：
 *  - **外层**：屏幕空间的 `alpha` + [BlurEffect]（半径即屏幕 px，不随窗口缩放而变小）。
 *  - **中层**：屏幕空间 `drawWithContent` —— 按当前窗口矩形做圆角裁剪 + 四边羽化。
 *  - **内层**：几何 —— 非等比缩放 + 平移，把内容映射到当前窗口矩形。
 *
 * 关键：**裁剪/羽化/模糊/corner 全部在窗口外层的屏幕坐标里做**。若像旧实现那样塞进
 * 被缩放的内层，22px 羽化在 t=0 会被缩成 0.7px，等于没有；圆角也会随之忽大忽小。
 *
 * @param fromRect 起点窗口矩形（被点对象）；null 时退化为居中的 97% 轻缩放淡入（兜底）
 * @param progress 0=起点（贴合对象的窗口态），1=全屏正常态
 * @param featherMaxPx 羽化边界峰值宽度（屏幕 px），t=1 时归零
 * @param blurMaxPx 失焦峰值半径（屏幕 px），两端归零
 */
@Composable
fun RevealLayer(
    fromRect: Rect?,
    progress: State<Float>,
    modifier: Modifier = Modifier,
    cornerDp: Float = Motion.RevealCornerDp,
    featherMaxPx: Float = Motion.RevealFeatherMaxPx,
    blurMaxPx: Float = Motion.RevealBlurMaxPx,
    onFirstLayout: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // 关闭时直接透传内容：不叠离屏层/裁剪/羽化/模糊（零开销、与常规页面无差）。
    if (!Motion.RevealEnabled) {
        content()
        return
    }
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 零尺寸窗口（窗口建立/折叠/多窗口收起的一帧）会让 screenW/H == 0，
    // 内层 scaleX = start.width / screenW 变成 0/0 = NaN，整层画不出来。直接透传内容兜底。
    if (screenW <= 0f || screenH <= 0f) {
        content()
        return
    }
    val cornerPx = with(density) { cornerDp.dp.toPx() }

    // 内容首次布局完成才回调：调用方据此才启动进入动画，保证**第一个画出来的帧**
    // 就是 p=0（精确停在对象上），而不是组合/measure 吃掉几帧后已经长大的样子。
    val laidOut = remember { mutableStateOf(false) }

    // t=0 的起点窗口：被点对象的真实矩形；无对象时给一个居中的 97% 屏幕框。
    val start = remember(fromRect, screenW, screenH) {
        if (fromRect != null) {
            Rect(fromRect.left, fromRect.top, fromRect.right, fromRect.bottom)
        } else {
            val w = screenW * 0.97f
            val h = screenH * 0.97f
            Rect((screenW - w) / 2f, (screenH - h) / 2f, (screenW + w) / 2f, (screenH + h) / 2f)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                if (!laidOut.value) {
                    laidOut.value = true
                    onFirstLayout()
                }
            }
            // 屏幕空间：整层淡入淡出 + 失焦（半径是屏幕 px，不随窗口缩放变小）。
            .graphicsLayer {
                val t = progress.value.coerceIn(0f, 1f)
                // 只在转场期间用离屏层：feather 的 DstIn 必须只作用于本层，否则会擦到父页面背景。
                // 落定后（t=1，feather 已归零、裁剪为全屏）恢复 Auto，不再整页每帧走离屏合成。
                compositingStrategy =
                    if (t < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                // 起点就**可见**（0.85 不透明 + 失焦 + 羽化）：窗口从被点对象处以软团出现，
                // 而非透明到看不见、等长大了才冒出来——那正是"不挨边"的来源。
                alpha = lerp(0.85f, 1f, t)
                val blur = Motion.revealBlurPx(t).coerceAtMost(blurMaxPx)
                renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur >= 0.5f) {
                    BlurEffect(blur, blur, TileMode.Clamp)
                } else {
                    null
                }
            }
            // 屏幕空间：按当前窗口矩形圆角裁剪 + 四边羽化（rect 与内容严丝合缝）。
            .drawWithContent {
                val t = progress.value.coerceIn(0f, 1f)
                val r = lerpRect(start, screenW, screenH, t)
                val corner = cornerPx * (1f - t)
                val feather = Motion.revealFeatherPx(t).coerceAtMost(featherMaxPx)
                val path = Path().apply {
                    addRoundRect(RoundRect(rect = r, cornerRadius = CornerRadius(corner, corner)))
                }
                clipPath(path) { this@drawWithContent.drawContent() }
                if (feather > 0.5f) drawFeatheredEdges(r, feather)
            }
            // 几何（最内层）：非等比缩放 + 平移，把内容精确铺满当前窗口矩形。
            // origin=左上角 ⇒ 内容点 p 映射为 (translation + scale·p)，于是 t=0 时
            // 窗口恰好 = start（被点对象），t=1 时恒等全屏。
            .graphicsLayer {
                val t = progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = lerp(start.width / screenW, 1f, t)
                scaleY = lerp(start.height / screenH, 1f, t)
                translationX = lerp(start.left, 0f, t)
                translationY = lerp(start.top, 0f, t)
            },
    ) {
        content()
    }
}

/** 窗口矩形随进度 t 从 [start] 线性铺到全屏。 */
private fun lerpRect(start: Rect, screenW: Float, screenH: Float, t: Float): Rect = Rect(
    left = lerp(start.left, 0f, t),
    top = lerp(start.top, 0f, t),
    right = lerp(start.right, screenW, t),
    bottom = lerp(start.bottom, screenH, t),
)

/**
 * 在 [r] 的四条边内侧画一圈 alpha 渐隐（外缘透明 → 内缘不透明），
 * 用 `BlendMode.DstIn` 把已绘制内容的边缘羽化掉。四角两次相乘 → 更柔，正是想要的。
 */
private fun DrawScope.drawFeatheredEdges(r: Rect, feather: Float) {
    val f = feather.coerceAtMost(min(r.width, r.height) / 2f)
    if (f <= 0f) return
    val clear = Color.Transparent
    val keep = Color.Black

    drawRect(
        brush = Brush.verticalGradient(0f to clear, 1f to keep, startY = r.top, endY = r.top + f),
        topLeft = Offset(r.left, r.top),
        size = Size(r.width, f),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        brush = Brush.verticalGradient(0f to keep, 1f to clear, startY = r.bottom - f, endY = r.bottom),
        topLeft = Offset(r.left, r.bottom - f),
        size = Size(r.width, f),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        brush = Brush.horizontalGradient(0f to clear, 1f to keep, startX = r.left, endX = r.left + f),
        topLeft = Offset(r.left, r.top),
        size = Size(f, r.height),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        brush = Brush.horizontalGradient(0f to keep, 1f to clear, startX = r.right - f, endX = r.right),
        topLeft = Offset(r.right - f, r.top),
        size = Size(f, r.height),
        blendMode = BlendMode.DstIn,
    )
}
