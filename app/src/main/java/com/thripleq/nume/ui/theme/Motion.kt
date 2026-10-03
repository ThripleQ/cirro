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
    /**
     * **卡片→全屏 / 全屏→卡片**这一段落档的「先加速、后减速」专用曲线。
     *
     * ## 为什么需要它，而不是沿用 [SheetSettle]
     * Compose 默认松手落档内部是 `NoOpDecayAnimationSpec`（完全没有甩动惯性）+ 一段
     * `FastOutSlowIn`（**只有减速、没有加速**）——所以用户松手后壳只有"贴上去"的减速感，
     * 永远读不出「先冲出去、再稳收住」。这条曲线是**中心对称的缓入缓出**（两端斜率趋平、
     * 中段最陡），在**静态动画**里就能做出先加速再减速；配合从当前偏移起跑，甩出去那一下
     * 也有惯性延续。
     *
     * 时长随「本次剩余行程占整段的比例」缩放：[SheetFullMinMs] 起、[SheetFullMaxMs] 封顶——
     * 松手点离目标越远跑越久（从容读出动线），越近也不至于缩到把加速减速抹没（仍保最少时长）。
     *
     * @param remain 本次落档剩余行程占「卡片↔全屏」整段的比例（0..1，松手点越靠近卡片档越小）。
     */
    fun sheetFullFrom(remain: Float): AnimationSpec<Float> = tween(
        durationMillis = (
            SheetFullMinMs +
                (SheetFullMaxMs - SheetFullMinMs) * remain.coerceIn(0f, 1f)
            ).roundToInt(),
        easing = SheetFullEase,
    )

    /** 卡片↔全屏落档：中心对称缓入缓出——起步沉、中段最快、末端稳稳收住（先加速再减速）。 */
    private val SheetFullEase = CubicBezierEasing(0.33f, 0f, 0.33f, 1f)

    /** 卡片↔全屏落档时长的下限：再近的松手点也要跑满它，否则加速减速读不出来。 */
    const val SheetFullMinMs = 320

    /** 卡片↔全屏落档时长的上限（松手点接近卡片档、行程最长时）。 */
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

    /**
     * 末端阻力（层 4）：接近全屏时把 τ 放大 [DragFollowStickGain] 倍，壳变「稠」——
     * 手指继续走、壳越走越慢，到顶前像被压住；反向（从全屏往下拉）同样先较一下劲才动，
     * 读成静摩擦。
     *
     * **只改 τ、不改端点映射**：跟随器最终一定收敛到 raw，所以「锚点 ↔ 视觉终点」严格对应，
     * 松手后由落位曲线接手把最后这段走完（读成「它自己滑进去」），不存在跳变或回弹发抖的路径。
     */
    const val DragFollowStickGain = 0.8f

    /** 末端阻力起算点：raw 走到全屏行程的该比例之后开始变稠（0.80 = 最后 20%）。
     *  且增长曲线取**平方**（见 `PlayerDockState.step`）—— 前一半几乎感觉不到，
     *  最后 10% 才「陡然压住」，与接入权重同一套节奏。 */
    const val DragFollowStickFrom = 0.80f

    /**
     * 滞后上限（progress 单位）：壳最多落在手指后面这么多。
     *
     * **没有它，甩得越快落后越多**（滞后 ≈ 速度 × τ，快甩能到 0.6 p —— 而「卡片→全屏」整段
     * 才 0.34 p，等于手指快到顶了、壳还在分裂段），那是游泳感不是重量；
     * 更糟的是无界滞后会在接入权重渐入处产生**拖动中的倒退**（实测 0.085 p、4 帧），看着像抖了一下。
     *
     * 0.12 p = 该段行程的 35%，也自动给出「慢拖精确、快拖有惯量」：慢拖滞后不饱和（≈0.07）、
     * 精确跟手；正常及以上速度统一钳在 0.12，重量恒定。
     *
     * 注意它与「接入权重」是两个独立旋钮：这里限的是**滞后最多多大**，
     * [DragFollowMixFrom] 限的是**滞后在哪一段才透出来**。
     */
    const val DragFollowMaxLagP = 0.12f

    /**
     * 跟随的接入权重区间（progress 单位）：p<[DragFollowMixFrom] **严格 1:1**，
     * [DragFollowMixFrom]→[DragFollowMixTo] 才渐入到全惯量，之后（[DragFollowMixTo], 2] 满额。
     *
     * **为什么整段压在末端**：这组效果（滞后、内容相位差、阴影速度项）是「展开将要完成」
     * 那一下的收束感 —— 用户 2026-10-03：「启动过程不需要这个过程，末端这些效果应该陡然增强，
     * 在这之前不能太明显」。1.0→1.76 是纯粹的跟手位移（壳与手指严格同相，一点延迟都没有），
     * 惯量只在最后一档行程里出现。
     *
     * **为什么终点顶到 1.99**：壳的两条边行程不等 —— 顶边走 `travelPx*(2-p)`（与手指 1:1），
     * 底边走 `dockHeight + gap`（≈504px，而 1.66→2 这段手指只走 ≈388px，即 1.3 倍速）。那个「底边软」
     * 的用户反馈（2026-10-03：「底边有很长一段处于效果陡增后的阶段」）由两件事一起解决：
     * ① 底边提前到位（见 `BOTTOM_FILL_SPAN`）；② 窗口终点推到 1.99 —— 陡增一结束就几乎到顶，
     * 「陡增之后」不再留下一截给底边走。
     *
     * 窗口**不能更窄**：滞后是靠跟随器在窗口内积累起来的（≈τ 量级），窗口短于 τ 就来不及建立，
     * 效果反而变弱（实测：窗口从 0.34 档收窄到 0.23 档，峰值滞后 0.12 p → 0.077 p）。
     * 觉得重量感不够时先放宽 [DragFollowMixFrom] 而不是加大滞后上限 —— 后者的代价是效果提前出现。
     *
     * 用混合而不是硬切：硬切会在交界处出现「跟手性突变」，读成掉帧。
     */
    const val DragFollowMixFrom = 1.76f
    const val DragFollowMixTo = 1.99f

    /**
     * 接入权重的曲线指数：[smoothstep] 之后再取它次幂 —— **越大越「陡」**
     * （1 = 原本的 S 曲线，中点 0.5；2 = 中点 0.25，前半段几乎为 0、后半段猛拉）。
     *
     * 这就是「陡然增强」这个手感的总闸：效果出现的时机与陡峭程度都在这一条曲线上。
     */
    const val DragFollowMixEase = 2f

    /** 内容跟随的时间常数（比壳慢 → 壳先到位、内容后到，相位差读出层次）。 */
    const val ContentFollowMs = 180

    /** 内容跟随的阻尼比：<1 故意欠阻尼 —— 手指停住或松手时内容会荡一下再稳（余振=质量）。 */
    const val ContentFollowDamping = 0.75f

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

    /**
     * 落位到全屏时**冲过锚点的比例**（相对「卡片→全屏」段长）——「重物停下来」那一下回弹。
     *
     * 为什么过冲要落在这条曲线上、而不是跟随器里：跟随器是平滑跟随，输入本身没有过冲时
     * 输出也不会有过冲（实测只有 0.06%，等于看不见）。要看得见的回弹，输入就必须真的越过
     * 终点一次，而「越过终点」只能由落位曲线提供。
     *
     * 位置**看不见**这次过冲（`progress` 被钳在 2，壳不会真的被拉出屏幕），
     * 透出来的是**内容档位**：封面/字号越过全屏比例约 1% 再收回 —— 那才是这一下回弹的观感。
     */
    const val SheetFullOvershoot = 0.012f

    /** 过冲后收回所用时长：短到读成「稳住」，长到不读成抖动。 */
    const val SheetFullReturnMs = 140

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
