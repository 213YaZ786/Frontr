package com.frontr.app.data.repository

import com.frontr.app.core.common.Outcome
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Feed
import com.frontr.app.data.reddit.RedditApi

/**
 * Where a sub's posts and a post's comments come from. One source: Reddit's
 * public pages, which page back through a sub for real.
 */
class FeedRepository(private val api: RedditApi) {

    suspend fun loadFeed(handle: String, cursor: String? = null): Outcome<Feed> = api.feed(handle, cursor)

    /** A post's comments. Never cached: they change all the time. */
    suspend fun loadConversation(id: String): Outcome<Conversation> = api.post(id)
}
