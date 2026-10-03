package com.thripleq.nume.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * 面板首行（「播放全部」）**从封面派生出来的面**。
 *
 * 用户 2026-10-03 的要求：这一行的背景「从封面处理得来，亮主题就亮一点、暗主题就暗一点」。
 *
 * ## 为什么不是「取封面主色直接用」
 *
 * 这一行是**承载文字的面**（标题 + 曲目数 + 三枚图标），不是一块色标：
 * 1. **明度锚在主题背景上**，只让出很小的一步 —— 暗主题往上抬、亮主题往下压。
 *    封面色再亮也压得回来，于是正文/图标继续用 `onSurface` 就稳，不必为每张封面
 *    再算一套前景色（那会牵扯一整条 `contentColorFor` 分支，还会随封面跳）。
 * 2. **饱和度封顶**：封面可以是荧光绿，这一行不行 —— 它的职责是托住文字。
 * 3. **色相照抄封面**：全页只有这一处颜色来自内容，于是「气」从封面接到了面板上：
 *    上面是封面的糊底（图形），下面是它的色（面），有承接关系。
 *
 * ## 三档而不是一档
 *
 * 这一行的下边两角是**反圆角**（向外张开、向下淌出的那对脚，见 `PlayAllRowShape`）。脚内侧那
 * 道弧的**凹面朝下**，与上边两角一致 ——**如果这一行也用 `surface`，那道弧线根本不存在**
 * （2026-10-03 用户反馈「下边的反圆角貌似没有」，一半原因在此）。所以两件事：
 *
 * - 面与它下面的东西之间必须**看得出的差**，弧线才有轮廓；
 * - 但差不能平均分配 —— 主体那 68dp 上压着正文，差异大一点对比度就掉一点。
 *
 * 于是明度偏移做成**沿行高递增的三档**（[top] → [middle] → [bottom]，配合调用点的
 * `Brush.verticalGradient`）：主体那一带只差一点点（借个色相），越往下越浓，到脚底差满 ——
 * 最需要轮廓的地方差异最大，最需要对比度的地方差异最小。顺带还多出一层「封面的色往下
 * 渗」的观感，比一个平涂色块自然。
 */
@Immutable
data class CoverFace(
    /** 上沿：几乎就是主题背景，只借了封面的色相。 */
    val top: Color,
    /** 中段 —— 与 [top] 一起把主体那一段的差异压在很小。 */
    val middle: Color,
    /** 底沿：离背景最远的一档，底边两角反圆角缺口的弧线就靠它。 */
    val bottom: Color,
) {
    companion object {
        /** 无封面 / 取色失败：[MaterialTheme.colorScheme.surface]，也就是这一行改动前的样子。 */
        fun flat(base: Color) = CoverFace(base, base, base)
    }
}

/**
 * 记住 [coverUrl] 的封面派生的面；取不到就回落到主题的 `surface`（不改动前的样子）。
 *
 * 亮暗由**背景自身的明度**判定，而不是再读一遍系统主题：面本来就要贴着这个背景走，
 * 两者必须来自同一个来源，否则会出现「亮主题下算出暗面」这种自相矛盾。
 */
@Composable
fun rememberCoverFace(coverUrl: String?): CoverFace {
    val context = LocalContext.current
    val base = MaterialTheme.colorScheme.surface
    val fallback = remember(base) { CoverFace.flat(base) }
    val dark = base.luminance() < 0.5f
    var face by remember(coverUrl, dark, base) { mutableStateOf(fallback) }

    LaunchedEffect(coverUrl, dark, base) {
        face = fallback
        val url = coverUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val seed = readCoverSeed(context, url) ?: return@LaunchedEffect
        face = faceOf(seed, base, dark)
    }
    return face
}

/**
 * 取封面主色。**不引 Palette 库**：它只有「主色 / 鲜艳色 / 柔色」几档现成结果，而这里
 * 要的是「色相拿走、明度交还给主题」，自算反而更短、也少一个依赖。
 */
private suspend fun readCoverSeed(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context)
        .data(url)
        // 只要够算色：Coil 直接按这个尺寸解码，不会把整张原图拉进内存。
        .size(SampleGrid)
        // 硬件位图读不了像素（getPixels 抛 IllegalStateException）。
        .allowHardware(false)
        .build()
    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
    val drawable = (result as? SuccessResult)?.drawable ?: return null
    // 拿到的可能就是 Coil 内存缓存里那一张（BitmapDrawable 不做拷贝），**不能 recycle**。
    // 24×24 也不值得为它多拷一份。
    val bitmap = runCatching { drawable.toBitmap() }.getOrNull() ?: return null
    return dominantSeed(bitmap)
}

/**
 * 主色种子：**中心加权的「鲜艳桶」投票**。
 *
 * 封面里占面积最大的往往是黑边、白底、大面积灰渐变 —— 按出现次数投票会选出一个中性色，
 * 那就等于没取（面会跟背景几乎一样）。所以先按 HSL 剔掉过黑 / 过白 / 过灰的像素，再让
 * 中间的像素多算几票（构图重心通常在中间，边角多是留白或黑边）。
 *
 * 全部像素落选（灰阶封面、纯黑封面）时返回 null，由调用方回落到 `surface` ——
 * 宁可这一行没有效果，也不能给一串跟封面无关的颜色。
 */
private fun dominantSeed(bitmap: Bitmap): Int? {
    val small = Bitmap.createScaledBitmap(bitmap, SampleGrid, SampleGrid, true)
    val pixels = IntArray(SampleGrid * SampleGrid)
    small.getPixels(pixels, 0, SampleGrid, 0, 0, SampleGrid, SampleGrid)
    if (small !== bitmap) small.recycle()

    val hsl = FloatArray(3)
    // key -> [权重票数, r 加权和, g 加权和, b 加权和]
    val buckets = HashMap<Int, FloatArray>()
    for (i in pixels.indices) {
        val argb = pixels[i]
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        ColorUtils.RGBToHSL(r, g, b, hsl)
        if (hsl[2] < NeutralDark || hsl[2] > NeutralLight) continue
        if (hsl[1] < NeutralSaturation) continue
        // 3 位/通道量化：同一片色不会被拆成几十个桶、把票数摊薄。
        val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
        val x = (i % SampleGrid) / (SampleGrid - 1f) - 0.5f
        val y = (i / SampleGrid) / (SampleGrid - 1f) - 0.5f
        // 中心 2 票 → 四角 1 票。
        val distance = ((x * x + y * y) / 0.5f).coerceIn(0f, 1f)
        val weight = 2f - distance
        val bucket = buckets.getOrPut(key) { FloatArray(4) }
        bucket[0] += weight
        bucket[1] += r * weight
        bucket[2] += g * weight
        bucket[3] += b * weight
    }

    val best = buckets.values.maxByOrNull { it[0] } ?: return null
    val votes = best[0].coerceAtLeast(1f)
    return android.graphics.Color.rgb(
        (best[1] / votes).toInt().coerceIn(0, 255),
        (best[2] / votes).toInt().coerceIn(0, 255),
        (best[3] / votes).toInt().coerceIn(0, 255),
    )
}

/** 把封面种子搬到主题的明度带上：色相归封面，明度归背景，饱和度封顶。 */
private fun faceOf(seed: Int, base: Color, dark: Boolean): CoverFace {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, seedHsl)
    val baseHsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), baseHsl)

    val recipe = if (dark) DarkRecipe else LightRecipe
    val lightness = baseHsl[2]
    return CoverFace(
        top = tinted(seedHsl, lightness + recipe.top, recipe.bodySaturation),
        middle = tinted(seedHsl, lightness + recipe.middle, recipe.bodySaturation),
        bottom = tinted(seedHsl, lightness + recipe.bottom, recipe.bottomSaturation),
    )
}

/** 色相取 [seedHsl]，明度钉在 [lightness]，饱和度不超过 [maxSaturation]。 */
private fun tinted(seedHsl: FloatArray, lightness: Float, maxSaturation: Float): Color {
    val hsl = floatArrayOf(
        seedHsl[0],
        seedHsl[1].coerceAtMost(maxSaturation),
        lightness.coerceIn(0f, 1f),
    )
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * 明度偏移（相对主题背景，HSL 0..1；正 = 更亮）与饱和度上限。
 *
 * [top] / [middle] 刻意取小：那一段压着正文，差异越大对比度掉得越多。暗主题下 `surface`
 * 约 L 0.08、`onSurfaceVariant` 约 L 0.65，主体只让 0.03 的话对比度与改动前基本持平；
 * [bottom] 那一带没有文字（底边两角的反圆角缺口就在它中间），可以放心让满 —— 弧线轮廓全靠它。
 */
private class FaceRecipe(
    val top: Float,
    val middle: Float,
    val bottom: Float,
    val bodySaturation: Float,
    val bottomSaturation: Float,
)

private val DarkRecipe = FaceRecipe(
    top = 0.030f,
    middle = 0.052f,
    bottom = 0.105f,
    bodySaturation = 0.26f,
    bottomSaturation = 0.36f,
)

private val LightRecipe = FaceRecipe(
    top = -0.028f,
    middle = -0.046f,
    bottom = -0.088f,
    bodySaturation = 0.20f,
    bottomSaturation = 0.30f,
)

/** 采样网格边长（像素）：576 次取色，开销可忽略，够分出主色。 */
private const val SampleGrid = 24

/** 明度低于此值的像素不参与投票（黑边、暗角）。 */
private const val NeutralDark = 0.10f

/** 明度高于此值的像素不参与投票（白底、高光）。 */
private const val NeutralLight = 0.90f

/** 饱和度低于此值的像素不参与投票（灰调、黑白照片）。 */
private const val NeutralSaturation = 0.14f
