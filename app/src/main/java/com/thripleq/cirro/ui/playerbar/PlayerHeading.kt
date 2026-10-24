package com.thripleq.cirro.ui.playerbar

/**
 * 标题块（歌名 + 红心/评论、歌手 + 徽标）以及这一行的尺寸常量。
 *
 * 从 `PlayerPageContent.kt` 分出来的理由：它是**版式里唯一有内部状态**的一块（红心/评论
 * 的淡显动画、评论按钮的窗口矩形上报），而且卡片档与歌词档**共用同一份**（两档同一位置、
 * 同一组件）。改标题块、徽标、动作图标只动这个文件，不会碰到主组合的逐帧几何。
 */
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.thripleq.cirro.ui.components.CirroPayBadge
import com.thripleq.cirro.ui.components.CirroTrialBadge
import com.thripleq.cirro.ui.components.FadingMarqueeText
import com.thripleq.cirro.ui.components.PayTag
import com.thripleq.cirro.ui.theme.Motion

/**
 * 歌名右侧红心/评论的图标尺寸。
 *
 * 2026-10-03 用户：「红心和评论太小了……最终调成一样高」。原来是 `Icon` 的默认 24dp，
 * 而全屏档歌名是 28sp —— 图标明显矮一头；现在歌名收到 24sp（见 `PlayerPageContent`
 * 里的 `titleFont`）、图标抬到 28dp，两者的**字面/图形高度**相当（中文字面高 ≈ 0.86em
 * ≈ 21dp，Material 图标墨迹 ≈ 0.83×28 ≈ 23dp）。
 *
 * 触控盒仍是 40dp（见 [TitleActionRowWidth]）：图标只动图形，不动点按区域。
 */
internal val TitleActionIcon = 28.dp

/** 红心/评论的触控盒边长（图标只动图形，不动这个）。 */
internal val TitleActionButton = 40.dp

/**
 * 红心 + 评论那一行的宽度（也是展开时宽度动画的终点值）。
 *
 * = 2 × [TitleActionButton]。这个值必须与盒宽之和严格相等：小的那个会被裁
 * （外层 `clipToBounds`），大的那个会凭空占走歌名的宽度。
 */
internal val TitleActionRowWidth = TitleActionButton * 2

/**
 * 标题块：歌名（带右侧红心/评论）+ 歌手（带试听标）。封面档在封面之下，歌词档**常驻
 * 歌词之上**（内联/沉浸同一位置、同一组件，换档时歌词不被顶走）。
 *
 * 版式沿革（保留自原内联实现）：参照网易云 —— 歌名/歌手左对齐上下叠放；红心+评论与
 * **歌名那一行**同轴居中对齐（用户：「红心，评论和标题对齐，齐平」）；文字过长横向跑马；
 * 右侧图标随全屏进度 sc 渐显（卡片档宽高都收为 0，不撑胖紧凑版式）。
 *
 * @param actionsVisible 沉浸歌词档传 false：红心/评论淡出且不可点（原需求「沉浸页隐藏
 *   各种按钮，标题、艺人名保留」）。**只淡出不撤位** —— 图标行仍占着宽高，标题块高度
 *   不变，换档时歌词不会因为页眉变矮而上移。
 */
@Composable
internal fun PlayerHeading(
    state: PlayerUiState,
    sc: Float,
    titleStyle: TextStyle,
    titleColor: Color,
    artistStyle: TextStyle,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onComments: (Rect) -> Unit,
    /** 评论按钮当前的窗口矩形（由 [onCommentBounds] 上报、外层 `PlayerPageContent` 持有），
     *  点评论时原样传给 [onComments] 作为浮层转场的源矩形。 */
    commentRect: Rect,
    onCommentBounds: (Rect) -> Unit,
    actionsVisible: Boolean = true,
) {
    val actionsAlpha by animateFloatAsState(
        targetValue = if (actionsVisible) 1f else 0f,
        animationSpec = tween(ImmersiveExitMs, easing = Motion.Standard),
        label = "headingActions",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FadingMarqueeText(
                text = state.title.ifEmpty { "暂无播放" },
                style = titleStyle,
                color = titleColor,
                // 权重：图标行贴右端、歌名占剩下的宽度（跑马按容器宽判定）。
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp * sc))
            // 红心：**卡片档就常驻**（用户 2026-10-24 二次确认：「我要的是播放卡片的红心，
            // 不是迷你条」）。卡片档 sc=0，之前整行的宽度/高度/透明度都乘 sc，心和评论一起
            // 被收成了 0 —— 现在红心不乘 sc，只随它从 24dp 长到 28dp。
            // 评论仍是全屏专属：卡片档没它的位置，sc>0 才组合进场（透明度也随 sc）。
            Row(
                modifier = Modifier
                    .width(TitleActionButton + TitleActionButton * sc)
                    .height(TitleActionButton)
                    .clipToBounds()
                    .graphicsLayer { alpha = actionsAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleLike,
                    enabled = actionsVisible,
                    modifier = Modifier.size(TitleActionButton),
                ) {
                    Icon(
                        // 空/实心随真状态走：以前这颗心是「永远实心 + 主色」的假状态，
                        // 点下去只弹「开发中」，读起来像「这首歌已经喜欢了」。
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "取消喜欢" else "喜欢",
                        tint = if (liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        // 卡片档 24dp（与 21sp 歌名相称）→ 全屏 28dp（压住 24sp 歌名）。
                        modifier = Modifier.size(lerp(24.dp, TitleActionIcon, sc)),
                    )
                }
                if (sc > 0.01f) {
                    IconButton(
                        onClick = { onComments(commentRect) },
                        enabled = actionsVisible,
                        modifier = Modifier
                            .size(TitleActionButton)
                            .graphicsLayer { alpha = sc * actionsAlpha }
                            .onGloballyPositioned { onCommentBounds(it.boundsInWindow()) },
                    ) {
                        Icon(
                            Icons.Filled.Chat,
                            "评论",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TitleActionIcon),
                        )
                    }
                }
            }
        }
        // 歌手为空时整行折叠，不留空洞 —— 但**徽标不跟着折**：它们是这一行的主要信息
        // （歌手名缺失时，用户更需要知道「这是 VIP 歌、当前只有 30 秒」）。
        if (state.artist.isNotEmpty() || state.trial != null || state.vip) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 徽标贴在歌手名前，与曲目列表行「徽标贴在副标题前」同一处语言。
                // VIP 在左、「试听」在右：前者是这首歌的静态属性，后者是此刻的播放状态，
                // 顺序与列表行一致（VIP 永远第一枚）。两枚**同时出现**时信息才完整。
                if (state.vip) {
                    CirroPayBadge(PayTag.VIP)
                    Spacer(Modifier.width(2.dp))
                }
                if (state.trial != null) {
                    CirroTrialBadge()
                    Spacer(Modifier.width(2.dp))
                }
                if (state.artist.isNotEmpty()) {
                    // weight(1f) 是必须的：FadingMarqueeText 内部是
                    // `BoxWithConstraints(modifier.fillMaxWidth())`，不给权重它会把整行
                    // 宽度吃光、把左边那枚徽标挤出去（无徽标时它仍占满整行，与改前一致）。
                    FadingMarqueeText(
                        text = state.artist,
                        style = artistStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
