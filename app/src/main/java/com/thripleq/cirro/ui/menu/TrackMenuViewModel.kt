package com.thripleq.cirro.ui.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.model.Track
import com.thripleq.cirro.core.playback.PlaybackLauncher
import com.thripleq.cirro.core.repo.InteractionRepository
import com.thripleq.cirro.core.repo.LibraryStateStore
import com.thripleq.cirro.core.repo.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 曲目菜单（⋮）的动作与状态。
 *
 * ## 为什么是**一个** ViewModel 而不是每屏一个
 *
 * 曲目行的 ⋮ 出现在**每一个**列表里（歌单 / 榜单 / 专辑 / 喜欢 / 已购 / 每日推荐 /
 * 搜索结果 / 歌手热歌…），而它要做的事**与列表毫无关系**：「下一首播放」是播放器的事，
 * 「喜欢」是账号的事，「评论」是单曲的事，「分享」是单曲页的事。
 *
 * 每屏各写一遍，就会有 N 份「乐观翻转 + 失败回滚」的实现慢慢走形 —— 播放页那颗红心
 * 与列表菜单里的「喜欢」必须是同一个行为，分叉了就是 bug。所以它挂一份在根上
 * （见 [TrackMenuController]），所有列表共用。
 *
 * 与 `PlayerActionsViewModel` 的分工：那边服务的是**正在播的那一首**（播放页 / dock），
 * 状态源是播放器当前曲目；这边服务的是**用户点开的任意一首**，曲目由调用方传入。
 * 写入序列两者必须一致（乐观翻转 → 写接口 → 失败回滚 / 成功失效缓存）。
 */
@HiltViewModel
class TrackMenuViewModel @Inject constructor(
    private val interactions: InteractionRepository,
    private val library: LibraryStateStore,
    private val profileRepo: ProfileRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * 当前账号喜欢的歌曲 id（[LibraryStateStore] 的进程级镜像），菜单据此画
     * 「喜欢 / 取消喜欢」的当前态。**未载入完成时是空集**（画成「喜欢」），
     * 装载时机见 [onMenuOpened]。
     */
    val likedIds: StateFlow<Set<String>> get() = library.likedIds

    private val _message = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)

    /** 一次性提示（喜欢失败 / 未登录 / 喜欢成功…），由根上弹 Toast。 */
    val message: SharedFlow<String> = _message.asSharedFlow()

    /**
     * 每次打开菜单调一次：把红心集合拉起来（每账号一次，之后零请求）。
     *
     * 惰性在这里是**刻意的**：`/weapi/song/like/get` 一次就回全部喜欢 id，但也没必要
     * 在 App 一启动、或每进一张列表时就发 —— 只有用户真要去点「喜欢」时才需要它。
     */
    fun onMenuOpened() {
        viewModelScope.launch {
            val uid = profileRepo.account()?.uid ?: return@launch
            library.ensureLikedLoaded(uid)
        }
    }

    /**
     * 红心 / 取消红心某一首歌。
     *
     * 未登录时不翻转、只提示：写接口在未登录时回 301/250，先在这里拦住更好懂。
     */
    fun toggleLike(trackId: String) {
        if (trackId.isEmpty()) return
        val target = trackId !in library.likedIds.value
        viewModelScope.launch {
            val uid = profileRepo.account()?.uid
            if (uid == null) {
                _message.tryEmit("登录后才能收藏")
                return@launch
            }
            library.ensureLikedLoaded(uid)
            library.markLiked(trackId, target)
            val r = interactions.setSongLiked(trackId, target)
            if (r.ok) {
                // 喜欢列表是「我的」页的一张卡（带计数），那份缓存已经旧了。
                profileRepo.invalidateLikedCache()
                _message.tryEmit(if (target) "已加入我喜欢的音乐" else "已取消喜欢")
            } else {
                library.markLiked(trackId, !target)
                _message.tryEmit(r.message)
            }
        }
    }

    /**
     * 「下一首播放」：插到当前播放的下一首，不打断正在播的这首。
     *
     * 队列为空（进程刚起、什么都没播过）时等价于直接播它 —— 否则这个动作在冷启动下
     * 毫无反应，那就是个坏按钮。插队细节见 [PlaybackLauncher.playNext]。
     */
    fun playNext(track: Track) {
        viewModelScope.launch { playback.playNext(context, track) }
    }
}
