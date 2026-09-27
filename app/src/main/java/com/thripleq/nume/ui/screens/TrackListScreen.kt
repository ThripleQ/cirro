package com.thripleq.nume.ui.screens

import com.thripleq.nume.ui.theme.NumeShape
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.LocalShellHeroAlpha
import com.thripleq.nume.ui.components.LocalShellProgress
import com.thripleq.nume.ui.components.LocalShellSettled
import com.thripleq.nume.ui.components.ShimmerImagePlaceholder
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.components.shellInset
import com.thripleq.nume.ui.playerbar.CollectionActions
import com.valentinilk.shimmer.shimmer
import com.thripleq.nume.ui.profile.TrackListSource
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * 统一"壳子 + 列表"详情页：榜单 / 歌单 / 专辑 / 喜欢 / 已购都是同一个结构——
 * 头部集合信息（封面/标题/数据/描述/操作按钮）+ 曲目列表。
 */
@Composable
fun TrackListScreen(
    source: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onActionsOffscreen: (Boolean) -> Unit = {},
    showTopBar: Boolean = true,
    /**
     * 是否由本屏注册系统返回键的 `BackHandler`。
     *
     * 作为 **nav 详情页**时 true：系统返回走 [onBack]（而非 NavHost 直接 pop），返回才是
     * 「浮现收回」。作为**胶囊壳内容**时 false：返回键交回 [ExpandableShell] 处理，否则本屏
     * 后注册的 BackHandler 会抢在壳之前、直接触发 onBack（=移除壳），跳过壳的收起动画。
     */
    backHandlerEnabled: Boolean = true,
    /** 封面是否显示集合名；调用方已在顶栏/壳顶标题栏显示标题时可传 false 避免重复。 */
    showName: Boolean = true,
    /**
     * 封面左右内缩是否跟随壳展开进度。true：封面随壳重排生长（contentFromStart 路径）。
     * false：用常量 16dp 内缩——配合 ExpandableShell 的固定终态排版，使列表 measure 在
     * 动画期间可被跳过（Profile 胶囊面板）。
     */
    coverInsetFollowsShell: Boolean = true,
    /** 回调 banner 封面的窗口坐标矩形（供 ExpandableShell hero 覆盖层做终点对齐）。 */
    onCoverRect: ((Rect) -> Unit)? = null,
    /** 高清 banner 封面加载成功时回调（供 hero 交接：hero 渐变淡出）。 */
    onCoverReady: (() -> Unit)? = null,
    /**
     * 加载阶段用于「先画封面」的封面 URL（通常由入口卡片传入，与终态 banner 同源）。
     * 非空时骨架屏不显示灰封面，而是立刻请求高清封面 + 显示 shimmer 行——这样 banner 封面
     * 不必等整张列表（含分页补全）下载完才开始加载，hero 也能尽早交接。
     */
    previewCoverUrl: String? = null,
    /** banner 缺封面时的内容属性水印图标（与入口卡片/hero 同源）。 */
    watermarkIcon: ImageVector? = null,
    bottomPadding: Dp = 16.dp,
) {
    // 系统返回键走 onBack（而非 NavHost 直接 pop）：返回是「浮现收回」，需先把导航方向
    // 标成逆向，子页才会沿原路缩回、父页直接露底。壳内容里禁用（见 [backHandlerEnabled]）。
    BackHandler(enabled = backHandlerEnabled) { onBack() }
    val vm: TrackListViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val src = remember(source) { TrackListSource.from(source) }

    // 头部三按钮的滚动位置：滚到接近视口顶（即将看不见）时上报，触发底部操作浮岛。
    val listState = rememberLazyListState()
    var actionsTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    val actionsThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }
    val actionsOffscreen by remember { derivedStateOf { actionsTop < actionsThresholdPx } }
    LaunchedEffect(actionsOffscreen) { onActionsOffscreen(actionsOffscreen) }

    // 加载与壳展开**并行**：动画一开始就发起请求，数据在后台拉取。但**切到列表**（LazyColumn +
    // 图片首次组合）推迟到壳展开动画**完全结束**之后：数据/封面一旦被缓存，重开时若允许在中途
    // 切换，列表会在动画期间首次组合 → 稳定掉帧（冷启动时数据未到、只画骨架反而顺）。
    // 非壳环境（shellSettled 恒 true / progress 恒 1）立即就绪。
    val shellSettled = LocalShellSettled.current
    var contentReady by remember { mutableStateOf(false) }
    LaunchedEffect(source, id) {
        vm.load(src, id, title)
        if (!shellSettled.value) {
            snapshotFlow { shellSettled.value }.first { it }
        }
        contentReady = true
    }

    // 大封面就绪前不切到真列表。hero 与列表共用同一张封面的就绪时机：若列表先于封面出现，
    // 就进入「已经能滚、hero 却因为大图没到还顶着」的窗口——hero 是浮层、不随列表滚动，
    // 封面看着像卡住。把切换也压到大封面就绪之后，则列表出现与 hero 交接同一刻发生，
    // 而等待期间是不可滚的骨架（还已挂着高清封面），观感无感。
    // previewCoverUrl 为空表示无可等之图；非壳环境（NumeApp 导航）不传该值 → 天然不等待。
    var coverReady by remember { mutableStateOf(previewCoverUrl == null) }
    val onCoverDrawn: () -> Unit = remember(onCoverReady) {
        {
            coverReady = true
            onCoverReady?.invoke()
        }
    }
    LaunchedEffect(Unit) { vm.openPlayer.collect { onOpenPlayer() } }

    // 数据到了直接显示列表（不预载封面：滚动到哪张就单张串行下载）。
    val collection = (state as? TrackListUiState.Ready)?.collection

    // 胶囊撑开：scale 0.92→1 + 圆角 28→0，内容像一颗胶囊被拉开成整屏。
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp),
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 内容目标态：数据到达、壳动画结束**且大封面已就绪**后才切到列表；其余为骨架/空/错误。
        // 目标态作**不透明底板**先画，骨架叠在其上渐隐——而不是 Crossfade 让两者同时半透明。
        // 两者同时半透明时谁也盖不住壳的深色底，封面/内容会短暂发暗（正常速度下就是
        // 「闪黑一下」）；底板恒在则全程不发暗。封面两态同源（骨架用 previewCoverUrl），
        // 淡化期间封面视觉无缝，不破坏 ExpandableShell 的 hero 交接对齐。
        val display: Any = when {
            collection != null && contentReady && coverReady -> collection
            state is TrackListUiState.Empty -> TrackListUiState.Empty
            state is TrackListUiState.Error -> TrackListUiState.Error
            else -> TrackListUiState.Loading
        }
        val skeletonAlpha = animateFloatAsState(
            targetValue = if (display is TrackListUiState.Loading) 1f else 0f,
            animationSpec = tween(260),
            label = "trackListSkeletonAlpha",
        )
        // 内容「浮现度」：与骨架淡出严格互补（不重组，供 draw 阶段读）。
        // 骨架下的 meta/按钮直接满不透明出现会像"闪现"，用它做出场淡入。
        val contentReveal = remember(skeletonAlpha) { derivedStateOf { 1f - skeletonAlpha.value } }
        // 收起退场：整块内容随壳收缩进度淡出，由 shell 统一处理（见 ExpandableShell），
        // 这里不再单开一个 200ms 退场时钟——列表只做出场浮现（draw 阶段读，不重组）。
        val rowsAlpha = contentReveal
        Box(Modifier.fillMaxSize()) {
            when (display) {
                is TrackCollection -> {
                    val target = display
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = if (showTopBar) 8.dp else 0.dp,
                            end = 0.dp,
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 统一 banner 头：封面是列表第一项（左右 16dp 内缩、随滚动移出），元信息叠在封面里；
                        // 列表行同样 16dp 内缩，与封面同宽。所有列表（榜单/歌单/专辑/喜欢/已购）共用此形态。
                        item(key = "header") {
                            TrackListBannerHeader(
                                target,
                                vm,
                                showName,
                                coverInsetFollowsShell,
                                onCoverRect,
                                onCoverDrawn,
                                watermarkIcon,
                                textAlpha = rowsAlpha,
                            ) { actionsTop = it }
                        }
                        itemsIndexed(
                            target.tracks,
                            key = { _, t -> t.id },
                            contentType = { _, _ -> "track" },
                        ) { index, track ->
                            // 行随内容浮现 / 收起退场淡入淡出（draw 阶段读，不重组）。
                            Box(Modifier.graphicsLayer { alpha = rowsAlpha.value }) {
                                TrackRow(index, track, hPadding = 16.dp) {
                                    vm.onTrackClick(target, index)
                                }
                            }
                        }
                    }
                }
                TrackListUiState.Empty -> CenteredHint("暂无曲目", MaterialTheme.colorScheme.onSurfaceVariant)
                TrackListUiState.Error -> CenteredHint("曲目加载失败", MaterialTheme.colorScheme.error)
            }
            if (skeletonAlpha.value > 0.001f) {
                TrackListSkeleton(
                    showTopBar = showTopBar,
                    coverInsetFollowsShell = coverInsetFollowsShell,
                    coverUrl = previewCoverUrl,
                    title = title,
                    onCoverRect = onCoverRect,
                    onCoverReady = onCoverDrawn,
                    // 叠在目标态之上淡出（draw 阶段读，不重组）。
                    modifier = Modifier.graphicsLayer { alpha = skeletonAlpha.value },
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * 列表骨架：与 banner 头同构——方形封面（内缩量同真实头，随壳展开进度收起）+ 居中三按钮 + 曲目行。
 *
 * [coverUrl] 非空时封面位置直接渲染**真实高清封面**（不再等整张列表），其余仍 shimmer——
 * 这样 banner 封面与整表下载解耦，hero 能尽早交接。封面块**不能**包在 shimmer 容器里，
 * 否则扫光会扫到真封面；故此时 shimmer 只挂在下方按钮/行。
 */
@Composable
private fun TrackListSkeleton(
    showTopBar: Boolean,
    coverInsetFollowsShell: Boolean = true,
    coverUrl: String? = null,
    title: String = "",
    onCoverRect: ((Rect) -> Unit)? = null,
    onCoverReady: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progress = LocalShellProgress.current
    // 展开动画期间 hero 正顶着封面：骨架封面与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    Column(
        modifier
            .fillMaxSize()
            .then(if (coverUrl == null) Modifier.shimmer() else Modifier)
            .padding(top = if (showTopBar) 8.dp else 0.dp),
    ) {
        val coverModifier = Modifier
            .fillMaxWidth()
            .then(
                if (coverInsetFollowsShell) Modifier.shellInset(progress, 16.dp)
                else Modifier.padding(horizontal = 16.dp),
            )
            .aspectRatio(1f)
            // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
            .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f }
        if (coverUrl != null) {
            Box(
                coverModifier
                    .then(
                        if (onCoverRect != null) {
                            Modifier.onGloballyPositioned {
                                onCoverRect.invoke(Rect(it.localToWindow(Offset.Zero), it.size.toSize()))
                            }
                        } else {
                            Modifier
                        },
                    )
                    .clip(NumeShape.Card),
            ) {
                BigCoverVisual(
                    coverUrl = coverUrl,
                    name = title,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 1024,
                    onLoadSuccess = onCoverReady,
                )
            }
        } else {
            SkeletonBox(coverModifier, NumeShape.Card)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                repeat(3) { SkeletonBox(Modifier.width(96.dp).height(40.dp), RoundedCornerShape(percent = 50)) }
            }
            Spacer(Modifier.height(12.dp))
            repeat(6) { SkeletonTrackRow() }
        }
    }
}

@Composable
private fun SkeletonTrackRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(48.dp), NumeShape.Chip)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
        }
        SkeletonBox(Modifier.size(24.dp), CircleShape)
    }
}

/**
 * Banner 头：封面作为列表第一项，随列表滚动移出。
 *
 * 封面与列表行的内容同宽（左右 16dp 内缩），方形、圆角；内缩量随壳展开进度从 0 收到 16dp，
 * 于是 p=0 时封面恰好满宽等于起点卡片（同源对齐契约），完全展开后与列表对齐。
 * 元信息叠在封面底部（名字下方，渐变遮罩保证可读）。
 */
@Composable
private fun TrackListBannerHeader(
    collection: TrackCollection,
    vm: TrackListViewModel,
    showName: Boolean = true,
    coverInsetFollowsShell: Boolean = true,
    onCoverRect: ((Rect) -> Unit)? = null,
    onCoverReady: (() -> Unit)? = null,
    watermarkIcon: ImageVector? = null,
    textAlpha: State<Float>? = null,
    onActionsTop: (Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val progress = LocalShellProgress.current
    // 展开动画期间 hero 正顶着封面：本封面与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    val meta = listOfNotNull(
        collectionMetaLine(collection).takeIf { it.isNotBlank() },
        collection.updateFrequency.takeIf { it.isNotBlank() },
        collection.description.takeIf { it.isNotBlank() },
    ).joinToString("\n")

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (coverInsetFollowsShell) Modifier.shellInset(progress, 16.dp)
                    else Modifier.padding(horizontal = 16.dp),
                )
                .aspectRatio(1f)
                .then(
                    // 仅需要测量终态矩形时才挂 onGloballyPositioned：否则动画期间它是每帧回调。
                    if (onCoverRect != null) {
                        Modifier.onGloballyPositioned {
                            onCoverRect.invoke(Rect(it.localToWindow(Offset.Zero), it.size.toSize()))
                        }
                    } else {
                        Modifier
                    },
                )
                .clip(NumeShape.Card)
                // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f },
        ) {
            BigCoverVisual(
                coverUrl = collection.coverUrl,
                name = collection.name,
                modifier = Modifier.fillMaxSize(),
                meta = meta.ifBlank { null },
                showName = showName,
                scrimTop = 0.35f,
                scrimAlpha = 0.85f,
                requestSize = 1024,
                onLoadSuccess = onCoverReady,
                watermarkIcon = watermarkIcon,
                textAlpha = textAlpha,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onActionsTop(it.positionInWindow().y) }
                // 操作行与封面里的文本一起出场淡入（draw 阶段读，不重组）。
                .graphicsLayer { alpha = textAlpha?.value ?: 1f },
            horizontalArrangement = Arrangement.Center,
        ) {
            CollectionActions(
                onPlayAll = { vm.onPlayAll(collection) },
                onPlaceholderAction = {
                    Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** 数据行：按有值的字段拼接，如 "250.4亿次播放 · 3752.2万人收藏"。 */
private fun collectionMetaLine(c: TrackCollection): String {
    val parts = mutableListOf<String>()
    if (c.playCount > 0) parts += "${formatCount(c.playCount)}次播放"
    if (c.subscribedCount > 0) parts += "${formatCount(c.subscribedCount)}人收藏"
    if (c.trackCount > 0) parts += "${c.trackCount}首"
    if (c.creator.isNotBlank()) parts += c.creator
    return parts.joinToString(" · ")
}

/** 数字缩写：亿 / 万 / 原样。 */
private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e8)) + "亿"
    n >= 10_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e4)) + "万"
    else -> "$n"
}

private fun trimZero(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s

/* ── 列表项：序号 + 封面 + 歌名/歌手 + 三点菜单 ───────── */

/** 行级不可变基础 modifier（fillMaxWidth + 圆角裁剪），避免每次重组重建 modifier 链。 */
private val trackRowBaseModifier = Modifier
    .fillMaxWidth()
    .clip(NumeShape.CardSmall)

@Composable
private fun TrackRow(index: Int, track: Track, hPadding: Dp = 8.dp, onClick: () -> Unit) {
    // model 整体 remember：AsyncImagePainter 以 model 为 key，每次重组新建 ImageRequest
    // 会重走请求分发；按 96px（48dp 封面 @2x）尺寸构造并缓存。
    val context = LocalContext.current
    val artwork = remember(track.artworkUrl) {
        track.artworkUrl?.let {
            ImageRequest.Builder(context).data(it).size(96).build()
        }
    }
    Row(
        modifier = trackRowBaseModifier
            .clickable(onClick = onClick)
            .padding(horizontal = hPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(NumeShape.Chip),
        ) {
            if (artwork != null) {
                val painter = rememberAsyncImagePainter(artwork)
                ShimmerImagePlaceholder(painter, Modifier.matchParentSize())
                Image(
                    painter = painter,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.artist.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { /* 三点菜单：暂无功能 */ }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
