package com.frontr.app.data.repository

import com.frontr.app.core.common.Outcome
import com.frontr.app.core.model.CommentLine
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Feed
import com.frontr.app.core.model.FeedSort
import com.frontr.app.core.model.PopularCountry
import com.frontr.app.core.model.Post
import com.frontr.app.data.accounts.AccountStore
import com.frontr.app.data.cache.FeedCache
import com.frontr.app.data.reddit.FeedAddress
import com.frontr.app.data.reddit.RedditApi

/**
 * Where a sub's posts and a post's comments come from. One source: Reddit's
 * public pages, which page back through a sub for real.
 */
class FeedRepository(
    private val api: RedditApi,
    private val accounts: AccountStore,
    private val cache: FeedCache
) {

    /**
     * A followed sub comes in the order chosen for it, Popular from its
     * country; a sub only opened, in Reddit's default order. Further pages
     * keep what the first one was asked with.
     */
    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> {
        if (cursor != null) return api.feed(handle, cursor)
        val account = accounts.accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) }
        val sort = account?.sort ?: FeedSort.BEST
        val country = if (handle.equals(PopularCountry.FEED, ignoreCase = true)) PopularCountry.resolve(account?.country) else null
        val first = api.feed(handle, address = FeedAddress.firstPage(spelling(handle, account?.displayName), sort, country))
        if (first !is Outcome.Success) return first
        return Outcome.Success(followOn(handle, first.value, sort))
    }

    /**
     * Reddit's first page holds 3 posts. Read alone at each refresh, the
     * posts that came between two refreshes past those 3 were never fetched
     * (Home asks a sub for more only at the very bottom). So a refresh reads
     * on, a page at a time (about 25 posts each), while pages bring posts
     * not stored yet, [EXTRA_PAGES] at most. In New order the first page
     * already tells: nothing new there, nothing new further.
     *
     * A page that fails ends the reading, and what came is kept.
     */
    private suspend fun followOn(handle: String, first: Feed, sort: FeedSort): Feed {
        val known = cache.read(handle)?.posts?.mapTo(HashSet()) { it.id }.orEmpty()
        val seen = first.posts.mapTo(HashSet()) { it.id }
        if (sort == FeedSort.NEW && known.isNotEmpty() && first.posts.all { it.id in known }) return first
        var feed = first
        for (page in 1..EXTRA_PAGES) {
            val cursor = feed.nextCursor ?: break
            val next = (api.feed(handle, cursor) as? Outcome.Success)?.value ?: break
            val fresh = next.posts.filter { seen.add(it.id) }
            feed = feed.copy(posts = feed.posts + fresh, nextCursor = next.nextCursor)
            if (fresh.none { it.id !in known }) break
        }
        return feed
    }

    /** The sub's name as Reddit spells it, learned from its posts, else as typed. */
    private suspend fun spelling(handle: String, followedName: String?): String =
        (followedName ?: cache.read(handle)?.displayName)
            ?.removePrefix("r/")
            ?.takeIf { it.equals(handle, ignoreCase = true) }
            ?: handle

    private companion object {
        /** Pages read after the first at a refresh: up to some 50 posts more per sub. */
        const val EXTRA_PAGES = 2
    }

    /** A post's comments. Never cached: they change all the time. */
    suspend fun loadConversation(id: String): Outcome<Conversation> = api.post(id)

    suspend fun loadMoreComments(more: CommentLine.More, post: Post): Outcome<List<CommentLine>> =
        api.moreComments(more, post.permalink, post.authorHandle)
}
