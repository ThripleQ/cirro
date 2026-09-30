package com.thripleq.nume.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.nume.ui.search.SearchViewModel
import com.thripleq.nume.ui.theme.NumeShape

/**
 * 搜索 tab：落地页给「语种 / 风格 / 场景」标签，提交后按
 * 单曲 / 歌单 / 播客 / 专辑 / 歌手 五类分页展示（对齐 kanade）。
 */
@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onOpenTracks: (source: String, id: String, title: String, origin: Rect) -> Unit,
    onOpenArtist: (id: String, name: String, avatarUrl: String, origin: Rect) -> Unit,
    onOpenRadio: (id: String, name: String, origin: Rect) -> Unit,
    islandHeight: Float = 0f,
    // 共享元素试验：歌手头像在「搜索结果行 ↔ 歌手页头部」间做官方 sharedElement。
    // 为空则退化为普通图片（不影响其他调用方）。
    shared: SharedTransitionScope? = null,
    avScope: AnimatedVisibilityScope? = null,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    // 结果态下返回键先退回落地页，而不是退出 app。
    BackHandler(enabled = state.active != null) { vm.onBack() }

    val bottomPadding = (islandHeight + 16f).dp

    Column(Modifier.fillMaxSize()) {
        SearchTopBar(
            query = state.query,
            inResults = state.active != null,
            onQueryChange = vm::onQueryChange,
            onSubmit = vm::onSubmit,
            onClear = vm::onClear,
            onBack = vm::onBack,
        )
        when {
            state.active == null -> LandingContent(bottomPadding, onTag = vm::onTagSearch)
            else -> ResultsContent(
                state = state,
                bottomPadding = bottomPadding,
                onTab = vm::onTabSelect,
                onLoadMore = vm::onLoadMore,
                onRetry = vm::onRetry,
                onPlayTrack = vm::onPlayTrack,
                onOpenTracks = onOpenTracks,
                onOpenArtist = onOpenArtist,
                onOpenRadio = onOpenRadio,
                shared = shared,
                avScope = avScope,
            )
        }
    }
}

/* ── 顶部搜索框 ─────────────────────────────────────────── */

@Composable
private fun SearchTopBar(
    query: String,
    inResults: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inResults) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = scheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(NumeShape.Capsule)
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.Enter || e.key == Key.NumPadEnter)
                        ) {
                            keyboard?.hide()
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "单曲、歌单、专辑以及更多内容",
                                color = scheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onClear()
                            keyboard?.hide()
                        },
                )
            }
        }
    }
}
