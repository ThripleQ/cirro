package com.thripleq.nume.di

import com.thripleq.nume.core.db.CollectionCache
import com.thripleq.nume.core.repo.CollectionRemote
import com.thripleq.nume.core.repo.CollectionStore
import com.thripleq.nume.core.repo.GatewayCollectionRemote
import com.thripleq.nume.core.util.AndroidDiagnostics
import com.thripleq.nume.core.util.Clock
import com.thripleq.nume.core.util.Diagnostics
import com.thripleq.nume.core.util.WallClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 接口 → 实现的绑定。
 *
 * 存在的理由见 [CollectionRemote] 的 KDoc：让集合取数的编排能脱离 JNI / Room 被断言。
 * 这里**只绑编排真正需要抽象的那两个**，不给其余十几个 Repository 造接口 ——
 * 它们仍是 concrete class，不是遗漏，是还没轮到（见 `docs/architecture.md`）。
 *
 * ⚠️ 四个都得**显式** `@Binds`：Dagger 眼里 `@Inject` 构造只绑定**实现类自己**，
 * 不会因为它实现了某个接口就连接口一起绑（缺了就报 `Dagger/MissingBinding`）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {

    @Binds
    abstract fun bindCollectionRemote(impl: GatewayCollectionRemote): CollectionRemote

    @Binds
    abstract fun bindCollectionStore(impl: CollectionCache): CollectionStore

    @Binds
    abstract fun bindClock(impl: WallClock): Clock

    @Binds
    abstract fun bindDiagnostics(impl: AndroidDiagnostics): Diagnostics
}
