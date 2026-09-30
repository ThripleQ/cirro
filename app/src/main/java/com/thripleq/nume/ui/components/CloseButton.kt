package com.thripleq.nume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeInk

/**
 * 浮层左上角收起键：36dp 圆 + 黑底（[NumeFade.CONTROL_SCRIM]）+ 白色下箭头。
 *
 * 取代官方壳 [ShellPanel] 与自研壳 `CoverExpandShell` 里两份相同的实现。
 * 调用方用 `modifier` 传入对齐 / `statusBarsPadding()` / 外边距。
 */
@Composable
fun NumeCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(NumeInk.Scrim.copy(alpha = NumeFade.CONTROL_SCRIM))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = "收起",
            tint = NumeInk.OnImage,
            modifier = Modifier.size(24.dp),
        )
    }
}
