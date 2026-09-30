package com.thripleq.nume.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer

/** 我的页骨架：与已登录内容同构——用户卡 + 四颗胶囊（内部图标/标题/尾部占位）。 */
@Composable
internal fun ProfileSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonLine(widthFraction = 0.4f, height = 18.dp)
                SkeletonLine(widthFraction = 0.22f, height = 12.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        repeat(4) {
            SkeletonCapsule()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkeletonCapsule() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NumeShape.Card)
            // 骨架家族统一用 surfaceVariant（与 Skeleton.kt 一致）；Highest 是播放页壳的层次
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(36.dp), NumeShape.CardSmall)
        Spacer(Modifier.width(14.dp))
        SkeletonLine(widthFraction = 0.34f, height = 16.dp)
        Spacer(Modifier.weight(1f))
        SkeletonBox(Modifier.size(24.dp), CircleShape)
    }
}

@Composable
internal fun ErrorRow(onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 24.dp)) {
        Text("加载失败", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
