package com.thripleq.cirro.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * 记住 [coverUrl] 的**封面强调色**：色相取自封面，明度贴着主题背景让开一小步，饱和度给足。
 *
 * ## 为什么只有一个色（原来是三档 top / middle / bottom）
 *
 * 2026-10-03 用户：「主要是播放全部按钮的背景不太好看，要么简化，要么用效果更好的」。
 *
 * 原来那套是**铺满整行**的纵向三档渐变：色相照抄封面，但为了不抢行里的文字，明度只让
 * ±0.03~0.10、饱和度封顶 0.26 —— 绿/蓝封面过了换算就成了浑浊的灰蓝灰紫：有颜色但不干净；
 * 明度差又小到看不出是渐变，屏幕上只剩「一块跟纸不一样、又说不上什么色的脏块」。更麻烦的是
 * 行是四角圆角，这块色因此有明确边界，读作「纸面上贴了一片色」；而它与下面曲目行的 `surface`
 * 还差一档，同一张面板被切成两种颜色 —— 正好和这一屏反复在追的「连成一片」是反的。
 *
 * 症结是**面积与浓度的关系搞反了**：大面积必须给稀、小面积可以给浓；原来是一整行给稀
 * （只能出泥色），而真正该有颜色的那颗圆钮反而完全没有信息量。
 *
 * 现在这一行**不铺色了**（回到 `surface`，与曲目行连成一整片纸），封面色改从
 * **圆钮身后一团径向晕**出来：面积小了二十倍，于是可以给浓。本文件因此只需要算**一个**色，
 * 三档的组合连同它存在的理由一起删掉。
 *
 * ## 亮暗由背景自身的明度判定
 *
 * 不再读一遍系统主题：色本来就要贴着背景走，两者必须来自同一个来源，
 * 否则会出现「亮主题下算出暗色」这种自相矛盾。
 */
@Composable
fun rememberCoverAccent(coverUrl: String?): Color {
    val context = LocalContext.current
    val base = MaterialTheme.colorScheme.surface
    val dark = base.luminance() < 0.5f
    var accent by remember(coverUrl, dark, base) { mutableStateOf(base) }

    LaunchedEffect(coverUrl, dark, base) {
        accent = base
        val url = coverUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val seed = readCoverSeed(context, url) ?: return@LaunchedEffect
        accent = accentOf(seed, base, dark)
    }
    return accent
}

/**
 * 取封面主色。**不引 Palette 库**：它只有「主色 / 鲜艳色 / 柔色」几档现成结果，而这里
 * 要的是「色相拿走、明度交还给主题」，自算反而更短、也少一个依赖。
 */
private suspend fun readCoverSeed(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context)
        .data(coverSizedUrl(url, SampleGrid))
        // 只要够算色：Coil 按这个尺寸解码，不会把整张原图拉进内存。
        // ⚠️ 但 size() 只管解码、不管下载 —— 这一处原先为了 24×24 的色块要拉整张
        // 原图（实测 4.5MB）。真正省下带宽的是上面那个地址参数，不是这里的 size()。
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
 * 那就等于没取（晕会跟背景几乎一样）。所以先按 HSL 剔掉过黑 / 过白 / 过灰的像素，再让
 * 中间的像素多算几票（构图重心通常在中间，边角多是留白或黑边）。
 *
 * 全部像素落选（灰阶封面、纯黑封面）时返回 null，由调用方回落到 `surface` ——
 * 宁可这一处没有效果，也不能给一串跟封面无关的颜色。
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
private fun accentOf(seed: Int, base: Color, dark: Boolean): Color {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, seedHsl)
    val baseHsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), baseHsl)

    val recipe = if (dark) DarkAccent else LightAccent
    return tinted(seedHsl, baseHsl[2] + recipe.lightness, recipe.saturation)
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
 * 这里可以**给满**，因为面积小：这团色只活在圆钮身后、直径约 112dp 的一圈里，
 * 不承载任何文字，且大部分被圆钮本身盖住。原来铺满整行时的克制正是那一版不好看的原因
 * （见 [rememberCoverAccent] 的说明），这里不做同样的让步。
 *
 * 明度偏移取「背景之上再走一步」：比背景明显、但不到刺眼。暗主题往上抬、亮主题往下压 ——
 * 两个方向都是「离纸面更远」，与纸面的对比因此是同一个量级。
 */
private class AccentRecipe(val lightness: Float, val saturation: Float)

private val DarkAccent = AccentRecipe(lightness = 0.150f, saturation = 0.55f)

private val LightAccent = AccentRecipe(lightness = -0.120f, saturation = 0.45f)

/** 采样网格边长（像素）：576 次取色，开销可忽略，够分出主色。 */
private const val SampleGrid = 24

/** 明度低于此值的像素不参与投票（黑边、暗角）。 */
private const val NeutralDark = 0.10f

/** 明度高于此值的像素不参与投票（白底、高光）。 */
private const val NeutralLight = 0.90f

/** 饱和度低于此值的像素不参与投票（灰调、黑白照片）。 */
private const val NeutralSaturation = 0.14f
