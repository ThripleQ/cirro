package com.thripleq.nume.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import com.thripleq.nume.ui.components.BannerCoverSize
import com.thripleq.nume.ui.components.BigCoverVisual
import com.thripleq.nume.ui.components.LocalShellHeroAlpha
import com.thripleq.nume.ui.components.LocalShellSettled
import com.thripleq.nume.ui.components.NumeContainer
import com.thripleq.nume.ui.components.NumeEmptyState
import com.thripleq.nume.ui.components.NumeErrorState
import com.thripleq.nume.ui.components.NumeArtwork
import com.thripleq.nume.ui.components.NumeArt
import com.thripleq.nume.ui.components.SkeletonBox
import com.thripleq.nume.ui.components.SkeletonLine
import com.thripleq.nume.ui.components.numeEntrySurface
import com.thripleq.nume.ui.components.rememberCoverFace
import com.thripleq.nume.ui.profile.TrackListSource
import com.thripleq.nume.ui.profile.TrackListUiState
import com.thripleq.nume.ui.profile.TrackListViewModel
import com.thripleq.nume.ui.theme.NumeFade
import com.thripleq.nume.ui.theme.NumeInk
import com.thripleq.nume.ui.theme.NumeShape
import com.valentinilk.shimmer.shimmer
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * 歌单页版式令牌 —— 2026-10-03 按官方歌单页实机抄量（1080×2400@480dpi，px÷3 换成 dp）。
 *
 * 头部与面板内缩 20dp、曲目行封面距屏边 16dp（官方两处本来就不同，照抄），方封面 98dp、
 * 「播放全部」圆钮 38dp（取整 40dp）、曲目行封面 44dp。曲目行本身仍是 nume 的条目卡
 * （见 [TrackListRow]）——官方那套紧挨的平铺行只借了封面尺寸与「歌手 - 专辑」这一行信息。
 *
 * [HeroCoverTop] 同时是 [com.thripleq.nume.ui.components.CoverExpandShell] 的 hero 终点
 * 预测值（封面相对内容顶的偏移）——两边必须同源，改一处就够。
 */
object TrackListMetrics {
    /** 头部 / 面板内缩。 */
    val SideInset = 20.dp

    /** 曲目行封面距屏幕边：条目卡外缩（[NumeContainer.Inset]）+ 卡内缩，两者相加是这个值。 */
    val RowInset = 16.dp

    /** 头部方封面边长。 */
    val CoverSize = 98.dp

    /** 左上角返回键 / 壳关闭键那一行的净高（浮层控件 48dp + 上下各 4dp 余量）。 */
    val TopBarHeight = 56.dp

    /** 头部与返回栏（壳路径则是壳顶空档）之间的间距。 */
    val HeaderTopGap = 12.dp

    /** 头部封面相对**内容顶**的偏移：壳路径用空档顶替返回栏，两条路径封面落在同一屏上位置。 */
    val HeroCoverTop = TopBarHeight + HeaderTopGap

    /**
     * 面板首行（「播放全部」）吸顶停靠线 = 顶部标题条（`TrackListTitleBar`）的下沿。
     *
     * 2026-10-03 照抄搜索页后标题条**高度跟着文字走**（headlineSmall + 行高裁剪，不再
     * 是固定 56dp），随用户字体缩放变化 —— 停靠线不再是常量：运行时由标题条
     * `onSizeChanged` 测出（stickyTopPx），列表内缩与头部内缩共用该值；头部封面落点
     * 仍是 [HeroCoverTop]，差额记在头部自己的上内缩里。
     */

    /**
     * 标题条文字的起始内缩：让开浮在左上的返回键 / 壳关闭键（48dp 触控盒 + 4dp 内缩）那一档，
     * 与那颗键同排但不叠。
     */
    val TitleBarTextStart = TopBarHeight + 8.dp

    /**
     * 内容圆角纸的顶角半径。
     *
     * 与探索 / 搜索页那张纸**同一个半径**（[HomeSheetRadius]）：详情页从 2026-10-03 起也走
     * 「容器色条 + 圆角纸」这套关系（见 [TrackListScreen] 里的圆角纸层），半径必须同源，
     * 否则站内三张纸的圆角不一致。
     */
    val SheetCorner = HomeSheetRadius

    /** 「播放全部」圆钮直径。 */
    val DiscSize = 40.dp

    /** 「播放全部」行的**内容高度**（圆钮 40dp + 上下各 14dp 内缩）。 */
    val PlayAllRowHeight = DiscSize + 28.dp

    /**
     * 「播放全部」行下边两角反圆角「向外张开」的那对**脚**的深度。
     *
     * 与纸角同半径：上下同半径才对称，且上边角在停靠线上与壳子的圆角严格重合。
     * 这一段没有内容、是纯色面，所以行盒总高要额外加上它（见 [PlayAllBoxHeight]）。
     */
    val PlayAllFeet = SheetCorner

    /** 行盒总高 = 内容 + 脚。封面派生色的渐变里，「脚的上沿」就落在两者之比处。 */
    val PlayAllBoxHeight = PlayAllRowHeight + PlayAllFeet

    /** 曲目行封面边长。 */
    val RowCover = 44.dp
}

/**
 * 统一"壳子 + 列表"详情页：榜单 / 歌单 / 专辑 / 喜欢 / 已购都是同一个结构。
 *
 * ## 版式（抄自官方歌单页）
 * 1. 页底 = 封面糊底 + 主题色蒙版（固定不滚）：头部那块透出封面色，面板往下是不透明面。
 * 2. 头部 = 左方封面 + 右侧标题 / 作者 / 简介，下面三枚等宽操作胶囊（分享 / 评论 / 收藏）。
 * 3. 圆角面板从「播放全部」行开始，向下是曲目行（条目卡：封面 + 标题 + 歌手 - 专辑 + ⋮）。
 *
 * 旧的「满宽方封面 + 名字压在封面上」那套（banner 头）已撤：封面不再承担标题，
 * 标题由封面右侧的文本承担，于是封面可以缩到 98dp —— 与官方一致的信息密度。
 */
@Composable
fun TrackListScreen(
    source: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onActionsOffscreen: (Boolean) -> Unit = {},
    /**
     * 是否由本屏画左上角返回键。作为 **nav 详情页**时 true；作为**胶囊壳内容**时 false——
     * 壳自带关闭键（同位、同浮层语言），再画一个就重叠了。
     */
    showBackButton: Boolean = true,
    /**
     * 是否由本屏注册系统返回键的 `BackHandler`。
     *
     * 作为 **nav 详情页**时 true：系统返回走 [onBack]（而非 NavHost 直接 pop），返回才是
     * 「浮现收回」。作为**胶囊壳内容**时 false：返回键交回 [ExpandableShell] 处理，否则本屏
     * 后注册的 BackHandler 会抢在壳之前、直接触发 onBack（=移除壳），跳过壳的收起动画。
     */
    backHandlerEnabled: Boolean = true,
    /** 回调头部封面的窗口坐标矩形（供 ExpandableShell hero 覆盖层做终点对齐）。 */
    onCoverRect: ((Rect) -> Unit)? = null,
    /** 封面加载成功时回调（供 hero 交接：hero 渐变淡出）。 */
    onCoverReady: (() -> Unit)? = null,
    /**
     * 加载阶段用于「先画封面」的封面 URL（通常由入口卡片传入，与终态头部同源）。
     * 非空时骨架屏不显示灰封面，而是立刻请求高清封面 + 显示 shimmer 行——这样封面
     * 不必等整张列表（含分页补全）下载完才开始加载，hero 也能尽早交接。
     */
    previewCoverUrl: String? = null,
    /** 缺封面时的内容属性水印图标（与入口卡片/hero 同源）。 */
    watermarkIcon: ImageVector? = null,
    bottomPadding: Dp = 16.dp,
    /**
     * 官方共享元素：附加到头部封面（骨架/终态都挂）的 modifier，用于和入口卡片封面
     * 做 `sharedElement` morph（对齐歌手头像那套）。默认空即无共享元素。
     */
    coverSharedModifier: Modifier = Modifier,
) {
    // 系统返回键走 onBack（而非 NavHost 直接 pop）：返回是「浮现收回」，需先把导航方向
    // 标成逆向，子页才会沿原路缩回、父页直接露底。壳内容里禁用（见 [backHandlerEnabled]）。
    BackHandler(enabled = backHandlerEnabled) { onBack() }
    val vm: TrackListViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val src = remember(source) { TrackListSource.from(source) }

    // 操作区（头部三胶囊）的滚动位置：滚到接近视口顶（即将看不见）时上报，触发底部操作浮岛。
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

    // 「播放全部」行的面：从封面派生（见 [rememberCoverFace]）。三档上浅下浓 —— 主体那一段
    // 几乎就是主题背景（压着正文，不让），往下越浓，到两角那只**脚**上让满：脚内侧那道
    // 反圆角的轮廓全靠「面 vs 底下曲目行」这点差。封面没到（previewCoverUrl）就先用正片的
    // URL，两者同源，取出来的色一致。
    val coverFace = rememberCoverFace(previewCoverUrl ?: collection?.coverUrl)
    val faceBrush = remember(coverFace) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to coverFace.top,
                // 脚的上沿 = 内容高度那一条线（68 / 96）。
                (TrackListMetrics.PlayAllRowHeight / TrackListMetrics.PlayAllBoxHeight) to coverFace.middle,
                1f to coverFace.bottom,
            ),
        )
    }

    // 尚未接通的入口（分享 / 评论 / 收藏 / 下载 / 排序）统一给一句「开发中」，
    // 与底部浮岛的占位反馈同一套语言——空点没反应会被当成坏了。
    val context = LocalContext.current.applicationContext
    val onPlaceholder = remember(context) {
        { Toast.makeText(context, "开发中", Toast.LENGTH_SHORT).show() }
    }

    Box(Modifier.fillMaxSize()) {
        // 糊底（封面柔焦层）2026-10-03 起挪进壳子：垫在列表之下，头部 item 是透明的，
        // 封面色从头部区域透出；头部滚走糊底淡出，露出的就是纸面。原「糊底铺到屏幕顶 +
        // 补回状态栏 offset」删除 —— 照抄搜索页后，顶部是标题条所在的容器色条，
        // 「官方糊底一直铺到屏幕顶」的语义由这条容器色承担。
        //
        // 头部滚出进度 0..1：头部 item 一离开视口就是 1（那一帧糊底正好淡完）。读在 layout/draw
        // 阶段，滚动只重画不重组；`visibleItemsInfo` 还空的那一帧给 0，免得首帧先闪一下纯色。
        val washExit = remember {
            derivedStateOf {
                val visible = listState.layoutInfo.visibleItemsInfo
                val header = visible.firstOrNull { it.index == HeaderItemIndex }
                when {
                    visible.isEmpty() -> 0f
                    header == null -> 1f
                    else -> {
                        val h = header.size.toFloat().coerceAtLeast(1f)
                        (-header.offset).toFloat().coerceIn(0f, h) / h
                    }
                }
            }
        }
        // nav 路径由本屏让开状态栏；壳路径的壳已经替内容让开了（shellTopInset / ShellPanel 的
        // statusBarsPadding），故两条路径的头部封面落在**同一屏上位置**（见 [TrackListMetrics.HeroCoverTop]）。
        //
        // 2026-10-03 照抄搜索页外壳（SearchScreen:89-148）：顶层铺 `surfaceContainer`
        // 放标题条，内容是一张 `surface` 圆角纸 —— 与搜索页同一种关系（那边容器色条
        // 放「搜索」大标题，这边放歌单标题条）。糊底/列表/骨架全部装进壳子，由壳子
        // 统一裁角；「播放全部」吸顶行不再自己切圆角（角外露出的是容器色条）。
        //
        // 壳路径还要**往上补一条**：壳替本屏让开了状态栏，那一条本屏够不到，而壳在自己那一段
        // 画的是它的 `containerColor`（`surface`，近黑）—— 不补就是「屏顶黑 + 标题条灰」两段色，
        // 中间一道横贯全屏的硬分界。搜索页没有壳、不躲状态栏，所以那边是连续一条容器色。
        val statusBarTop = rememberStatusBarTop()
        val statusBarTopPx = with(LocalDensity.current) { statusBarTop.toPx() }
        val containerColor = MaterialTheme.colorScheme.surfaceContainer
        Column(
            Modifier
                .fillMaxSize()
                .background(containerColor)
                .then(
                    if (showBackButton) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier.shellStatusBarBand(containerColor, statusBarTopPx)
                    },
                ),
        ) {
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
            // 骨架与真列表 crossfade 期间**两者同时组合**：共享元素 key 只能有一个宿主，否则
            // 框架拿到两个同 key 元素、封面在交接点跳一下。骨架在前（盖在真列表之上），故共享
            // 元素先挂骨架；骨架彻底退场后再交给真头部封面——两者几何相同（同位同尺寸），
            // 接管时位置不变，无感。用派生布尔只在翻转时重组，不逐帧重排。
            val skeletonGone by remember { derivedStateOf { skeletonAlpha.value <= 0.001f } }
            // 内容「浮现度」：与骨架淡出严格互补（不重组，供 draw 阶段读）。
            // 骨架下的头部直接满不透明出现会像"闪现"，用它做出场淡入。
            val contentReveal = remember(skeletonAlpha) { derivedStateOf { 1f - skeletonAlpha.value } }

            val density = LocalDensity.current
            // 吸顶停靠线 = 标题条实测下沿。2026-10-03 照抄搜索页后标题条高度跟着文字走
            // （headlineSmall + 行高裁剪），随用户字体缩放变化 —— 不再是常量，运行时
            // onSizeChanged 测出；列表内缩 / 头部内缩共用这一个值。首帧给 0，布局阶段
            // 即上报修正（跳变发生在壳动画/骨架期，不可见）。
            var stickyTopPx by remember { mutableIntStateOf(0) }
            val stickyTopDp = with(density) { stickyTopPx.toDp() }
            // 纸的顶角半径（原来给独立纸层，现在给壳子）。半径同源：SheetCorner = HomeSheetRadius，
            // 与探索 / 搜索页三张纸一致。
            val paperShape = remember {
                RoundedCornerShape(
                    topStart = TrackListMetrics.SheetCorner,
                    topEnd = TrackListMetrics.SheetCorner,
                )
            }
            // 「播放全部」这一行的外形：上边凸圆角（与纸同半径，停靠时与壳子的裁角重合）+
            // 下边反圆角（向外张开的凹角）。详见 [PlayAllRowShape]。
            val playAllShape = remember { PlayAllRowShape(TrackListMetrics.SheetCorner) }
            // 标题条区：搜索页同款（headlineSmall 粗体、高度跟文字走），导航键浮在同区
            // （键盒右沿让出 TitleBarTextStart，见 TrackListMetrics）。
            Box {
                TrackListTitleBar(
                    label = src.label,
                    title = title,
                    reveal = washExit,
                    modifier = Modifier.onSizeChanged { stickyTopPx = it.height },
                )
                // 返回键压在最上层：不被列表滚走、不参与浮现淡入（骨架期也能退出）。
                // 键必须常驻标题条区 —— 吸顶行会滚，键不能跟着滚走；壳路径的关闭键
                // 同位（两者语言一致）。
                if (showBackButton) {
                    TrackListBackButton(onBack, Modifier.align(Alignment.TopStart))
                }
            }
            // 壳子：搜索页同款「clip(顶角) + background(surface)」的纸。糊底 / 列表 /
            // 骨架全部装在里面，由壳子统一裁角。
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(paperShape)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // 糊底垫底：头部 item 透明，封面色从头部区域透出；头部滚走淡出成纸面。
                TrackListBackdrop(
                    coverUrl = previewCoverUrl ?: collection?.coverUrl,
                    exit = washExit,
                    modifier = Modifier.fillMaxSize(),
                )
                    when (display) {
                        is TrackCollection -> {
                            val target = display
                            LazyColumn(
                                state = listState,
                                // 顶部让开标题条实测下沿（stickyTopDp，高度跟文字走），做成
                                // 吸顶停靠线 = 壳子顶 = 标题条下沿，**零内缩**：LazyColumn 本就
                                // 装在壳子里（壳子顶已在标题条之下），「播放全部」吸顶后紧贴标题条、
                                // 上角与壳子的裁角重合，两角外露容器色条 —— 完全契合。
                                // 不走 contentPadding 的原因不变：它跟着内容滚走，撑不出稳定停靠线。
                                //
                                // 2026-10-03 勘误：这里曾误保留旧坐标系的双重内缩（padding
                                // top = stickyTopDp）—— 旧结构里 LazyColumn 占整屏、内缩让出
                                // 标题条；新结构壳子顶已让过，再内缩 = 标题条高被算两次，吸顶
                                // 行与标题条间露出一条空带、头部封面整体偏低一个标题条高。
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomPadding),
                            ) {
                                item(key = "header") {
                                    Box(Modifier.padding(top = TrackListMetrics.HeroCoverTop - stickyTopDp)) {
                                        TrackListHeader(
                                            target,
                                            onCoverRect,
                                            onCoverDrawn,
                                            watermarkIcon,
                                            textAlpha = contentReveal,
                                            onPlaceholder = onPlaceholder,
                                            // 骨架还在时共享元素挂骨架（见 skeletonGone），避免同 key 双宿主。
                                            coverSharedModifier =
                                                if (skeletonGone) coverSharedModifier else Modifier,
                                        ) { actionsTop = it }
                                    }
                                }
                                // 面板：从「播放全部」行起，往下都是不透明面（糊底只在头部透出来）。
                                // 行与行之间不留缝，所以不能用列表的 spacedBy——面板要连成一块。
                                //
                                // 这一行是**吸顶**的（抄官方歌单页）：滚到停靠线（标题条下沿）就停住，
                                // 曲目行从它下面过；返回键 / 壳关闭键在标题条里，一直看得见也点得到
                                // （停靠线已经让开了它们那一档，不必再互相让位）。
                                // **这一行自带外形**（[PlayAllRowShape]）：上边两角普通凸圆角 ——
                                // 停靠时与壳子的 `clip(paperShape)` 严格重合（同半径、同位置），
                                // 没停靠时它也照样是「纸的顶盖」；下边两角**反圆角**，面往两端
                                // 各淌下一只向内弯的脚。
                                // 壳子只能裁到纸顶，裁不出这一行下边的凹角 —— 所以外形必须挂在本行
                                // 自己身上，不能像早先那版整个交给壳子。
                                //
                                // 行盒比内容高一个 [TrackListMetrics.PlayAllFeet]：反圆角的脚有几深，
                                // 画布就得留多高，否则脚画在盒外被直接裁掉、下边又变成直角。
                                stickyHeader(key = "playall") { _ ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer { alpha = contentReveal.value }
                                            // 必须是**不透明面**：它是吸顶的、画在曲目行之上，
                                            // 没有底就盖不住从下面滚过的行，也挡不住身下那块糊底
                                            // （糊底一直铺到面板这一带，见 [BackdropHeight]）。
                                            // 面来自封面（[rememberCoverFace]）而不是主题背景：
                                            // 与下面曲目行的 `surface` 拉开的那点差，正是脚内侧那道
                                            // 反圆角的轮廓。
                                            // 顺序：`graphicsLayer`（alpha）在最外，否则 alpha 罩不住
                                            // 下面这层 clip + background。
                                            .clip(playAllShape)
                                            .background(brush = faceBrush),
                                    ) {
                                        PlayAllRow(
                                            target,
                                            onPlayAll = { vm.onPlayAll(target) },
                                            onPlaceholder = onPlaceholder,
                                        )
                                        // 脚：纯色、无内容。两只脚之间这一段是**空的**，
                                        // 反圆角外露出的就是滚过这一行的内容。
                                        Spacer(Modifier.height(TrackListMetrics.PlayAllFeet))
                                    }
                                }
                                itemsIndexed(
                                    target.tracks,
                                    key = { _, t -> t.id },
                                    contentType = { _, _ -> "track" },
                                ) { index, track ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            // 行随内容浮现 / 收起退场淡入淡出（draw 阶段读，不重组）。
                                            .graphicsLayer { alpha = contentReveal.value }
                                            // 面板必须是**不透明面**：糊底整块垫在壳子里，比条目卡
                                            // 大一圈，行自己不铺底的话卡片四周那 8dp 缝里会透出封面色
                                            // ——真机上就是「卡片之间的缝是绿的」，且随糊底退场进度
                                            // 一路变色。（壳的 `surface` 底在糊底**之下**，盖不住它。）
                                            .background(MaterialTheme.colorScheme.surface),
                                    ) {
                                        TrackListRow(track, onClick = { vm.onTrackClick(target, index) })
                                    }
                                }
                                item(key = "sheetTail") {
                                    // 只占底部留白；同样必须自带不透明底，理由见上一行。
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(bottomPadding)
                                            .background(MaterialTheme.colorScheme.surface),
                                    )
                                }
                            }
                        }
                        TrackListUiState.Empty -> NumeEmptyState(
                            "暂无曲目",
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        )
                        // 错误必须给出路：文案本身可点重试（与播客/评论/歌手页同交互语言）。
                        TrackListUiState.Error -> NumeErrorState(
                            text = "曲目加载失败，点此重试",
                            onRetry = vm::retry,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        )
                    }
                    // 用派生布尔（!skeletonGone）而非直接读 skeletonAlpha.value：后者每帧变化都会让
                    // 整个 TrackListScreen 重组（含头部/列表），前者只在跨阈值时翻一次。
                    if (!skeletonGone) {
                        TrackListSkeleton(
                            coverUrl = previewCoverUrl,
                            title = title,
                            onCoverRect = onCoverRect,
                            onCoverReady = onCoverDrawn,
                            coverSharedModifier = coverSharedModifier,
                            // 骨架装在壳子里（与糊底/列表同层），顶空档要减去标题条那一档，
                            // 才与真列表首屏逐像素对齐（见 [TrackListSkeleton] 的 stickyTop）。
                            stickyTop = stickyTopDp,
                            // 叠在目标态之上淡出（draw 阶段读，不重组）。
                            modifier = Modifier.graphicsLayer { alpha = skeletonAlpha.value },
                        )
                    }
            }
        }
    }
}

/* ── 页底 ──────────────────────────────────────────────────────── */

/**
 * 糊底铺多高（自壳子顶算起）。梯度在这一段里从「封面透出一点」化到全不透明，再往下才是
 * 干净的纸面 —— 官方同款：面板以下是不透明面。
 *
 * ⚠️ 「面板以下是不透明面」是**面板自己保证**的，糊底并不会自动停在面板上沿：糊底整块垫在
 * 壳子里、铺满本值（360dp，比头部高得多，面板的头几行也落在它范围内）。所以吸顶行 / 曲目行 /
 * 尾部 / 骨架面板都必须各自铺不透明底，否则条目卡四周那几 dp 的缝里会透出封面色 ——
 * 真机上就是「卡片之间的缝是绿的」，且随糊底退场进度一路变色。
 */
private val BackdropHeight = 360.dp

/** 糊底放大倍数：把 `blur` 的软边推到可视带之外，带口就不会看到一圈「糊边」。 */
private const val BackdropScale = 1.15f

/**
 * 糊底退场时额外上移的量：头部滚出去的过程里，糊底同步往上让开这么多（同时淡出）。
 * 取值只需与头部高度同量级（头部实测约 174dp），读起来才像「色块被头部带走」。
 */
private val BackdropExitShift = 120.dp

/** 头部 item 在列表里的下标：吸顶停靠线与糊底退场都按它判断。 */
private const val HeaderItemIndex = 0

/**
 * 放大后上下各溢出的量（= 带高 × (倍数-1)/2）。放大若由布局表达（见 [TrackListBackdrop]），
 * 这截**必须**由蒙版一起盖住 —— 它是没过蒙版的原图色，露在可视区里就是一条纯色带。
 */
private val BackdropBleed = BackdropHeight * ((BackdropScale - 1f) / 2f)

/**
 * 状态栏高度。
 *
 * 读的是**平台**的根 insets（[ViewCompat.getRootWindowInsets]），不是 Compose 的
 * `WindowInsets.statusBars`：壳路径里内容已被壳用 `statusBarsPadding()` 整体让开过，
 * Compose 的 insets 在内容这一层读出来是 0（那份已被消费），拿不到真实高度。
 */
@Composable
private fun rememberStatusBarTop(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val px = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        with(density) { px.toDp() }
    }
}

/**
 * 壳路径：把本屏的容器色条**往上补到屏幕顶**（补 [heightPx] 那一条，色 = [color]）。
 *
 * 壳（`ShellPanel` / `ExpandableShell`）会用 `statusBarsPadding()` / `shellTopInset` 把内容整体
 * 推到状态栏之下，于是本屏的顶边就在状态栏下沿 —— 而屏顶那一条由壳画成它的 `containerColor`
 * （`surface`，近黑），与本屏在标题条铺的 `surfaceContainer` 不同色：不补就是「黑 + 灰」两段、
 * 中间一道横贯全屏的硬分界。搜索页没有壳、自己不躲状态栏，所以那边天然是一条连续容器色。
 *
 * 做法是**画到自己边界之外**（`topLeft.y` 取负）：壳给的是 padding、不是 `clip`，所以这截画得
 * 出来 —— 与本屏早先糊底用 `offset(y = -statusBarTop)` 补状态栏是同一套办法。代价是上游一旦
 * 改成 `clip`，这截会被裁掉（届时改为把色条交给壳来画）。
 */
private fun Modifier.shellStatusBarBand(color: Color, heightPx: Float): Modifier = drawBehind {
    if (heightPx <= 0f) return@drawBehind
    drawRect(
        color = color,
        topLeft = Offset(0f, -heightPx),
        size = Size(size.width, heightPx),
    )
}

/**
 * 页底：封面柔焦放大 + 主题色蒙版。
 *
 * 复用人物 hero 那套（放大 + `blur` + 同色渐隐）——同一份「不受控位图当背景」的解法不必写两遍。
 * 低清解码（[BackdropRequestSize]）再放大本身就糊，`blur` 在 API<31 是 no-op 也退化成低清放大，
 * 两档都不崩。
 *
 * **放大走布局**（格子比可视带大一个 [BackdropScale]、上下各顶出去 [BackdropBleed]），不走
 * `graphicsLayer` 的 `scale`：图层放大不改变布局边界，多出来的那截等于「画到自己格子之外」，
 * 而蒙版只按格子铺 —— 溢出的上沿一旦落在可视区里（壳路径把内容整体让开状态栏，正是如此）
 * 就是一条没过蒙版的亮色。改成布局边界后位图与蒙版范围严格同一，谁也不越界。
 *
 * 蒙版用 `surface` 而非固定黑：明暗两套各自把封面压到「透出一点封面色」的程度，
 * 头部文字因此始终用主题墨色（onSurface / onSurfaceVariant），不必为浅色主题另备一套白字。
 */
@Composable
private fun TrackListBackdrop(
    coverUrl: String?,
    exit: State<Float>,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    // 退场位移：糊底不只淡出，还往上让开一段 —— 读起来是「封面色跟着头部走掉」，
    // 而不是眼看着内容从一块不动的色块上滑过去。
    val exitShiftPx = with(LocalDensity.current) { BackdropExitShift.toPx() }
    // 位图与蒙版共用同一套「格子」：都要盖住放大后的整块范围。
    val band = Modifier
        .fillMaxWidth()
        .height(BackdropHeight + BackdropBleed * 2)
        .offset(y = -BackdropBleed)
        // 两个 offset 叠加：固定让开（上一条）+ 滚动退场（这条）。退场量读在 layout 阶段。
        .offset { IntOffset(0, -(exit.value * exitShiftPx).roundToInt()) }
    Box(modifier.fillMaxSize().background(surface)) {
        // 糊底自己的退场底色：透明度跟着糊底退场进度走（`exit`），到 1 时整块化进
        // `surfaceContainer`（= 顶部那条容器色，收起后不露白）。
        //
        // 2026-10-03 勘误：这条原先写着「= 圆角纸两角外露出、吸顶后 = 状态栏那一带」——
        // 那是糊底还铺在纸**外面**那套的说法。糊底挪进壳子（纸的里面）之后，那两个位置都
        // 不在这里了：纸角由壳子的 `clip(paperShape)` 裁掉，状态栏那一条由本屏自己往上补
        // （见 [shellStatusBarBand]）。
        //
        // 顺序要紧：`graphicsLayer`（alpha）必须在 `background` **之前** —— 它是外层图层，
        // 写到后面背景就画在图层之外、alpha 完全不生效（下面那条蒙版原先正是这么写的，
        // 于是它一直没淡出过：底色同为 `surface` 才没露馅）。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = exit.value }
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        if (coverUrl != null) {
            val context = LocalContext.current
            val model = remember(coverUrl) {
                ImageRequest.Builder(context).data(coverUrl).size(BackdropRequestSize).build()
            }
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = band.blur(28.dp).graphicsLayer { alpha = 1f - exit.value },
            )
        }
        Box(
            band
                // alpha 图层必须在 `background` 之前（见上一条注释）：否则这条蒙版不淡出。
                .graphicsLayer { alpha = 1f - exit.value }
                .background(
                    Brush.verticalGradient(
                        // 顶部压得比中段重：状态栏图标与返回键都落在这一段，且封面可能是纯亮图
                        // （白封面时 0.78 会让白图标糊在浅灰上）；中段留住封面色相即可。
                        0f to surface.copy(alpha = 0.86f),
                        0.55f to surface.copy(alpha = 0.90f),
                        1f to surface,
                    ),
                ),
        )
    }
}

/** 糊底解码尺寸（px）：只要一团色，不要细节。 */
private const val BackdropRequestSize = 64

/* ── 头部 ──────────────────────────────────────────────────────── */

/**
 * 头部：左方封面 + 右侧标题 / 作者 / 简介，下面三枚等宽操作胶囊。
 *
 * 全部墨色走主题色（onSurface / onSurfaceVariant），因为背后是 `surface` 蒙过的糊底，
 * 明暗两套都压得住——不需要 on-image 那套白字。
 */
@Composable
private fun TrackListHeader(
    collection: TrackCollection,
    onCoverRect: ((Rect) -> Unit)?,
    onCoverReady: (() -> Unit)?,
    watermarkIcon: ImageVector?,
    textAlpha: State<Float>?,
    onPlaceholder: () -> Unit,
    coverSharedModifier: Modifier = Modifier,
    onActionsTop: (Float) -> Unit,
) {
    // 展开动画期间 hero 正顶着封面：本封面与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    Column(
        Modifier
            .fillMaxWidth()
            // 头部整体随内容浮现淡入（draw 阶段读，不重组）；封面在里面所以一并淡入。
            .graphicsLayer { alpha = textAlpha?.value ?: 1f }
            .padding(horizontal = TrackListMetrics.SideInset),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(TrackListMetrics.CoverSize)
                    .then(
                        // 仅需要测量终态矩形时才挂 onGloballyPositioned：否则是每次布局一回调。
                        if (onCoverRect != null) {
                            Modifier.onGloballyPositioned {
                                onCoverRect.invoke(Rect(it.localToWindow(Offset.Zero), it.size.toSize()))
                            }
                        } else {
                            Modifier
                        },
                    )
                    // 官方共享元素：与入口卡片封面同 key，框架 morph 位置/尺寸（不重排内容）。
                    .then(coverSharedModifier)
                    .clip(NumeShape.CardSmall)
                    // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                    .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f },
            ) {
                BigCoverVisual(
                    coverUrl = collection.coverUrl,
                    name = collection.name,
                    modifier = Modifier.fillMaxSize(),
                    // 名字/元信息已由右侧文本承担，封面只当图看：关掉图上文字与压暗渐变
                    // （小图上 0.66 黑渐变占过半，真机看就是「深色阴影」）。
                    showName = false,
                    scrimAlpha = 0f,
                    requestSize = BannerCoverSize,
                    onLoadSuccess = onCoverReady,
                    watermarkIcon = watermarkIcon,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 作者头像：接口只给了昵称（creator 对象里本有 avatarUrl，但没进
                    // TrackCollection，缓存层也没存），先用占位圆——比留一块空白更像「头像位」。
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = authorLine(collection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = collection.description.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 单行：官方就是「暂无简介 ›」一行，长简介点开才展开。这里没有展开页，
                    // 但仍取单行——头部高度才与封面齐平（两行会高出封面 28dp，封面显小）。
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // 面板上沿的投影：官方在面板顶上方压了一条更暗的带（实拍差值 ~11/255），
        // 有它才读得出「面板浮在封面糊底上」；顺带把三胶囊与面板分开。
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onActionsTop(it.positionInWindow().y) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackListAction(
                icon = Icons.Outlined.Share,
                label = "分享",
                modifier = Modifier.weight(1f),
                onClick = onPlaceholder,
            )
            TrackListAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = "评论",
                modifier = Modifier.weight(1f),
                onClick = onPlaceholder,
            )
            TrackListAction(
                icon = Icons.Outlined.FavoriteBorder,
                label = collection.subscribedCount.takeIf { it > 0 }?.let(::formatCount) ?: "收藏",
                modifier = Modifier.weight(1f),
                onClick = onPlaceholder,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to NumeInk.Scrim.copy(alpha = NumeFade.SHEET_SHADOW),
                    ),
                ),
        )
    }
}

/** 作者行文案：昵称 + 更新频率（榜单才有），接口缺字段时退化成一句不空的说明。 */
private fun authorLine(c: TrackCollection): String {
    val parts = mutableListOf<String>()
    if (c.creator.isNotBlank()) parts += c.creator
    if (c.updateFrequency.isNotBlank()) parts += c.updateFrequency
    return parts.joinToString(" · ").ifBlank { "网易云音乐" }
}

/** 一枚等宽操作胶囊：图标 + 文案/计数，透明白底（压在糊底上明暗两套都看得见）。 */
@Composable
private fun TrackListAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(NumeShape.Pill)
            // onSurface 低透明度当底：浅色主题下是压暗的灰片、深色主题下是提亮的白片，
            // 一两行代码同时满足两套，不用为「透明白」再开一个固定色。
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 左上角返回键：浮在糊底 / 标题条上、不随列表滚（壳路径的关闭键同位，两者语言一致）。 */
@Composable
private fun TrackListBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier.padding(4.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* ── 顶部标题条 ────────────────────────────────────────────────── */

/**
 * 顶部标题条：**铺在容器色上的一条透明带**，不画底、不切圆角 —— 与搜索页「搜索」大标题
 * 同一种做法（那边也是标题直接铺 `surfaceContainer`，圆角只由下面那张纸给）。
 *
 * 圆角纸的顶盖在**面板首行**（「播放全部」）那一档，不在这一条。早先这条也切了同半径的圆角、
 * 还自带 `surface` 底，于是屏上同时立着两个圆角顶（标题条一个、纸一个），中间夹出一道束腰，
 * 且这条的 `surface` 与圆角外露出的 `surfaceContainer` 还对不上色。现在底交给
 * [TrackListBackdrop] 的容器色条、圆角交给纸，站内三张纸的「容器色条 + 圆角纸」才真正一致。
 *
 * [label]（类型：歌单 / 榜单 / 专辑 / 喜欢 / 已购）与 [title]（实际标题）**叠在同一格**交叉淡变：
 * 换字进度由 [reveal]（糊底退场进度，头部一滚走就是 1）重映射，两个 alpha 都读在 draw 阶段，
 * 滚动只重画不重组。长标题单行截断（`maxLines = 1` + Ellipsis）；文字左起让开浮层控件那一档。
 */
@Composable
private fun TrackListTitleBar(
    label: String,
    title: String,
    reveal: State<Float>,
    modifier: Modifier = Modifier,
) {
    // 头部退到 35% 才开始换字、到 70% 换完：太早「歌单」一闪而过，太晚标题迟迟不出现。
    val morph = remember(reveal) {
        derivedStateOf { ((reveal.value - 0.35f) / 0.35f).coerceIn(0f, 1f) }
    }
    // 2026-10-03 照抄 SearchTitleBar 的写法：headlineSmall + 行高居中裁剪
    // （LineHeightStyle Center / Trim.Both）+ 粗体，高度跟着文字走 —— 不再固定 56dp。
    // 两条 Text 的 style 同源，交叉淡变时行盒不变、不跳。
    val barStyle = MaterialTheme.typography.headlineSmall.copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = TrackListMetrics.TitleBarTextStart,
                end = TrackListMetrics.SideInset,
            ),
    ) {
        Text(
            text = label,
            style = barStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = 1f - morph.value },
        )
        Text(
            text = title,
            style = barStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = morph.value },
        )
    }
}

/* ── 面板首行：播放全部 ────────────────────────────────────────── */

/**
 * 「播放全部」吸顶行的外形：**上边两角是普通（凸）圆角，下边两角是反圆角（凹角）**。
 *
 * 这是本页的一个小巧思，不是从官方歌单页搬来的：上边凸角让这一行像从容器色里长出来的
 * 一张纸的顶盖（与搜索页搜索条同一种关系，也与壳子的 `clip(paperShape)` 严丝合缝）；
 * 下边两角则**反着圆、向外张开** —— 这一行的「面」在两端往下淌出一个 [radius] 深的小脚，
 * 两脚之间的凹弧正好把底下那条条目卡的顶角让出来。于是吸顶时这一行不是拿一条直边把
 * 列表拦腰切断，而是把底下的卡片「拢」在角里；角外的凹弧里透出的是下面滚过的内容。
 *
 * 半径与纸同源（[TrackListMetrics.SheetCorner]）：上下同半径才对称，且上边角在停靠线上
 * 与壳子的圆角完全重合、不会出现「两个圆角顶夹一道束腰」。
 *
 * ## 反圆角为什么要「长出行盒」（2026-10-03 两次做错的地方）
 *
 * 凹角 = **补料**，不是**去料**。拿角点当圆心、半径 [radius] 画一段四分之一圆去「咬」角，
 * 弧是朝角鼓的 —— 那是**普通凸圆角**，四个角全这么画出来就是一张 `RoundedCornerShape`
 * （中途做错过一版，真机上四个角一模一样，等于没做）。要让角**凹**，圆心必须落在**弧的外侧**：
 * 边界先直着走到底边之外 `radius` 处，再反向扫弧收回到主体底边。于是两角各伸出一只
 * **向内弯的脚**（脚的外沿就是行盒的左右边），脚内侧那道弧的凹面朝下 —— 与上边两角一致。
 *
 * 代价是形状**比内容高一个 [TrackListMetrics.PlayAllFeet]**，画布必须照此留高，否则脚被裁掉
 * 又变回直角。调用点的 `Spacer` 就是干这个的。
 */
private class PlayAllRowShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        // 大半径在小盒上会自交，先夹一下（行盒 96dp，正常取不到这个上限）。
        val r = with(density) { radius.toPx() }
            .coerceAtMost(size.width / 2f)
            .coerceAtMost(size.height / 2f)
        val w = size.width
        // 主体的底边：两角的反圆角从这里往下长，一直长到行盒底。
        val h = size.height - r
        val path = Path().apply {
            // 上左角：普通凸圆角（圆心 (r,r) 在行内），180° 顺时针扫到 270°。
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            // 上边直线 → 上右角。
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            // 右边直下，越过主体底边、到脚尖（= 行盒底）。
            lineTo(w, h + r)
            // 下右角：**反圆角** —— 圆心 (w-r, h+r) 落在弧的**下方**，弧朝角 (w,h) 鼓出、
            // 把角补满；边界于是从脚尖沿弧收回到主体底边。0° 逆时针扫到 270°。
            arcTo(Rect(w - 2 * r, h, w, h + 2 * r), 0f, -90f, false)
            // 主体底边直线 → 下左角，同样反向扫（270° → 180°）。
            lineTo(r, h)
            arcTo(Rect(0f, h, 2 * r, h + 2 * r), 270f, -90f, false)
            // close() 从 (0, h+r) 沿左边收回 (0, r)。
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 「播放全部」行：红圆钮 + 标题 + 曲目数/播放量 + 右侧三枚图标。
 *
 * 副标题里「N首」用常规墨色、播放量用 tertiary（暖金）——官方那句「含20首VIP歌曲」是金色，
 * 本地没有 VIP 信息，就把金色留给「数据」这一档，位置与视觉权重与官方一致。
 */
@Composable
private fun PlayAllRow(
    collection: TrackCollection,
    onPlayAll: () -> Unit,
    onPlaceholder: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayAll)
            .padding(start = TrackListMetrics.SideInset, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(TrackListMetrics.DiscSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "播放全部",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "播放全部",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            val count =
                if (collection.trackCount > 0) collection.trackCount
                else collection.tracks.size.toLong()
            // 曲目数与播放量是**一段**文本（两种颜色）而不是并排两个 Text：中文可在任意字间断行，
            // 两个 Text 各自换行时后一个会折成两行，而 Row 是居中对齐 —— 真机上就成了
            // 「· 32.2亿次 / 播放」错位两行。单段 + softWrap=false：放不下就截尾，绝不折行。
            Text(
                text = buildAnnotatedString {
                    append("$count 首")
                    if (collection.playCount > 0) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                            append("  ·  ${formatCount(collection.playCount)}次播放")
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PanelIcon(Icons.Outlined.FavoriteBorder, "收藏", onPlaceholder)
        PanelIcon(Icons.Outlined.Download, "下载", onPlaceholder)
        PanelIcon(Icons.Outlined.Sort, "排序", onPlaceholder)
    }
}

/**
 * 面板行尾的图标钮：40dp 触控盒，不是 [IconButton] —— M3 的 IconButton 有 48dp 最小尺寸，
 * 三枚就把中间那段挤窄到副标题放不下（官方实测图标间距约 43dp，本行照此收紧）。
 */
@Composable
private fun PanelIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/* ── 曲目行 ────────────────────────────────────────────────────── */

/**
 * 曲目行：封面 + 标题 + 「歌手 - 专辑」+ 右侧 ⋮。
 *
 * 行仍是 nume 的**条目卡**（[numeEntrySurface]：8dp 外缩 + 圆角 + `surfaceContainer`），
 * 浮在面板底上；卡内再内缩到 [TrackListMetrics.RowInset]。与官方一致的是**封面到屏边**的
 * 距离（8 + 8 = 16dp），卡边是 nume 自己的条目语言。行高由 44dp 封面 + 上下 8dp 内缩撑起。
 */
@Composable
private fun TrackListRow(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .numeEntrySurface()
            .clickable(onClick = onClick)
            .padding(
                start = TrackListMetrics.RowInset - NumeContainer.Inset,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumeArtwork(
            url = track.artworkUrl,
            contentDescription = track.name,
            size = TrackListMetrics.RowCover,
            shape = NumeShape.Chip,
            requestSize = NumeArt.RequestRow,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                track.albumName.takeIf { it.isNotBlank() },
            ).joinToString(" - ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // ⋮ 用 44dp 触控盒而不是 [IconButton]：M3 的 IconButton 有 48dp 最小高度，
        // 大于 44dp 封面，会把整行撑到 66dp —— 行距就和官方（62dp）对不上了。
        Box(
            Modifier
                .size(TrackListMetrics.RowCover)
                .clip(CircleShape)
                .clickable { /* 三点菜单：暂无功能 */ },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* ── 骨架 ──────────────────────────────────────────────────────── */

/**
 * 列表骨架：与真头部**同构同位**——98dp 方封面（同位、挂同一共享元素）+ 右侧两行标题 +
 * 三枚胶囊 + 播放全部行 + 曲目行。位置一致是硬要求：骨架→真列表是直接切，错位就是「跳一下」。
 *
 * [coverUrl] 非空时封面位置直接渲染**真实高清封面**（不再等整张列表），其余仍 shimmer——
 * 这样封面与整表下载解耦，hero 能尽早交接。封面块**不能**包在 shimmer 容器里，
 * 否则扫光会扫到真封面；故此时 shimmer 只挂在下方按钮/行。
 */
@Composable
private fun TrackListSkeleton(
    coverUrl: String? = null,
    title: String = "",
    onCoverRect: ((Rect) -> Unit)? = null,
    onCoverReady: (() -> Unit)? = null,
    coverSharedModifier: Modifier = Modifier,
    /**
     * 顶部标题条的实测高度（[TrackListMetrics] 的 stickyTop）。
     *
     * 骨架整体装在**壳子**里，而壳子顶就在标题条下沿 —— 真列表的头部 item 因此把顶空档
     * 记成 `HeroCoverTop - stickyTop`（见 LazyColumn 的 `item(key = "header")`）。骨架若还按
     * `HeroCoverTop` 铺，就整整低一个标题条（≈32dp），骨架→真列表的交接会「跳一下」。
     */
    stickyTop: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // 展开动画期间 hero 正顶着封面：骨架封面与 hero 互补，避免两层重影（见 LocalShellHeroAlpha）。
    val heroAlpha = LocalShellHeroAlpha.current
    Column(
        modifier
            .fillMaxSize()
            .then(if (coverUrl == null) Modifier.shimmer() else Modifier)
            // 与真列表同一个顶部空档（见 LazyColumn 的头部 item）：差一点就是「跳一下」。
            .padding(top = TrackListMetrics.HeroCoverTop - stickyTop),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TrackListMetrics.SideInset),
            verticalAlignment = Alignment.Top,
        ) {
            val coverModifier = Modifier
                .size(TrackListMetrics.CoverSize)
                .then(
                    if (onCoverRect != null) {
                        Modifier.onGloballyPositioned {
                            onCoverRect.invoke(Rect(it.localToWindow(Offset.Zero), it.size.toSize()))
                        }
                    } else {
                        Modifier
                    },
                )
                // 官方共享元素：与入口卡片封面同 key（骨架阶段先挂，面板一出现即可 morph）。
                .then(coverSharedModifier)
                // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐。
                .graphicsLayer { alpha = if (heroAlpha.value >= 1f) 0f else 1f }
            if (coverUrl != null) {
                Box(coverModifier.clip(NumeShape.CardSmall)) {
                    BigCoverVisual(
                        coverUrl = coverUrl,
                        name = title,
                        modifier = Modifier.fillMaxSize(),
                        showName = false,
                        scrimAlpha = 0f,
                        requestSize = BannerCoverSize,
                        onLoadSuccess = onCoverReady,
                    )
                }
            } else {
                SkeletonBox(coverModifier, NumeShape.CardSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
            ) {
                SkeletonLine(widthFraction = 0.9f, height = 20.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonLine(widthFraction = 0.55f, height = 14.dp)
                Spacer(Modifier.height(14.dp))
                SkeletonLine(widthFraction = 0.75f, height = 12.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TrackListMetrics.SideInset)
                .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonBox(Modifier.weight(1f).height(40.dp), NumeShape.Pill)
            }
        }
        Spacer(Modifier.height(24.dp))
        // 面板整块：**不自带圆角** —— 顶角由壳子统一给（与真列表的吸顶「播放全部」行同一种
        // 关系）。自带一份会在页面中间多立一个圆角顶，角外还露出被裁过的糊底。
        // 但**必须自带不透明底**：糊底一直铺到这一带，不铺底的话骨架行之间会透出封面色。
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .then(if (coverUrl != null) Modifier.shimmer() else Modifier),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = TrackListMetrics.SideInset, end = 20.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(TrackListMetrics.DiscSize), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(widthFraction = 0.35f, height = 16.dp)
                    SkeletonLine(widthFraction = 0.5f, height = 12.dp)
                }
            }
            repeat(6) {
                // 与真行同一套容器与内缩（条目卡 + 16dp 封面内缩）：骨架↔列表是直接切，
                // 差一点就是「跳一下」。
                Box(Modifier.fillMaxWidth().numeEntrySurface()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = TrackListMetrics.RowInset - NumeContainer.Inset,
                                end = 4.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBox(Modifier.size(TrackListMetrics.RowCover), NumeShape.Chip)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonLine(widthFraction = 0.6f, height = 14.dp)
                            SkeletonLine(widthFraction = 0.35f, height = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

/** 数字缩写：亿 / 万 / 原样。 */
private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e8)) + "亿"
    n >= 10_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e4)) + "万"
    else -> "$n"
}

private fun trimZero(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s
