package com.thripleq.cirro.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    /** 原子替换：先写壳（REPLACE 会级联删旧曲目），再补新曲目。 */
    @Transaction
    suspend fun upsert(collection: CollectionEntity, tracks: List<CollectionTrackEntity>) {
        upsertCollection(collection)
        deleteTracks(collection.cacheKey)
        if (tracks.isNotEmpty()) upsertTracks(tracks)
    }
}
