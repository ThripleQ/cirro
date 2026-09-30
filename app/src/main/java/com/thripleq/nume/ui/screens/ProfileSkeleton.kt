package com.thripleq.nume.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer

/** 我的页骨架：与已登录内容同构——用户卡 + 2×2 大卡网格。 */
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
        // 2×2 大卡，与 ProfileCardGrid 同构（方形、行距 12、列距 12）。
        Column(Modifier.fillMaxWidth()) {
            repeat(2) { rowIndex ->
                if (rowIndex > 0) Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        SkeletonBox(
                            Modifier.weight(1f).aspectRatio(1f),
                            NumeShape.Card,
                        )
                    }
                }
            }
        }
    }
}
