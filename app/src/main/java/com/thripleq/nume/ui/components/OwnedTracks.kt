package com.thripleq.nume.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.thripleq.nume.core.repo.LibraryStateStore
import com.thripleq.nume.core.repo.Track

/**
 * 「当前账号买了什么」在 UI 侧的只读快照：已购单曲 id + 已购数字专辑 id。
 *
 * ## 为什么走 CompositionLocal 而不是逐层传参
 *
 * 徽标出现在**每一个曲目列表**里（歌单/榜单/专辑详情/搜索/歌手热歌/首页猜你喜欢…），
 * 而这份数据是**账号级、全局唯一**的（[LibraryStateStore] 单例，换账号自动重拉）。
 * 逐层传参会把「已购」这份与页面本身无关的状态穿过 5~6 层签名，且每加一个列表就要再穿一次；
 * 这与 [LocalCommentsOpener] 是同一类问题（深处要用、来源在根上），所以用同一套办法。
 *
 * ## 判定口径
 *
 * 见 [LibraryStateStore.isOwned]：单曲购买 ∪ 所属数字专辑已购。这里只是把两个集合
 * 从 StateFlow 里取出来做一个纯函数包装，**不复制任何判定逻辑**。
 */
data class OwnedTracks(
    val songs: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
) {
    fun owns(track: Track): Boolean =
        (track.id.isNotEmpty() && track.id in songs) ||
            (track.albumId.isNotEmpty() && track.albumId in albums)

    companion object {
        /** 未提供（预览、未登录）时的缺省值：什么都没买 —— 徽标退化成全红 PAY。 */
        val None = OwnedTracks()
    }
}

/**
 * 由 [com.thripleq.nume.NumeApp] 在根上提供（它持有 Activity 作用域的 ProfileViewModel，
 * 是唯一一处能保证「App 一启动就有账号态」的地方）。
 *
 * `staticCompositionLocalOf`：这份值只在换账号/载入完成时整体替换，用 static 变体
 * 可以省掉每次重组时的读取跟踪（与 [LocalCommentsOpener] 同理）。
 */
val LocalOwnedTracks = staticCompositionLocalOf { OwnedTracks.None }

/**
 * 曲目行画徽标的唯一入口：`fee` + 「买了没」→ 徽标序列（见 [payTagsOf]）。
 *
 * 用本函数而不是让调用方自己读 [LocalOwnedTracks]，是为了保证**所有列表用同一套判据** ——
 * 徽标颜色错了，用户没有别的线索能看出来（它不像红心点一下就知道对不对）。
 */
@Composable
fun rememberPayTags(track: Track): List<PayTag> =
    payTagsOf(track.fee, LocalOwnedTracks.current.owns(track))
