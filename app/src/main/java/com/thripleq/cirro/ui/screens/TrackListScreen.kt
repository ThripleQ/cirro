package com.thripleq.cirro.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.core.repo.CommentThread
import com.thripleq.cirro.core.model.TrackCollection
import com.thripleq.cirro.ui.components.CloseButtonRaise
import com.thripleq.cirro.ui.components.LocalCommentsOpener
import com.thripleq.cirro.ui.components.LocalShellSettled
import com.thripleq.cirro.ui.components.CirroCloseButton
import com.thripleq.cirro.ui.components.CirroEmptyState
import com.thripleq.cirro.ui.components.CirroErrorState
import com.thripleq.cirro.ui.components.rememberPayTags
import com.thripleq.cirro.ui.components.rememberCoverAccent
import com.thripleq.cirro.ui.menu.rememberTrackMenuOpener
import com.thripleq.cirro.ui.menu.shareTo
import com.thripleq.cirro.ui.profile.TrackListSource
import com.thripleq.cirro.ui.profile.TrackListUiState
import com.thripleq.cirro.ui.profile.TrackListViewModel
import com.thripleq.cirro.ui.profile.TrackSort
import com.thripleq.cirro.ui.theme.CirroShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 统一"壳子 + 列表"详情页：榜单 / 歌单 / 专辑 / 喜欢 / 已购都是同一个结构。
 *
 * ## 版式（抄自官方歌单页）
 * 1. 页底 = 封面糊底 + 主题色蒙版（固定不滚）：头部那块透出封面色，面板往下是不透明面。
 * 2. 头部 = 左方封面 + 右侧标题 / 作者 / 简介，下面三枚等宽操作胶囊。
 * 3. 圆角面板从「播放全部」行开始，向下是曲目行（条目卡：封面 + 标题 + 歌手 - 专辑 + ⋮）。
 *
 * 旧的「满宽方封面 + 名字压在封面上」那套（banner 头）已撤：封面不再承担标题，
 * 标题由封面右侧的文本承担，于是封面可以缩到 98dp —— 与官方一致的信息密度。
 *
 * ## 动作分两层，各按能力出现（2026-10-10 起）
 *
 * 六类列表**外观**共用，能做的**动作**不共用。判据只有一条：这个列表在服务端有没有
 * 真实对象（见 [TrackListCapabilities]）。
 *
 * - **列表级**（头部三胶囊 / 面板行尾图标）：歌单、榜单、专辑能评论与收藏；
 *   喜欢、已购、每日推荐是本地拼的壳，这两样不成立 —— 头部换成随机播放与刷新，
 *   行尾不画收藏。**不支持的动作不出现，而不是出现后弹一句「不支持」**。
 * - **单曲级**（每行的 ⋮，见 [TrackMenuSheet]）：下一首播放 / 喜欢 / 评论 / 分享，
 *   **每首歌都有**（红心、`R_SO_4_` 评论线、官方单曲页都与列表来源无关）。本地那三类
 *   列表缺的评论能力正好在这里补上 —— 与官方同构：歌单页的评论线在头部，
 *   任何一首歌长按也都有自己的评论。
 */
@Composable
fun TrackListScreen(
    source: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    /**
     * 是否由本屏画顶部那颗键（**右上角**，[CirroCloseButton]，与壳路径那颗同规格同位置）。
     *
     * 作为 **nav 详情页**时 true（本屏自己画）；作为**面板壳内容**时 false——
     * 壳自带同一颗键（同位、同浮层语言），再画一个就重叠了。
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

    // 列表滚动状态（吸顶、糊底退场进度都读它）。
    val listState = rememberLazyListState()

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
    // previewCoverUrl 为空表示无可等之图；非壳环境（CirroApp 导航）不传该值 → 天然不等待。
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

    // 「播放全部」那一行的**强调色**：从封面派生（见 [rememberCoverAccent]），只用在圆钮身后的
    // 径向晕上 —— 行的背景本身不再铺色（回到 `surface`，与曲目行连成一整片纸）。
    // 封面没到（previewCoverUrl 为空）就先用正片的 URL，两者同源，取出来的色一致。
    val coverAccent = rememberCoverAccent(previewCoverUrl ?: collection?.coverUrl)

    // ── 列表能力：决定头部三枚胶囊与行尾图标各是哪几枚 ────────────────
    //
    // 六类列表共用本屏，但能做的**列表级**动作不同：歌单/榜单/专辑在服务端有真实对象
    // （能评论、能收藏），喜欢/已购/每日推荐是本地拿 uid + 已有曲目拼的壳（两样都不成立）。
    // 判据只在 [TrackListSource.capabilities] 一处（见 [TrackListCapabilities]），
    // 本屏与头部、面板行都读它 —— 别在调用点手写 `if (source == ...)`。
    val capabilities = remember(src) { src.capabilities }

    val appContext = LocalContext.current.applicationContext
    // 分享要拿 **Activity context** 起选择器：applicationContext 起 chooser 得加
    // FLAG_ACTIVITY_NEW_TASK，且部分 ROM 上会丢掉调用方身份、选择器样式异常。
    val shareContext = LocalContext.current
    val onShare: () -> Unit = {
        val c = collection
        if (c != null) shareTo(shareContext, c.name, shareUrlOf(src, c.id))
    }
    // 评论浮层挂在根上（见 [LocalCommentsOpener]）：本屏可能正被展开壳裁着，
    // 就地画会被壳的圆角连内容一起切掉。拿不到宿主时安静地不动。
    val commentsOpener = LocalCommentsOpener.current
    val onComments: (Rect) -> Unit = { rect ->
        val c = collection
        // 本地列表那三类根本不会画这颗胶囊（见 capabilities.comments），所以这里
        // 不再有「这个列表没有评论」的兜底 Toast：到不了的口分支就是死代码。
        // 它们的评论入口在**每一行**的 ⋮ 菜单里（单曲评论线与列表来源无关）。
        val thread = c?.let { commentThreadOf(src, it.id) }
        if (thread != null) commentsOpener?.open(thread, rect)
    }
    // ⋮ 菜单挂在**根**上（见 [rememberTrackMenuOpener] → `TrackMenuHost`）：它要做的事
    // （下一首播放 / 喜欢 / 评论 / 分享）与列表无关，全站共用一份，所以本屏不持有它的
    // 状态，只负责在 ⋮ 被点时把曲目交出去。
    val openTrackMenu = rememberTrackMenuOpener()
    var sortSheetOpen by remember { mutableStateOf(false) }
    val sort by vm.sort.collectAsStateWithLifecycle()
    // 收藏 / 刷新的一次性反馈，由界面弹 Toast（成功也给一句：这两个动作几百毫秒内
    // 就结束、画面几乎没变化，没有反馈就分不清「做了」和「点漏了」）。
    LaunchedEffect(vm) {
        vm.message.collect { Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show() }
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
        // 头部**滚出的像素量**（0..头部高度）：糊底的视差位移按它折算（见 [BackdropParallax]）。
        // 与 [washExit] 同源但不同量纲 —— 那个是 0..1 的进度，视差要用 px 才对得上「背景慢半拍」
        // 的比例。头部整个滚出视口时给 0：那一帧 washExit 已经是 1、糊底 alpha 归零、不可见。
        val headerScrollPx = remember {
            derivedStateOf {
                val header = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == HeaderItemIndex }
                    ?: return@derivedStateOf 0f
                (-header.offset).toFloat().coerceAtLeast(0f)
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
            // 纸的顶角形状：**直接引用站内那一份 token**（[CirroShape.SheetTop]）—— 与探索 /
            // 搜索 / 我的三页那张纸是**同一个对象**，不再本地拼一份同参数的 RoundedCornerShape
            // （那样 token 以后调整半径，详情页会静默留在旧值）。token 是常量，不必 remember。
            val paperShape = CirroShape.SheetTop
            // 「播放全部」这一行的**面**：四角都是普通圆角，半径与纸同源
            // [TrackListMetrics.SheetCorner] —— 吸顶时上边两角正好与壳子裁出来的纸角重合
            // （角外露容器色条），下边两角则切进**面板自己的底色**里。
            //
            // 下边两角为什么能圆（2026-10-03 用户：「切圆角露背景」→「做不到，也太逗了吧」）：
            // 圆角本身从来不是问题，问题是**角外有什么接住**。这一行是吸顶的、底下垫着封面糊底
            // （见 [BackdropHeight]），只裁面不铺底，切口里透出的就是亮紫/亮棕的封面色，
            // 看着像在面板底边抠了两个洞 —— 那是**缺一层底**，不是形状做不了。
            // 现在把它补上（见 stickyHeader 里那层 `surface`），四角圆角自然成立。
            val playAllShape = remember { RoundedCornerShape(TrackListMetrics.SheetCorner) }
            // 面板（「播放全部」行）**上沿在根坐标系里的 y** —— 封面糊底到这条线为止。
            //
            // 为什么要回传坐标（2026-10-03）：糊底整块垫在壳子里、铺 [BackdropHeight]，
            // 一路盖到面板这一带，所以**面板四角切掉的那四块后面一直是封面**，不管怎么改
            // 形状都会透出封面色。用户原话：「没有补色露的是封面背景，把背景也削掉」——
            // 那就把糊底本身裁掉：封面空气只活在头部那一段，越过面板上沿就没了，
            // 面板四角切开后看到的只能是**面板自己的纸**（壳子的 `surface`）。
            //
            // 这条线拿不到静态值：这一行是**吸顶**的，位置由列表滚动决定（头部滚走时它顶到
            // 壳子顶）。布局阶段回传、draw 阶段读，不触发重组。
            // 首帧给正无穷 = 先不裁，第二帧即修正（发生在壳动画 / 骨架期，不可见）。
            var panelTopInRootPx by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
            // 标题条区：搜索页同款（headlineSmall 粗体、高度跟文字走）。
            //
            // 让位方向按路径分（2026-10-03 用户：「歌单上边的标题不要给收起按钮让位，收起按钮放
            // 右上角，标题壳内」）：
            //   · **壳路径**：收起键挪到右上角了，左边不再有东西 —— 标题左起对齐探索页 / 搜索页
            //     那三个大标题（[TrackListMetrics.TitleBarTextAligned] = 24dp，三页逐像素齐平），
            //     右边才让开那颗键（[TrackListMetrics.TitleBarTextEnd]）；
            //   · **nav 路径**：左上仍是本屏自己画的返回键（站内详情页的统一语言，见 `ScreenTopBar`），
            //     左右内缩反过来。
            // 标题条与顶部那颗键**不分路径**（2026-10-05 用户：「屏幕顶部的标题、圆角纸
            // 跟探索页不一样，改成一样的」）：内缩、字形、让位方向两条路径同一份，于是
            // 「详情页顶栏」与「探索 / 搜索 / 我的」三页的大标题逐像素齐平，纸顶也落在同一条线。
            //
            // 唯一按路径分的是**键在哪一侧**：nav 路径的键是浮层（见本屏外层 Box 里的
            // [CirroCloseButton]，与壳路径那颗同位同规格），壳路径的键由壳自己画（[ShellPanel]
            // / [com.thripleq.cirro.ui.components.CoverExpandShell]），本屏不重复画。
            Box {
                TrackListTitleBar(
                    label = src.label,
                    title = title,
                    reveal = washExit,
                    textStart = TrackListMetrics.TitleBarTextAligned,
                    textEnd = TrackListMetrics.TitleBarTextEnd,
                    modifier = Modifier.onSizeChanged { stickyTopPx = it.height },
                )
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
                // 裁到面板上沿为止（`panelTopInRoot`）—— 见 [panelTopInRootPx] 那段说明。
                TrackListBackdrop(
                    coverUrl = previewCoverUrl ?: collection?.coverUrl,
                    exit = washExit,
                    parallaxPx = { headerScrollPx.value },
                    panelTopInRoot = { panelTopInRootPx },
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
                                            capabilities,
                                            onCoverRect,
                                            onCoverDrawn,
                                            watermarkIcon,
                                            textAlpha = contentReveal,
                                            exit = washExit,
                                            onShare = onShare,
                                            onComments = onComments,
                                            onSubscribe = vm::toggleSubscribe,
                                            onShuffle = vm::shuffleAll,
                                            onRefresh = vm::refresh,
                                            // 骨架还在时共享元素挂骨架（见 skeletonGone），避免同 key 双宿主。
                                            coverSharedModifier =
                                                if (skeletonGone) coverSharedModifier else Modifier,
                                        )
                                    }
                                }
                                // 面板：从「播放全部」行起，往下都是不透明面（糊底只在头部透出来）。
                                // 行与行之间不留缝，所以不能用列表的 spacedBy——面板要连成一块。
                                //
                                // 这一行是**吸顶**的（抄官方歌单页）：滚到停靠线（标题条下沿）就停住，
                                // 曲目行从它下面过；返回键 / 壳关闭键在标题条里，一直看得见也点得到
                                // （停靠线已经让开了它们那一档，不必再互相让位）。
                                // **这一行自带外形**（[playAllShape]）：四角普通圆角的一块面 ——
                                // 上接头部（封面空气的那一段），下压曲目行（面板的 `surface`）。
                                //
                                // 圆角切掉的那四块露什么，是这一块的**全部要害**（2026-10-03 来回四版）：
                                //   · 只给这一行铺底 `surface` → 屏幕上就是「圆角外面刷了块黑」，被否；
                                //   · 干脆不铺，让切口透出去 → 透出来的是**封面糊底**（亮紫/亮棕），被否；
                                //   · 正解：**把糊底自己裁掉**（见 [TrackListBackdrop] 的 `panelTopInRoot`），
                                //     于是切口后面站着的是壳子的 `surface` —— 这一行什么都不用刷，
                                //     四个角是真·空的，露出来的就是面板自己的纸，
                                //     与紧跟其后的曲目行严丝合缝连成一整块。
                                // 分工：**上边两角**在裁切线之上，切进头部的封面空气（那是面板这张纸
                                // 的顶角，本来就该露空气）；**下边两角**在裁切线之下，切进纸面。
                                //
                                // 层级从外到内：`graphicsLayer`(alpha) → `clip`(四角圆) → 不透明底。
                                // 1. 底就是主题的 `surface`，**不再铺封面色**（2026-10-03 用户：
                                //    「播放全部按钮的背景不太好看，要么简化，要么用效果更好的」）：
                                //    这一行与紧跟其后的曲目行同色，整张面板连成一整片纸。原先铺
                                //    封面派生色的那版反而把面板切成两种颜色，恰与这一屏反复在追的
                                //    「连成一片」相悖，详见 [rememberCoverAccent]。
                                //    封面色没丢，只是挪到**圆钮身后一团径向晕**（见 [PlayAllRow]）：
                                //    面积小了二十倍，于是可以给浓、给干净。
                                //    底必须不透明：吸顶时它要盖住从下面滚过的行。
                                // 2. `graphicsLayer`(alpha) 必须在最外，否则 alpha 罩不住里面这些层；
                                //    `clip` 在底之前，顺带把内容与点按水波收进圆角。
                                // 3. 布局坐标回传用 `onGloballyPositioned`：它只是**测量**，
                                //    不给这一行加任何绘制。
                                stickyHeader(key = "playall") { _ ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned {
                                                panelTopInRootPx = it.positionInRoot().y
                                            }
                                            .graphicsLayer { alpha = contentReveal.value }
                                            .clip(playAllShape)
                                            .background(MaterialTheme.colorScheme.surface),
                                    ) {
                                        PlayAllRow(
                                            target,
                                            accent = coverAccent,
                                            subscribeEnabled = capabilities.subscribe,
                                            onPlayAll = { vm.onPlayAll(target) },
                                            onSubscribe = vm::toggleSubscribe,
                                            onShuffle = vm::shuffleAll,
                                            onSort = { sortSheetOpen = true },
                                        )
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
                                        TrackListRow(
                                            track = track,
                                            // 徽标 = 付费档位（内容属性）× 「我买了没」（账号态）：
                                            // fee=1 → VIP，fee=4 → PAY；买了的 PAY 变蓝，VIP 歌
                                            // 买了则是「VIP + 蓝 PAY」两枚（实测确实存在：用户
                                            // 已购的 9 首单曲全是 fee=1）。判定与颜色见 PayBadge.kt。
                                            payTags = rememberPayTags(track),
                                            onClick = { vm.onTrackClick(target, index) },
                                            onMore = { openTrackMenu(track) },
                                        )
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
                        TrackListUiState.Empty -> CirroEmptyState(
                            "暂无曲目",
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        )
                        // 错误必须给出路：文案本身可点重试（与播客/评论/歌手页同交互语言）。
                        TrackListUiState.Error -> CirroErrorState(
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

        // 顶部那颗键（**只在 nav 路径**）：与壳路径那颗 [CirroCloseButton] **同位同规格**
        // —— 36dp 圆 + 黑底白下箭头、右上角、12dp 外边距、上提一档让圆心压在标题条中线上。
        //
        // ## 为什么它跑到右上角、又为什么必须浮在这一层
        //
        // 1. 右上角：左上放返回箭头时，标题得给它让出一档内缩（左起 64dp），而探索页的大标题
        //    左起 24dp —— 并排看就是「标题位置不一样」。把键翻到右端后两处标题左起同一根线，
        //    让位翻到右边，与壳路径也统一了（2026-10-05 用户：「改成一样的」）。
        // 2. 浮在这一层：36dp 的圆比标题条（[com.thripleq.cirro.ui.components.CirroTitleBarHeight]）
        //    高，塞进上面那个标题条 Box 会把它撑到 36dp，**下方的圆角纸整体下移** —— 纸顶又与
        //    探索页错开。浮层不参与 Column 测量，纸顶只由标题条决定。
        //
        // 键常驻这里：吸顶行会滚走，键不能跟着滚（骨架期也要能退出）。
        if (showBackButton) {
            CirroCloseButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .offset(y = -CloseButtonRaise)
                    .padding(12.dp),
            )
        }

        // 排序面板：与播放页的「播放音质」面板同一套语言（ModalBottomSheet + RadioButton）。
        // 排序是**纯本地重排**（不重拉网络），所以这里没有任何加载态——选完即生效。
        if (sortSheetOpen) {
            TrackListSortSheet(
                current = sort,
                onPick = {
                    vm.setSort(it)
                    sortSheetOpen = false
                },
                onDismiss = { sortSheetOpen = false },
            )
        }
    }
}

/**
 * 排序面板。抄 [com.thripleq.cirro.ui.playerbar.PlayerSettingsSheet] 那套
 * 「ModalBottomSheet + RadioButton」：站内两处选择类面板长得一样。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackListSortSheet(
    current: TrackSort,
    onPick: (TrackSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        // 先跑完自身收起动画再移除组合，否则面板会被当场拆掉、看不出收起（见
        // [com.thripleq.cirro.ui.playerbar.PlayerQueueSheet] 的同一处处理）。
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        Text(
            text = "排序",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        TrackSort.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(mode) }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == mode, onClick = { onPick(mode) })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 分享用的官方链接。**只有歌单 / 榜单 / 专辑有公开页面** —— 喜欢 / 已购 / 每日推荐
 * 是本地组装的列表，它们的 id 分别是 uid、无意义串，硬拼进 `playlist?id=` 会打开
 * 一张不相干的歌单（比不给链接更糟）。这几类只分享文字。
 */
private fun shareUrlOf(source: TrackListSource, id: String): String? = when (source) {
    TrackListSource.PLAYLIST, TrackListSource.CHART -> "https://music.163.com/#/playlist?id=$id"
    TrackListSource.ALBUM -> "https://music.163.com/#/album?id=$id"
    else -> null
}

/** 评论线程序号；返回 null 表示这个列表没有评论线（本地组装的那三类）。 */
private fun commentThreadOf(source: TrackListSource, id: String): String? = when (source) {
    // 榜单本身就是歌单，共用 `A_PL_0_`。
    TrackListSource.PLAYLIST, TrackListSource.CHART -> CommentThread.playlist(id)
    TrackListSource.ALBUM -> CommentThread.album(id)
    else -> null
}
