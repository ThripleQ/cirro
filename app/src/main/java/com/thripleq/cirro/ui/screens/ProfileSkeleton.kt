package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.components.SkeletonBox
import com.thripleq.cirro.ui.components.SkeletonLine
import com.thripleq.cirro.ui.theme.CirroShape
import com.valentinilk.shimmer.shimmer

/**
 * 我的页骨架：**与已登录内容逐块同构** —— 账号抬头 + 「喜欢的音乐」全宽横幅 +
 * 三张紧凑行卡（已购 / 创建 / 收藏）。
 *
 * 2026-10-05 跟着版式更新：以前的骨架还是「用户卡 + 2×2 方形大卡」，
 * 那是杂志版 hero 时期的版式，早就和真内容对不上了 —— 骨架的价值全在"位置一致"，
 * 对不上时加载完成会整体跳一下，比不做骨架还差。
 */
@Composable
internal fun ProfileSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        // 账号抬头（同 ProfileHeaderRow：64dp 圆头像 + 一行昵称，左内缩 14）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(64.dp), CircleShape)
            Spacer(Modifier.width(14.dp))
            SkeletonLine(widthFraction = 0.36f, height = 20.dp)
        }
        Spacer(Modifier.height(12.dp))

        // 主入口：「喜欢的音乐」全宽横幅（21:10，同 LikedHeroCard）。
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(21f / 10f), CirroShape.Card)
        Spacer(Modifier.height(10.dp))

        // 尾部三张紧凑行卡（已购 / 创建的歌单 / 收藏的歌单，同 ProfileRowCard）。
        repeat(3) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CirroShape.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                SkeletonBox(Modifier.size(64.dp), CirroShape.CardSmall)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonLine(widthFraction = 0.3f, height = 16.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(widthFraction = 0.5f, height = 12.dp)
                }
            }
            if (i < 2) Spacer(Modifier.height(10.dp))
        }
    }
}
