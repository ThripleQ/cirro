package com.thripleq.nume.ui.playerbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import kotlin.math.roundToInt

/** 封面：卡片/全屏共用；无图时用弱化音符占位，有图按目标像素解码。 */
@Composable
internal fun CoverArt(
    state: PlayerUiState,
    dim: Dp,
    corner: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val px = with(density) { dim.toPx() }.roundToInt().coerceIn(64, 1280)
    Box(
        modifier = modifier
            .size(dim)
            .clip(RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        val coverUrl = state.coverUrl
        if (coverUrl == null) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
            // 空状态：一块空白封面太秃，放个弱化的音符占位。
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(iconSize),
            )
        } else {
            val model = remember(coverUrl, px) {
                ImageRequest.Builder(context).data(coverUrl).size(px).build()
            }
            val painter = rememberAsyncImagePainter(model)
            ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
            Image(
                painter = painter,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


internal fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
