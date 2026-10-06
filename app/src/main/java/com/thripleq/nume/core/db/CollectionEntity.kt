package com.thripleq.nume.core.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 集合（榜单 / 歌单 / 专辑）壳元数据的缓存。`cacheKey` 形如 `pl:<id>`（榜单与歌单
 * 共用，因为榜单 id 就是歌单 id、走同一端点）或 `al:<id>`。
 *
 * 这张表（连同 [CollectionTrackEntity]）是**常态读路径**，不再只是离线兜底：进页面先用
 * 它渲染，再拿 [trackFingerprint] 判要不要重新拉。所以界面会读的字段都必须在这里有位置
 * —— `subscribed` 就是为此加的（没有它，首屏与离线都会读回 false，收藏按钮退回空心）。
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
    /** 歌单/榜单的收藏态（服务端 `playlist.subscribed`）。专辑恒 false —— 那个接口没有此字段。 */
    val subscribed: Boolean,
    /**
     * 曲目 id 序列的指纹（[com.thripleq.nume.core.repo.playlistFingerprint] 的产物）。
     * 与下一次「检查」请求算出的指纹比对：相同 → 曲目表仍然有效，不必拉全量。
     * 空串 = 还不知道（v1 迁移过来的旧行、或专辑）→ 下次进页面走一次全量把指纹补上。
     */
    @ColumnInfo(name = "track_fingerprint")
    val trackFingerprint: String,
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
    /**
     * 曲目展示要用的两个字段，必须跟着曲目一起落库。
     *
     * Room 是**常态读路径**（见 [CollectionEntity] 的说明），而 [com.thripleq.nume.core.repo.CollectionRefresher]
     * 在「指纹没变」时会**原样复用这里的曲目行**、不重拉全量 —— 若这两列不存在，
     * 那份复用的曲目就会读回 `fee=0`/`albumId=""`，徽标整批消失（且只在"曲目没变"时消失，
     * 看起来毫无规律）。加列的迁移见 [NumeDatabase.MIGRATION_2_3]。
     */
    val fee: Int,
    val albumId: String,
)

/** 壳 + 曲目的一次查询结果。 */
data class CollectionWithTracks(
    @Embedded
    val collection: CollectionEntity,
    @Relation(parentColumn = "cache_key", entityColumn = "cache_key")
    val tracks: List<CollectionTrackEntity>,
)
