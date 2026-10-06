package com.thripleq.cirro.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 集合缓存库（壳 + 曲目）。当前版本 3，见 [MIGRATION_1_2] / [MIGRATION_2_3]。 */
@Database(
    entities = [CollectionEntity::class, CollectionTrackEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CirroDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "cirro.db"

        /**
         * v1 → v2：`collection` 表加两列。
         *
         * - `subscribed`：歌单/榜单的收藏态。Room 现在是**常态读路径**（不再只是离线兜底），
         *   它渲染的字段就必须齐 —— 缺这一列，首屏和离线都会读回 false，收藏按钮退回空心。
         * - `track_fingerprint`：曲目 id 序列的指纹，判「要不要重新拉」用（见
         *   [com.thripleq.cirro.core.repo.CollectionRefresher]）。旧行取空串 ⇒ 第一次进该
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

        /**
         * v2 → v3：`collection_track` 加两列（`fee` / `albumId`），并把**所有壳的指纹清空**。
         *
         * 加列本身是常规操作（同 [MIGRATION_1_2]，必须 ALTER 就地加，不能重建表）。
         * 清指纹才是这条迁移的重点：老行两列的默认值 `0`/`''` 与「免费歌 / 无归属」
         * 完全同形，而 [com.thripleq.cirro.core.repo.CollectionRefresher] 一旦看到
         * 指纹相同就会**原样复用这些曲目行、连全量都不拉** —— 于是升级后那批缓存集合
         * 会永远画不出徽标。清空指纹 ⇒ 下次进页面必走一次全量，把两列补齐，自愈。
         *
         * 只清指纹、**不删曲目行**：留着的旧曲目仍可先渲染（只是暂时没有徽标），
         * 比让页面先空一下再长出来平滑。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE collection_track ADD COLUMN fee INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE collection_track ADD COLUMN albumId TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL("UPDATE collection SET track_fingerprint = ''")
            }
        }
    }
}
