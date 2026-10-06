package com.thripleq.cirro.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CollectionDao {

    @Transaction
    @Query("SELECT * FROM collection WHERE cache_key = :key")
    suspend fun get(key: String): CollectionWithTracks?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<CollectionTrackEntity>)

    @Query("DELETE FROM collection_track WHERE cache_key = :key")
    suspend fun deleteTracks(key: String)

    /**
     * 只更新壳那一行。
     *
     * **必须用 `@Update`（生成 `UPDATE`），不能用 `upsertCollection`** ——
     * 后者是 `INSERT OR REPLACE`，而 `collection_track` 是通过
     * `onDelete = CASCADE` 挂在壳上的，REPLACE 会先删壳行、把整份曲目表连带删掉。
     * 「检查发现曲目没变、只有元数据变了」这条路径要保住曲目表，所以单开这个方法
     * （`upsert` 里那句先写壳再 `deleteTracks` 再补曲目，就是替 REPLACE 收尾的）。
     */
    @Update
    suspend fun updateMeta(collection: CollectionEntity)

    /**
     * 只改收藏态：乐观更新后落库，免得退出再进时 Room 把旧值读回来。
     *
     * ⚠️ 列名注意：`CollectionEntity` 里只有 `cacheKey` / `trackFingerprint` 带
     * `@ColumnInfo` 改了名，其余列**就是字段名本身**（驼峰），所以这里是
     * `subscribedCount` 而不是 `subscribed_count` —— Room 会在编译期校验，
     * 写错会以 `no such column` 的形式在 ksp 阶段失败。
     */
    @Query(
        "UPDATE collection SET subscribed = :subscribed, subscribedCount = :subscribedCount " +
            "WHERE cache_key = :key",
    )
    suspend fun updateSubscribed(key: String, subscribed: Boolean, subscribedCount: Long)

    /** 账号切换时整表清掉（`subscribed` / 收藏数都是随账号变的）。 */
    @Query("DELETE FROM collection")
    suspend fun clearAll()

    /** 原子替换：先写壳（REPLACE 会级联删旧曲目），再补新曲目。 */
    @Transaction
    suspend fun upsert(collection: CollectionEntity, tracks: List<CollectionTrackEntity>) {
        upsertCollection(collection)
        deleteTracks(collection.cacheKey)
        if (tracks.isNotEmpty()) upsertTracks(tracks)
    }
}
