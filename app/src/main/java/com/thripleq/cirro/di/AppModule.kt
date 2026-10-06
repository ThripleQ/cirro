package com.thripleq.cirro.di

import android.content.Context
import androidx.room.Room
import com.thripleq.cirro.core.db.CollectionDao
import com.thripleq.cirro.core.db.CirroDatabase
import com.thripleq.cirro.core.net.NetEaseGateway
import com.thripleq.cirro.core.net.CirroNative
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt wiring for the libnetease data gateway. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGateway(@ApplicationContext context: Context): NetEaseGateway {
        // libnetease 启动契约：Android 宿主拿不到 NE_API_BASE 环境变量，
        // 必须显式设置 API base（request.h 的嵌入宿主要求）。
        CirroNative.setApiBase("https://music.163.com")
        // Point libnetease's cookie jar at an app-private file on first use.
        CirroNative.setCookieFile(
            File(context.filesDir, NETEASE_COOKIE_FILE).absolutePath,
        )
        return NetEaseGateway()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CirroDatabase =
        Room.databaseBuilder(context, CirroDatabase::class.java, CirroDatabase.NAME)
            // 没有 destructive fallback：漏一个迁移就是启动崩，所以每个版本都要在这里挂上。
            .addMigrations(CirroDatabase.MIGRATION_1_2, CirroDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCollectionDao(database: CirroDatabase): CollectionDao = database.collectionDao()

    private const val NETEASE_COOKIE_FILE = "netease_cookies.json"
}