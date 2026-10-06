package com.thripleq.nume.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.HomeRepository
import com.thripleq.nume.core.repo.InteractionRepository
import com.thripleq.nume.core.repo.ProfileRepository
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which data source a [TrackListScreen] shows. */
enum class TrackListSource(val wire: String, val label: String) {
    CHART("chart", "榜单"),
    LIKED("liked", "喜欢的音乐"),
    PURCHASED("purchased", "已购音乐"),
    PLAYLIST("playlist", "歌单"),
    ALBUM("album", "专辑"),

    /** 每日推荐歌曲（探索页「精选推荐」卡入口；无后端壳，ViewModel 组装简化壳）。 */
    DAILY("daily", "每日推荐");

    companion object {
        fun from(wire: String): TrackListSource =
            entries.firstOrNull { it.wire == wire } ?: PLAYLIST
    }
}

sealed interface TrackListUiState {
    data object Loading : TrackListUiState
    data object Empty : TrackListUiState
    data object Error : TrackListUiState
    data class Ready(val collection: TrackCollection) : TrackListUiState
}

/**
 * 曲目列表的排序方式。
 *
 * **纯本地排序**，不重新请求：官方歌单页的「排序」也只是把已有的曲目换一个顺序
 * 显示（曲目全集早就下来了）。所以排序不改变 [TrackListUiState.Ready.collection]
 * 里那一组曲目的**来源**，只改展示顺序 —— 也因此，服务端给的原始顺序必须另存
 * 一份（[TrackListViewModel.loaded]），否则切回「默认顺序」就再也回不去了。
 *
 * [name] 用中文 Collator 比较（见 [TrackListViewModel.sortedBy]），不是按 UTF-16
 * 码点 —— 后者把中文按字形排，出来的顺序人眼读不出规律。
 */
enum class TrackSort(val label: String) {
    DEFAULT("默认顺序"),
    TITLE("歌曲名"),
    ARTIST("歌手"),
    DURATION("时长"),
    ALBUM("专辑"),
}

/**
 * 统一"壳子 + 列表"页：榜单 / 歌单 / 专辑走真实后端壳，
 * 喜欢 / 已购 / 每日推荐没有独立壳，用已有数据组装一个简化壳（标题 + 曲目数）。
 */
@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val chartRepo: ChartRepository,
    private val profileRepo: ProfileRepository,
    private val homeRepo: HomeRepository,
    private val interactions: InteractionRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrackListUiState>(TrackListUiState.Loading)
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    /** 当前排序方式（「播放全部」与列表共用同一份顺序，见 [setSort]）。 */
    private val _sort = MutableStateFlow(TrackSort.DEFAULT)
    val sort: StateFlow<TrackSort> = _sort.asStateFlow()

    /** 一次性提示（收藏失败、不支持收藏…）：由界面弹 Toast。 */
    private val _message = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private var loadedKey: String? = null

    /**
     * 服务端给的**原始顺序**那一份壳。
     *
     * [TrackListUiState.Ready] 里那份是按当前排序重排过的视图，不能拿它当排序输入
     * （切回「默认顺序」时会变成「按上次排序排完之后再排」）。所有重排都以本字段为
     * 源，切排序只换视图、不重拉网络。
     */
    private var loaded: TrackCollection? = null

    // 上次加载参数：错误态"点此重试"要用同参重拉，不依赖 UI 再次传参。
    private var lastArgs: Triple<TrackListSource, String, String>? = null

    fun load(source: TrackListSource, id: String, title: String) {
        val key = "${source.wire}:$id"
        if (loadedKey == key) return
        loadedKey = key
        lastArgs = Triple(source, id, title)
        viewModelScope.launch {
            _uiState.value = TrackListUiState.Loading
            val collection = when (source) {
                TrackListSource.CHART -> chartRepo.chartCollection(id)
                TrackListSource.PLAYLIST -> profileRepo.playlistCollection(id)
                TrackListSource.ALBUM -> profileRepo.albumCollection(id)?.let {
                    // 接口无 album 对象，标题从首曲推断；拿不到时用导航参数兜底
                    if (it.name.isBlank()) it.copy(name = title) else it
                }
                TrackListSource.LIKED -> profileRepo.likedTracks(id.toLongOrNull() ?: 0L)
                    .let { simpleShell(id, title, it) }
                TrackListSource.PURCHASED -> profileRepo.purchasedSongs()
                    .let { simpleShell(id, title, it) }
                TrackListSource.DAILY -> homeRepo.dailySongs()
                    .let { simpleShell(id, title.ifBlank { "每日推荐" }, it) }
            }
            loaded = collection
            // 换列表时排序方式**跟着重置**：新列表保持服务端顺序，「排序」的当前项也
            // 回到「默认顺序」——否则排序 sheet 会显示上一张列表选的那一项，而这张
            // 列表其实还是原序，自相矛盾。
            _sort.value = TrackSort.DEFAULT
            _uiState.value = readyOr(collection)
        }
    }

    /** 错误态重试：同参重拉（load() 有同 key 幂等门，先清 key）。 */
    fun retry() {
        val (source, id, title) = lastArgs ?: return
        loadedKey = null
        load(source, id, title)
    }

    /**
     * 换一种排序。**只重排本地已有的曲目**（不重拉网络），并同时影响「播放全部」
     * 的播放顺序 —— 用户按下「播放全部」时期待的就是眼前这个顺序。
     */
    fun setSort(mode: TrackSort) {
        if (_sort.value == mode) return
        _sort.value = mode
        _uiState.value = readyOr(loaded)
    }

    /**
     * 收藏 / 取消收藏当前列表（头部胶囊与「播放全部」行尾那枚图标共用这一个动作）。
     *
     * 走**乐观更新**：先把按钮翻过去、收藏数 ±1，请求失败再翻回来并提示。
     * 写接口是「设成目标态」而不是「切换」（重复调幂等），所以这样安全。
     *
     * 歌单与榜单是一条接口（榜单 id 就是歌单 id），专辑是另一条；喜欢 / 已购 /
     * 每日推荐这三类没有后端壳、本来就不支持收藏，给一句明确的话而不是静默失败。
     */
    fun toggleSubscribe() {
        val (source, _, _) = lastArgs ?: return
        val before = loaded ?: return
        val target = !before.subscribed
        viewModelScope.launch {
            val result = when (source) {
                TrackListSource.PLAYLIST, TrackListSource.CHART ->
                    interactions.setPlaylistSubscribed(before.id, target)
                TrackListSource.ALBUM ->
                    interactions.setAlbumSubscribed(before.id, target)
                TrackListSource.LIKED, TrackListSource.PURCHASED, TrackListSource.DAILY -> {
                    _message.tryEmit("这个列表不支持收藏")
                    return@launch
                }
            }
            if (result.ok) {
                applySubscribed(target)
            } else {
                _message.tryEmit(result.message)
            }
        }
    }

    /** 把订阅态写进本地两份状态（视图 + 原始壳）并同步仓库缓存。 */
    private fun applySubscribed(subscribed: Boolean) {
        val base = loaded ?: return
        // 收藏数只在「从未收藏 → 已收藏」时 +1：它是个计数，方向跟 subscribed 一致。
        // 服务端那个计数还会被别的用户改，所以这里只是乐观值，下次拉详情会校正。
        val delta = if (subscribed == base.subscribed) 0L else if (subscribed) 1L else -1L
        val next = base.copy(
            subscribed = subscribed,
            subscribedCount = (base.subscribedCount + delta).coerceAtLeast(0L),
        )
        loaded = next
        (lastArgs?.first)?.let { profileRepo.cacheSubscribed(next.id, subscribed, next.subscribedCount) }
        _uiState.value = readyOr(next)
    }

    /** 按当前排序把 [base] 变成视图状态。 */
    private fun readyOr(base: TrackCollection?): TrackListUiState = when {
        base == null -> TrackListUiState.Error
        base.tracks.isEmpty() -> TrackListUiState.Empty
        else -> TrackListUiState.Ready(base.applySort(_sort.value))
    }

    /** 列表里点某一首：播它 —— **不弹播放页**（2026-10-03 用户：「点一首歌进行播放不要
     *  进入播放页，就单纯开始播放就行」）。[onPlayAll] 是另一回事，仍会弹出播放页。 */
    fun onTrackClick(collection: TrackCollection, index: Int) {
        viewModelScope.launch {
            playback.play(context, collection.tracks, index)
        }
    }

    /** 头部「播放」按钮：从第一首开始整单播放。**仍然弹出播放页** —— 用户那条「点了不要
     *  进播放页」说的是列表里点某一首（[onTrackClick]）；「播放全部」是整单播放的入口，
     *  官方也是弹播放页的。若这条也要去掉，删下面那行 emit 即可（本类是该 flow 的唯一生产者）。 */
    fun onPlayAll(collection: TrackCollection) {
        viewModelScope.launch {
            playback.play(context, collection.tracks, 0)
            _openPlayer.tryEmit(Unit)
        }
    }

    private fun simpleShell(id: String, title: String, tracks: List<Track>): TrackCollection? {
        if (tracks.isEmpty()) return null
        return TrackCollection(
            id = id,
            name = title,
            coverUrl = tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl,
            playCount = 0L,
            subscribedCount = 0L,
            trackCount = tracks.size.toLong(),
            updateFrequency = "",
            description = "",
            creator = "",
            tracks = tracks,
        )
    }
}

/**
 * 中文比较器：`Collator.getInstance(Locale.CHINA)` 按**拼音**排中文，正是用户在
 * 「按歌曲名排序」时期待的顺序。用默认的 [compareBy]（UTF-16 码点）会把中文排成
 * 字形顺序 —— 那是没有规律可言的乱序。
 *
 * Collator **不是线程安全的**，所以在这里建一次、只允许从 ViewModel 里同步调用
 * （[TrackListViewModel.setSort] 都在主线程）。别把它挪进任何并发上下文。
 */
private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

/**
 * 依次用中文 Collator 比较 [keys]，先分出胜负的那个键说了算。
 *
 * **不要写成 `compareBy(zhCollator, { it.name })`**：java.text.Collator 在 Kotlin
 * 眼里是 `Comparator<Any!>`，`compareBy` 的 `Comparator<in K>` + `(T) -> K` 两个类型
 * 参数都推不出来（实测报 Cannot infer type for type parameter 'T'）。这里显式收窄
 * 到 `Comparator<Track>`，双向都定死。
 */
private fun zhComparator(vararg keys: (Track) -> String): Comparator<Track> =
    Comparator { a, b ->
        for (key in keys) {
            val c = zhCollator.compare(key(a), key(b))
            if (c != 0) return@Comparator c
        }
        0
    }

/**
 * 按 [sort] 重排这份壳的曲目。返回**新的一份**（`copy`），不改原对象 ——
 * 原始顺序那份由 [TrackListViewModel.loaded] 持有，是各次重排的唯一输入。
 */
private fun TrackCollection.applySort(sort: TrackSort): TrackCollection {
    val comparator = when (sort) {
        TrackSort.DEFAULT -> return this
        // 同名时按歌手再分一层：中文歌名撞名不少（「离别」之类），二级键让顺序稳定，
        // 免得同一份数据两次排序出来的顺序不一样。
        TrackSort.TITLE -> zhComparator({ it.name }, { it.artist })
        TrackSort.ARTIST -> zhComparator({ it.artist }, { it.name })
        // 时长是数字键，不走 Collator；等长时用歌名收尾。
        TrackSort.DURATION -> Comparator<Track> { a, b ->
            val c = a.durationMs.compareTo(b.durationMs)
            if (c != 0) c else zhCollator.compare(a.name, b.name)
        }
        TrackSort.ALBUM -> zhComparator({ it.albumName }, { it.name })
    }
    return copy(tracks = tracks.sortedWith(comparator))
}
