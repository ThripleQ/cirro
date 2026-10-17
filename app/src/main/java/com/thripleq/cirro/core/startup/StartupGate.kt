package com.thripleq.cirro.core.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 开屏动画的「放行」信号：开屏不只演一段动画，它还**盖住**启动期那段没有内容的窗口
 * （见 [com.thripleq.cirro.ui.splash.SplashOverlay]），所以必须知道「什么时候可以不盖了」。
 *
 * ## 为什么要有这么个东西
 * 开屏是在 MainActivity 里起的，而它要等的「首页有内容了」发生在 [HomeViewModel] 里 ——
 * 两者隔着导航层，传回调要把整条链打通。用一个进程内的一次性闩（latch）就够：
 * 谁先把第一块内容发布出去谁就 [release]，开屏那边只 [await] 这个流。
 *
 * ## 判据是「首页第一块内容落地」
 * 不是「所有请求都回来」：首页是逐块回填的（见 `HomeViewModel.reload` 的 `publish`），
 * 第一块落地就说明网络通、页面已经能画，剩下的块在开屏退场后接着填 —— 等全部只会
 * 白白把开屏拖长。取数彻底失败（落到 `HomeUiState.Error`）同样放行：那时该给用户看
 * 错误页，而不是把错误页藏在红背后。
 *
 * ## 一定配超时
 * 开屏那边用 `withTimeoutOrNull(Motion.SplashMaxHoldMs)` 兜底 —— 这个闩**只负责如实
 * 上报**，不负责保证一定会被打开（离线时没有任何一方会调 [release]）。
 */
@Singleton
class StartupGate @Inject constructor() {

    private val _ready = MutableStateFlow(false)

    /** 开屏可以退场了。只会 false → true 一次。 */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 放行（幂等）。 */
    fun release() {
        _ready.value = true
    }
}
