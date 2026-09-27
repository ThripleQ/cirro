package com.thripleq.nume.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.nume.core.playback.PlaybackLauncher
import com.thripleq.nume.core.repo.ChartRepository
import com.thripleq.nume.core.repo.ProfileRepository
import com.thripleq.nume.core.repo.Track
import com.thripleq.nume.core.repo.TrackCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which data source a [TrackListScreen] shows. */
enum class TrackListSource(val wire: String) {
    CHART("chart"),
    LIKED("liked"),
    PURCHASED("purchased"),
    PLAYLIST("playlist"),
    ALBUM("album");

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
 * 统一"壳子 + 列表"页：榜单 / 歌单 / 专辑走真实后端壳，
 * 喜欢 / 已购没有独立壳，用已有数据组装一个简化壳（标题 + 曲目数）。
 */
@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val chartRepo: ChartRepository,
    private val profileRepo: ProfileRepository,
    private val playback: PlaybackLauncher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TrackListUiState>(TrackListUiState.Loading)
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    private var loadedKey: String? = null

    fun load(source: TrackListSource, id: String, title: String) {
        val key = "${source.wire}:$id"
        if (loadedKey == key) return
        loadedKey = key
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
            }
            _uiState.value = when {
                collection == null -> TrackListUiState.Error
                collection.tracks.isEmpty() -> TrackListUiState.Empty
                else -> TrackListUiState.Ready(collection)
            }
        }
    }

    fun onTrackClick(collection: TrackCollection, index: Int) {
        viewModelScope.launch {
            playback.play(context, collection.tracks, index)
            _openPlayer.tryEmit(Unit)
        }
    }

    /** 头部「播放」按钮：从第一首开始整单播放。 */
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
