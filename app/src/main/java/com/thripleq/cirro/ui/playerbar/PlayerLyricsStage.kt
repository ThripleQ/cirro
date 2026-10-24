package com.thripleq.cirro.ui.playerbar

/**
 * 歌词三级状态机的**状态定义与手势连接器**。
 *
 * 从 `PlayerPageContent.kt` 分出来的理由：这一套（三个档、四个时长/距离常量、换档手势的
 * 累计与去抖）是**纯逻辑**，与版式无关，但以前内联在主组合函数体里，改手势要靠读一整页
 * 布局代码才能确认没有副作用。现在它自成一个文件，只看档位关系就能改。
 */
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 歌词三级状态（用户 2026-10-24 的方案）：
 * - [Cover]     原版式：封面 + 标题 + 底部控制。点封面（仅全屏档、有曲目）进 [Inline]。
 * - [Inline]    标题+歌手常驻顶部（用户：「标题和艺人名字放高点」），歌词紧接其下
 *               （「歌词区域也可以低一些」）；底部整簇下移。无操作 [IdleImmersiveMs]、
 *               或向上翻动歌词累计 [ImmersivePullDp] → [Immersive]。这一级列表照常滚动，
 *               换档只是旁路观察、不拦手势。点歌词区任意处 → [Cover]。
 * - [Immersive] 隐藏进度条/三键/功能胶囊；标题+歌手原地不动，歌词视口只向下变高 ——
 *               正在看的行不会被顶走。点击任意处 / 返回键 / 到底后继续上滑 → [Inline]。
 */
internal enum class LyricsStage { Cover, Inline, Immersive }

/** 内联歌词无操作多久后自动进沉浸（用户：「无操作一段时间」—— 取 5s：读完两三行歌词
 *  又不碰屏幕的量；太短会让想点行 seek 的人被抢走控制条）。 */
internal const val IdleImmersiveMs = 5_000L

/** 无操作计时的轮询间隔：远小于 [IdleImmersiveMs]，触发误差可忽略。 */
internal const val IdleCheckMs = 500L

/** 手势换档（向上翻歌词进沉浸 / 沉浸到底后继续上滑回内联）要攒够的位移量。 */
internal val ImmersivePullDp = 72.dp

/** 沉浸进/出的转场时长：与壳的 `Motion.ShellCloseMs` 同量级 —— 沉浸切换是「同一页换形态」，
 *  比浮层显隐（`Motion.MicroMs`）重、比页面导航轻。退场减半：让位要利索（同
 *  `Motion.NavExitMs` 取向）。 */
internal const val ImmersiveMs = 360
internal const val ImmersiveExitMs = 180

/**
 * 内联 ⇄ 沉浸的换档手势：全部走嵌套滚动**旁路观察**，不消费任何位移。
 *
 * - 内联档「向上翻动歌词」（用户：「向下翻动歌词进入沉浸」—— 指往歌曲后面翻，手指上推、
 *   列表向上滚）累计 [ImmersivePullDp] → 沉浸。判定放在 `onPreScroll`，列表自己照常吃
 *   完这段滚动，所以歌词跟手、不会出现「换档期间滑动被禁用」。2026-10-24 修正：此前用
 *   独立 pointerInput 判「手指下滑」，方向反了，且为了独占手势把内联列表设成不可滚 ——
 *   两个问题一起改掉。
 * - 沉浸档：列表滚到底后多余的「上滑」冒到 `onPostScroll`，攒满即退回内联。
 * - 刚进入沉浸、或刚在底部退出沉浸的那一手都不判反向切换，否则同一根手指继续推会让
 *   底部来回打摆。
 *
 * @param stage 读当前档位。传 λ（而不是值）是刻意的：本实例被 `remember` 住、跨重组存活，
 *   每次判定都要拿**最新**的档位，不能固化在创建时的那一份。
 */
internal class LyricsScrollConnector(
    density: Density,
    private val stage: () -> LyricsStage,
    private val onEnterImmersive: () -> Unit,
    private val onExitImmersive: () -> Unit,
) : NestedScrollConnection {
    private var acc = 0f
    private var justEntered = false
    private var justExited = false
    private val threshold = with(density) { ImmersivePullDp.toPx() }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (stage() != LyricsStage.Inline) return Offset.Zero
        // 刚在底部退出沉浸的那一手不再判进入：否则同一根手指继续上推会立刻又弹回
        // 沉浸，底部来回打摆（只是「不要再进」，列表照样滚）。
        if (justExited) return Offset.Zero
        if (available.y < 0f) {
            if (acc > 0f) acc = 0f
            acc += available.y
            if (acc <= -threshold) {
                acc = 0f
                justEntered = true
                onEnterImmersive()
            }
        } else if (available.y > 0f) {
            acc = 0f
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (stage() != LyricsStage.Immersive || justEntered) return Offset.Zero
        if (available.y < 0f) {
            if (acc > 0f) acc = 0f
            acc += available.y
            if (acc <= -threshold) {
                acc = 0f
                justExited = true
                onExitImmersive()
            }
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        acc = 0f
        justEntered = false
        justExited = false
        return Velocity.Zero
    }
}
