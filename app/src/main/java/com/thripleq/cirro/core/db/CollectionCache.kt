package com.thripleq.cirro.core.db

import com.thripleq.cirro.core.repo.Track
import com.thripleq.cirro.core.repo.TrackCollection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 集合离线的读写门面：Repository 用它做「网络成功则写回、网络失败则回退」的读穿缓存，
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
                )
            },
        )
    }

    /** 写缓存（整壳 + 曲目一起替换）。 */
    suspend fun put(key: String, collection: TrackCollection) {
        dao.upsert(
            CollectionEntity(
                cacheKey = key,
                id = collection.id,
                name = collection.name,
                coverUrl = collection.coverUrl,
                playCount = collection.playCount,
                subscribedCount = collection.subscribedCount,
                trackCount = collection.trackCount,
                updateFrequency = collection.updateFrequency,
                description = collection.description,
                creator = collection.creator,
                updatedAt = System.currentTimeMillis(),
            ),
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
                )
            },
        )
    }
}
