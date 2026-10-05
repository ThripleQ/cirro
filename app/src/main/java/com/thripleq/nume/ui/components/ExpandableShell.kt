package com.thripleq.nume.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.thripleq.nume.ui.theme.Motion
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeInk
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * 壳当前展开进度（0..1）的 [State]，供内容里的浮层（如关闭按钮、banner 内缩）读取。
 *
 * 刻意暴露 [State] 而非裸 Float：内容方在读 `value` 时可自行选择在**布局/绘制阶段**读取，
 * 从而避免动画每帧触发整棵内容树重组。
 *
 * 注意：本进度是**单一时间基**（见 [ExpandableShell]），宽高/圆角/hero/scrim 全由它派生，
 * 因此这里读到的 t 与壳的实际几何严格同步。
 */
val LocalShellProgress: androidx.compose.runtime.ProvidableCompositionLocal<State<Float>> =
    staticCompositionLocalOf { mutableStateOf(1f) }

/**
 * 壳的**打开动画是否已结束**（[State]，只在需要时读取）。内容可借此把「加载/组合重列表」推迟到
 * 动画之后，避免动画期间组合列表造成尖峰帧。首个值在动画完成后翻 true。
 *
 * 非壳环境（直接使用 [TrackListScreen] 的页面）默认恒 true，因此立即加载。
 */
val LocalShellSettled: androidx.compose.runtime.ProvidableCompositionLocal<State<Boolean>> =
    staticCompositionLocalOf { mutableStateOf(true) }

/**
 * hero 覆盖层当前的不透明度（[State]，只在 draw 阶段读取）：
 * 1 = hero 封面顶着（交接前），0 = 已交接给内容里的高清封面。
 *
 * 内容里的 banner / 骨架封面据此**在 hero 之下铺底**：`alpha = if (该值 >= 1) 0 else 1`。
 * - hero 完全不透明时内容封面隐藏 —— 否则展开期间「hero + 固定排版的内容封面」两层重影。
 * - hero 一开始淡出（交接/收起）内容封面就变为**不透明**，作为交叉淡化的底板；hero 在其上
 *   渐隐。若让内容封面与 hero 同时半透明（互补 alpha），中段两层都盖不住壳的深色底，
 *   封面会短暂发暗（正常速度下就是「闪黑一下」）。
 *
 * 非壳环境默认 0，内容封面恒 1、照常显示。
 */
val LocalShellHeroAlpha: androidx.compose.runtime.ProvidableCompositionLocal<State<Float>> =
    staticCompositionLocalOf { mutableStateOf(0f) }

/**
 * 壳是否正在播放**收起**动画（[State]，只在需要时读取）。内容可借此把「列表行淡出」等退场
 * 过渡与壳的缩回同步；否则行区只会被壳的裁剪窗口"擦掉"，显得生硬。
 *
 * 非壳环境默认 false，内容不参与退场淡化。
 */
val LocalShellClosing: androidx.compose.runtime.ProvidableCompositionLocal<State<Boolean>> =
    staticCompositionLocalOf { mutableStateOf(false) }

/**
 * 请求收起整只壳：调用后由 [ExpandableShell] 统一播放**收起动画**，动画彻底复位后才 [onDismiss]。
 *
 * 供壳内**非返回键**的关闭入口（收起按钮、下滑手势等）使用。它们**必须**走这里，
 * 不能直接调 `onDismiss`——后者语义是「收起动画已结束」，直接调会跳过收起动画、壳瞬没
 * （正是「关闭看不到动画」的来源）。
 *
 * 非壳环境默认 no-op。
 */
val LocalShellRequestClose: androidx.compose.runtime.ProvidableCompositionLocal<() -> Unit> =
    staticCompositionLocalOf { {} }

/**
 * 水平内缩随壳展开进度收缩：语义等价于 `padding(horizontal = maxInset * progress)`，
 * 但在 **layout 阶段**读取 [progress]——因此宿主 composable 不会被每帧重组，
 * 只触发这一处重排。banner 封面 / 骨架封面用它替代组合期的 `16.dp * p`。
 */
fun Modifier.shellInset(progress: State<Float>, maxInset: Dp): Modifier =
    layout { measurable, constraints ->
        val inset = (maxInset.toPx() * progress.value).roundToInt()
        val w = (constraints.maxWidth - 2 * inset).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, placeable.height) { placeable.place(inset, 0) }
    }

/**
 * 顶部内缩随壳展开进度增长：语义等价于 `padding(top = inset * progress)`，但在 **layout 阶段**
 * 读取 [progress]——宿主 composable 不会被每帧重组。
 *
 * 壳顶一路长到屏幕顶（0）后，用它在全屏时把内容推回状态栏下方；p=0 时内缩为 0，
 * 内容仍与起点胶囊内部布局对齐。
 */
fun Modifier.shellTopInset(progress: State<Float>, insetPx: Float): Modifier =
    layout { measurable, constraints ->
        val top = (insetPx * progress.value).roundToInt().coerceIn(0, constraints.maxHeight)
        val h = (constraints.maxHeight - top).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(constraints.maxWidth, h) { placeable.place(0, top) }
    }

/**
 * 通用「胶囊壳 → 全屏面板」伸展覆盖层。
 *
 * 从一个胶囊的 [fromRect]（窗口坐标 Rect）平滑伸展到全屏，再缩回原位。
 * 用于「点击小胶囊 → 展开成面板」的过渡，收起动画保证最后一帧精确复位。
 *
 * ## 单一时间基（为什么不再横竖分轴）
 * 整只壳只有**一个** `progressAnim: 0..1`，宽、高、平移、圆角、hero、scrim 全部由这同一个
 * `t` 派生。之前用 `horizontal` / `vertical` 两条独立 tween（展开 320/380、收起 280/340），
 * 两条曲线在不同时刻收尾 ⇒ 中间帧宽高比失真，看着「不像一个整体在动」，而且圆角挂在竖轴上、
 * 与宽度脱节，观感忽方忽圆。同一条时钟下宽高比单调插值，形状不再中途变形。
 *
 * 层次感（谁先谁后）不再靠「不同时长」实现——那正是失真来源——而靠**同轴上的不同映射**：
 * 圆角前段恒定、后段收敛（[Motion.cornerTaper]）；scrim 随 t 渐深；hero 在 [Motion.HeroHandoffAt]
 * 才交接给内容里的高清封面。
 *
 * ## 与父级内容的完美衔接（硬契约）
 * - **t=0**：壳矩形 ≡ [fromRect]、圆角 ≡ 父级卡片真圆角（[Motion.CornerHold] 前段不收敛）、
 *   hero 尺寸 ≡ 卡片封面 ⇒ 首帧与父级逐像素吻合，看不出发生了切换。
 * - **t=1**：内容与终态排版重合（固定排版 + 壳裁剪，见下）。
 * - 圆角另加 **短边钳制**（`min(cornerPx, 短边×0.5)`）：壳还很小而圆角相对很大时，
 *   钳制保证它不会长成「药丸」，与任何尺寸的父级卡片都衔接得住。
 *
 * ## 对齐契约（务必遵守，否则收起尾帧会跳位）
 * 1. [header] 必须与「起点胶囊的头部」是**同一个 composable**（颜色/文字/图标相对位置天然一致）。
 *    胶囊未展开时的头部由调用方渲染，展开壳顶部的标题栏由本组件渲染同一份 [header]。
 * 2. 起点胶囊的测量：`onGloballyPositioned` 的 bounds 必须是**视觉胶囊本身**——
 *    胶囊自身的 padding 不能加在 Card/bounds modifier 上，要放内部，否则 bounds 含 padding、终点偏。
 * 3. 壳内部布局必须与胶囊内部布局**同构**（同样的垂直 padding），否则内容相对壳顶的位置对不上。
 *
 * ## 打开 / 收起
 * - 打开：单一 t 按 [Motion.shellOpen] 走到 1（果断起步 + 长减速尾）。
 * - 收起：t 按 [Motion.shellClose] 回到 0（末段减速停稳，不硬切）；结束后等两帧
 *   （[withFrameNanos]）让「完全复位」的画面真正绘制落地再调 [onDismiss]，避免最后一帧被跳过。
 *
 * @param fromRect  起点胶囊的窗口坐标 Rect；null 时用兜底几何（左/顶各 16dp、宽 328dp）
 * @param fullTopPx 内容需要避开的顶部高度（px），通常 = 状态栏高度。壳本身会一直长到屏幕顶
 *                  （含状态栏那段，不再另设填充块），内容在展开期间按此值下移
 * @param shapeCornerDp  壳圆角（动画期按实际短边钳制并在后段收敛，见上「完美衔接」）
 * @param containerColor 壳背景色（应与胶囊 Card 颜色一致）
 * @param recessedBottom 内容区（窟窿）底部让位量：壳本身延伸到屏幕底，
 *                       内容区底部留出此高度露出底下导航岛，与岛同色融合
 * @param progress 外部受控展开进度（0..1）：非 null 时壳几何由它插值驱动（跟手），
 *                 自动开启动画被跳过；null 时用内部动画。关闭动画从当前进度续跑，
 *                 且续跑时长按剩余距离等比缩短（[Motion.shellResume]）。
 * @param contentFromStart 内容从头可见、无独立 header：内容不淡入（alpha 恒 1），
 *                 让内容自身的第一项（如全宽封面）在 p=0 时恰好等于起点卡片、随壳生长；
 *                 header 槽不渲染，[recessedBottom] 也忽略（改由内容自行留底部空间）。
 *                 用于「大封面折进列表」——封面是列表第一项、随滚动移出。
 * @param heroTargetRect 内容首项（封面）的**窗口坐标**终态矩形（[State]，只在 layout/draw 阶段读取，
 *                 不订阅重组）。提供且 [contentFromStart] 为 false 时，在壳内叠加一张 hero 封面：
 *                 几何从起点胶囊矩形插值到该矩形，透明度与内容互补。用于「内容固定终态排版 +
 *                 壳裁剪」路径下恢复首尾与父级卡片的无缝对齐——p=0 时 hero 恰好等于卡片，
 *                 p=1 时与内容里的 banner 封面重合。
 * @param heroReady     高清封面是否已绘制出来（[State]，只在 layout/draw 阶段读取）。为 true 时 hero
 *                      原地渐变淡出、交接给内容里的高清封面；为 false（数据/图片未到）时 hero 一直顶着。
 * @param heroContent   hero 覆盖层内容（通常与起点卡片封面同源）。null 则不绘制 hero。
 * @param heroCornerDp  hero 封面圆角（四角），由 hero 自己那层 `.clip` 施加，与壳的圆角
 *                      （[shapeCornerDp]）相互独立——保证 p=0 时与起点卡片、p=1 时与终态
 *                      封面逐像素吻合；应等于起点卡片/终态封面的圆角。
 * @param onDismiss 关闭动画完全结束、壳复位后才回调（调用方借此移除本组件）
 * @param header    壳顶部标题栏（必须与胶囊头部同源）；接收 [onClose]，收起按钮应调它触发关闭动画。
 *                  [contentFromStart] 为 true 时不渲染。
 * @param content   壳内内容区（占剩余空间）
 */
@Composable
fun ExpandableShell(
    fromRect: Rect?,
    fullTopPx: Float,
    shapeCornerDp: Dp,
    containerColor: androidx.compose.ui.graphics.Color,
    recessedBottom: Dp = 0.dp,
    progress: Float? = null,
    contentFromStart: Boolean = false,
    heroTargetRect: State<Rect?>? = null,
    heroReady: State<Boolean>? = null,
    heroContent: (@Composable () -> Unit)? = null,
    heroCornerDp: Dp = 16.dp,
    onDismiss: () -> Unit,
    header: @Composable (onClose: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // 触觉编排：展开就位/收起落地各一次 CONTEXT_CLICK 级轻震（与 PlayerDock.doClose 的
    // 触觉习惯同族），把动画的"开始/结束"落到指尖——视觉焦点 + 触觉确认同时到位。
    val hapticView = LocalView.current
    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }
    // 非 contentFromStart：header 的实际高度，用于给内容区算固定的终态高度。
    var headerHeightPx by remember { mutableStateOf(0) }

    // 起点（胶囊）几何，兜底 16dp 边距 + 328dp 宽。
    val capsuleLeft = fromRect?.left ?: with(density) { 16.dp.toPx() }
    val capsuleTop = fromRect?.top ?: fullTopPx
    val capsuleWidthPx = fromRect?.width ?: with(density) { 328.dp.toPx() }
    val capsuleHeightPx = fromRect?.height ?: 0f
    // 终点（全屏）几何：铺满整屏（顶到 0、底到屏底）。壳顶越过状态栏长到屏幕顶，
    // 是为了让状态栏那段也属于壳本身——否则要另设一块"让位填充"，它与还在小的壳脱节，
    // 点击瞬间像顶部突然涂色。内容再用 [shellTopInset] 下移 fullTopPx：全屏时让开状态栏，
    // p=0 时内缩为 0、仍与起点胶囊内部布局对齐。
    val fullLeft = 0f
    val fullWidthPx = viewWidth.toFloat()
    val fullTop = 0f
    val fullHeightPx = viewHeight.toFloat()

    var closing by remember { mutableStateOf(false) }
    // ── 单一时间基：整只壳只有这一个进度时钟 ──
    // 宽 / 高 / 平移 / 圆角 / hero / scrim 一律由它派生，不再各跑各的 tween。
    val progressAnim = remember { Animatable(0f) }
    // Hero 透明度：1 = hero 封面顶着，0 = 已交接给高清封面。
    val heroAlpha = remember { Animatable(1f) }
    // 暴露给内容的只读进度 State（Animatable 本身不是 State，用 derivedStateOf 包一层）。
    val progressState = remember { derivedStateOf { progressAnim.value } }
    // hero 透明度的只读 State：内容封面据此与 hero 互补（见 [LocalShellHeroAlpha]）。
    val heroAlphaState = remember { derivedStateOf { heroAlpha.value } }
    // 收起的只读 State：内容据此做退场淡出（与壳缩回同步，见 [LocalShellClosing]）。
    val closingState = remember { derivedStateOf { closing } }

    // 外部受控进度：跟手时直接驱动壳几何（snap），不受内部动画干扰。
    // 关闭动画从当前进度续跑；打开动画仅在无外部进度时自动跑。
    val followProgress = progress != null
    var hasFollowed by remember { mutableStateOf(false) }
    // 打开动画是否结束（供内容把重加载推迟到动画之后）。
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (progress != null) {
            hasFollowed = true
            progressAnim.snapTo(progress)
            settled.value = true
        } else if (hasFollowed) {
            // 从跟手态定格（progress→null）：从当前进度续跑打开动画到全屏。
            // 时长按剩余距离等比给（Motion.shellResume），不再固定 300ms——
            // 快拉满还跑完整时长显得拖沓，刚开始跑同样时长又显得 rushed。
            if (!closing && progressAnim.value < 1f) {
                progressAnim.animateTo(1f, Motion.shellResume(progressAnim.value))
            }
            settled.value = true
        }
    }

    // 打开：仅无外部进度时自动跑（单一时钟，宽高天然同步）。
    if (!followProgress) {
        LaunchedEffect(Unit) {
            progressAnim.animateTo(1f, Motion.shellOpen())
            settled.value = true
            // 展开就位：轻震确认（不到 LongPress 的重震级别）
            hapticView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    // Hero 交接：高清封面就绪、且壳已基本展开后，hero 原地渐变淡出（数据没到就一直顶着，
    // 不查"加载状态"，只看封面是否已绘制出来）。关闭时 hero 立即接管封面：内容封面**同帧**隐藏，
    // 避免 hero 与内容封面同时可见、彼此错位。交接后 hero 恰好停在内容封面终态位（p=1 对齐契约），
    // 故瞬时替换在视觉上不可见。
    // 等壳展开再淡出很关键：否则高清封面若本来就绪，hero 会在展开初期就消失，露出固定排版的
    // 裁切内容（看起来像没优化）。
    val ready = heroReady?.value == true
    LaunchedEffect(ready, closing) {
        when {
            closing -> heroAlpha.snapTo(1f)
            !ready -> Unit
            else -> {
                snapshotFlow { progressAnim.value }.first { it >= Motion.HeroHandoffAt }
                heroAlpha.animateTo(0f, tween(Motion.HeroFadeMs, easing = Motion.EmphasizedDecelerate))
            }
        }
    }

    fun startClose() {
        if (closing) return
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            // 收起：同一条时钟回 0，用 Emphasized（末段减速停稳）。
            // 之前横竖分别 280/340 且收尾用减速不足的曲线，末段像「撞」回卡片。
            progressAnim.animateTo(0f, Motion.shellClose())
            // animateTo 返回时值已到位，但该值的画面还要等重组+绘制才落地；
            // 立即 onDismiss 会把最后一帧跳过，壳停在 progress≈0 处（偏上）。
            // 等两帧让「完全复位」的画面真正画出来再移除覆盖层。
            withFrameNanos { }
            withFrameNanos { }
            // 落地触觉：卡片归位的确认（在移除覆盖层前一刻，与尾帧同拍）
            hapticView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onDismiss()
        }
    }

    BackHandler { startClose() }

    // 注意：动画值（progressAnim / heroAlpha）一律不在组合阶段读取，只在 layout / draw 的
    // lambda 里读——否则每帧都会重组整个 ExpandableShell（含内容子树：列表/网格），
    // 这是胶囊壳展开卡顿的主因。
    // 启动动画的 LaunchedEffect 不依赖这些值，读值下沉不影响动画本身。

    // 派生映射（纯函数，在 layout/draw 阶段按当前 t 求值，零分配、零重组）。
    val cornerPx = with(density) { shapeCornerDp.toPx() }

    /** 当前 t 下的壳宽度（px）。 */
    fun widthAt(t: Float) = lerp(capsuleWidthPx, fullWidthPx, t)
    /** 当前 t 下的壳高度（px）。 */
    fun heightAt(t: Float) = lerp(capsuleHeightPx, fullHeightPx, t)
    /** 当前 t 下的壳左边界（px）。 */
    fun leftAt(t: Float) = lerp(capsuleLeft, fullLeft, t)
    /** 当前 t 下的壳顶边界（px）。 */
    fun topAt(t: Float) = lerp(capsuleTop, fullTop, t)
    /**
     * 当前 t 下的圆角（px）。
     *
     * 两层保护：
     * 1. **[Motion.cornerTaper] 前段恒定** —— t ≤ CornerHold 期间保持父级卡片的真圆角，
     *    保证 p=0 与父级逐像素吻合，中段也不会提前"变形"；越过该点后 smoothstep 收敛。
     * 2. **短边钳制** —— 半径永不超过当前短边一半，任何尺寸的起点卡片都不会被画成药丸。
     */
    fun cornerAt(t: Float): Float {
        val tapered = cornerPx * Motion.cornerTaper(t)
        val maxSafe = min(widthAt(t), heightAt(t)) * 0.5f
        return min(tapered, maxSafe)
    }

    // 占位层：盖住底下页面、拦截触摸。
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth = it.width; viewHeight = it.height },
    ) {
        // 背景 scrim：随展开进度渐深（draw 阶段读值，不重组）——胶囊壳「升起」的层次来源，
        // 底下页面退暗、壳浮在前，展开即建立 modal 焦点。
        // 暗度走 Motion.scrimT（EmphasizedDecelerate 前快后慢）：前 20% 进度即建立大半
        // 暗度，焦点在动画一启动就成立；线性 t 前段太"迟疑"，焦点来得晚半拍。
        // 必须是**独立一层**：若把 alpha 加在整个占位层上，壳与内容会被一起淡化（展开后的
        // 面板变半透明），还会给整屏内容套一层 offscreen、滚动时每帧重录。它只盖住「壳以外」
        // 的区域——壳不透明且展开后铺满全屏，稳态下被壳完全遮住。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = NumeFade.SHELL_SCRIM * Motion.scrimT(progressAnim.value) }
                .background(NumeInk.Scrim),
        )
        // 触摸拦截层（在壳之下、底下页面之上）：吃掉所有落在壳外的指针事件。
        // 否则展开动画期间壳还小，手指会穿透去滑动底下的列表；底下页面一滚，收起时
        // fromRect（进入时捕获的卡片位置）就与实际位置错位了。
        // 与 dock 的那层是同一个 [swallowPointerInput]（2026-10-05 提取共用）。
        Box(
            Modifier
                .fillMaxSize()
                .swallowPointerInput(),
        )
        // 壳：位置/宽高全由**同一个 t** 插值驱动（显式宽高 + layout 阶段平移）。
        // 壳顶越过状态栏一直长到屏幕顶（0），状态栏那段就是壳本身；底到屏底。
        Box(
            modifier = Modifier
                // 壳尺寸随动画每帧变化：把 animatable 读取下沉到 layout 阶段，
                // 于是只触发重排（不重组）——内容子树的组合被跳过。
                .layout { measurable, _ ->
                    val t = progressAnim.value
                    val w = widthAt(t).roundToInt().coerceAtLeast(0)
                    val h = heightAt(t).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                // 平移同样在 layout 阶段读取；用 offset（只改放置、不新建图层）而非 graphicsLayer，
                // 避免给整屏壳再套一层 offscreen。
                .offset {
                    val t = progressAnim.value
                    IntOffset(leftAt(t).roundToInt(), topAt(t).roundToInt())
                }
                // 裁剪/投影只在动画期间需要：稳态下壳铺满全屏，圆角与投影都不可见，
                // 保留会给整屏壳套一层 RenderNode/offscreen，让滚动时整个列表每帧重录
                // display list——这是列表卡顿的主因。
                // 圆角走 [cornerAt]：前段保持父级真圆角 + 短边钳制，后段 smoothstep 收敛，
                // 既守住与父级的衔接，又消除末段"啪"地变方。
                .then(
                    if (!settled.value || closing) {
                        Modifier.graphicsLayer {
                            shape = RoundedCornerShape(cornerAt(progressAnim.value).toDp())
                            clip = true
                            // 投影随展开渐升（0→2dp）：小卡片几乎贴着页面，全屏面板浮起——
                            // 「升起」不只是暗度，还有影子长高。
                            shadowElevation = 2.dp.toPx() * progressAnim.value
                        }
                    } else {
                        Modifier
                    },
                )
                .background(containerColor),
        ) {
            Column(
                Modifier
                    // contentFromStart 路径内容随壳生长，仍按进度下移以对齐起点胶囊内部。
                    // 固定排版路径**不再逐帧下移**：内容屏幕位置恒定（见下方 content Box 的双向
                    // 抵消 offset），列表只做淡入淡出、不做平移。
                    .then(if (contentFromStart) Modifier.shellTopInset(progressState, fullTopPx) else Modifier)
                    .fillMaxSize()
                    .padding(vertical = if (contentFromStart) 0.dp else 4.dp),
            ) {
                // 头部标题栏：与起点胶囊的头部同一份 composable，颜色/图文相对位置天然一致。
                // 把关闭动画触发器传给 slot：收起按钮调它走完整关闭动画，而不是直接移除壳。
                // contentFromStart 时无独立 header（封面本身是内容的第一项）。
                if (!contentFromStart) {
                    Box(Modifier.onSizeChanged { headerHeightPx = it.height }) {
                        header(::startClose)
                    }
                }
                // 内容区：
                // - contentFromStart：随壳重排（封面从卡片尺寸生长），用 weight 占剩余空间。
                // - 否则：按终态尺寸（全宽 × 屏高-header-内边距）固定排版一次。内容约束在动画
                //   期间不变，Compose 会跳过 measure（OuterMeasurablePlaceable 同约束缓存），
                //   于是列表/网格每帧零重排；水平再用 -shellLeft 抵消壳平移，使内容在屏幕上
                //   静止，只由壳的裁剪窗口逐步露出。
                val contentModifier = if (contentFromStart) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    val vPadPx = with(density) { 8.dp.toPx() }
                    val fixedHeightPx =
                        (viewHeight - fullTopPx - headerHeightPx - vPadPx).coerceAtLeast(0f)
                    Modifier
                        .requiredWidth(with(density) { viewWidth.toDp() })
                        .requiredHeight(with(density) { fixedHeightPx.toDp() })
                }
                Box(
                    contentModifier
                        .padding(bottom = if (contentFromStart) 0.dp else recessedBottom)
                        .offset {
                            // 固定排版路径：**双向**抵消壳的平移（x 与 y 都抵消），使内容在屏幕上
                            // 完全静止——展开时列表不再随壳上移，只由壳的裁剪窗口逐步露出、靠 alpha
                            // 淡入淡出。用 offset 而非 graphicsLayer，避免内容子树再套一层 offscreen
                            // （layout 期读取，不重组）。contentFromStart 路径内容随壳生长，不抵消。
                            if (contentFromStart) {
                                IntOffset(0, 0)
                            } else {
                                val t = progressAnim.value
                                IntOffset(
                                    -leftAt(t).roundToInt(),
                                    (fullTopPx - topAt(t)).roundToInt(),
                                )
                            }
                        }
                        // 内容编排层（choreography）：与壳的裁剪/圆角层同款「动画期才挂」模式
                        // （settled 后摘掉，稳态零 offscreen、滚动不重录）。draw 阶段读 t，不重组。
                        // - 展开期（!settled）：t∈[ContentRiseFrom, ContentRiseTo] 内容做 12dp 上浮
                        //   ——位移**滞后于壳**（壳先长、内容再流入），这是编排感与机械同步的分界。
                        // - 收起期（closing）：随收缩进度淡出 + 6dp 下拖，内容像被「吸回」卡片
                        //   （原实现只淡出不位移，收束感偏"干"）。
                        .then(
                            if (!contentFromStart && (!settled.value || closing)) {
                                Modifier.graphicsLayer {
                                    val t = progressAnim.value
                                    if (closing) {
                                        alpha = t
                                        translationY = (1f - t) * Motion.ContentDragDp.dp.toPx()
                                    } else {
                                        val ct = (
                                            (t - Motion.ContentRiseFrom) /
                                                (Motion.ContentRiseTo - Motion.ContentRiseFrom)
                                            ).coerceIn(0f, 1f)
                                        // smoothstep：起步不猛、收尾不顿
                                        val s = ct * ct * (3f - 2f * ct)
                                        translationY = (1f - s) * Motion.ContentRiseDp.dp.toPx()
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    content = {
                        CompositionLocalProvider(
                            LocalShellProgress provides progressState,
                            LocalShellSettled provides settled,
                            LocalShellHeroAlpha provides heroAlphaState,
                            LocalShellClosing provides closingState,
                            LocalShellRequestClose provides ::startClose,
                        ) {
                            content()
                        }
                    },
                )
            }

            // Hero 封面：固定排版路径下恢复首尾对齐。
            // 起点 = 胶囊矩形（壳局部坐标 (0,0,capsuleW,capsuleH)），终点 = heroTargetRect 换算到
            // **壳局部坐标**（窗口矩形 - 壳绘制原点；壳原点随动画移动，相减后终点恒定）。
            // 几何在 layout 阶段读同一个 t，不触发重组；透明度与内容互补。
            if (!contentFromStart && heroContent != null) {
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val t = progressAnim.value
                            val target = heroTargetRect?.value
                            val tW = target?.width ?: capsuleWidthPx
                            val tH = target?.height ?: capsuleHeightPx
                            val w = lerp(capsuleWidthPx, tW, t).roundToInt().coerceAtLeast(0)
                            val h = lerp(capsuleHeightPx, tH, t).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        // 平移单独放最外层：圆角/模糊层都在它内部，于是裁剪窗口随 hero 一起移动。
                        .graphicsLayer {
                            val t = progressAnim.value
                            val target = heroTargetRect?.value
                            // 终点用**屏幕绝对坐标**再乘 t：壳原点在屏幕上按 capsule*(1-t) 线性移动，
                            // 只有 local(t) = t*target 才能让 hero 的**屏幕**轨迹是线性 lerp(capsule, target)。
                            // 若先减壳原点再 lerp（旧写法），会多出 capsule*(1-t) 一项 → hero 上边/左边
                            // 比窗口跑得快，冲出窗口被其直边切平 → 顶角变方、出现锐角（19:26 截图）。
                            val targetLeft = target?.left ?: capsuleLeft
                            val targetTop = target?.top ?: capsuleTop
                            translationX = targetLeft * t
                            translationY = targetTop * t
                        }
                        // 圆角**单独一层、且不含 alpha / 模糊**：图层尺寸逐帧动画时，`alpha`（或
                        // `renderEffect`）与 `shape/clip` 同层会丢掉该层的 clip outline、四角变方、
                        // 出现缺角。拆开后该层无 alpha/effect，圆角在整段动画里都保留。
                        .clip(RoundedCornerShape(heroCornerDp))
                        // 透明度 + 运行时模糊放圆角层**内部**：模糊结果被外层圆角裁掉，于是"失焦"
                        // 不会把四角糊成直角。半径按进度两端为 0、中段最强，< 0.5px 即摘掉 effect——
                        // "图层最满屏"的那几帧并不糊，规避整屏逐帧模糊开销。
                        .graphicsLayer {
                            alpha = heroAlpha.value
                            // p=0 半径 0（与卡片逐像素吻合）、p=1 归零（交接前已清晰），中段最强。
                            // API < 31 无 RenderEffect，优雅降级为不模糊。
                            val blur = Motion.heroBlurPx(progressAnim.value)
                            renderEffect = if (Build.VERSION.SDK_INT >= 31 && blur >= 0.5f) {
                                BlurEffect(blur, blur, TileMode.Clamp)
                            } else {
                                null
                            }
                        },
                ) {
                    // 把壳进度/收起状态也下发给 hero 槽：hero 文本据此在展开初期淡出、收起末期才淡入，
                    // 避免盒子逐帧重排导致元信息"跳动"（见 CoverExpandShell 的 heroContent）。
                    CompositionLocalProvider(
                        LocalShellProgress provides progressState,
                        LocalShellClosing provides closingState,
                    ) {
                        heroContent()
                    }
                }
            }
        }
    }
}

/**
 * 通用「大封面卡 → 全屏内容」伸展壳：把卡片（窗口坐标 [fromRect]）长成全屏，期间用 hero 封面
 * 从卡片位置插值到内容里的头部封面，首尾无缝；**右上角**浮一个随展开进度淡入的收起按钮。
 *
 * 探索页（歌单/榜单 → 曲目列表）与「我的」页（大卡 → 曲目列表/歌单网格）共用本组件。
 *
 * **对齐契约**：内容首项若是封面，其位置由调用方用 [heroCoverInset] / [heroCoverTop] /
 * [heroCoverSide] 三个参数**声明**（本组件按此**预测** hero 终点矩形，不再每帧测量回写）；
 * 调用方必须让内容里的封面真的落在该处——预测与真实排版脱钩就是「hero 停在别处」。
 * 曲目列表页的真实值集中在 `TrackListMetrics`；歌单网格是满宽 banner，用默认值即可。
 *
 * @param coverUrl hero 封面 URL（应与起点卡片同源）；null 时 hero 用占位底
 * @param title    hero 封面上的名字
 * @param meta     hero 封面名字下方的元信息（起点卡片上那行，如「114 首」）。传入后在
 *                 p=0 时 hero 与卡片逐项一致——否则动画一开始卡片上的数量就被 hero 盖掉、
 *                 结尾再冒出来，像闪一下。已拿到的数据应全程可见，不该重放。
 * @param heroCoverInset hero 终点封面距屏幕左边缘
 * @param heroCoverTop   hero 终点封面顶边距**状态栏下沿**
 * @param heroCoverSide  hero 终点封面边长；null = 满宽（左右各留 [heroCoverInset]）
 * @param watermarkIcon hero 的内容属性水印图标：与起点卡片、内容 banner 传同一个，
 *                 缺封面时三处都显示同一枚图标（否则 p=0 的 hero 与卡片对不上）
 * @param content  面板内容；参数 `onCoverReady` 在内容里的高清封面画出来后调用，
 *                 触发 hero 交接淡出（数据/图片未到则 hero 一直顶着）
 */
@Composable
fun CoverExpandShell(
    fromRect: Rect?,
    coverUrl: String?,
    title: String,
    onDismiss: () -> Unit,
    meta: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shapeCornerDp: Dp = 16.dp,
    watermarkIcon: ImageVector? = null,
    heroCoverInset: Dp = 16.dp,
    heroCoverTop: Dp = 4.dp,
    heroCoverSide: Dp? = null,
    content: @Composable (onCoverReady: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val statusBarTopPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    // hero 终态矩形用「预测值」：参数化几何 + 方形，与内容里的头部封面同位。
    // 不再每帧测量回写（省掉每帧 onGloballyPositioned）。
    val coverSidePx = heroCoverSide?.let { with(density) { it.toPx() } }
        ?: (LocalConfiguration.current.screenWidthDp * density.density -
            with(density) { (heroCoverInset * 2).toPx() })
    val coverLeftPx = with(density) { heroCoverInset.toPx() }
    val coverTopPx = statusBarTopPx + with(density) { heroCoverTop.toPx() }
    // 按影响几何的输入做 key：配置/insets 变化（多窗口缩放、折叠展开、显示切换）时重算，
    // 否则 hero 终点停在旧矩形、与内容里声明的封面错位。
    val coverRect = remember(coverLeftPx, coverTopPx, coverSidePx) {
        mutableStateOf<Rect?>(
            Rect(coverLeftPx, coverTopPx, coverLeftPx + coverSidePx, coverTopPx + coverSidePx),
        )
    }
    val coverReady = remember { mutableStateOf(false) }

    ExpandableShell(
        fromRect = fromRect,
        fullTopPx = statusBarTopPx,
        shapeCornerDp = shapeCornerDp,
        containerColor = containerColor,
        contentFromStart = false,
        heroTargetRect = coverRect,
        heroReady = coverReady,
        heroContent = {
            // hero 盒子逐帧缩放，其中的名字/元信息会被每帧重新排版——长文本的换行/省略号
            // 因此逐帧移动（看着像"跳动/自动截断"）。文本只在壳体≈卡片大小时可见：
            // 展开早期淡出、收起末尾才淡入（[Motion.heroTextAlpha]），观感改为纯淡入淡出。
            val heroProgress = LocalShellProgress.current
            val shellClosing = LocalShellClosing.current
            val heroTextAlpha = remember(heroProgress, shellClosing) {
                derivedStateOf {
                    Motion.heroTextAlpha(heroProgress.value, shellClosing.value)
                }
            }
            BigCoverVisual(
                coverUrl = coverUrl,
                name = title,
                modifier = Modifier.fillMaxSize(),
                meta = meta,
                // hero 与卡片**同源同尺寸**（[CardCoverSize]）：卡片按此解码，hero 请求命中内存、
                // 瞬时出现，且 p=0 时与卡片逐像素吻合。展开中的失焦由 ExpandableShell 里的运行时
                // 模糊按进度驱动，不再靠"低清放大"。
                requestSize = CardCoverSize,
                textAlpha = heroTextAlpha,
                watermarkIcon = watermarkIcon,
            )
        },
        onDismiss = onDismiss,
        header = {},
        content = {
            Box(Modifier.fillMaxSize()) {
                content { coverReady.value = true }
                // 收起键：浮在**右上角**、不随列表滚，随展开进度淡入（p=0 不可见、不响应点击）。
                //
                // 2026-10-03 从左上挪到右上（用户：「歌单上边的标题不要给收起按钮让位，收起按钮
                // 放右上角」）：壳内容的标题在左上，键在左上就得给它让出一档内缩，标题白缩一块；
                // 挪到右上后标题左起对齐内容缩进，让位翻到右边。站内所有壳页面统一。
                val shellProgress = LocalShellProgress.current
                val requestClose = LocalShellRequestClose.current
                // 走 requestClose（= startClose），完整播放收起动画后才 onDismiss；
                // 绝不可直接 onDismiss——那会跳过动画、壳瞬没。
                NumeCloseButton(
                    onClick = { if (shellProgress.value > 0.5f) requestClose() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // 上提一档：内容标题条只有 ~24dp 高，键按 12dp 外边距站就会比标题
                        // 低 16dp（用户：「有点靠下」）。详见 [CloseButtonRaise]。
                        .offset(y = -CloseButtonRaise)
                        .padding(12.dp)
                        .graphicsLayer { alpha = shellProgress.value },
                )
            }
        },
    )
}
