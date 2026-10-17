package com.thripleq.cirro.core.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 开屏的两件事：**什么时候可以不盖了**（[ready]）与**这一进程还有没有演出的名额**（[acquire]）。
 *
 * ## 一、什么叫「App 就绪」
 * 开屏不是装饰，它盖住的是启动期那段「还没做完的事」—— 所以放行判据必须等于一句话：
 * **退场之后，用户看到的那一屏不会再因为「还没做完的事」而自己变样。**
 *
 * 拆成三条互相独立的信号（[Signal]），**全到齐**才放行。逐条的取舍：
 *
 * - [Signal.Content] 首页**第一块**内容落地 —— 说明网络通、页面已经能画。不是「所有请求
 *   都回来」：首页是逐块回填的（见 `HomeViewModel.reload` 的 `publish`），等全部只会白白
 *   把开屏拖长。取数彻底失败（落到 `HomeUiState.Error`）同样算到：那时该给用户看的是
 *   错误页，而不是把错误页藏在红背后。
 * - [Signal.Session] 登录 / 未登录已经定下来 —— 它决定「每日推荐 + 登录引导」这一整块的
 *   有无。未定就放行，开屏退场后首页会凭空长出（或收掉）一块。
 * - [Signal.Playback] 上次会话的播放队列已从磁盘恢复 —— 它决定**底部迷你条有没有**，而迷你条
 *   是一条会挤压整页布局的横条：未定就放行，退场后会看到它突然从底部弹出来。
 *
 * 为什么只列这三条：**判据的代价是开屏的时长**，每加一条都要回答「它会不会让画面自己变样，
 * 而且是那种用户一眼看得出来的变样」。封面渐进加载不算（那本来就是渐进的，与别的区块一样），
 * 定位 / 推送 / 字体这类更不算（它们不改变首屏长什么样）。
 *
 * ## 二、一定配超时
 * 这个闩**只负责如实上报**，不保证一定会被打开 —— 离线时没有任何一方会 [signal]，登录态那
 * 一笔甚至可能是个挂住的网络请求。开屏那边因此用 `Motion.SplashMaxHoldMs` 兜底（**含**动画
 * 本身，是整段开屏的预算，不是「演完再等 3 秒」）—— 宁可早放，也不能把用户锁在红屏里。
 *
 * ## 三、一个进程只发一个名额
 * [acquire] 是「这一进程还能不能演开屏」。名额跟 `@Singleton` 同生共死 ⇒ **进程冷启动 =
 * 有名额**，而旋转屏幕、切深色模式、从后台回到前台、从通知栏另开一个 Activity 这些都是
 * 同一个进程里的事，一律不再演。
 *
 * 不用 `savedInstanceState == null` 判冷启动：Compose 没有 View 层状态要存，Activity 被系统
 * 回收后重建时那个 Bundle **也可能是 null** —— 于是「明明没重启，却演了一遍开屏」，而那正是
 * 开屏最招人烦的地方（用户会以为 App 又重启了一次）。名额制不依赖任何 Activity 的状态。
 */
@Singleton
class StartupGate @Inject constructor() {

    /**
     * 「App 就绪」的三条信号，见类注释。**全到齐**才 [ready]。
     */
    enum class Signal {
        /** 首页第一块内容已落地（或已确定取不到）。 */
        Content,

        /** 登录 / 未登录已定。 */
        Session,

        /** 上次的播放队列已恢复（迷你条的有无已定）。 */
        Playback,
    }

    private val lock = Any()
    private val pending = Signal.values().toMutableSet()
    private val _ready = MutableStateFlow(false)

    /** 开屏可以退场了。只会 false → true 一次。 */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * 还没到的信号 —— 只给诊断用（开屏退场时若想知道是被谁拖住的，看这个）。
     * 正式路径一律只看 [ready]。
     */
    val pendingSignals: Set<Signal>
        get() = synchronized(lock) { pending.toSet() }

    /**
     * 上报一条信号（幂等；重复上报同一条不扣分）。可能从**任意线程**来
     * （[Signal.Playback] 就来自 IO 线程那趟预热），所以内部加锁。
     */
    fun signal(s: Signal) {
        synchronized(lock) {
            pending.remove(s)
            _ready.value = pending.isEmpty()
        }
    }

    private val taken = AtomicBoolean(false)

    /**
     * 抢开屏的演出名额：**同一个进程里只有第一次返回 true**，之后一律 false。
     *
     * 调用方注意短路：已经确定不演的时候（比如 `savedInstanceState != null`）别调它 ——
     * 白抢一个名额，等于把真正的冷启动那次也赔进去。
     */
    fun acquire(): Boolean = !taken.getAndSet(true)
}
