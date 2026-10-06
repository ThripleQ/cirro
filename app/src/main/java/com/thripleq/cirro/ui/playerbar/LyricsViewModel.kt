package com.thripleq.cirro.ui.playerbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.repo.LyricRepository
import com.thripleq.cirro.core.repo.Lyrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data object Empty : LyricsUiState
    data class Ready(val lyrics: Lyrics) : LyricsUiState
}

/**
 * 播放页歌词：按当前曲目惰性加载（打开歌词面板才请求）。Activity 作用域，
 * 随曲目变化重载；[LyricRepository] 内存缓存避免反复拉同一首。
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repo: LyricRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var job: Job? = null

    fun load(songId: String?) {
        if (songId.isNullOrBlank()) {
            loadedId = null
            job?.cancel()
            _state.value = LyricsUiState.Idle
            return
        }
        if (loadedId == songId) return
        loadedId = songId
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LyricsUiState.Loading
            val lyrics = repo.lyrics(songId)
            if (loadedId != songId) return@launch
            _state.value = if (lyrics.isEmpty) LyricsUiState.Empty else LyricsUiState.Ready(lyrics)
        }
    }
}
