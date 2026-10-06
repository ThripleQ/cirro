package com.thripleq.cirro.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.model.Comment
import com.thripleq.cirro.core.repo.CommentRepository
import com.thripleq.cirro.core.repo.CommentThread
import com.thripleq.cirro.core.repo.InteractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 *
 * 按 **[threadId]** 工作（不再是 songId）：歌单页的「评论」胶囊传
 * [CommentThread.playlist]，播放页传 [CommentThread.song]，专辑页传
 * [CommentThread.album] —— 三条路径共用这一个 ViewModel。
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repo: CommentRepository,
    private val interactions: InteractionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    /** 评论点赞失败时的提示（成功不打扰）。 */
    private val _message = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private var threadId: String? = null
    private var offset = 0

    fun load(thread: String) {
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
        val thread = threadId ?: return
        threadId = null
        load(thread)
    }

    /**
     * 点赞 / 取消点赞一条评论。
     *
     * 乐观更新：先把这一条翻过去（**热门区与最新区都要翻** —— 同一条评论可能两边
     * 都有，只改一处会看到两个不一样的心），请求失败再翻回来并提示。
     *
     * 注意热门区那份是服务端给的快照，翻页重载后会被服务端真值覆盖 —— 这也正是
     * 想要的（本地只是抢先画一下）。
     */
    fun toggleLike(comment: Comment) {
        val thread = threadId ?: return
        val target = !comment.liked
        updateComment(comment.id) { it.copy(liked = target, likedCount = nextCount(it, target)) }
        viewModelScope.launch {
            val r = interactions.setCommentLiked(thread, comment.id, target)
            if (!r.ok) {
                updateComment(comment.id) { it.copy(liked = !target, likedCount = nextCount(it, !target)) }
                _message.tryEmit(r.message)
            }
        }
    }

    /** 点赞数跟着状态走：翻过去 +1、翻回来 -1，且不为负（服务端计数可能已被别人改）。 */
    private fun nextCount(c: Comment, liked: Boolean): Long =
        if (liked == c.liked) c.likedCount
        else (c.likedCount + if (liked) 1L else -1L).coerceAtLeast(0L)

    /** 同一条评论在热门区和最新区各改一遍。 */
    private fun updateComment(id: String, f: (Comment) -> Comment) {
        _uiState.update { s ->
            s.copy(
                hot = s.hot.map { if (it.id == id) f(it) else it },
                latest = s.latest.map { if (it.id == id) f(it) else it },
            )
        }
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
