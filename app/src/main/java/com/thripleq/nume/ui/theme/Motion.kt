package com.thripleq.nume.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * 全局动效令牌 —— 伸展壳（`ExpandableShell`）与 dock（`PlayerDock`）**共用同一套**
 * 「时长 + 曲线 + 形状」词汇。相邻的两处开合动画必须读这里，不再各写各的魔数，
 * 否则并排看就是「两个不同 App 的手感」。
 *
 * ## 为什么是这几条曲线
 * 目标手感是 iOS 级「同一个物体在连贯开合」：
 * - 展开用 **Emphasized Decelerate**：出闸果断、尾巴长而平——物体"自己冲出去再滑停"，
 *   比 `FastOutSlowIn` 更有质量感，也不会匀速到显得死板。
 * - 收起用 **Emphasized**：起步利索、**末段减速停稳**。绝不能用 accelerate 系收尾，
 *   那会让壳「啪」一下撞回卡片位置——正是之前收尾难看的直接原因。
 */
object Motion {

    // ── 曲线族（Material3 Emphasized 家族） ──────────────────────────
    /** 展开 / 进入：快速起步 + 长减速尾。 */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 收起 / 归位：利索起步 + 减速停稳（不弹跳、不硬切）。 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 微交互：对称的进出曲线，适合短时长淡入淡出/尺寸伸缩（不与 Emphasized 撞值）。 */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * 浮现收回专用：对称的缓入缓出，把「全屏 → 源矩形」的尺寸变化**均匀铺满整段时长**。
     *
     * 不能用 [Emphasized]（0.2,0,0,1）：它前段极快，收缩的大半发生在头一两帧，
     * 人眼只看到"闪没了"。收回是"物体归位"，需要每一帧都在明显变小才读得出动线。
     */
    val RevealExitEasing = CubicBezierEasing(0.33f, 0f, 0.33f, 1f)

    // ── 壳展开动画实现（试验开关）──────────────────────────────────
    /**
     * 胶囊壳展开动画用哪套实现：
     * - `false` = 自研 [ExpandableShell]/[CoverExpandShell]（壳矩形由单一 progress 逐帧插值 +
     *   hero 封面交接）。旧实现，代码整体保留，不删。
     * - `true`  = 官方 `SharedTransitionLayout` + `Modifier.sharedBounds` 容器变换
     *   （`ui/components/SharedShell.kt`）：源卡片表面由框架 morph 成全屏面板。
     *
     * 编译期常量，分支不随重组变化。随时可切回 `false` 对比/回退。
     */
    const val SharedShellEnabled = true

    /**
     * 共享元素版面板的淡入/淡出（<自研壳的 [ShellOpenMs]/[ShellCloseMs]）。
     *
     * 自研壳是「表面自己在长」，配 420ms。共享元素版里**封面 morph 才是主角**（350ms），
     * 面板只负责铺底——它若也跑 420ms，中段就成了「封面在飞 + 面板慢慢显」的交叉淡化，
     * 读着散。缩短面板淡入、让封面位移主导，才聚。
     */
    const val ShellPanelInMs = 260
    const val ShellPanelOutMs = 200

    /**
     * 共享封面的内容交叉时长：源封面淡出 / 目标封面淡入。
     *
     * 封面两端的**内容不同**（卡片只有图，banner 还带名字/元信息），故必须走
     * `sharedBounds` 的 enter/exit 交叉——不能用 `sharedElement`（它假设两端内容完全一致，
     * 且在 overlay 里每帧把内容重排到插值尺寸，banner 的文字会逐帧换行抖动、掉帧）。
     */
    const val ShellCoverFadeMs = 200

    // ── 时长族（ms） ────────────────────────────────────────────────
    /** 壳展开总时长。 */
    const val ShellOpenMs = 420

    /** 壳收起总时长：短于展开，符合"回原处比去新地方快"的预期。 */
    const val ShellCloseMs = 320

    /** 微交互统一时长：dock 内浮层显隐用它，不再 180/200 混用导致成对动画不同步。 */
    const val MicroMs = 200

    // ── 形状族 ──────────────────────────────────────────────────────
    /**
     * 圆角保持"父级卡片真圆角"的时间占比。
     *
     * t < CornerHold 时圆角完全不收敛——此阶段壳仍是卡片比例，圆角必须与父级卡片
     * 逐像素一致（这是「与父级内容完美衔接」契约的一部分）；越过该点后 smoothstep
     * 平滑收敛到 0，既不提前变形，也不在末段"啪"地变方。
     *
     * 取 0.85（原为 0.55）：壳在「明显还是个小于全屏的窗口」的整段都保持圆角，只有临近
     * 铺满的最后 15% 才归零——否则后程圆角早早消失、窗口看着很尖锐。
     */
    const val CornerHold = 0.85f

    /**
     * hero 低清封面与内容里高清 banner 的交接阈值。
     *
     * 取 1（壳完全展开）而非更早：交接时内容封面要在**与 hero 像素对齐后**才变为不透明，
     * 若在 0.98 等壳尚未长到终态时交接，内容封面会比 hero 偏下若干 px、露出边缘。
     */
    const val HeroHandoffAt = 1f

    /** 交接淡出时长。 */
    const val HeroFadeMs = 220

    /** hero 封面运行时模糊的峰值半径（px）。 */
    const val HeroBlurMaxPx = 28f

    /** hero 模糊升到峰值所占的进度比例：之后一路衰减回 0。 */
    const val HeroBlurRise = 0.25f

    /**
     * hero 封面的运行时模糊半径（px）随展开进度 t——「拉开时轻微失焦、落定前重新合焦」。
     *
     * - `t=0` 为 0：首帧必须与起点卡片逐像素吻合，不能是糊的。
     * - 前 [HeroBlurRise] 段快速升到 [HeroBlurMaxPx]：此时图层还小、模糊最便宜。
     * - 之后 smoothstep 衰减，`t=1` 精确归零：交接前已清晰，与内容里的高清封面同形，
     *   淡化不可见；且全屏（图层最大）那一刻半径已≈0，调用方可直接摘掉 effect。
     */
    fun heroBlurPx(t: Float): Float {
        fun smooth(x: Float) = x * x * (3f - 2f * x)
        val rise = smooth((t / HeroBlurRise).coerceIn(0f, 1f))
        val fall = smooth((1f - t).coerceIn(0f, 1f))
        return HeroBlurMaxPx * rise * fall
    }

    /**
     * hero 文本（名字/元信息）淡出/淡入的进度窗口。
     *
     * 展开时窗口宽（[HeroTextFadeOutAt]）：壳一开始长大就让文本淡走，观感自然。
     * 收起时窗口窄（[HeroTextFadeInAt]）：hero 缩回过程中全程不放文本，直到**几乎等于卡片大小**
     * 才淡入——否则文本随盒子逐帧重排、换行/省略号来回移动，看着"跳（自动截断）"。
     */
    const val HeroTextFadeOutAt = 0.18f
    const val HeroTextFadeInAt = 0.06f

    /**
     * hero 文本透明度随进度 t：两端为 1（p=0 与卡片、p=1 与内容封面逐项吻合），中间为 0，
     * 避免 hero 盒子逐帧缩放时文本被每帧重新排版而"跳动"。
     *
     * @param closing 收起方向用更窄的窗口：只在末尾、盒子已≈卡片大小时才淡入，收起全程看不到重排。
     */
    fun heroTextAlpha(t: Float, closing: Boolean): Float {
        fun smooth(x: Float) = x * x * (3f - 2f * x)
        val window = if (closing) HeroTextFadeInAt else HeroTextFadeOutAt
        return 1f - smooth((t / window).coerceIn(0f, 1f))
    }

    // ── 壳动画 spec ─────────────────────────────────────────────────
    /** 壳展开（点击进入全屏）。 */
    fun shellOpen(): AnimationSpec<Float> =
        tween(ShellOpenMs, easing = EmphasizedDecelerate)

    /** 壳收起（回原处）。 */
    fun shellClose(): AnimationSpec<Float> =
        tween(ShellCloseMs, easing = Emphasized)

    /**
     * 跟手松手后的续跑 spec：时长按**剩余距离**等比给。
     *
     * 固定时长是错的——已经跟手拉到 0.9 再跑完整 300ms 会显得拖沓，
     * 只拉到 0.1 就只剩 300ms 又显得 rushed。手已停（零初速），故仍用减速曲线。
     */
    fun shellResume(from: Float): AnimationSpec<Float> {
        val remain = (1f - from.coerceIn(0f, 1f))
        return tween(
            delayMillis = 0,
            durationMillis = (ShellOpenMs * remain).roundToInt().coerceAtLeast(90),
            easing = EmphasizedDecelerate,
        )
    }

    // ── dock 档位 spec ──────────────────────────────────────────────
    /** 档位吸附 / 收起：干净无回弹。 */
    val SheetSettle: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // ── 卡片 ↔ 全屏 落档（松手甩动）───────────────────────────────
    // 曲线本身在 `ui/playerbar/SettleWithVelocity.kt`：**继承松手速度**的单调减速。这里只放
    // 它的时长参数。
    //
    // 为什么不能用 `tween`（用了一版，真机翻车）：`tween` **忽略 `initialVelocity`** —— 无论手指
    // 以多快的速度离开屏幕，曲线都从速度 0 起步。于是松手瞬间出现速度断崖：手指还在 2393px/s
    // 地滑、壳忽然停住（用户读作「突然受到很大阻力」），随后曲线中段又爬到峰值（「又重新加速」），
    // 末端再归零。真机日志实锤：`fling v=2393`，而落位段第一帧位移近乎为 0。用户 2026-10-03：
    // 「违反常识」。落位曲线必须自带速度继承，所以改成 [SettleWithVelocity]。

    /**
     * 落档时长的**基准**下界：按「剩余行程占整段的比例」缩放后不低于它，再近的松手点也要跑满，
     * 否则加速减速读不出来。
     *
     * 注意松手**快**时实际时长会比基准更短 —— 由物理匀减速 `2Δx / v0` 给出（见 [SettleWithVelocity]），
     * 那时这两个常量只是上限。
     */
    const val SheetFullMinMs = 320

    /** 落档时长基准的上限（松手点接近卡片档、行程最长时）。 */
    const val SheetFullMaxMs = 540

    // ── 导航转场 ────────────────────────────────────────────────────
    /**
     * 页面级转场时长：比 [MicroMs] 略长（页面位移行程长于浮层显隐），同时明显短于
     * 壳动画（[ShellOpenMs]）——导航是"换地方"，壳是"同一个物体变形"，后者才配 400ms 级。
     */
    const val NavEnterMs = 220

    /**
     * 退出让位时长：被覆盖的旧页快速淡走，把屏幕交给进来的新页；90ms 内曲线差异
     * 不可感知，用 [Standard] 即可。
     */
    const val NavExitMs = 90

    /** pop 收回：比进入慢于让位、快于进入——"跟手返回"要能看见页面滑走。 */
    const val NavPopExitMs = 160

    // ── 共享元素 ────────────────────────────────────────────────────
    /**
     * sharedElement/sharedBounds 的 boundsTransform：与壳展开同族的 [Emphasized]——
     * 共享元素位移本质也是"同一个物体在动"，弹簧默认值（StiffnessMediumLow）位移
     * 尾巴过长、跨页时看着拖。350ms 覆盖典型跨页行 程，且与 [ShellOpenMs] 同量级。
     */
    fun sharedBoundsSpec(): FiniteAnimationSpec<Rect> =
        tween(350, easing = Emphasized)

    // ── 内容编排（choreography）────────────────────────────────────
    /**
     * 展开期内容编排窗口：壳长到 [ContentRiseFrom] 前内容保持静止（被裁剪窗口藏着），
     * 之后从 [ContentRiseFrom]→[ContentRiseTo] 区间做 12dp 上浮——**滞后于壳**的位移
     * 制造"壳先就位、内容流入"的层次，这是编排感（choreography）与机械同步的分界。
     */
    const val ContentRiseFrom = 0.62f
    const val ContentRiseTo = 0.92f

    /** 内容流入位移量（dp）。大到能读出"流入"、小到不与裁剪露出的内容打架。 */
    const val ContentRiseDp = 12

    /** 收起时内容的下拖量（dp）：与淡出同轴，内容像被"吸回"卡片。 */
    const val ContentDragDp = 6

    /** scrim 暗度曲线：EmphasizedDecelerate 前快后慢——前 20% 进度即建立大半暗度，
     *  modal 焦点在动画一启动就成立（线性 t 在前段太"迟疑"）。 */
    fun scrimT(t: Float): Float = EmphasizedDecelerate.transform(t.coerceIn(0f, 1f))



    /** 点击直达全屏：低阻尼带一点弹性，让"生长"过程看得见。 */
    val SheetExpand: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 点按展开（点迷你条 → 全屏）的时长，配 [EmphasizedDecelerate]。
     *
     * 与 [SheetExpand] 的分工：那条是把 offset 从当前位置弹到某个锚点的落档曲线（spring），
     * 行程不定、尾段收敛慢；点按走的是一条**固定行程的专用时间轴**
     * （见 `PlayerDockState.openByTap`）—— tween 才能让全程可控、落位干净。
     * spring 跑长距离那条指数尾巴，正是此前「点一下要等它慢慢蹭到位」的来源。
     *
     * 420ms：略短于手势落档区间（[SheetFullMinMs]–[SheetFullMaxMs]）—— 点按是「我要看播放页」
     * 的明确指令，起手要快；[EmphasizedDecelerate] 前段走掉大半行程，观感上更快。
     */
    const val TapExpandMs = 420

    // ── 卡片 ↔ 全屏：手动拖动的手感（「质量感」= 这一段不严格跟手）──────────────
    // 拖动路径下 progress 不再等于手指位移：壳用一阶低通追 raw、内容用更慢的欠阻尼二阶追
    // raw（见 `PlayerDockState.runFollowLoop`）。下面这组数是**唯一**要真机调的东西。
    //
    // 之所以要「不跟手」：纯线性 1:1 读起来是「UI 在等比缩放」，不是一个有重量的东西被抬起来。
    // 惯量（滞后 + 追上来的尾巴）才是「重」；而滞后一旦过量就读成「卡」，所以这几个数要小步调。

    /** 壳跟随的时间常数：上行（卡片→全屏）。越大越「重」，过大就成拖沓。 */
    const val DragFollowRiseMs = 110

    /** 壳跟随的时间常数：下行（全屏→卡片）。**比上行小** —— 落比抬干脆，
     *  抬起来要使劲、掉下去快，这一条非对称就是「重力感」的主要来源。 */
    const val DragFollowFallMs = 80

    // ── 末端阻尼：一个窗口，两条边各自的倍数 ──────────────────────────────────────
    //
    // 「卡片 → 全屏」最后一段的收束感全部来自这里，**只有三个数**（[DockEndFrom] /
    // [DockEndTop] / [DockEndBottom]）。窗口之外 τ→0（严格 1:1 跟手），窗口之内从 0 渐增
    // 到满额 —— 用户 2026-10-03：「启动过程不需要这个过程，末端这些效果应该陡然增强，
    // 在这之前不能太明显」。
    //
    // 做法就一句话：**越靠近全屏，每条边的 τ 越大**。τ 一大，那条边就跟不上手指、攒出滞后
    // （这就是「阻尼」），而手指一停它自己就收敛 —— 不需要任何「什么时候该放开」的额外
    // 判断，这正是它只要三个数的原因。（早先试过的「位移压缩」改的是目标位置，于是必须
    // 再养一套「什么时候松开压缩」的机器，参数翻倍、还踩了三个坑，已删。）
    //
    // 上下边分开给数，是因为两条边的行程天生不等 —— 理由见 [DockEndTop]。
    //
    // 【窗口长度就是落差的物理上限】一条边在窗口里最多落后「它本应在窗口里走完的行程」：
    // τ 再大也只是就地冻住，落后量不可能超过窗口长度。所以「更末端」与「落差更大」是同一枚
    // 硬币的两面 —— 要求「最后 1/64 屏才刹车」，落差就只能是 1/64 屏的量级（37px 上下）。
    // 想让落差更大，唯一的旋钮是把 [DockEndFrom] 往前挪，兑换率见它的表。

    /**
     * 末端阻尼的**起点**（raw 单位，2 = 盖满全屏）。
     *
     * 它是**唯一**一个窗口：`[DockEndFrom], 2]` 之内 τ 从 0 渐增到满额，所以这一档之前是
     * 严格的 1:1 跟手（两条边与手指逐像素同相，一点延迟都没有）。
     *
     * 取 **1.976** = 窗口内底边行程 **37.5px = 屏高的 1/64**（2400/64；底边 1572px/档）。
     * 用户 2026-10-05：「只要下边极端一些，在最后一点点，一点点就屏幕长的 1/64，急刹车」。
     *
     * 【决定「多末端」的是起点、决定「多急」的是窗口有多短】—— 这与先前三次「再缩 50%」
     * 拧的方向不同：那三次改的是窗口**之内**的曲线形状（幂次 7→15→30），窗口本身没动，
     * 于是「刹车起点」一直在 raw 1.88 没变过，用户三次都说「没什么变化」。
     *
     * 复算脚本 `brake_check.py`（K40 口径、上边 0、下边 20、匀速 v=1.2 档/秒。「刹车距离」=
     * 底边速度比从 80% 掉到 10% 之间走过的 px，越小越像急刹）：
     *
     * | 起点 | 窗口（底边行程） | 底边落差 | 刹车距离 | 窗口过半时底边速度比 |
     * |---|---|---|---|---|
     * | 1.88（旧） | 189px | 142px | 110px（一路发黏地爬） | 14% |
     * | 1.9237 | 120px | 108px | 16px | 2.5% |
     * | 1.9523 | 75px（1/32 屏） | 74px | 16px | 1.3% |
     * | **1.976（现）** | **37.5px（1/64 屏）** | **41px** | **16px** | **1.5%** |
     * | 1.9822 | 28px | 31px | — | 1.1% |
     *
     * 读法：换到 1.976 之后**刹车距离（110px → 16px）塌了 7 倍**，16px ≈ 一帧、已经是帧粒度
     * 的下限（也就是说「最后 37px 里从全速直接钉住」）；代价是落差只剩 41px（旧的 1/3.5）。
     * 这张表就是上面那句话的量化：**窗口多长，落差最大就是多少**。嫌落差不够，把起点往前
     * 挪一档即可（1.9523 = 1/32 屏 → 74px；1.9237 = 1/16 屏 → 108px）。
     */
    const val DockEndFrom = 1.976f

    /**
     * **上边**（壳顶 / 左右 / 圆角 / 底色 / dock 淡出）的阻尼倍数：
     * 窗口内 τ 的末值 = 基础 τ × (1 + 它)。
     *
     * 0（现） = 上边不吃末端阻尼。
     *
     * ## 为什么上下边要分开给数
     *
     * 两条边的行程不一样，而且是几何决定的、改不掉。窗口挪到 1.976 之后这个差距变成了
     * 「有 / 没有」：窗口内**上边只剩不到 2px 可走**（`dockTop*(1-p/2)*(1-t1)` 两段同向
     * 相乘，p→2 时导数 →0 —— 它早就钉在屏顶了），而下边还有 37.5px。所以同一个倍数落到上边
     * 身上是**亚像素**级的 —— 用户 2026-10-05「注意我说的只有下边哦」在几何上自动成立，
     * 这个旋钮留着只是为了让「想给上边也加一点」时不必改结构（那就得把窗口一起往前挪）。
     *
     * （旧窗口 1.88 时上边还能走 40px，那一版的对照是：0 → 10.0px 滞后、6 → 22.8px、12 → 26.1px。）
     */
    const val DockEndTop = 0f

    /**
     * **下边**的阻尼倍数 —— 这一个数就是用户说的「阻力」。窗口内 τ 被放大到 (1+它) 倍，
     * 底边于是跟不上手指：20 对应窗口里实际速度比 1% 上下，读成**钉住**。
     *
     * 复算（脚本 `brake_check.py`，起点 1.976、上边 0、v=1.2 档/秒）：
     *
     * | 增益 | 下边落差 | 窗口过半时速度比 |
     * |---|---|---|
     * | 0 | 31.8px | 15.4% |
     * | 6（旧值） | 37.8px | 3.7% |
     * | 12 | 39.6px | 2.2% |
     * | **20（现）** | **41.1px** | **1.5%** |
     * | 40 | 43.0px | 0.8% |
     *
     * 收益已经饱和（6 之后每翻一倍只剩一两像素、速度比却只从 3.7% 掉到 1.5%），因为**落差被
     * 窗口长度兜住了**（见 [DockEndFrom] 那段）。它只决定「钉得多死」，20 之后 1.5% 与 0.8%
     * 已经读不出差别 —— 真嫌不够狠，该动的是窗口起点而不是这里。
     *
     * **这条机制比旧的 τ 放大稳得多**：落差几乎不随手速变（v=0.5 → 34.2px、1.2 → 41.1px、
     * 3.0 → 39.2px，都贴着窗口长度），而旧版同一段拖动慢拖速度比 18.5%、快甩 29.6%，
     * 「变沉的位置」会随手速漂。
     */
    const val DockEndBottom = 20f

    /**
     * 滞后上限（progress 单位）：一条边最多落在手指后面这么多。
     *
     * **没有它，甩得越快落后越多**（滞后 ≈ 速度 × τ，快甩能到 0.6 p —— 整段「卡片→全屏」
     * 才 0.34 p，等于手指快到顶了、壳还在分裂段），那是游泳感不是重量；更糟的是无界滞后会
     * 在窗口渐入处产生**拖动中的倒退**（实测 0.085 p、4 帧），看着像抖了一下。
     *
     * 0.12 p 落到下边 ≈ **189px**、上边 ≈ 114px。
     *
     * 【2026-10-05 起它基本退休了】窗口挪到 1.976（1/64 屏）之后，阻尼能造成的落差最多 37px，
     * 离 189px 差得远 —— **现在真正兜住落差的是窗口长度，不是这个上限**。留着它只为防
     * 「窗口又挪早 / 增益又调大」时突然冒出无界滞后（快甩 0.6 p 那种游泳感）。
     * 注意口径：想靠抬这个数把落差做大是没用的 —— 它只会在越界时把阻尼**整个废掉**
     * （一被钳住，那条边就恢复 1:1 前进，速度差当场消失）。
     */
    const val DragFollowMaxLagP = 0.12f

    /**
     * 松手落位期间的时间常数（比 [DragFollowRiseMs] 小得多）：壳迅速贴住落位轨迹。
     *
     * **为什么必须单独给一个**：落位动画（`SheetFlingBehavior.performFling`）跑的那条曲线
     * （`SettleWithVelocity`，继承了松手速度）**本身就是最终轨迹**，不该再被跟随器滤一遍。
     * 滤一遍的后果是滞后一路累积（回放脚本复现：残余 65.8px），等它跑完 raw 冻住，
     * 壳才开始追一个静止目标，要 **983ms** 才挪完 —— 与真机逐帧实测（约 51px / 900ms）吻合。
     * 那截「本该已经结束、画面还在动」的蠕行就是拖尾巴的感觉。
     *
     * 30ms（且落位期间不施末端阻尼，见 `PlayerDockState.step` 里的 `damp`）的效果：松手瞬间的
     * 滞后在 ~100ms 内抹平，速度与手指同量级（≈1000px/s），读成「被带住」而不是顿一下；
     * 抹平后壳紧贴轨迹走完，终点只剩 **4.9px / 75ms** 的收尾。
     */
    const val DragFollowReleaseMs = 30

    /** 内容跟随的时间常数（比壳慢 → 壳先到位、内容后到，相位差读出层次）。 */
    const val ContentFollowMs = 180

    /**
     * 内容跟随的阻尼比：<1 欠阻尼。
     *
     * 取 0.9（原 0.75）：0.75 的超调量 ≈2.8%，在 `sc` 上是 **2.8% 的缩放摆动、约 2.9Hz** ——
     * 静止画面上看就是封面/字号来回晃，用户 2026-10-03 报成「颤动」。0.9 的超调降到 0.15%，
     * 肉眼不可见，而**相位滞后（层次感的真正来源）基本不变** —— 壳先到位、内容后到靠的是
     * [ContentFollowMs]，不是靠余振。
     */
    const val ContentFollowDamping = 0.9f

    /**
     * 内容档位 `sc` 的上限：允许越过 1 一点，让上面那条余振**看得见**（封面/字号略微过冲再收回）。
     *
     * 为什么过冲做在内容层而不是壳的位置上：全屏锚点就是「盖满屏幕」，位置越过它没有画面
     * （`progress` 也被钳在 2），只有内容比例还有过冲的余地。
     */
    const val ContentOvershootMax = 1.03f

    /** 速度耦合的壳阴影上限（dp）：拖得越快壳越「沉」，甩起来的东西有风；停下即归零。 */
    const val ShellShadowSpeedDp = 6f

    /** 阴影速度项的参考速度（progress 单位/秒）：到这里取满 [ShellShadowSpeedDp]。 */
    const val ShellShadowSpeedRef = 8f

    // 注：「落位时冲过全屏锚点再收回」曾以 SheetFullOvershoot / SheetFullReturnMs 做过，
    // 但 `AnchoredDraggableState.offset` 被钳在锚点范围内 —— 超出 Full 的正 delta 全被吞掉、
    // 负 delta 照常生效，于是两段式实际变成「先到顶 → 再往下缩一段 → 停在没到顶的位置」：
    // 既抖一下、终点还差约 10px（用户 2026-10-03 报的「惯性滑行时候会颤动」之一）。
    // 已在 2026-10-03 删除。回弹若要重做，必须走**不经过 offset** 的通道（内容档位）。

    /**
     * 圆角收敛系数：`1` = 保持父级真圆角，`0` = 完全收敛。
     *
     * 前 [hold] 段恒为 1，之后以 smoothstep 落到 0——保证 p=0 与父级卡片像素级吻合，
     * 同时消除末段线性归零带来的"啪"感。
     */
    fun cornerTaper(t: Float, hold: Float = CornerHold): Float {
        if (t >= 1f) return 0f
        if (t <= hold) return 1f
        val k = (t - hold) / (1f - hold)
        return 1f - k * k * (3f - 2f * k)
    }

    // ── Origin-Reveal（从点击处浮现）──────────────────────────────────
    // 与胶囊展开（`PlayerDock` 的 container-morph，**仅限播放器**）不同：
    // 本族是**通用**的 origin 浮现——任意被点对象矩形 → 全屏，靠「边界羽化」
    // 免去逐像素起点对齐的硬契约。见 `ui/components/RevealTransition.kt`。
    //
    // ⚠️ 当前**关闭**：这套浮现转场先不启用，全部详情页/评论浮层回到常规导航转场
    // （NavHost 的 fade+slide）。代码整体保留，改这一个开关即可重新启用。
    // 关闭时：`isRevealTarget()` 恒 false → 走常规转场；`RevealLayer` 直接透传内容
    // （不再叠离屏层/羽化/模糊）；浮层进度恒 1（立即呈现）。开关为编译期常量，
    // 分支不随重组变化。
    const val RevealEnabled = false

    /** 进入时长。 */
    const val RevealEnterMs = 360

    /**
     * 收起时长。曾用 280（"回原处比去新地方快"），但配合 Emphasized 的前段极快，
     * 收缩大半在头 ~80ms 内就跑完了，观感成了"啪一下消失、看不出收回动画"。
     * 放慢到与进入同量级，让整段收拢过程可被看见。
     */
    const val RevealExitMs = 420

    /**
     * 进入前的「起手延迟」：重内容页（评论列表、详情页）首次组合/measure 会吃掉一两帧，
     * 若进度此刻已在跑，**第一个画出来的帧就已长大**，看着不贴着被点对象。延迟这段时间
     * 让窗口先以 p=0 精确停在对象上（此态透明度为 0，只露出源页），内容就绪后再开始长。
     */
    const val RevealArmDelayMs = 48

    /** 起点圆角（dp）：t=1 时收敛到 0（全屏硬边）。 */
    const val RevealCornerDp = 22f

    /** 羽化边界峰值宽度（px）：t=0 最宽，t=1 归零。 */
    const val RevealFeatherMaxPx = 14f

    /** 失焦峰值半径（px）：起点最强（软团），落定/收回终点前归零。 */
    const val RevealBlurMaxPx = 7f

    /** 进入 spec（起手延迟让内容先就绪 + 中段起步，避免窗口在一帧内就冲到快满屏）。 */
    fun revealEnter(): FiniteAnimationSpec<Float> =
        tween(RevealEnterMs, delayMillis = RevealArmDelayMs, easing = Standard)

    /** 收起 spec（对称缓出，全程持续收缩、看得见收回动线）。 */
    fun revealExit(): FiniteAnimationSpec<Float> =
        tween(RevealExitMs, easing = RevealExitEasing)

    /**
     * 羽化宽度随进度 t：t=0 最宽、t=1 归零。**t=1 必须为 0**，
     * 否则满屏静止态内容四周会持续发虚。
     */
    fun revealFeatherPx(t: Float): Float = RevealFeatherMaxPx * (1f - t.coerceIn(0f, 1f))

    /**
     * 失焦半径随进度 t：**t=0 最强**（小窗口贴着对象出现时是软的一团，盖住"缩放后
     * 内容与对象像素不同"的突兀）、随展开一路合焦，t=1 精确归零（正常页面）。
     */
    fun revealBlurPx(t: Float): Float {
        fun smooth(x: Float) = x * x * (3f - 2f * x)
        return RevealBlurMaxPx * (1f - smooth(t.coerceIn(0f, 1f)))
    }
}
