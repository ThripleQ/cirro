package com.thripleq.cirro.ui.playerbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.repo.InteractionRepository
import com.thripleq.cirro.core.repo.LibraryStateStore
import com.thripleq.cirro.core.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全屏播放页那颗红心的状态与动作。
 *
 * ## 为什么红心态不查接口
 *
 * 红心是个开关，得先把当前态画出来。逐曲查询是最直观的做法，但 **往返次数等于
 * 切歌次数** —— 而这件事有更省的做法：`/weapi/song/like/get` 一次就返回当前账号
 * 全部喜欢的歌曲 id（实测 115 首 ≈ 1KB），之后本地查表即可，切歌零请求。
 * 这份缓存在 [LibraryStateStore]（进程级单例），本类只是在它之上把「当前曲目」
 * 折成一个布尔。
 *
 * 代价写在明处：**集合还没载入完时 [liked] 是 false**（心是空的）。首帧因此可能
 * 少亮一下，换来的是此后每次切歌都不用等网络。这个取舍是有意的。
 *
 * ## 乐观更新
 *
 * 点击立刻翻转本地状态，请求失败再翻回来并给一句提示（见 [message]）。写接口是
 * 「设成目标态」而不是「切换」，重复调幂等，所以不怕重试。
 */
@HiltViewModel
class PlayerActionsViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val interactions: InteractionRepository,
    private val library: LibraryStateStore,
) : ViewModel() {

    private val trackId = MutableStateFlow<String?>(null)

    /** 当前曲目是否已红心。没曲目 / 还没载入时 false。 */
    val liked: StateFlow<Boolean> =
        combine(trackId, library.likedIds) { id, ids ->
            !id.isNullOrEmpty() && id in ids
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _message = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)

    /** 红心失败 / 未登录的提示，由界面弹 Toast。 */
    val message: SharedFlow<String> = _message.asSharedFlow()

    /** 切歌时调用：记住当前曲目，并确保「喜欢」集合已载入（每账号一次）。 */
    fun onTrackChanged(id: String?) {
        trackId.value = id
        if (id.isNullOrEmpty()) return
        viewModelScope.launch { loadLiked() }
    }

    /** 点红心：当前已喜欢则取消，否则加喜欢。 */
    fun toggleLike() {
        val id = trackId.value ?: return
        val target = !liked.value
        viewModelScope.launch {
            if (loadLiked() == null) {
                _message.tryEmit("登录后才能收藏")
                return@launch
            }
            library.markLiked(id, target)
            val r = interactions.setSongLiked(id, target)
            if (r.ok) {
                // 「我的」页那份喜欢缓存已经旧了：清掉，下次进页面重算计数。
                profileRepo.invalidateLikedCache()
            } else {
                library.markLiked(id, !target)
                _message.tryEmit(r.message)
            }
        }
    }

    /** 确保喜欢集合已载入，返回当前 uid；未登录返回 null。 */
    private suspend fun loadLiked(): Long? {
        val uid = profileRepo.account()?.uid ?: return null
        library.ensureLikedLoaded(uid)
        return uid
    }
}
