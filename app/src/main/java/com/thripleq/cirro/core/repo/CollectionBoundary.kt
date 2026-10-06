package com.thripleq.cirro.core.repo

import com.thripleq.cirro.core.model.TrackCollection
import com.thripleq.cirro.core.net.ApiResult
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.NeteaseOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 集合取数（歌单 / 榜单 / 专辑）的两个边界。
 *
 * ## 为什么要有这两个接口
 *
 * [CollectionRefresher] 承载的是「Room 先出 → 冷却 → 检查 → 变了才拉全量」这条编排，
 * 它是全项目最需要被断言的一段逻辑：改错一步的表现是「数据永远不新」或「每次都全量拉」，
 * 都不崩、只能靠真机体会。但它原来直接依赖三样在 JVM 上碰不得的东西 —— JNI 网关
 * （[NetEaseGateway]）、Room（[com.thripleq.cirro.core.db.CollectionCache]）、
 * 墙上时钟（[System.currentTimeMillis]）—— 于是一条测试都写不了。
 *
 * 这两个接口就是这条可测性缺口的最小缝合：**只暴露编排真正用得着的那几个动作**，
 * 不试图给整个 gateway / DAO 造抽象（那才是过度设计）。
 *
 * 生产实现分别是 [GatewayCollectionRemote] 与
 * [com.thripleq.cirro.core.db.CollectionCache]，绑定见 `di/RepoModule`。
 */
interface CollectionRemote {

    /**
     * `/weapi/v6/playlist/detail`。
     *
     * @param n 曲目详情条数：`"0"` = 只要元数据 + 完整 trackIds 的轻量检查形态，
     *   空串 = 库内回落到上游默认的全量。见 [CollectionRefresher.CHECK_N]。
     */
    suspend fun playlistDetail(id: String, n: String): ApiResult

    /** `/weapi/v1/album/{id}`：顶层只有 songs，没有 album 对象（实测）。 */
    suspend fun albumDetail(id: String): ApiResult

    /** `/weapi/v3/song/detail`：给大歌单补 `tracks[]` 之外的曲目。 */
    suspend fun songDetail(ids: String): ApiResult
}

/** 集合缓存（Room）的读写面 —— 见类的 KDoc 里「为什么抽」的理由。 */
interface CollectionStore {

    /** 读缓存；无则返回 null。 */
    suspend fun get(key: String): TrackCollection?

    /** 整壳 + 曲目一起替换。 */
    suspend fun put(key: String, collection: TrackCollection)

    /** 只更新壳（元数据 + 收藏态 + 指纹），曲目表原样不动 —— 检查命中时走这条。 */
    suspend fun putMeta(key: String, collection: TrackCollection)

    /** 只改收藏态（乐观更新落库）。 */
    suspend fun putSubscribed(key: String, subscribed: Boolean, subscribedCount: Long)

    /** 账号切换：整表清掉。 */
    suspend fun clearAll()
}

/** [CollectionRemote] 的生产实现：把 op 号与固定参数收在这里，编排层只认语义。 */
@Singleton
class GatewayCollectionRemote @Inject constructor(
    private val gateway: NetEaseGateway,
) : CollectionRemote {

    // 第二参是 offset，恒为 "0"：n=0 那次是检查、n 空那次是全量，都不分页偏移。
    override suspend fun playlistDetail(id: String, n: String): ApiResult =
        gateway.call(NeteaseOp.PLAYLIST_DETAIL, id, "0", n)

    override suspend fun albumDetail(id: String): ApiResult =
        gateway.call(NeteaseOp.ALBUM_DETAIL, id)

    override suspend fun songDetail(ids: String): ApiResult =
        gateway.call(NeteaseOp.SONG_DETAIL, ids)
}
