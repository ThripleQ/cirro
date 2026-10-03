package com.thripleq.nume.ui.artist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ArtistAlbum
import com.thripleq.nume.core.repo.ArtistProfile
import com.thripleq.nume.core.repo.ArtistRepository
import com.thripleq.nume.core.repo.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 歌手主页状态。 */
sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data object Error : ArtistUiState
    data class Ready(
        val profile: ArtistProfile,
        val hotSongs: List<Track>,
        val albums: List<ArtistAlbum>,
    ) : ArtistUiState
}

/**
 * 歌手主页 ViewModel：并发拉取资料/热门单曲（artist detail）与专辑列表
 * （artist albums）。点歌以 hotSongs 为队列整单起播（**不进播放页**，2026-10-03 起）。
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repo: ArtistRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private var loadedId: String? = null

    /** 当前加载 job。id 快速变化时取消上一个，避免旧请求的结果（尤其是 Error）
     *  在新请求之后落地，把新歌手的页面覆盖掉。 */
    private var job: Job? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        _uiState.value = ArtistUiState.Loading
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val (page, albums) = coroutineScope {
                    val p = async { repo.page(id) }
                    val a = async { runCatching { repo.albums(id) }.getOrDefault(emptyList()) }
                    p.await() to a.await()
                }
                _uiState.value = ArtistUiState.Ready(page.profile, page.hotSongs, albums)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = ArtistUiState.Error
            }
        }
    }

    fun retry() {
        val id = loadedId ?: return
        loadedId = null
        load(id)
    }

    /** 热歌行：点了直接播 —— **不弹播放页**（2026-10-03 用户：「点一首歌进行播放不要进入
     *  播放页，就单纯开始播放就行」）。 */
    fun onPlayTrack(index: Int) {
        val ready = _uiState.value as? ArtistUiState.Ready ?: return
        if (index !in ready.hotSongs.indices) return
        viewModelScope.launch {
            playback.play(context, ready.hotSongs, index)
        }
    }
}
