package com.thripleq.cirro.core.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 集合（榜单 / 歌单 / 专辑）壳元数据的离线缓存。`cacheKey` 形如 `pl:<id>`（榜单与歌单
 * 共用，因为榜单 id 就是歌单 id、走同一端点）或 `al:<id>`。
 */
@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Long,
    val updateFrequency: String,
    val description: String,
    val creator: String,
    val updatedAt: Long,
)

/** 集合的曲目行，按 [position] 保序；父壳删除时级联清理。 */
@Entity(
    tableName = "collection_track",
    primaryKeys = ["cache_key", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["cache_key"],
            childColumns = ["cache_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cache_key")],
)
data class CollectionTrackEntity(
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    val position: Int,
    val trackId: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val albumName: String,
)

/** 壳 + 曲目的一次查询结果。 */
data class CollectionWithTracks(
    @Embedded
    val collection: CollectionEntity,
    @Relation(parentColumn = "cache_key", entityColumn = "cache_key")
    val tracks: List<CollectionTrackEntity>,
)
