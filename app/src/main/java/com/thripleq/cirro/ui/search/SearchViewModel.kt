package com.thripleq.cirro.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.playback.PlaybackLauncher
import com.thripleq.cirro.core.repo.SearchAlbum
import com.thripleq.cirro.core.repo.SearchArtist
import com.thripleq.cirro.core.repo.SearchPlaylist
import com.thripleq.cirro.core.repo.SearchRadio
import com.thripleq.cirro.core.repo.SearchRepository
import com.thripleq.cirro.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 搜索结果的分类页签（顺序与 kanade 对齐）。 */
enum class SearchTab { SONGS, PLAYLISTS, RADIOS, ALBUMS, ARTISTS }

/** 搜索结果页状态。`active == null` 表示还没搜过（展示分类标签的落地页）。 */
data class SearchUiState(
    val query: String = "",
    val active: String? = null,
    val tab: SearchTab = SearchTab.SONGS,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val songs: List<Track> = emptyList(),
    val playlists: List<SearchPlaylist> = emptyList(),
    val radios: List<SearchRadio> = emptyList(),
    val albums: List<SearchAlbum> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val error: Boolean = false,
    /** 触底翻页失败：列表尾部显示「点此重试」而非静默消失。 */
    val loadMoreFailed: Boolean = false,
    /** 最近搜索关键词（最新的在前），落地页展示；SharedPreferences 持久化。 */
    val history: List<String> = emptyList(),
)

/**
 * 搜索页 ViewModel：一个服务多分类（单曲/歌单/播客/专辑/歌手），按需懒加载、
 * 触底翻页。切换页签只拉取尚未开始的分类；重搜时整体重置。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: SearchRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 最近搜索：换行分隔的字符串（保留顺序，Set 会乱序）。
    private val historyPrefs =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    init {
        _uiState.update { it.copy(history = readHistory()) }
    }

    private fun readHistory(): List<String> =
        historyPrefs.getString(KEY_HISTORY, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

    private fun pushHistory(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val next = (listOf(kw) + readHistory().filter { it != kw }).take(MAX_HISTORY)
        historyPrefs.edit().putString(KEY_HISTORY, next.joinToString("\n")).apply()
        _uiState.update { it.copy(history = next) }
    }

    /** 点历史词：直接以该词搜索（并把它提到最前）。 */
    fun onHistoryClick(keyword: String) {
        if (keyword.isBlank()) return
        _uiState.update { it.copy(query = keyword) }
        pushHistory(keyword)
        resetTo(keyword)
        load(SearchTab.SONGS, reset = true)
    }

    fun onClearHistory() {
        historyPrefs.edit().remove(KEY_HISTORY).apply()
        _uiState.update { it.copy(history = emptyList()) }
    }

    // 每个分类的翻页游标与「已到底」标记，只对当前 active 关键词有效。
    private val offsets = mutableMapOf<SearchTab, Int>()
    private val started = mutableSetOf<SearchTab>()
    private val done = mutableSetOf<SearchTab>()

    /** 每个分类各自的加载 job。共用一个 job 会在切页签时误杀其他分类的请求，
     *  而被打断的分类已记入 started 却永远进不了 done，切回去就再也不会重拉（永久空白）。 */
    private val jobs = mutableMapOf<SearchTab, Job>()

    fun onQueryChange(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    /** 提交搜索（软键盘搜索键 / 点标签）。 */
    fun onSubmit() {
        val kw = _uiState.value.query.trim()
        if (kw.isEmpty()) return
        pushHistory(kw)
        resetTo(kw)
        load(SearchTab.SONGS, reset = true)
    }

    /** 点分类标签：直接以标签文字为关键词搜索。 */
    fun onTagSearch(tag: String) {
        _uiState.update { it.copy(query = tag) }
        pushHistory(tag)
        resetTo(tag)
        load(SearchTab.SONGS, reset = true)
    }

    fun onTabSelect(tab: SearchTab) {
        if (_uiState.value.active == null) return
        _uiState.update { it.copy(tab = tab) }
        if (tab !in started && tab !in done) load(tab, reset = true)
    }

    fun onLoadMore() {
        val s = _uiState.value
        val tab = s.tab
        if (s.active == null || s.loading || s.loadingMore) return
        if (tab in done) return
        load(tab, reset = false)
    }

    /** 首屏失败（error 空态）点此重试：当前分类同关键词重拉。 */
    fun onRetry() {
        val s = _uiState.value
        if (s.active == null || !s.error) return
        // 失败时 started 已被移除，直接 reset 拉取即可。
        load(s.tab, reset = true)
    }

    fun onClear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        offsets.clear(); started.clear(); done.clear()
        // 只重置当前搜索，保留最近搜索历史。
        _uiState.value = SearchUiState(history = _uiState.value.history)
    }

    fun onBack() {
        if (_uiState.value.active != null) onClear()
    }

    /** 单曲行点击：整张「单曲」结果作为队列，从该首开始播 —— **不弹播放页**
     *  （2026-10-03 用户：「点一首歌进行播放不要进入播放页，就单纯开始播放就行」）。 */
    fun onPlayTrack(index: Int) {
        val tracks = _uiState.value.songs
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            playback.play(context, tracks, index)
        }
    }

    /** 歌手行点击：没有独立歌手页，退回按该歌手名再搜一遍单曲。 */
    fun onArtistClick(name: String) {
        if (name.isBlank()) return
        onTagSearch(name)
    }

    private fun resetTo(keyword: String) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        offsets.clear(); started.clear(); done.clear()
        _uiState.update {
            it.copy(
                query = keyword,
                active = keyword,
                tab = SearchTab.SONGS,
                loading = true,
                loadingMore = false,
                songs = emptyList(),
                playlists = emptyList(),
                radios = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                error = false,
            )
        }
    }

    private fun load(tab: SearchTab, reset: Boolean) {
        val kw = _uiState.value.active ?: return
        val offset = if (reset) 0 else offsets[tab] ?: 0
        if (reset) {
            started.add(tab); done.remove(tab)
            clearTab(tab)
            _uiState.update { it.copy(loading = true, error = false, loadingMore = false, loadMoreFailed = false) }
        } else {
            _uiState.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        }
        jobs[tab]?.cancel()
        jobs[tab] = viewModelScope.launch {
            try {
                val page = fetch(tab, kw, offset)
                offsets[tab] = offset + page.size
                if (page.size < PAGE_SIZE) done.add(tab)
                _uiState.update { st ->
                    st.copy(
                        loading = if (st.tab == tab) false else st.loading,
                        loadingMore = false,
                        error = false,
                    ).let { withItems(it, tab, page, reset) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // 首屏失败（offset==0）：移出 started，切回该页签会自动整拉重试。
                // 翻页失败（offset>0）：**保留 started 与已翻数据**——若移除，用户切走再切回
                // 会走 reset 整拉，翻了几页的结果与滚动位置全部丢失；正确做法是留在原地，
                // 尾部给「点此重试」（loadMoreFailed），offsets 未动故重试从同一页继续。
                if (offset == 0) started.remove(tab)
                _uiState.update { st ->
                    st.copy(
                        loading = if (st.tab == tab) false else st.loading,
                        loadingMore = false,
                        error = offset == 0,
                        loadMoreFailed = offset > 0,
                    )
                }
            }
        }
    }

    private suspend fun fetch(tab: SearchTab, kw: String, offset: Int): List<Any> = when (tab) {
        SearchTab.SONGS -> repo.songs(kw, PAGE_SIZE, offset)
        SearchTab.PLAYLISTS -> repo.playlists(kw, PAGE_SIZE, offset)
        SearchTab.RADIOS -> repo.radios(kw, PAGE_SIZE, offset)
        SearchTab.ALBUMS -> repo.albums(kw, PAGE_SIZE, offset)
        SearchTab.ARTISTS -> repo.artists(kw, PAGE_SIZE, offset)
    }

    @Suppress("UNCHECKED_CAST")
    private fun withItems(
        st: SearchUiState,
        tab: SearchTab,
        page: List<Any>,
        reset: Boolean,
    ): SearchUiState = when (tab) {
        SearchTab.SONGS -> st.copy(songs = merge(st.songs, page as List<Track>, reset))
        SearchTab.PLAYLISTS -> st.copy(playlists = merge(st.playlists, page as List<SearchPlaylist>, reset))
        SearchTab.RADIOS -> st.copy(radios = merge(st.radios, page as List<SearchRadio>, reset))
        SearchTab.ALBUMS -> st.copy(albums = merge(st.albums, page as List<SearchAlbum>, reset))
        SearchTab.ARTISTS -> st.copy(artists = merge(st.artists, page as List<SearchArtist>, reset))
    }

    private fun <T> merge(current: List<T>, page: List<T>, reset: Boolean): List<T> =
        if (reset) page else current + page

    private fun clearTab(tab: SearchTab) {
        _uiState.update {
            when (tab) {
                SearchTab.SONGS -> it.copy(songs = emptyList())
                SearchTab.PLAYLISTS -> it.copy(playlists = emptyList())
                SearchTab.RADIOS -> it.copy(radios = emptyList())
                SearchTab.ALBUMS -> it.copy(albums = emptyList())
                SearchTab.ARTISTS -> it.copy(artists = emptyList())
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val KEY_HISTORY = "recent"
        const val MAX_HISTORY = 12
    }
}
