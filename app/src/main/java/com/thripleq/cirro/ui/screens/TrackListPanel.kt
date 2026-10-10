package com.thripleq.cirro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.thripleq.cirro.core.model.TrackCollection
import com.thripleq.cirro.ui.components.BannerCoverSize
import com.thripleq.cirro.ui.components.BigCoverVisual
import com.thripleq.cirro.ui.components.LocalShellHeroAlpha
import com.thripleq.cirro.ui.profile.TrackListCapabilities
import com.thripleq.cirro.ui.theme.CirroShape
import java.util.Locale

/* ── 头部 ──────────────────────────────────────────────────────── */

/**
 * 头部：左方封面 + 右侧标题 / 作者 / 简介，下面三枚等宽操作胶囊。
 *
 * 全部墨色走主题色（onSurface / onSurfaceVariant），因为背后是 `surface` 蒙过的糊底，
 * 明暗两套都压得住——不需要 on-image 那套白字。
 *
 * ## 三枚胶囊按 [capabilities] 组装，不是写死的三颗
 *
 * 六类列表共用这一个头部，但能做的**列表级**动作不同（见 `TrackListCapabilities`）：
 *
 * | 列表来源 | 三枚 |
 * |---|---|
 * | 歌单 / 榜单 / 专辑（服务端有真实对象） | 分享 · 评论 · 收藏 |
 * | 喜欢 / 已购 / 每日推荐（本地拼的壳） | 分享 · 随机播放 · 刷新 |
 *
 * 右列那两枚是**替换**关系而不是「灰掉」关系：本地列表画一颗点不动的「收藏」、
 * 点了只弹一句「不支持收藏」，那不是功能、是把实现细节泄露给用户。而随机播放与刷新
 * 恰恰是这三类列表**真正需要**的两个动作（喜欢/已购的内容会在别处变、每日推荐每天换）。
 * 于是三枚永远齐整、也永远都有用。
 */
@Composable
internal fun TrackListHeader(
    collection: TrackCollection,
    capabilities: TrackListCapabilities,
    onCoverRect: ((Rect) -> Unit)?,
    onCoverReady: (() -> Unit)?,
    watermarkIcon: ImageVector?,
    textAlpha: State<Float>?,
    /** 头部滚出进度 0..1（= `washExit`）：驱动封面的退场微缩（见 [HeaderExitShrink]）。 */
    exit: State<Float>,
    onShare: () -> Unit,
    /** 评论键：参数是按钮自己的窗口矩形，作为评论浮层的浮现起点。 */
    onComments: (Rect) -> Unit,
    onSubscribe: () -> Unit,
    /** 整单随机播放（本地列表的第三枚，见上面的表）。 */
    onShuffle: () -> Unit,
    /** 手动刷新这张列表（本地列表的第二枚，见上面的表）。 */
    onRefresh: () -> Unit,
    coverSharedModifier: Modifier = Modifier,
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
                    .clip(CirroShape.CardSmall)
                    // hero 顶着时透明；hero 一开始淡出即变为不透明底板、hero 在其上渐隐（draw 阶段读，不重组）。
                    .graphicsLayer {
                        alpha = if (heroAlpha.value >= 1f) 0f else 1f
                        // 退场微缩：封面跟着头部滚走时缩一点、往下沉一点，读作「被面板吸进去」
                        // （2026-10-03「退场编舞」的第三条腿，见 [HeaderExitShrink]）。
                        // 同样只在绘制层，**不动布局** —— 上面 `onGloballyPositioned` 回传的
                        // 终态矩形因此不受影响（hero 终点契约不破）。
                        val e = exit.value
                        val s = 1f - HeaderExitShrink * e
                        scaleX = s
                        scaleY = s
                        translationY = HeaderExitSink.toPx() * e
                    },
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
        // 三枚等宽操作胶囊，按 [capabilities] 组装（服务端：分享/评论/收藏；
        // 本地壳：分享/随机播放/刷新 —— 见本函数的表）。三枚都是真动作。
        //
        // 评论键要把自己的窗口矩形交出去（评论浮层从这颗按钮处浮现），所以这一枚
        // 单独挂 `onGloballyPositioned` —— 另外两枚不需要，别顺手全挂上（每次布局
        // 都会回调）。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackListAction(
                icon = Icons.Outlined.Share,
                label = "分享",
                modifier = Modifier.weight(1f),
                onClick = onShare,
            )
            if (capabilities.comments) {
                var commentRect by remember { mutableStateOf(Rect.Zero) }
                TrackListAction(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "评论",
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { commentRect = it.boundsInWindow() },
                    onClick = { onComments(commentRect) },
                )
            } else {
                TrackListAction(
                    icon = Icons.Outlined.Shuffle,
                    label = "随机播放",
                    modifier = Modifier.weight(1f),
                    onClick = onShuffle,
                )
            }
            if (capabilities.subscribe) {
                TrackListAction(
                    // 已收藏给实心 + 主色：与播放页那颗红心同一套「点亮」语言。
                    icon = if (collection.subscribed) Icons.Filled.Favorite
                    else Icons.Outlined.FavoriteBorder,
                    label = collection.subscribedCount.takeIf { it > 0 }?.let(::formatCount) ?: "收藏",
                    modifier = Modifier.weight(1f),
                    onClick = onSubscribe,
                    active = collection.subscribed,
                )
            } else {
                TrackListAction(
                    icon = Icons.Outlined.Refresh,
                    label = "刷新",
                    modifier = Modifier.weight(1f),
                    onClick = onRefresh,
                )
            }
        }
        //
        // 2026-10-03 撤掉「面板上沿的投影」：这里原先压了一条 8dp 的 `Transparent → Scrim`
        // 渐变，本意是抄官方「面板浮在封面糊底上」的那点暗带。真机上它就是「播放全部」
        // 上方一坨抹不开的黑影（用户：「播放全部上面的黑色阴影太丑了，去掉」），而且和
        // 这一行自己从封面派生的面色打架。留 8dp 空白只为把面板与三胶囊隔开。
        Spacer(Modifier.height(8.dp))
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
    /** 点亮态（收藏）：图标与文字转主色。底不变——胶囊自己在糊底上，改底色会跟封面色打架。 */
    active: Boolean = false,
) {
    // 点亮色与「未点亮」色的分工：图标承状态、文字保持墨色。文字也染主色时，整条胶囊在
    // 浅色主题下会变成一块粉色，与三枚里的另外两枚失衡。
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CirroShape.Pill)
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
            tint = tint,
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

/* ── 面板首行：播放全部 ────────────────────────────────────────── */

/**
 * 「播放全部」行：红圆钮 + 标题 + 曲目数/播放量 + 右侧图标。
 *
 * ## 颜色只从圆钮身后出来
 *
 * 2026-10-03 起这一行的**背景不再铺色**（就是主题 `surface`，与曲目行连成一整片纸），
 * 封面色改由 [accent] 在圆钮身后化成**一团径向晕** —— 这一行唯一的颜色来源。
 * 面积小了二十倍，于是可以给浓、给干净，原来的问题与取舍见 [rememberCoverAccent]。
 *
 * 副标题里「N首」用常规墨色、播放量用 tertiary（暖金）——官方那句「含20首VIP歌曲」是金色，
 * 本地没有 VIP 信息，就把金色留给「数据」这一档，位置与视觉权重与官方一致。
 *
 * ## 行尾图标：三枚全都是真动作
 *
 * 官方这一行是「收藏 / 下载 / 排序」。这里换掉了其中的**下载**：
 *
 * - 下载在 cirro 里从来没有落地过，一直是个点了弹「开发中」的占位。离线下载要连
 *   `PlaybackCache`（512MB LRU，**可被淘汰**）一起设计「已下载」状态与容量策略，
 *   仓促做出来会变成一个会撒谎的按钮 —— 那是单独一轮的事（见 2026-10-10 记录）。
 * - 空出来的位置给**随机播放**：六类列表都需要它（官方喜欢页自己也有这一颗），
 *   而它在这里是实打实能做的 —— 与头部的收藏胶囊同一个「同一动作的第二个入口」思路。
 *
 * 收藏那一枚受 [subscribeEnabled] 控制：喜欢 / 已购 / 每日推荐是本地拼的壳，
 * 没有可收藏的对象，画一颗点不动的收藏不如不画（判据见 `TrackListCapabilities`）。
 *
 * @param accent 封面派生的强调色；取色失败时它等于 `surface`，晕自然不可见（不是 bug）
 */
@Composable
internal fun PlayAllRow(
    collection: TrackCollection,
    accent: Color,
    /** 这个列表能不能收藏（本地拼的壳不能）—— false 时那一枚不出现。 */
    subscribeEnabled: Boolean,
    onPlayAll: () -> Unit,
    onSubscribe: () -> Unit,
    onShuffle: () -> Unit,
    onSort: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayAll)
            .padding(start = TrackListMetrics.SideInset, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 晕走 `drawBehind` 而不是在外面套一个更大的 Box：套 Box 会把 Row 撑到 112dp 高
        // （大于行本身的 68dp），布局跟着变形。`drawBehind` 画在圆钮自己的绘制范围内、向外溢出，
        // **不参与测量**，行高不变。溢出的那圈由行的 `clip(playAllShape)` 收掉 —— 而那个位置
        // 早衰减到近乎透明（见下面的 colorStops），所以裁切线看不出来。
        Box(
            Modifier
                .size(TrackListMetrics.DiscSize)
                .drawBehind {
                    val r = size.minDimension * (PlayAllHaloRatio / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            // 衰减刻意前重后轻：浓度集中在圆钮附近的那一小圈里，
                            // 越往外越快趋近于零 —— 于是行边裁掉的全是无色的尾巴。
                            colorStops = arrayOf(
                                0f to accent.copy(alpha = PlayAllHaloCore),
                                0.40f to accent.copy(alpha = PlayAllHaloCore * 0.45f),
                                0.65f to accent.copy(alpha = PlayAllHaloCore * 0.10f),
                                1f to accent.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )
                }
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
        if (subscribeEnabled) {
            PanelIcon(
                icon = if (collection.subscribed) Icons.Filled.Favorite
                else Icons.Outlined.FavoriteBorder,
                label = "收藏",
                onClick = onSubscribe,
                active = collection.subscribed,
            )
        }
        PanelIcon(Icons.Outlined.Shuffle, "随机播放", onShuffle)
        PanelIcon(Icons.Outlined.Sort, "排序", onSort)
    }
}

/**
 * 面板行尾的图标钮：40dp 触控盒，不是 [IconButton] —— M3 的 IconButton 有 48dp 最小尺寸，
 * 三枚就把中间那段挤窄到副标题放不下（官方实测图标间距约 43dp，本行照此收紧）。
 *
 * [active] 是点亮态（收藏）：图标转主色。这枚钮是**同一个动作的第二个入口**（头部那枚
 * 胶囊才是第一个），两处必须同源同状态 —— 都读 `collection.subscribed`。
 */
@Composable
private fun PanelIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
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
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 数字缩写：亿 / 万 / 原样。 */
private fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e8)) + "亿"
    n >= 10_000 -> trimZero(String.format(Locale.US, "%.1f", n / 1.0e4)) + "万"
    else -> "$n"
}

private fun trimZero(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s
