package com.thripleq.nume.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 集合缓存库（壳 + 曲目）。当前版本 2，见 [MIGRATION_1_2]。 */
@Database(
    entities = [CollectionEntity::class, CollectionTrackEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NumeDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "nume.db"

        /**
         * v1 → v2：`collection` 表加两列。
         *
         * - `subscribed`：歌单/榜单的收藏态。Room 现在是**常态读路径**（不再只是离线兜底），
         *   它渲染的字段就必须齐 —— 缺这一列，首屏和离线都会读回 false，收藏按钮退回空心。
         * - `track_fingerprint`：曲目 id 序列的指纹，判「要不要重新拉」用（见
         *   [com.thripleq.nume.core.repo.CollectionRefresher]）。旧行取空串 ⇒ 第一次进该
         *   集合会走一次全量把指纹补上，自愈。
         *
         * 必须用 `ALTER TABLE ADD COLUMN` 就地加：不能重建表，否则已有的曲目行会一起丢。
         * （`AppModule` 也没开 `fallbackToDestructiveMigration`，漏写本迁移会直接崩在启动。）
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE collection ADD COLUMN subscribed INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE collection ADD COLUMN track_fingerprint TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }
}
