package com.thripleq.cirro.ui.splash

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import com.thripleq.cirro.BuildConfig
import com.thripleq.cirro.core.startup.StartupGate
import com.thripleq.cirro.ui.theme.BrandMark
import com.thripleq.cirro.ui.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * 开屏底：品牌红。与 `R.color.cirro_splash_background` 同值 —— 系统开屏窗（Compose 第一帧
 * 之前）铺的就是那个资源，两层必须同色，交接才看不出来。
 */
private val SplashBackdrop = Color(0xFFF51F43)

/** mark 的墨色：暖白。与启动图标前景 `R.drawable.ic_launcher_foreground` 同值。 */
private val SplashInk = Color(0xFFFFFAF6)

/**
 * 开屏：品牌红底 + mark 自己画出来（描边生长 → 填充显现），并**盖住**启动期那段还没有
 * 内容的窗口 —— 这就是它作为「准备缓冲」的意义。
 *
 * ## 三条时间线（比例照设计稿那份 HTML，见 [Motion] 里开屏那一节的对照表）
 * 1. **入场**：整层淡入 + 从 [Motion.SplashEnterScale] 长到 1，与描边生长同时起手；
 * 2. **描边 → 填充**：轮廓沿路径长出来，长完才把填充淡进来（PNG 时代做不到的效果，
 *    这里靠 `PathMeasure` 现算，不需要任何切图）；
 * 3. **缓冲 → 退场**：等 [gate] 放行（App 就绪 = [StartupGate] 那三条信号全到齐；**整段**
 *    开屏最长 [Motion.SplashMaxHoldMs]，含前面已经演掉的动画），然后整层淡出并放大到
 *    [Motion.SplashExitScale]，把舞台交给页面。
 *
 * ## 它盖住的到底是什么
 * 首页在它底下照常组合、照常取数。退场那一刻必须已经满足「画面不会再自己变样」：内容有了
 * （第一块落地）、登录态定了（决定「每日推荐」那块的有无）、播放队列恢复了（决定底部迷你条
 * 的有无）—— 少一条就会看到开屏退场后页面又自己动一下，那比多盖 200ms 难看得多。
 *
 * ## 为什么图形是算出来的而不是一张 PNG / 一个 AnimatedVectorDrawable
 * - 与启动图标**同一份几何**（`BrandMark.OUTLINES`，由 `tools/gen_launcher_icon.py`
 *   一并写出）：换 mark 只需重跑脚本，不存在「图标换了、开屏忘了换」；
 * - 系统开屏（core-splashscreen）的图标动画只在 Android 12+ 才播、且时长受系统限制，
 *   放在 Compose 里则**所有版本一致**，时长由我们自己定。
 *
 * ## 为什么逐帧动画不引起重组
 * 四个动画值**只在绘制期读**（`graphicsLayer { }` 与 `Canvas` 的 lambda 里），与
 * `TitleCollapse` 那一处同源：进度变化只作废绘制，不重跑组合 —— 开屏正好压在启动
 * 最紧张的那几百毫秒上，不该再往主线程堆组合工作。
 */
@Composable
fun SplashOverlay(
    gate: StartupGate,
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit,
) {
    // 几何是常量：解析一次就够（PathParser 逐帧跑是白花钱）。
    // 注意 API 形状：`PathParser` 是**实例**（不是伴生对象），流程 =
    // parsePathString(d) → toPath(target)。
    val outlines: List<Path> = remember {
        BrandMark.OUTLINES.map { PathParser().parsePathString(it).toPath(Path()) }
    }
    val measures: List<PathMeasure> = remember { List(outlines.size) { PathMeasure() } }
    val lengths: List<Float> = remember {
        measures.mapIndexed { i, m -> m.setPath(outlines[i], forceClosed = false); m.length }
    }
    val bounds: Rect = remember {
        outlines.drop(1).fold(outlines.first().getBounds()) { acc, p ->
            val b = p.getBounds()
            Rect(
                left = minOf(acc.left, b.left),
                top = minOf(acc.top, b.top),
                right = maxOf(acc.right, b.right),
                bottom = maxOf(acc.bottom, b.bottom),
            )
        }
    }
    /** getSegment 的输出容器，逐帧复用（重置后再写，不新建）。 */
    val scratch = remember { Path() }

    val wrap = remember { Animatable(0f) }   // 入场：alpha + 缩放
    val draw = remember { Animatable(0f) }   // 描边生长 0→1
    val fill = remember { Animatable(0f) }   // 填充显现 0→1
    val exit = remember { Animatable(0f) }   // 退场：整层淡出 + 放大

    // 系统「移除动画」开关（对应设计稿的 prefers-reduced-motion）：那时不演，直接亮终态。
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }

    LaunchedEffect(Unit) {
        // 记起手时刻：[Motion.SplashMaxHoldMs] 是**整段开屏**的预算（含下面这段动画），
        // 不是「演完再等 3 秒」—— 加一条就绪判据就多等 3 秒，用户等不起。
        val startedAt = SystemClock.elapsedRealtime()
        if (reduceMotion) {
            // 不演，但**缓冲照等** —— 缓冲不是动画，去掉了就变成「红屏一闪」。
            wrap.snapTo(1f); draw.snapTo(1f); fill.snapTo(1f)
        } else {
            launch { wrap.animateTo(1f, Motion.splashWrapIn()) }
            draw.animateTo(1f, Motion.splashDraw())
            fill.animateTo(1f, Motion.splashFill())
        }
        // 缓冲：等 App 就绪（三条信号，见 [StartupGate]）。从来不保证会被打开（离线时
        // 没有一方会上报），所以必须带超时 —— 宁可早放，也不能把用户锁在红屏里。
        val remaining = Motion.SplashMaxHoldMs - (SystemClock.elapsedRealtime() - startedAt)
        if (remaining > 0L) withTimeoutOrNull(remaining) { gate.ready.first { it } }
        if (BuildConfig.DEBUG) {
            // 诊断：开屏是走了「就绪」还是被超时轰下来的、被谁拖住。[pendingSignals] 只
            // 在这条日志里出现 —— 正式路径一律只看 `ready`，别拿它判该不该退场。
            Log.d(
                "CirroSplash",
                "gate ${if (gate.ready.value) "ready" else "timeout"} " +
                    "pending=${gate.pendingSignals} " +
                    "at ${SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }
        if (reduceMotion) exit.snapTo(1f) else exit.animateTo(1f, Motion.splashExit())
        onDismissed()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(SplashBackdrop)
            .graphicsLayer {
                alpha = 1f - exit.value
                val s = lerp(Motion.SplashEnterScale, 1f, wrap.value) *
                    lerp(1f, Motion.SplashExitScale, exit.value)
                scaleX = s
                scaleY = s
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawMark(
                outlines = outlines,
                measures = measures,
                lengths = lengths,
                bounds = bounds,
                scratch = scratch,
                drawProgress = draw.value,
                fillProgress = fill.value,
            )
        }
    }
}

/**
 * 画 mark：先把整条轮廓按外接框**居中缩放**到 [Motion.SplashLogoFraction] 见方，再
 * 逐条画「填充（按 [fillProgress] 淡入）+ 已生长的那一段描边」。
 */
private fun DrawScope.drawMark(
    outlines: List<Path>,
    measures: List<PathMeasure>,
    lengths: List<Float>,
    bounds: Rect,
    scratch: Path,
    drawProgress: Float,
    fillProgress: Float,
) {
    val target = size.minDimension * Motion.SplashLogoFraction
    val scale = target / max(bounds.width, bounds.height)
    val left = (size.width - bounds.width * scale) / 2f - bounds.left * scale
    val top = (size.height - bounds.height * scale) / 2f - bounds.top * scale

    withTransform({
        translate(left, top)
        // pivot 必须是原点：默认按绘制区中心缩放会把 mark 推出画布。
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        val stroke = Stroke(
            width = Motion.SplashStrokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        for (i in outlines.indices) {
            // 填充（SVG 里 fill 在 stroke 之下，这里同序）
            if (fillProgress > 0f) {
                drawPath(outlines[i], SplashInk, alpha = fillProgress.coerceIn(0f, 1f))
            }
            // 描边：只取 0 → length×progress 那一段（= 设计稿的 stroke-dashoffset）
            if (drawProgress > 0f) {
                scratch.reset()
                measures[i].getSegment(
                    startDistance = 0f,
                    stopDistance = lengths[i] * drawProgress,
                    destination = scratch,
                    startWithMoveTo = true,
                )
                drawPath(scratch, SplashInk, style = stroke)
            }
        }
    }
}
