package com.frontr.app.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.Outcome
import com.frontr.app.core.model.CommentLine
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Post
import com.frontr.app.data.cache.FeedCache
import com.frontr.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ThreadState {
    data object Loading : ThreadState
    data class Ready(val conversation: Conversation) : ThreadState
    data class Failed(val error: AppError) : ThreadState
}

data class PostDetailUiState(
    val post: Post? = null,
    val lookingUp: Boolean = true,
    val thread: ThreadState = ThreadState.Loading,
    /** Cursors of the "more comments" lines being loaded, and of those that failed. */
    val loadingMore: Set<String> = emptySet(),
    val failedMore: Set<String> = emptySet()
) {
    /** Neither the phone nor the server could produce the post. */
    val missing: Boolean get() = post == null && !lookingUp && thread !is ThreadState.Loading
}

/**
 * Shows the post at once from what the phone already has, then fetches its
 * conversation: what it answers, the author's thread, and the comments,
 * then the comments Reddit leaves out of the page, as the reader gets to them.
 * Replies are never saved, they are only worth reading fresh.
 */
class PostDetailViewModel(
    private val cache: FeedCache,
    private val repository: FeedRepository
) : ViewModel() {

    /** What Share and Copy link hand out: the post's reddit.com address. */
    fun shareLink(post: Post): String = post.permalink

    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var hint: String? = null

    /**
     * [id] is the post's thing id, t3_ and the post id. Reddit answers
     * /comments/<id> for any post, so a post opened from any link can always
     * be looked up, and its page brings the post itself when the phone does
     * not have it.
     */
    fun load(id: String, from: String?) {
        if (loadedId == id) return
        loadedId = id
        hint = from
        viewModelScope.launch {
            val known = cache.find(id, from) ?: RecentPosts.get(id)
            _state.value = PostDetailUiState(post = known, lookingUp = false, thread = ThreadState.Loading)
            fetchThread()
        }
    }

    fun reload() {
        val id = loadedId ?: return
        loadedId = null
        _state.value = PostDetailUiState()
        load(id, hint)
    }

    fun retryThread() {
        if (_state.value.thread is ThreadState.Loading) return
        _state.value = _state.value.copy(thread = ThreadState.Loading)
        viewModelScope.launch { fetchThread() }
    }

    /**
     * Loads the comments a "more" line stands for and puts them in its place.
     * The answer may end with a further "more" line, which then takes over.
     */
    fun loadMore(more: CommentLine.More) {
        val now = _state.value
        val post = now.post ?: return
        if (more.cursor in now.loadingMore) return
        _state.value = now.copy(loadingMore = now.loadingMore + more.cursor, failedMore = now.failedMore - more.cursor)
        viewModelScope.launch {
            val outcome = repository.loadMoreComments(more, post)
            val current = _state.value
            val ready = current.thread as? ThreadState.Ready
            _state.value = when {
                outcome is Outcome.Success && ready != null -> {
                    val lines = ready.conversation.comments
                    val at = lines.indexOf(more)
                    // Comments already shown are not shown twice.
                    val shown = lines.mapNotNull { (it as? CommentLine.Reply)?.post?.id }.toSet()
                    val fresh = outcome.value.filter { it !is CommentLine.Reply || it.post.id !in shown }
                    val merged = if (at < 0) lines else lines.subList(0, at) + fresh + lines.subList(at + 1, lines.size)
                    RecentPosts.remember(fresh.mapNotNull { (it as? CommentLine.Reply)?.post })
                    current.copy(
                        thread = ThreadState.Ready(ready.conversation.copy(comments = merged)),
                        loadingMore = current.loadingMore - more.cursor
                    )
                }
                else -> current.copy(loadingMore = current.loadingMore - more.cursor, failedMore = current.failedMore + more.cursor)
            }
        }
    }

    private suspend fun fetchThread() {
        val id = loadedId ?: return
        when (val outcome = repository.loadConversation(id)) {
            is Outcome.Success -> {
                val conversation = outcome.value
                RecentPosts.remember(conversation)
                val known = _state.value.post
                _state.value = _state.value.copy(
                    post = conversation.main?.let { fresh -> known?.mergedWith(fresh) ?: fresh } ?: known,
                    thread = ThreadState.Ready(conversation)
                )
            }
            is Outcome.Failure -> _state.value = _state.value.copy(thread = ThreadState.Failed(outcome.error))
        }
    }
}

/**
 * Posts seen in conversations during this session, so tapping a reply opens
 * it at once instead of waiting for the network. Memory only, never saved.
 */
internal object RecentPosts {
    private const val CAPACITY = 300
    private val posts = object : LinkedHashMap<String, Post>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Post>?) = size > CAPACITY
    }

    @Synchronized
    fun get(id: String): Post? = posts[id]

    @Synchronized
    fun remember(conversation: Conversation) {
        remember(
            conversation.ancestors + listOfNotNull(conversation.main) + conversation.continuation +
                conversation.comments.mapNotNull { (it as? CommentLine.Reply)?.post }
        )
    }

    @Synchronized
    fun remember(list: List<Post>) {
        list.forEach { posts[it.id] = it }
    }
}
