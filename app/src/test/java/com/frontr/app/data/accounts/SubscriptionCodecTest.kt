package com.frontr.app.data.accounts

import com.frontr.app.core.model.FollowedAccount
import com.frontr.app.core.model.FollowedAccount.Companion.MAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCodecTest {

    @Test
    fun `folders survive an export and an import`() {
        val accounts = listOf(
            FollowedAccount("space", folder = "Science"),
            FollowedAccount("astronomy", folder = "Science"),
            FollowedAccount("worldnews", folder = "News"),
            FollowedAccount("android")
        )
        val file = SubscriptionCodec.export(accounts, listOf(MAIN, "News", "Science", "Empty"), nowMillis = 0)

        assertEquals(
            listOf(
                SubscriptionCodec.Entry("space", "Science"),
                SubscriptionCodec.Entry("astronomy", "Science"),
                SubscriptionCodec.Entry("worldnews", "News"),
                SubscriptionCodec.Entry("android", null)
            ),
            SubscriptionCodec.import(file)
        )
        assertTrue("\"name\":\"Empty\"" in file)
        assertTrue("\"name\":\"Main\"" !in file)
    }

    @Test
    fun `plain text takes sub names and reddit links, a post link gives its sub`() {
        assertEquals(
            listOf(SubscriptionCodec.Entry("android"), SubscriptionCodec.Entry("space"), SubscriptionCodec.Entry("worldnews")),
            SubscriptionCodec.import("https://www.reddit.com/r/Android/comments/abc123/a_title/\nspace, r/worldnews")
        )
    }
}
