package com.thripleq.cirro.ui.screens

import androidx.compose.ui.unit.dp
import com.thripleq.cirro.ui.components.TitleBarCloseInset
import com.thripleq.cirro.ui.components.TitleBarStartInset
import com.thripleq.cirro.ui.theme.CirroShape

/**
 * 歌单页版式令牌 —— 2026-10-03 按官方歌单页实机抄量（1080×2400@480dpi，px÷3 换成 dp）。
 *
 * 内缩统一 16dp（头部 / 「播放全部」/ 曲目行封面同一根竖线，与探索、搜索两页齐平；官方原本
 * 头部 20dp、曲目行 16dp 两档错位，未照抄那一处），方封面 98dp、「播放全部」圆钮 38dp
 * （取整 40dp）、曲目行封面 44dp。曲目行本身仍是 cirro 的条目卡
 * （见 [TrackListRow]）——官方那套紧挨的平铺行只借了封面尺寸与「歌手 - 专辑」这一行信息。
 *
 * [HeroCoverTop] 同时是 [com.thripleq.cirro.ui.components.CoverExpandShell] 的 hero 终点
 * 预测值（封面相对内容顶的偏移）——两边必须同源，改一处就够。
 */
object TrackListMetrics {
    /**
     * 头部 / 面板内缩，也是本屏内容的**唯一**左右边距。
     *
     * 2026-10-03 从 20dp 收到 16dp（用户：「没改的改一下」—— 上一条让标题去看探索页 / 搜索页，
     * 这条要求内容也跟上）。原先照抄官方歌单页「头部 20dp、曲目行 16dp」两档错位，可站内
     * 探索页 / 搜索页的内容内缩**都是 16dp**，本屏夹在中间就那一处对不齐：头部封面与「播放全部」
     * 圆钮比曲目行封面多缩 4dp，同一页里两套竖线。
     *
     * 收成 16dp 后本屏内部三处（头部、[PlayAllRow]、曲目行封面）与站内两页**齐平**，
     * 也与 [RowInset] 同值 —— 两个常量语义不同（见下），只是当前恰好相等。
     */
    val SideInset = 16.dp

    /**
     * 曲目行**封面**距屏幕边：条目卡外缩（[CirroContainer.Inset]）+ 卡内缩，两者相加是这个值。
     *
     * 与 [SideInset] 当前同值但**不是同一个东西**：[SideInset] 是本屏自己加的 padding，
     * 本值要减去卡片自带的内缩才是行内 padding（见 [TrackListRow]），卡片换外缩时必须各自改。
     */
    val RowInset = 16.dp

    /** 头部方封面边长。 */
    val CoverSize = 98.dp

    /**
     * 头部空档的**基准高度**（头部封面落点 [HeroCoverTop] 的口径，不是标题条高度）。
     *
     * 2026-10-05 起标题条两条路径都统一成 [com.thripleq.cirro.ui.components.CirroTitleBarHeight]，
     * 本值不再是「返回栏 / 收起键那一行」的净高；它只作为 [HeroCoverTop] 的基数存在，
     * 保证 nav 与壳两条路径的**头部封面落在同一屏上位置**。真正的空档由
     * `HeroCoverTop - 标题条实测高度` 记在头部 item 自己的上内缩里（见 LazyColumn 的头部 item）。
     */
    val TopBarHeight = 56.dp

    /** 头部与返回栏（壳路径则是壳顶空档）之间的间距。 */
    val HeaderTopGap = 12.dp

    /** 头部封面相对**内容顶**的偏移：壳路径用空档顶替返回栏，两条路径封面落在同一屏上位置。 */
    val HeroCoverTop = TopBarHeight + HeaderTopGap

    /**
     * 面板首行（「播放全部」）吸顶停靠线 = 顶部标题条（`TrackListTitleBar`）的下沿。
     *
     * 2026-10-03 照抄搜索页后标题条**高度跟着字体走**（headlineSmall + 行高裁剪，不再是
     * 固定 56dp）；2026-10-05 与三个 tab 页统一到同一个 [CirroTitleBarHeight]（站内一份）。
     * 停靠线仍由标题条 `onSizeChanged` 运行时测出（stickyTopPx）—— 值现在是确定的，但
     * 让它跟着实测走，将来标题条再改也不会与列表内缩脱节；列表内缩与头部内缩共用该值，
     * 头部封面落点仍是 [HeroCoverTop]，差额记在头部自己的上内缩里。
     */

    /**
     * 标题条文字的起始内缩 —— 与探索页 / 搜索页 / 我的页的大标题左起**逐像素齐平**。
     *
     * 2026-10-03 用户：「标题的内缩去看搜索页面和探索，和他们保持一致」——那几页的标题条都
     * 是 `padding(start = 24.dp)`（[com.thripleq.cirro.ui.components.CirroPageTitleBar]），
     * 本屏原先走 [SideInset]（当时 20dp），比它们少，三页并排看就是不齐。
     *
     * **不复用 [SideInset]，两个值本来就不该相等**：探索页也是「标题 24dp / 内容 16dp」这组
     * 关系（[CirroPageTitleBar] 的 24 与下方列表的 16）。大标题比内容多缩一档、压在内容竖线外
     * 一点，是站内共用的排版关系；把标题拉到 16 反而会与探索页错开。
     *
     * 2026-10-05 起 **nav / 壳两条路径同用本值**（用户：详情页顶部「跟探索页不一样，改成
     * 一样的」）—— nav 路径原先要给左上角的返回键让到 64dp，现在那颗键翻到了右上角。
     *
     * 2026-10-05 晚：这个数不再本地写死，改引 [TitleBarStartInset]（站内一份）。
     */
    val TitleBarTextAligned = TitleBarStartInset

    /**
     * 标题条文字在**右侧**让开右上角那颗 36dp 圆键的那一档：36dp 圆 + 12dp 外边距 + 8dp 缝
     * = 56dp。
     *
     * 与 [TitleBarTextAligned] 是一对 —— 让位是从左边**翻到**右边，不是取消让位：键是浮层、
     * 就压在这一排上，标题再长也不能钻到它下面去（标题是 `maxLines = 1` + Ellipsis，收在键之前）。
     *
     * 两条路径同用：壳路径的键由壳画（[ShellPanel] / `CoverExpandShell`），nav 路径的键由本屏
     * 画（[CirroCloseButton]，同位同规格）—— 键都在右上角，让位方向自然也一致。
     *
     * 2026-10-05 晚：改引站内共用的 [TitleBarCloseInset]，与歌手 / 播客 / 评论三页
     * （[CirroPaperPage]）同一份 —— 那三页的键也从左上翻到了右上角。
     */
    val TitleBarTextEnd = TitleBarCloseInset

    /**
     * 内容圆角纸的顶角半径。
     *
     * **站内一份**：[CirroShape.SheetRadius]（28dp）—— 与探索 / 搜索 / 我的三张纸同源。
     * 详情页从 2026-10-03 起也走「容器色条 + 圆角纸」这套关系（见 [TrackListScreen] 里的
     * 圆角纸层），半径必须同一个值，否则站内几张纸的圆角并排看就是不齐。
     */
    val SheetCorner = CirroShape.SheetRadius

    /** 「播放全部」圆钮直径。 */
    val DiscSize = 40.dp

    /** 曲目行封面边长。 */
    val RowCover = 44.dp
}

/* ── 页底 ──────────────────────────────────────────────────────── */

/**
 * 糊底铺多高（自壳子顶算起）。梯度在这一段里从「封面透出一点」化到全不透明，再往下才是
 * 干净的纸面 —— 官方同款：面板以下是不透明面。
 *
 * ⚠️ 这个值只是**上限**：真正决定糊底在哪里停的是**面板上沿**（见 [TrackListBackdrop] 的
 * `panelTopInRoot`），本值只保证糊底长得够、够到面板。2026-10-03 之前糊底确实一路铺满本值、
 * 盖到面板的头几行上，于是面板行切掉的圆角后面全是封面 —— 那是「切圆角露背景」的真因，
 * 现在由裁切解决，不再靠各行自己铺底去遮。
 *
 * 各行的不透明底（吸顶行 / 曲目行 / 尾部）仍然必要：裁切线以下是壳子的 `surface`，
 * 但条目卡是 `surfaceContainer` 且四周内缩 8dp，那几 dp 的缝里若没有行的 `surface` 底，
 * 露出来的就是壳的纸色 —— 色号不同，会显出一条深色格线。
 */
internal val BackdropHeight = 360.dp

/** 糊底放大倍数：把 `blur` 的软边推到可视带之外，带口就不会看到一圈「糊边」。 */
internal const val BackdropScale = 1.15f

/**
 * 糊底在**面板上沿**之前收口的长度：裁切线之上这最后一段，封面空气化进纸面，
 * 于是「裁」和「化」是同一件事，切口不留硬边。取值只要够盖住蒙版末段的余色即可。
 */
internal val BackdropFade = 28.dp

/* ── 退场编舞的三条腿（2026-10-03） ─────────────────────────────
 * 用户挑了「糊底随滚动化开」，实际要的是**一整套**读起来连贯的退场：糊底在散、在慢、
 * 封面在缩。三条腿挂在**同一个信号源**上（头部滚出进度），比例才咬得住 ——
 * 否则会出现「糊底在化开、封面却硬邦邦地滚走」这种割裂。
 *
 * 三条都只在 draw / layout 阶段读值，滚动时只重绘不重组。
 */

/**
 * 糊底基础模糊半径（头部未滚走时的样子）。
 *
 * 2026-10-03 用户：「模糊强度太大了，减到 60%」—— 原 28dp → **16.8dp**（= 28 × 0.6）。
 * 糊底是低清解码（[BackdropRequestSize]）后放大的，本身就没有细节；原半径在 480dpi 上
 * 是 84px，糊得连色团边界都散了，读起来是「一块塑料」而不是「柔焦的封面」。
 * 收到 60% 后色团的走向还能看见，正是「封面虚化」该有的样子。
 */
internal val BackdropBlur = 16.8.dp

/**
 * 头部滚走时糊底额外「化开」的模糊量：16.8 → 21.6dp，封面越走越散。
 *
 * 也跟着缩到 60%（原 8dp → 4.8dp），保持「行进中的化开幅度」与基础值同比例 ——
 * 只缩基础值、留着原来的增幅，会让滚走后的糊度追平甚至超过改前。
 */
internal val BackdropBlurGrow = 4.8.dp

/**
 * 糊底随退场额外放大的比例：1.0 → 1.05。
 *
 * 与模糊是**互补**的两件事：模糊让边界变软，放大让色团铺得更开。只做前者，画面会
 * 显得「糊在原地」；两者一起，才是封面在视野里一点点化掉。
 *
 * 走 `graphicsLayer` 的缩放（绘制层），不改布局尺寸 —— 每帧改布局尺寸会触发重排。
 */
internal const val BackdropSpread = 0.05f

/**
 * 糊底的**视差系数**：头部滚走 x px，糊底只上移 x × 此值。
 *
 * 取 0.5 = 背景以一半速度跟随前景，这是视差产生深度的经验值。
 * 关键在于它**与头部的实际滚出量成正比**（原来是一个固定 120dp 的终值）：头部高度随字体
 * 缩放 / 屏高变化，固定终值会让视差比在别的设备上跑掉。
 *
 * 头部实测约 174dp（1080×2400 @480dpi、默认字体），滚到底 ×0.5 ≈ 87dp —— 与原来那个
 * 固定值同量级，但现在是**算出来的**。
 */
internal const val BackdropParallax = 0.5f

/**
 * 头部封面随退场**微缩**的比例：滚走时 1.0 → (1 - 此值)。
 *
 * 缩得很少是故意的 —— 多了就成了「有个东西在动」的抢戏；0.06 的幅度只有和滚动同步时才感知得到，
 * 读作「封面被面板吸进去」，而不是「封面在缩放」。
 */
internal const val HeaderExitShrink = 0.06f

/** 微缩同时的下沉量（dp）：小幅度平移，让「被吸进去」有方向感。 */
internal val HeaderExitSink = 4.dp

/**
 * 「播放全部」圆钮身后那团封面色晕的直径 = 圆钮直径 × 此值。
 * 1.4 → 晕直径 56dp：够在圆钮四周留出一圈看得见的颜色，又不会大到读成「一块色斑」。
 */
internal const val PlayAllHaloRatio = 1.4f

/** 色晕圆心处的不透明度。晕不承载文字，可以给到这个量级。 */
internal const val PlayAllHaloCore = 0.55f

/** 头部 item 在列表里的下标：吸顶停靠线与糊底退场都按它判断。 */
internal const val HeaderItemIndex = 0

/**
 * 放大后上下各溢出的量（= 带高 × (倍数-1)/2）。放大若由布局表达（见 [TrackListBackdrop]），
 * 这截**必须**由蒙版一起盖住 —— 它是没过蒙版的原图色，露在可视区里就是一条纯色带。
 */
internal val BackdropBleed = BackdropHeight * ((BackdropScale - 1f) / 2f)

/** 糊底解码尺寸（px）：只要一团色，不要细节。 */
internal const val BackdropRequestSize = 64
