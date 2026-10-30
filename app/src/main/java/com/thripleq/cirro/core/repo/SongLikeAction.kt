package com.thripleq.cirro.core.repo

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「红心一首歌」这件事的唯一实现：播放页那颗心与通知栏那颗心共用。
 *
 * 逻辑本来就只在 `PlayerActionsViewModel` 里，2026-10-30 通知栏也要一颗心之后，
 * 把「目标态怎么算、乐观更新、失败回滚、清缓存」原样搬到这里 —— 两份拷贝迟早分叉，
 * 而分叉的表现是「通知里点亮了，播放页还是空心」。
 *
 * 写接口是「设成目标态」而不是切换（见 [InteractionRepository.setSongLiked]），
 * 所以调用方先乐观翻本地、失败再翻回来。
 */
@Singleton
class SongLikeAction @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val interactions: InteractionRepository,
    private val library: LibraryStateStore,
) {

    /** 这首歌现在是否红心（本地口径；喜欢集合还没载入完时为 false）。 */
    fun isLiked(trackId: String?): Boolean =
        !trackId.isNullOrEmpty() && trackId in library.likedIds.value

    /**
     * 切换红心。返回 null 表示成功；否则是给用户看的一句失败原因
     * （未登录 / 下架 / 网络），本地状态已回滚。
     */
    suspend fun toggle(trackId: String): String? {
        val uid = profileRepo.account()?.uid ?: return "登录后才能收藏"
        library.ensureLikedLoaded(uid)
        val target = trackId !in library.likedIds.value
        library.markLiked(trackId, target)
        val result = interactions.setSongLiked(trackId, target)
        if (result.ok) {
            // 「我的」页那份喜欢缓存已经旧了：清掉，下次进页面重算计数。
            profileRepo.invalidateLikedCache()
            return null
        }
        library.markLiked(trackId, !target)
        return result.message
    }
}
