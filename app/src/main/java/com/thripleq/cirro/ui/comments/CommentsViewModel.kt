package com.thripleq.cirro.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.repo.Comment
import com.thripleq.cirro.core.repo.CommentRepository
import com.thripleq.cirro.core.repo.CommentThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 歌曲评论页状态。 */
data class CommentsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val hot: List<Comment> = emptyList(),
    val latest: List<Comment> = emptyList(),
    val total: Long = 0L,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/**
 * 评论 ViewModel：首屏拿热门 + 最新，触底用 offset 翻页。
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repo: CommentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private var threadId: String? = null
    private var offset = 0

    fun load(songId: String) {
        val thread = CommentThread.song(songId)
        if (threadId == thread) return
        threadId = thread
        offset = 0
        _uiState.value = CommentsUiState(loading = true)
        viewModelScope.launch {
            try {
                val page = repo.comments(thread, PAGE_SIZE, 0, 0L)
                offset = page.latest.size
                _uiState.value = CommentsUiState(
                    loading = false,
                    hot = page.hot,
                    latest = page.latest,
                    total = page.total,
                    hasMore = page.hasMore,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = CommentsUiState(loading = false, error = true)
            }
        }
    }

    fun retry() {
        val id = threadId ?: return
        threadId = null
        val songId = id.removePrefix("R_SO_4_")
        load(songId)
    }

    fun loadMore() {
        val thread = threadId ?: return
        val s = _uiState.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repo.comments(thread, PAGE_SIZE, offset, 0L)
                offset += page.latest.size
                _uiState.update {
                    it.copy(
                        latest = it.latest + page.latest,
                        loadingMore = false,
                        hasMore = page.hasMore,
                        total = if (page.total > 0) page.total else it.total,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loadingMore = false) }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
