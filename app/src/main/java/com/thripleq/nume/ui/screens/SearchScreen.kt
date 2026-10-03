package com.thripleq.nume.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.nume.ui.components.NumePageTitleBar
import com.thripleq.nume.ui.search.SearchViewModel

/**
 * 搜索 tab：落地页给「语种 / 风格 / 场景」标签，提交后按
 * 单曲 / 歌单 / 播客 / 专辑 / 歌手 五类分页展示（对齐 kanade）。
 */
@Composable
fun SearchScreen(
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
    val focusManager = LocalFocusManager.current

    // 结果态下返回键先退回落地页，而不是退出 app。
    BackHandler(enabled = state.active != null) { vm.onBack() }

    val bottomPadding = (islandHeight + 16f).dp

    // 搜索框是否聚焦。历史只在「点开搜索框」时露头：聚焦 + 空输入才展示，输入或失焦即收起，
    // 不占用落地页默认视野。
    var fieldFocused by remember { mutableStateOf(false) }
    val showHistory = fieldFocused && state.query.isEmpty()

    // 与探索页同一套外壳：顶层铺 `surfaceContainer` 放「搜索」大标题，内容是一张 `surface`
    // 圆角纸。搜索条就是这张纸的顶盖 —— 占满整宽、紧贴纸上沿，并直接顺承纸的 28dp 顶角，
    // 于是容器色从两角露出、搜索条与内容浑然一体。
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        NumePageTitleBar("搜索")
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = HomeSheetRadius, topEnd = HomeSheetRadius))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SearchField(
                query = state.query,
                inResults = state.active != null,
                onQueryChange = vm::onQueryChange,
                onSubmit = vm::onSubmit,
                onClear = vm::onClear,
                onBack = vm::onBack,
                onFocusChange = { fieldFocused = it },
            )
            // 点搜索条以外的空白处即收起键盘与历史（点标签/历史词由子节点消费，不触发）。
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures { focusManager.clearFocus() }
                    },
            ) {
                when {
                    state.active == null -> LandingContent(
                        bottomPadding = bottomPadding,
                        history = state.history,
                        showHistory = showHistory,
                        onTag = vm::onTagSearch,
                        onHistory = vm::onHistoryClick,
                        onClearHistory = vm::onClearHistory,
                    )
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
    }
}

/* ── 搜索条：壳子纸的顶盖 ───────────────────────────────── */

@Composable
private fun SearchField(
    query: String,
    inResults: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 结果态 ← 返回落地页；落地态是放大镜提示。
        if (inResults) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = scheme.onSurface,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onBack),
            )
        } else {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                onSubmit()
            }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) }
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
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
