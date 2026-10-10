package com.thripleq.cirro.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.core.repo.CommentThread
import com.thripleq.cirro.ui.components.CirroArt
import com.thripleq.cirro.ui.components.CirroArtwork
import com.thripleq.cirro.ui.components.LocalCommentsOpener
import com.thripleq.cirro.ui.theme.CirroShape
import kotlinx.coroutines.launch

/* ── 控制器：谁都能开，只有根上那一个在画 ──────────────────────── */

/**
 * 打开曲目菜单的入口。由 [com.thripleq.cirro.CirroApp] 在根上提供（[LocalTrackMenu]），
 * 任何列表深处的曲目行都可以取来用。
 *
 * ## 为什么走 CompositionLocal 而不是逐层传 `onMore`
 *
 * ⋮ 出现在**每一个**列表里（歌单 / 榜单 / 专辑 / 喜欢 / 已购 / 每日推荐 / 搜索结果 /
 * 歌手热歌…），而它要做的事**与列表毫无关系**：「下一首播放」是播放器的事、「喜欢」
 * 是账号的事、「评论」是单曲的事、「分享」是单曲页的事。逐层传参等于把「列表有 ⋮」
 * 这个事实穿过五六层签名，且每加一个列表就要再穿一次 —— 与 `LocalCommentsOpener` /
 * `LocalOwnedTracks` 是同一类问题，所以用同一套办法。
 */
class TrackMenuController {
    /** 当前打开的那一首；null = 菜单没打开。 */
    var target by mutableStateOf<Track?>(null)
        private set

    fun open(track: Track) {
        target = track
    }

    fun close() {
        target = null
    }
}

/**
 * 曲目菜单控制器。`staticCompositionLocalOf`：这份值一生只换一次（根上那个实例），
 * 用 static 变体可以省掉每次重组时的读取跟踪。
 *
 * 取不到时（预览、测试）[rememberTrackMenuOpener] 给出的是空操作，不崩。
 */
val LocalTrackMenu = staticCompositionLocalOf<TrackMenuController?> { null }

/**
 * 取「打开曲目菜单」这个动作。**列表页的 ⋮ 都该用它**（而不是自己去读
 * [LocalTrackMenu]）：点击回调是普通 lambda，不能在里面对 CompositionLocal 取
 * `.current`；本函数在组合期读一次、把控制器封进返回的 lambda 里。
 *
 * 用法：`val openMenu = rememberTrackMenuOpener()` → `onMore = { openMenu(track) }`。
 */
@Composable
fun rememberTrackMenuOpener(): (Track) -> Unit {
    val controller = LocalTrackMenu.current
    return remember(controller) { { track: Track -> controller?.open(track) } }
}

/**
 * 菜单宿主：在根上渲染一次，任何列表点 ⋮ 都走到这里。
 *
 * 与 [TrackMenuSheet] 分开是为了让「谁去拿红心态、谁去弹 Toast、评论浮层从哪来」这类
 * 接线只写一遍 —— 列表页不必知道菜单内部要什么，只要一个 [rememberTrackMenuOpener]。
 *
 * ⚠️ `controller.target` 与 `vm.likedIds` 都在**本函数体内**读，不要提到调用点去读：
 * 本函数是独立的重组作用域，状态在这里读，菜单开关只重组它自己；提到根上读就会让
 * 整棵导航树跟着菜单开关一起重组。
 */
@Composable
fun TrackMenuHost(
    controller: TrackMenuController,
    vm: TrackMenuViewModel,
) {
    val track = controller.target ?: return
    // 菜单一打开就把红心集合拉起来（每账号一次）。惰性在这里是刻意的：没必要在
    // App 启动或每进一张列表时就发这个请求，只有用户真要去点「喜欢」时才需要。
    LaunchedEffect(track.id) { vm.onMenuOpened() }
    val likedIds by vm.likedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val commentsOpener = LocalCommentsOpener.current
    TrackMenuSheet(
        track = track,
        liked = track.id in likedIds,
        onPlayNext = { vm.playNext(track) },
        onToggleLike = { vm.toggleLike(track.id) },
        // 起点给 null（居中浮现）：⋮ 自己的窗口矩形要在**每一行**挂 `onGloballyPositioned`
        // 才拿得到，而那是每行每次布局都回调的代价（见列表页头部评论胶囊那条注释）；
        // 何况此刻浮层接的是"刚收起的菜单"，从某一行长出来反而对不上因果关系。
        onComments = { commentsOpener?.open(CommentThread.song(track.id), null) },
        onShare = {
            val title = listOf(track.name, track.artist)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            shareTo(context, title, songUrlOf(track.id))
        },
        onDismiss = { controller.close() },
    )
}

/**
 * 单曲的官方页面。**每首歌都有**（与它是从哪张列表点进来的无关），所以菜单里的分享
 * 总是带链接 —— 这也是本地那三类列表（喜欢 / 已购 / 每日推荐）分享能力的补足：
 * 列表分享不了，歌分享得了。
 */
internal fun songUrlOf(id: String) = "https://music.163.com/#/song?id=$id"

/**
 * 起系统分享面板。**必须传 Activity context**：applicationContext 起 chooser 要加
 * `FLAG_ACTIVITY_NEW_TASK`，且部分 ROM 上会丢掉调用方身份、选择器样式异常。
 */
internal fun shareTo(context: Context, title: String, url: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, if (url != null) "$title\n$url" else title)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(send, "分享到"))
}

/* ── 面板本身 ─────────────────────────────────────────────────── */

/**
 * 曲目行的 ⋮ 菜单 —— **单曲级**动作。
 *
 * ## 为什么单曲动作要独立存在
 *
 * 六类列表长得一样，能做的**列表级**动作却不同（见 `TrackListCapabilities`）：
 * 喜欢 / 已购 / 每日推荐是本地拼出来的壳，服务端没有它们对应的对象，所以既没有
 * 列表级评论线也没有收藏对象。但**单曲**不一样 —— 每一首歌在服务端都是真实对象，
 * 红心、评论线（`R_SO_4_`）、分享链接**与它是从哪张列表点进来的毫无关系**。
 *
 * 于是这三类列表缺的能力，正好由这里补上：评论入口从头部（列表级）下沉到每一行
 * （单曲级）。这不是权宜之计 —— 官方也是这个结构：歌单页的评论线在头部，而任何
 * 一首歌长按都有自己的「评论」。
 *
 * ## 与排序面板同一套语言
 *
 * [ModalBottomSheet] + 图标行，与列表页的排序面板 / 播放页的设置面板同构；
 * 面板顶部先给出「操作的是哪一首」（封面 + 歌名 + 歌手），否则一屏几十行点了 ⋮
 * 之后无从确认自己点的是哪一首。
 *
 * 每一项都在**面板收起之后**才执行：面板是独立窗口、画在一切之上，先开操作
 * （评论浮层 / 系统分享）会让它被面板盖住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackMenuSheet(
    track: Track,
    /** 这首歌当前是否已红心（[TrackMenuViewModel.likedIds] 的镜像）。 */
    liked: Boolean,
    onPlayNext: () -> Unit,
    onToggleLike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 先跑完面板自己的收起动画再执行动作：立即执行的话，评论浮层会在这块面板
    // 底下打开（面板是独立窗口，盖在根浮层之上），用户看到的还是这块菜单。
    fun pick(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        TrackMenuHeader(track)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        TrackMenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, "下一首播放") { pick(onPlayNext) }
        // 红心用实心 + 主色，与头部收藏胶囊、播放页那颗红心同一套「点亮」语言。
        TrackMenuItem(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = if (liked) "取消喜欢" else "喜欢",
            active = liked,
        ) { pick(onToggleLike) }
        TrackMenuItem(Icons.Outlined.ChatBubbleOutline, "评论") { pick(onComments) }
        TrackMenuItem(Icons.Outlined.Share, "分享") { pick(onShare) }
        Spacer(Modifier.height(24.dp))
    }
}

/** 菜单顶部：封面 + 歌名 + 歌手 - 专辑。只读，不可点。 */
@Composable
private fun TrackMenuHeader(track: Track) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CirroArtwork(
            url = track.artworkUrl,
            contentDescription = null,
            size = CirroArt.Row,
            shape = CirroShape.Chip,
            requestSize = CirroArt.RequestRow,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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
    }
}

/**
 * 菜单里的一行：图标 + 文案。
 *
 * [active] 是点亮态（已喜欢）：图标与文字转主色，与站内其它「点亮」语言一致
 * （头部收藏胶囊、播放页那颗红心）。
 */
@Composable
private fun TrackMenuItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标装在一个圆底片里：菜单只有图标 + 文字、没有分隔线，纯图标行会显得散。
        // 底片用 onSurface 8% —— 与头部胶囊、面板行尾同一个「灰片」语言，明暗两套通用。
        Row(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
        )
    }
}
