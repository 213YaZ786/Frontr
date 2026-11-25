package com.frontr.app.data.reddit

import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.Outcome
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.core.model.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * The guard of RedditApi.feed and post (readPage), reached without a network: a page whose parsing throws
 * is a failure, never an exception up to the screen, and a cancelled read still cancels.
 * Ports https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/html/HtmlSource.kt#fetchTimeline
 * (its catch of Throwable and the rethrown CancellationException).
 */
class RedditApiTest {

    private val url = "https://www.reddit.com/r/examples/"

    @Test
    fun `a parse that throws gives a failure, logged with the exception's class`() {
        val log = RequestLog()
        val outcome = readPage<Feed>(log, RequestLog.Kind.PROFILE, url) { throw IllegalStateException("boom") }
        assertTrue(outcome is Outcome.Failure)
        assertEquals(AppError.ClientRefused(RedditParser.HOST, 200), (outcome as Outcome.Failure).error)
        val entry = log.entries.value.single()
        assertEquals("page could not be read", entry.outcome)
        assertEquals("java.lang.IllegalStateException", entry.detail)
        assertEquals(url, entry.url)
    }

    @Test
    fun `an error as well as an exception is caught, a stack overflow included`() {
        val log = RequestLog()
        val outcome = readPage<Feed>(log, RequestLog.Kind.THREAD, url) { throw StackOverflowError() }
        assertTrue(outcome is Outcome.Failure)
    }

    @Test
    fun `a cancelled read still cancels, and is not logged`() {
        val log = RequestLog()
        try {
            readPage<Feed>(log, RequestLog.Kind.PAGE, url) { throw CancellationException("left the screen") }
            fail("the cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("left the screen", expected.message)
        }
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun `a good read passes through untouched`() {
        val log = RequestLog()
        val feed = RedditParser.feed("<shreddit-post id=\"t3_aaa111\" subreddit-name=\"Examples\"></shreddit-post>", "examples", nowMillis = 1)
        val outcome = readPage(log, RequestLog.Kind.PROFILE, url) { Outcome.Success(feed) }
        assertEquals(Outcome.Success(feed), outcome)
        assertTrue(log.entries.value.isEmpty())
    }
}
