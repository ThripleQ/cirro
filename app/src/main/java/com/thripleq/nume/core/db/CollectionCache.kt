package com.thripleq.nume.core.db

import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 集合缓存的读写门面：Repository 用它做「先用缓存渲染、再判要不要重拉」的读穿缓存，
 * 领域模型与 Room 实体在此互转，Repository 不必接触 DAO。
 */
@Singleton
class CollectionCache @Inject constructor(
    private val dao: CollectionDao,
) {

    /** 读缓存；无则返回 null。 */
    suspend fun get(key: String): TrackCollection? {
        val row = dao.get(key) ?: return null
        val c = row.collection
        return TrackCollection(
            id = c.id,
            name = c.name,
            coverUrl = c.coverUrl,
            playCount = c.playCount,
            subscribedCount = c.subscribedCount,
            trackCount = c.trackCount,
            updateFrequency = c.updateFrequency,
            description = c.description,
            creator = c.creator,
            tracks = row.tracks.sortedBy { it.position }.map { t ->
                Track(
                    id = t.trackId,
                    name = t.name,
                    artist = t.artist,
                    artworkUrl = t.artworkUrl,
                    durationMs = t.durationMs,
                    albumName = t.albumName,
                    albumId = t.albumId,
                    fee = t.fee,
                )
            },
            subscribed = c.subscribed,
            fingerprint = c.trackFingerprint,
        )
    }

    /** 写缓存（整壳 + 曲目一起替换）。 */
    suspend fun put(key: String, collection: TrackCollection) {
        dao.upsert(
            entity(key, collection),
            collection.tracks.mapIndexed { i, t ->
                CollectionTrackEntity(
                    cacheKey = key,
                    position = i,
                    trackId = t.id,
                    name = t.name,
                    artist = t.artist,
                    artworkUrl = t.artworkUrl,
                    durationMs = t.durationMs,
                    albumName = t.albumName,
                    fee = t.fee,
                    albumId = t.albumId,
                )
            },
        )
    }

    /**
     * 只更新壳（元数据 + 收藏态 + 指纹），**曲目表原样不动**。
     *
     * 「检查发现曲目没变」时走这条：元数据（名称/封面/收藏数/播放数）已经是服务端最新值，
     * 但曲目表不必重写 —— 对 2000 首的歌单，重写一次就是 2000 行的删+插。
     */
    suspend fun putMeta(key: String, collection: TrackCollection) {
        dao.updateMeta(entity(key, collection))
    }

    /** 只改收藏态（乐观更新落库）。 */
    suspend fun putSubscribed(key: String, subscribed: Boolean, subscribedCount: Long) {
        dao.updateSubscribed(key, subscribed, subscribedCount)
    }

    /** 账号切换：整表清掉（收藏态与收藏数都是随账号变的）。 */
    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun entity(key: String, c: TrackCollection) = CollectionEntity(
        cacheKey = key,
        id = c.id,
        name = c.name,
        coverUrl = c.coverUrl,
        playCount = c.playCount,
        subscribedCount = c.subscribedCount,
        trackCount = c.trackCount,
        updateFrequency = c.updateFrequency,
        description = c.description,
        creator = c.creator,
        subscribed = c.subscribed,
        trackFingerprint = c.fingerprint,
        updatedAt = System.currentTimeMillis(),
    )
}
