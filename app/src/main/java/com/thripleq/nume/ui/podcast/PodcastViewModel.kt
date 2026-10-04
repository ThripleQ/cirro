package com.thripleq.nume.ui.podcast

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.PodcastRepository
import com.thripleq.nume.core.repo.Program
import com.thripleq.nume.core.repo.RadioDetail
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 播客/电台页状态。 */
data class PodcastUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val detail: RadioDetail? = null,
    val programs: List<Program> = emptyList(),
    val loadingMore: Boolean = false,
    val end: Boolean = false,
)

/**
 * 播客页 ViewModel：并发拉取电台资料与首页节目，触底翻页。
 * 点节目以「可播放节目」为队列整单起播（**不进播放页**，2026-10-03 起）。
 */
@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repo: PodcastRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastUiState())
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    private var loadedId: String? = null
    private var offset = 0

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        offset = 0
        _uiState.value = PodcastUiState(loading = true)
        viewModelScope.launch {
            try {
                val (detail, programs) = coroutineScope {
                    val d = async { repo.radio(id) }
                    val p = async { repo.programs(id, PAGE_SIZE, 0) }
                    d.await() to p.await()
                }
                offset = programs.size
                _uiState.value = PodcastUiState(
                    loading = false,
                    detail = detail,
                    programs = programs,
                    end = programs.size < PAGE_SIZE,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = PodcastUiState(loading = false, error = true)
            }
        }
    }

    fun retry() {
        val id = loadedId ?: return
        loadedId = null
        load(id)
    }

    fun loadMore() {
        val s = _uiState.value
        val id = loadedId ?: return
        if (s.loading || s.loadingMore || s.end) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repo.programs(id, PAGE_SIZE, offset)
                offset += page.size
                _uiState.update {
                    it.copy(
                        programs = it.programs + page,
                        loadingMore = false,
                        end = page.size < PAGE_SIZE,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** 点某期节目：以可播放节目为队列，从该节目位置开始播 —— **不弹播放页**
     *  （2026-10-03 用户：「点一首歌进行播放不要进入播放页，就单纯开始播放就行」）。 */
    fun onPlayProgram(program: Program) {
        val songId = program.songId ?: return
        val programs = _uiState.value.programs
        // 节目列表 → 可播队列的转换按列表实例缓存：连点同一批里的第二期时直接复用，
        // 不必再为上百期节目逐个 toTrack 新建对象。列表只在加载/翻页时换实例，
        // 所以引用比较就够；换实例就重算，语义与原来一致。
        val queue = if (cachedPrograms === programs) {
            cachedQueue
        } else {
            programs.mapNotNull { it.toTrack() }.also {
                cachedPrograms = programs
                cachedQueue = it
            }
        }
        val index = queue.indexOfFirst { it.id == songId }
        if (index < 0) return
        viewModelScope.launch {
            playback.play(context, queue, index)
        }
    }

    private var cachedPrograms: List<Program>? = null
    private var cachedQueue: List<Track> = emptyList()

    private companion object {
        const val PAGE_SIZE = 30
    }
}
