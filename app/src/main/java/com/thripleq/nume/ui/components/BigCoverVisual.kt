package com.thripleq.nume.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeInk

/**
 * 卡片 / hero 共用的封面解码尺寸（px）。
 *
 * hero 与起点卡片**同源同尺寸**（都按此解码）：`p=0` 时 hero 与卡片逐像素吻合，且 hero 请求
 * 命中卡片已在内存里的同一张——无需额外预解码。拉开过程中的失焦改用运行时模糊
 * （[com.thripleq.nume.ui.theme.Motion.heroBlurPx]），不再是"低清放大"。
 */
const val CardCoverSize = 480

/**
 * 歌手头像在「来源行（搜索结果）↔ 歌手页头部」两端**共用**的解码尺寸（px）。
 *
 * 共享元素两端必须请求同一尺寸才会命中 Coil 内存缓存：否则目标端（歌手页头像）会另起一次
 * 解码，morph 期间先闪 shimmer 占位再出现，观感就是「跳一下 / 抖一下」。360 足够 96dp 头像
 * 在 @3x 下清晰，又小到可忽略内存。
 */
const val ArtistAvatarSize = 360

/**
 * 封面 + 底部渐变遮罩 + 左下角名字（可选元信息）。
 *
 * 横滑大封面卡（起点）与「大封面折进列表」的 banner 头（终点）**共用同一份**——
 * 这是展开/收起对齐契约的关键：p=0 时 banner 恰好等于卡片，收起尾帧精确复位。
 * 按 480px 请求封面，兼顾从卡片（116dp）长到满宽（360dp）的清晰度。
 *
 * @param meta 名字下方的元信息（可多行）；卡片不传 → 仅名字，与旧版一致
 * @param showName 是否显示名字；调用方已在别处显示标题时（如壳顶标题栏）可传 false 避免重复
 * @param scrimTop 渐变遮罩起始位置（0..1，越大遮罩越短）；banner 需承载多行文字故可调高
 * @param scrimAlpha 渐变底部黑度（0..1），保证文字可读
 * @param requestSize 解码尺寸（px）。卡片/hero 用 [CardCoverSize]（同源、内存命中）；
 *                    全屏 banner 用更大值（1024）拿高清。
 * @param textAlpha 文本图层透明度（[State]，只在 draw 阶段读）——展开时与骨架淡出互补地淡入，
 *                  避免元信息"闪现"。null 时恒 1。
 * @param onLoadSuccess 封面真正绘制出来（加载成功或失败落定）时回调一次；供 hero 交接。
 * @param watermarkIcon 内容属性水印：非空时——缺封面用 `secondaryContainer` 底 + 大号图标兜底；
 *                  有封面则在同一位置压一枚淡水印。卡片/hero/banner 传同一图标，三位一体。
 */
@Composable
fun BigCoverVisual(
    coverUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    showName: Boolean = true,
    scrimTop: Float = 0.5f,
    scrimAlpha: Float = NumeFade.IMAGE_SCRIM,
    requestSize: Int = CardCoverSize,
    textAlpha: State<Float>? = null,
    onLoadSuccess: (() -> Unit)? = null,
    watermarkIcon: ImageVector? = null,
) {
    Box(modifier) {
        val context = LocalContext.current
        val model = remember(coverUrl, requestSize) {
            coverUrl?.let { ImageRequest.Builder(context).data(it).size(requestSize).build() }
        }
        if (model != null) {
            // 占位微光仅在加载中组合，加载完成即移除（不再常驻无限扫光）。
            val painter = rememberAsyncImagePainter(model)
            LaunchedEffect(painter.state) {
                // 成功或失败都算「已落定」，避免封面 404 时 hero 永远顶着不交接。
                val s = painter.state
                if (s is AsyncImagePainter.State.Success || s is AsyncImagePainter.State.Error) {
                    onLoadSuccess?.invoke()
                }
            }
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LaunchedEffect(Unit) { onLoadSuccess?.invoke() }
            // 缺封面：有水印图标就用「secondaryContainer 底 + 内容属性图标」，与卡片同源；
            // 否则退回旧的 MusicNote 占位（探索卡片无水印时的兜底）。
            Box(
                Modifier.matchParentSize().background(
                    if (watermarkIcon != null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (watermarkIcon != null) {
                    Icon(
                        watermarkIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = NumeFade.WATERMARK_ON_CONTAINER),
                        modifier = Modifier.fillMaxSize(0.37f),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        // 有封面时压一枚淡水印：与卡片同一图标、同一位置、同一比例。
        if (watermarkIcon != null && model != null) {
            Icon(
                watermarkIcon,
                contentDescription = null,
                tint = NumeInk.Watermark,
                modifier = Modifier.align(Alignment.Center).fillMaxSize(0.37f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        scrimTop to Color.Transparent,
                        1f to NumeInk.Scrim.copy(alpha = scrimAlpha),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                // 文本随内容"浮现"淡入（draw 阶段读，不重组）；封面本身不参与，保持不透明。
                .graphicsLayer { alpha = textAlpha?.value ?: 1f },
        ) {
            if (showName) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NumeInk.OnImage,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!meta.isNullOrBlank()) {
                if (showName) Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = NumeInk.OnImageMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
