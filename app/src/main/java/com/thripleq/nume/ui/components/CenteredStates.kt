package com.thripleq.nume.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 居中外壳 —— 取代各屏重复的 `CenteredBox` / `Centered` / `Center` / `SearchCenteredBox`。
 * 内容在整屏居中，用于空态 / 错误态 / 加载态。
 */
@Composable
fun NumeCenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 空态：居中、次要文字色。 */
@Composable
fun NumeEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    NumeCenteredBox(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 错误态：居中、`error` 色，整块可点重试。
 *
 * 统一全 app 的错误交互语言 —— 之前有的用 `Button`、有的 `TextButton`、有的文案可点，
 * 颜色也在 `onSurface`/`onSurfaceVariant`/`error` 间漂移。这里固定「文案即重试入口」。
 */
@Composable
fun NumeErrorState(
    text: String = "加载失败，点此重试",
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NumeCenteredBox(modifier.clickable(onClick = onRetry)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 列表尾部「加载中」spinner（翻页）。 */
@Composable
fun NumeLoadMoreIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp))
    }
}

/**
 * 列表尾部「翻页失败，点此重试」。
 *
 * 翻页失败不能静默：spinner 消失后列表看起来「到底了」，用户无从得知还有下一页。
 */
@Composable
fun NumeLoadMoreFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(NumeShape.Chip)
            .clickable(onClick = onRetry)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "加载失败，点此重试",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
