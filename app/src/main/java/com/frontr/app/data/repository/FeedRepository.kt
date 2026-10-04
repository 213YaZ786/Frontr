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
        val country = if (handle.equals(PopularCountry.FEED, ignoreCase = true)) PopularCountry.resolve(account?.country) else null
        return api.feed(handle, address = FeedAddress.firstPage(spelling(handle, account?.displayName), account?.sort ?: FeedSort.BEST, country))
    }

    /** The sub's name as Reddit spells it, learned from its posts, else as typed. */
    private suspend fun spelling(handle: String, followedName: String?): String =
        (followedName ?: cache.read(handle)?.displayName)
            ?.removePrefix("r/")
            ?.takeIf { it.equals(handle, ignoreCase = true) }
            ?: handle

    /** A post's comments. Never cached: they change all the time. */
    suspend fun loadConversation(id: String): Outcome<Conversation> = api.post(id)

    suspend fun loadMoreComments(more: CommentLine.More, post: Post): Outcome<List<CommentLine>> =
        api.moreComments(more, post.permalink, post.authorHandle)
}
