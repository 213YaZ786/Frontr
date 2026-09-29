package com.frontr.app.data.reddit

import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.Outcome
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.core.link.RedditLink
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Feed
import com.frontr.app.core.network.ErrorMapper
import com.frontr.app.core.network.HostThrottle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reddit's public pages, as a logged out browser gets them: a sub's page and
 * the partial pages that continue it, and a post's page with its comments.
 * The HTML carries the data as attributes of Reddit's own elements, see
 * RedditParser.
 *
 * One host, one throttle. A Home refresh asks for every followed sub at once,
 * and a reader who reads politely is not the one Reddit blocks.
 */
class RedditApi(
    private val client: HttpClient,
    private val throttle: HostThrottle,
    private val log: RequestLog
) {

    /** A sub's newest page, or the page [cursor] points at further back. */
    suspend fun feed(sub: String, cursor: String? = null): Outcome<Feed> {
        val url = cursor?.let { "https://${RedditParser.HOST}$it" } ?: RedditLink.subUrl(sub)
        val kind = if (cursor == null) RequestLog.Kind.PROFILE else RequestLog.Kind.PAGE
        return when (val read = get(url, kind, sub)) {
            is Outcome.Success -> {
                val feed = RedditParser.feed(read.value, sub, System.currentTimeMillis())
                // A first page without a single post is not an empty sub, it is
                // a page Frontr could not read: an interstitial, a block, a change.
                if (cursor == null && feed.posts.isEmpty()) {
                    Outcome.Failure(AppError.ClientRefused(RedditParser.HOST, 200))
                } else {
                    Outcome.Success(feed)
                }
            }
            is Outcome.Failure -> read
        }
    }

    /** A post and its first comments. [thingId] is t3_ and the post id. */
    suspend fun post(thingId: String): Outcome<Conversation> =
        when (val read = get(RedditLink.postUrl(thingId), RequestLog.Kind.THREAD)) {
            is Outcome.Success -> RedditParser.conversation(read.value)?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.PostUnavailable(RedditParser.HOST, "This post is gone or not shown to logged out readers"))
            is Outcome.Failure -> read
        }

    private suspend fun get(url: String, kind: RequestLog.Kind, handle: String? = null): Outcome<String> =
        withContext(Dispatchers.IO) {
            val host = RedditParser.HOST
            if (!throttle.acquire(host)) {
                return@withContext Outcome.Failure(AppError.RateLimited(host, throttle.cooldownRemainingMs(host) / 1000))
            }
            val started = System.currentTimeMillis()
            try {
                val response = client.get(url)
                val body = response.bodyAsText()
                val status = response.status.value
                log.record(
                    kind = kind,
                    url = url,
                    outcome = if (status == 200) "ok" else "http $status",
                    httpStatus = status,
                    bodyBytes = body.length,
                    durationMillis = System.currentTimeMillis() - started
                )
                if (status == 429) throttle.penalise(host, response.headers["Retry-After"]?.toLongOrNull())
                ErrorMapper.fromStatus(host, status, response.headers["Retry-After"]?.toLongOrNull(), body, handle)
                    ?.let { Outcome.Failure(it) }
                    ?: Outcome.Success(body)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                log.record(kind, url, "transport failure", durationMillis = System.currentTimeMillis() - started, detail = failure.message)
                Outcome.Failure(ErrorMapper.fromThrowable(host, failure))
            }
        }
}
