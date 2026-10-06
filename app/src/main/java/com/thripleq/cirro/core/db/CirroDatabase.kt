package com.thripleq.cirro.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** 离线元数据缓存库。版本 1；当前只有集合（壳 + 曲目）缓存。 */
@Database(
    entities = [CollectionEntity::class, CollectionTrackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CirroDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "cirro.db"
    }
}
