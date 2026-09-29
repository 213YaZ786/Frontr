package com.frontr.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PostMergeTest {

    private fun post(text: String) = Post(
        id = "t3_abc", authorHandle = "googlepixel", authorName = "r/GooglePixel",
        text = text, publishedAtMillis = 1, permalink = "https://www.reddit.com/r/GooglePixel/comments/abc/"
    )

    @Test
    fun `a text post opened from the list gets its body from its own page`() {
        val fromList = post("I'm Done")
        val fromPage = post("I'm Done\n\nUnless there is an immediate about face...")
        assertEquals(fromPage.text, fromList.mergedWith(fromPage).text)
        // and a list read later does not cut it back to the title
        assertEquals(fromPage.text, fromPage.mergedWith(fromList).text)
    }
}
