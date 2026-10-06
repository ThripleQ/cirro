package com.thripleq.cirro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 封面 / 头像尺寸令牌 —— 全 app 封面尺寸与解码尺寸的**单一来源**。
 *
 * 之前同一语义的「歌曲行封面」在不同屏用了 48 / 52dp 两套，圆角也在 `Chip`(8) 与
 * `CardSmall`(12) 间漂移；解码尺寸（96/120/320/480…）散落各处。此处归一：
 * **列表行封面统一 [Row]（52dp + [CirroShape.Chip]）**，其余按角色取。
 *
 * 解码尺寸 = dp × 约 2.3（@2~3x 下够清晰又不浪费内存）；两端做共享元素时须请求同一值
 * （见 [ArtistAvatarRequest]、[CardCoverSize]）。
 */
object CirroArt {
    /** 列表行封面：所有「歌曲行 / 媒体行 / 榜单行 / 小卡」统一 52dp。 */
    val Row = 52.dp

    /** 行封面解码尺寸（px）。 */
    const val RequestRow = 120

    /** 大卡 / 专辑卡 / 头像的解码尺寸（px）。 */
    const val RequestLarge = 360

    /** 全屏头像 / 电台头等大封面。 */
    val Radio = 112.dp
    val AlbumCard = 118.dp

    /** 头像：评论 36dp / 用户与登录卡 64dp / 歌手 96dp。 */
    val AvatarSm = 36.dp
    val AvatarMd = 64.dp
    val AvatarLg = 96.dp
}

/**
 * 通用封面图：`remember(url)` + Coil 解码 + 加载微光 + 缺图音符兜底。
 *
 * 取代各屏自写的 `SearchCover` / `Cover` 及内联 Box。`shape` 默认小圆角 [CirroShape.Chip]（列表行），
 * 圆形头像传 `CircleShape`，大卡传 [CirroShape.Card]。
 */
@Composable
fun CirroArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = CirroArt.Row,
    shape: Shape = CirroShape.Chip,
    requestSize: Int = CirroArt.RequestRow,
    fallbackIcon: ImageVector = Icons.Filled.MusicNote,
    fallbackIconSize: Dp = 22.dp,
) {
    val context = LocalContext.current
    val model = remember(url, requestSize) {
        url?.let { ImageRequest.Builder(context).data(it).size(requestSize).build() }
    }
    // size = null：由调用方用 modifier 自行定尺寸（如 fillMaxWidth × 固定高）。
    val sizedModifier = if (size != null) modifier.size(size) else modifier
    Box(sizedModifier.clip(shape)) {
        if (model != null) {
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(fallbackIconSize),
                )
            }
        }
    }
}
