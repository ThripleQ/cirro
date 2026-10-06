package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.components.CirroMediaRowSkeleton
import com.valentinilk.shimmer.shimmer

/** 搜索结果骨架：分类页签下方重复结果行（封面 + 标题/副标题）。四类页签行高一致，通用。 */
@Composable
internal fun SearchSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .shimmer()
            .padding(top = 4.dp),
    ) {
        repeat(9) { CirroMediaRowSkeleton() }
    }
}
