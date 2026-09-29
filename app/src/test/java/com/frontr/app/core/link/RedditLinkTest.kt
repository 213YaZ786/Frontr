package com.frontr.app.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditLinkTest {

    @Test
    fun `reads a sub on every Reddit host`() {
        assertEquals(RedditLink.Sub("Android"), RedditLink.parse("https://www.reddit.com/r/Android/"))
        assertEquals(RedditLink.Sub("Android"), RedditLink.parse("https://old.reddit.com/r/Android"))
        assertEquals(RedditLink.Sub("Android"), RedditLink.parse("http://m.reddit.com/r/Android/?feed=home"))
    }

    @Test
    fun `reads a post with or without its sub`() {
        assertEquals(RedditLink.Post("Android", "abc123"), RedditLink.parse("https://www.reddit.com/r/Android/comments/abc123/a_title/"))
        assertEquals(RedditLink.Post(null, "abc123"), RedditLink.parse("https://www.reddit.com/comments/abc123/"))
        assertEquals(RedditLink.Post(null, "abc123"), RedditLink.parse("https://redd.it/abc123"))
        assertEquals("t3_abc123", RedditLink.Post(null, "abc123").thingId)
    }

    @Test
    fun `leaves users, share links and other sites to the browser`() {
        assertNull(RedditLink.parse("https://www.reddit.com/user/someone"))
        assertNull(RedditLink.parse("https://www.reddit.com/r/Android/s/AbCdEf"))
        assertNull(RedditLink.parse("https://example.com/r/Android"))
        assertNull(RedditLink.parse("r/Android"))
    }

    @Test
    fun `builds the addresses it opens and shares`() {
        assertEquals("https://www.reddit.com/r/android/", RedditLink.subUrl("android"))
        assertEquals("https://www.reddit.com/comments/abc123/", RedditLink.postUrl("t3_abc123"))
    }
}
