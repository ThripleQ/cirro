package com.thripleq.cirro.ui.playerbar

/**
 * 底部整簇：进度行 → 试听结束提示 → 控制行（播放顺序 / 三键 / 播放列表）→ 功能胶囊行
 * → 错误文案，连同整簇的退场动画。
 *
 * 从 `PlayerPageContent.kt` 分出来的理由：这一簇是**最常被改的一块**（加按钮、换图标、调
 * 间距），而它的全部间距量（[TransportBottomGap]、播放键盒子、侧键尺寸）都只依赖展开进度
 * `sc` 与歌词档因子 `lyricsLayout` 两个输入 —— 拿两个输入就能自己算，不必由主组合转手。
 * 分出来之后，「改底部按钮」只看这个文件，不会顺手碰到封面/标题那段逐帧几何。
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import android.widget.Toast
import androidx.media3.common.Player
import com.thripleq.cirro.core.playback.PlayerHolder
import com.thripleq.cirro.ui.theme.Motion

/**
 * @param visible 沉浸歌词档传 false：整簇按「高度收拢 + 淡出、**向下**收」退场 —— 它们贴着
 *   列底，向下消失才有「沉出屏幕」的方向感；回内联时从下方长回来。
 * @param sc 展开进度（0=卡片档，1=全屏）。本簇所有尺寸/间距都只由它和 [lyricsLayout] 决定。
 * @param lyricsLayout 歌词档版式因子（0=封面档，1=歌词档），见 `PlayerPageContent` 里的说明。
 */
@Composable
internal fun PlayerTransportSection(
    player: Player,
    state: PlayerUiState,
    positionState: State<Long>,
    seekFrozen: MutableState<Boolean>,
    visible: Boolean,
    sc: Float,
    lyricsLayout: Float,
    sleepEndAt: Long,
    onSleepEndAtChange: (Long) -> Unit,
    onSettings: () -> Unit,
    /** 播放列表（队列）面板。 */
    onQueue: () -> Unit,
) {
    val context = LocalContext.current
    // 三键↔进度行的间距：卡片档 10dp → 全屏 18dp；歌词档再收到 10dp 把高度让给歌词。
    val ctrlGap = lerp(10.dp, 18.dp, sc)
    val bottomCtlGap = lerp(ctrlGap, 10.dp, lyricsLayout)
    // 三键之下、胶囊行之上的间距。歌词档再收掉约 47%（57→30dp），三键跟着下移。
    val capsuleGap = TransportBottomGap * sc * (1f - 0.47f * lyricsLayout)
    // 三档尺寸拉开主次（2026-10-31 用户：「参考一下网易云的大小」）。照着同屏网易云
    // 播放页量了字形（本机密度 2.75）：播放三角高约 35dp、切歌约 20dp、顺序/列表约 18dp。
    // 换算成我们的圆角字形盒子就是 62 / 44 / 27 —— 播放和切歌各比上一版大一圈，
    // 顺序/列表不动（那一档本来就与网易云相当）。
    // 之前四枚侧键同尺寸时：Repeat / QueueMusic 的笔画几乎占满视口，而 SkipPrevious /
    // SkipNext 是窄字形，看上去顺序/列表反而比切歌更大，主次正好是反的。
    val skipBtnDim = lerp(29.dp, 44.dp, sc)
    val utilBtnDim = lerp(22.dp, 27.dp, sc)
    // 播放键：用户「不要这种有红色圆圈的」「更大」。盒子 54→72 只当触控区（图标变大后要
    // 留余量），图标 50→62。
    val playBox = lerp(54.dp, 72.dp, sc)
    val playIcon = lerp(50.dp, 62.dp, sc)
    // 没有容器色了，播放图标与切歌图标同色 —— 靠尺寸（62 vs 44 vs 27）分主次。
    val playTint = MaterialTheme.colorScheme.onSurface

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(tween(ImmersiveMs, easing = Motion.EmphasizedDecelerate)) +
            expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(ImmersiveMs, easing = Motion.EmphasizedDecelerate),
            ),
        exit = fadeOut(tween(ImmersiveExitMs, easing = Motion.Emphasized)) +
            shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(ImmersiveExitMs, easing = Motion.Emphasized),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 进度行：滑块 + 两侧时间整块挪进独立组件 —— 它是全页**唯一解包进度值**的地方，
            // 250ms 的轮询与拖动的逐帧写入因此只重组这一行，不再牵着内容区陪跑。
            // 位置不变：进度条之上不再画东西（原来那条 1dp 分割线已按用户要求删掉，
            // 省下的 33dp 原样挪到三键之下，见 [TransportBottomGap]）。
            PlayerProgressRow(
                player = player,
                positionState = positionState,
                seekFrozen = seekFrozen,
                durationMs = state.durationMs,
            )

            // 试听片段播完的提示（承接「片段末尾停住」的收尾）。
            //
            // 三件事都刻意的：
            // ① **只在全屏档长出来**（高与透明随 sc）—— 卡片档那套版式是逐像素调过的，多一行
            //    就整体错位；高度 lerp 到 0 则完全不参与布局。
            // ② 插在进度行**下方**：进度行上方是弹性空白（封面档的 weight Spacer / 歌词档
            //    的歌词区），会吸收这点新高度，所以底部三键与功能胶囊**不会**被顶下去。
            // ③ 文案用 tertiary（与「试听」标同色）而不是报错色：它不是错误，是权益边界。
            if (state.trialEnded) {
                Spacer(Modifier.height(lerp(0.dp, 8.dp, sc)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lerp(0.dp, 18.dp, sc))
                        .clipToBounds()
                        .graphicsLayer { alpha = sc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "试听已结束 · 开通 VIP 可听完整版",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(bottomCtlGap))

            // 控制行，版式照网易云（2026-10-30 用户给的截图）：最左是**播放顺序**一枚键
            // （列表循环 / 单曲循环 / 随机三档轮换），最右是**播放列表**，切歌与播放居中。
            // 五枚图标 SpaceEvenly 均分整行 —— 与进度条同一宽度语言，两侧键贴边但不贴屏幕边。
            //
            // 三键仍是**裸图标**（用户：「播放按钮不要这种有红色圆圈的」），切歌/播放用
            // Rounded 变体（用户：「切歌，播放按钮都换成圆润一点的」）—— Filled 的跳曲/播放
            // 是硬直边，Rounded 的圆角与整页的胶囊语言一致；顺序/列表两枚用 Filled，
            // 与网易云那套线面关系一致，也更容易和中间的 Rounded 三键分清主次。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = lerp(4.dp, 8.dp, sc)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    PlayerHolder.cyclePlayOrder(player)
                    // 三档共用一枚键，档位之间只有图标差别，用一句 Toast 明确「现在是什么」。
                    Toast.makeText(context, playOrderLabel(player), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = when {
                            state.shuffleEnabled -> Icons.Filled.Shuffle
                            state.repeatMode == Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "播放顺序：" +
                            playOrderLabelOf(state.shuffleEnabled, state.repeatMode),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(utilBtnDim),
                    )
                }
                IconButton(onClick = { PlayerHolder.skipPrevious(player) }) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(skipBtnDim),
                    )
                }
                // 盒子只当触控区（图标变大后要留余量），本身不画任何底。
                Box(
                    modifier = Modifier.size(playBox),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { PlayerHolder.togglePlay(player) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = when {
                                state.isBuffering -> Icons.Rounded.MoreHoriz
                                state.isPlaying -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = playTint,
                            modifier = Modifier.size(playIcon),
                        )
                    }
                }
                IconButton(onClick = { PlayerHolder.skipNext(player) }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(skipBtnDim),
                    )
                }
                IconButton(onClick = onQueue) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放列表",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(utilBtnDim),
                    )
                }
            }

            // 全屏专属：功能胶囊行，高度与透明度随 sc 长出。
            // 随机/循环挪进控制行之后这里只剩两枚，居中排 —— 原先 SpaceBetween 的两端
            // 分别留给顺序和队列，现在它们已经在控制行两头了。
            Spacer(Modifier.height(capsuleGap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp * sc)
                    .clipToBounds()
                    .graphicsLayer { alpha = sc },
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SleepTimerChip(endAt = sleepEndAt, onEndAtChange = onSleepEndAtChange)
                FullChip(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    active = false,
                    onClick = onSettings,
                )
            }

            state.errorText?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 三档播放顺序的文案（与网易云同一套说法）。 */
private fun playOrderLabelOf(shuffle: Boolean, repeatMode: Int): String = when {
    shuffle -> "随机播放"
    repeatMode == Player.REPEAT_MODE_ONE -> "单曲循环"
    else -> "列表循环"
}

/** 轮换刚结束时的档位（直接读 player，不复用组合里那份可能还没刷新的 [PlayerUiState]）。 */
private fun playOrderLabel(player: Player): String =
    playOrderLabelOf(player.shuffleModeEnabled, player.repeatMode)
