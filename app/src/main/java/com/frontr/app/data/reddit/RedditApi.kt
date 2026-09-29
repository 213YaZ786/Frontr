package com.frontr.app.data.reddit

import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.Outcome
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.core.link.RedditLink
import com.frontr.app.core.model.CommentLine
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Feed
import com.frontr.app.core.network.ErrorMapper
import com.frontr.app.core.network.HostThrottle
import com.frontr.app.core.network.PageBrowser
import com.frontr.app.core.network.WebCookieJar
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.parameters
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val log: RequestLog,
    private val browser: PageBrowser,
    private val cookies: WebCookieJar
) {

    private val checks = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checked = AtomicBoolean(false)
    private val passing = Mutex()
    private val passedAt = AtomicLong(0)

    /** A sub's newest page, or the page [cursor] points at further back. */
    suspend fun feed(sub: String, cursor: String? = null): Outcome<Feed> {
        val url = cursor?.let { "https://${RedditParser.HOST}$it" } ?: RedditLink.subUrl(sub)
        val kind = if (cursor == null) RequestLog.Kind.PROFILE else RequestLog.Kind.PAGE
        return when (val read = get(url, kind, sub)) {
            is Outcome.Success -> {
                var page = read.value
                var feed = RedditParser.feed(page, sub, System.currentTimeMillis())
                if (cursor == null && feed.posts.isEmpty() && !RedditParser.isCheckPage(read.value)) {
                    // A page that came straight but bare, the frame of the site
                    // without its posts: the web engine gets the page a browser
                    // would, posts included.
                    log.record(kind, url, "page without posts, opening it in the web engine", bodyBytes = read.value.length, detail = RedditParser.describe(read.value))
                    passing.withLock { browser.open(url) }?.let {
                        page = it
                        feed = RedditParser.feed(it, sub, System.currentTimeMillis())
                    }
                }
                // A first page without a single post is not an empty sub, it is
                // a page Frontr could not read: an interstitial, a block, a change.
                if (cursor == null && feed.posts.isEmpty()) {
                    // What the page was, so a log sent in shows it: a sub page
                    // that changed shape, a check page, a notice.
                    log.record(kind, url, "no posts read", httpStatus = 200, bodyBytes = page.length, detail = RedditParser.describe(page))
                    check(sub, url, page)
                    Outcome.Failure(AppError.ClientRefused(RedditParser.HOST, 200))
                } else {
                    // Whether the sub's icon and banner were found, and what
                    // the banner block held, so a log sent in tells a sub
                    // without a banner from a page Frontr misread.
                    if (cursor == null) log.record(kind, url, "sub header", detail = RedditParser.headerNote(page))
                    Outcome.Success(feed)
                }
            }
            is Outcome.Failure -> {
                // Only when Reddit answered and said no: offline, a timeout or
                // a rate limit have nothing to learn from other addresses.
                val refused = read.error is AppError.ClientRefused || read.error is AppError.ServerError ||
                    read.error is AppError.AccountNotFound || read.error is AppError.AccountUnavailable
                if (cursor == null && refused) check(sub, url, null)
                read
            }
        }
    }

    /**
     * Once per launch, the first time a sub's page cannot be read, even
     * through the web engine: keeps that page whole in the log, then asks for
     * the same posts in the other ways Reddit serves them to a logged out
     * visitor, and records what each gives. One run of the app then tells
     * which one Reddit still answers, instead of a guess and another try. Spaced out, in the background, the
     * error is shown without waiting for it.
     */
    private fun check(sub: String, url: String, page: String?) {
        if (!checked.compareAndSet(false, true)) return
        page?.let { log.keepPage("page of r/$sub that gave no posts, $url", it) }
        checks.launch {
            val tries = listOf(
                "next posts partial" to "https://${RedditParser.HOST}/svc/shreddit/community-more-posts/hot/?name=$sub",
                "old Reddit page" to "https://old.reddit.com/r/$sub/",
                "RSS feed" to "https://${RedditParser.HOST}/r/$sub/.rss",
                "JSON listing" to "https://${RedditParser.HOST}/r/$sub/.json?raw_json=1"
            )
            for ((label, address) in tries) {
                delay(CHECK_GAP_MS)
                val started = System.currentTimeMillis()
                try {
                    val response = client.get(address)
                    val body = response.bodyAsText()
                    val status = response.status.value
                    val read = RedditParser.posts(body).size
                    log.record(
                        kind = RequestLog.Kind.PROBE,
                        url = address,
                        outcome = "check, $label: http $status, $read posts read, ${RedditParser.postIds(body)} post ids seen",
                        httpStatus = status,
                        bodyBytes = body.length,
                        durationMillis = System.currentTimeMillis() - started,
                        detail = RedditParser.describe(body)
                    )
                    if (read == 0) log.keepPage("check, $label, http $status, $address", body)
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    log.record(RequestLog.Kind.PROBE, address, "check, $label: transport failure", durationMillis = System.currentTimeMillis() - started, detail = failure.toString())
                }
            }
        }
    }

    /** A post and its comments. [thingId] is t3_ and the post id. */
    suspend fun post(thingId: String): Outcome<Conversation> =
        when (val read = get(RedditLink.postUrl(thingId), RequestLog.Kind.THREAD)) {
            is Outcome.Success -> RedditParser.conversation(read.value)?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.PostUnavailable(RedditParser.HOST, "This post is gone or not shown to logged out readers"))
            is Outcome.Failure -> read
        }

    /**
     * Comments Reddit left out of a post's page, the way its own page gets
     * them: a form sent back to the address the page names, with the
     * cursor it gave and the csrf_token cookie the site set. [post] is the
     * post's address, the page the call is made from.
     */
    suspend fun moreComments(more: CommentLine.More, post: String, sub: String): Outcome<List<CommentLine>> =
        withContext(Dispatchers.IO) {
            val host = RedditParser.HOST
            val url = "https://$host${more.path}"
            if (!throttle.acquire(host)) {
                return@withContext Outcome.Failure(AppError.RateLimited(host, throttle.cooldownRemainingMs(host) / 1000))
            }
            val csrf = cookies.value("https://$host/", "csrf_token")
            val started = System.currentTimeMillis()
            try {
                val response = client.submitForm(
                    url = url,
                    formParameters = parameters {
                        append("cursor", more.cursor)
                        csrf?.let { append("csrf_token", it) }
                    }
                ) {
                    header("Origin", "https://$host")
                    header("Referer", post)
                }
                val body = response.bodyAsText()
                val status = response.status.value
                val lines = if (status == 200) RedditParser.comments(body, sub, at = more) else emptyList()
                val replies = lines.count { it is CommentLine.Reply }
                log.record(
                    kind = RequestLog.Kind.THREAD,
                    url = url,
                    outcome = if (status == 200) "more comments: $replies read, ${lines.size - replies} further blocks" else "more comments: http $status",
                    httpStatus = status,
                    bodyBytes = body.length,
                    durationMillis = System.currentTimeMillis() - started,
                    // Whether the cookie was there, never its value.
                    detail = "csrf_token ${if (csrf != null) "sent" else "not set"}" +
                        if (replies == 0) ", ${RedditParser.describe(body)}" else ""
                )
                if (lines.isEmpty()) log.keepPage("more comments that gave none, http $status, $url", body)
                if (status == 429) throttle.penalise(host, response.headers["Retry-After"]?.toLongOrNull())
                ErrorMapper.fromStatus(host, status, response.headers["Retry-After"]?.toLongOrNull(), body, null)
                    ?.let { Outcome.Failure(it) }
                    ?: if (lines.isEmpty()) Outcome.Failure(AppError.ClientRefused(host, status)) else Outcome.Success(lines)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                log.record(RequestLog.Kind.THREAD, url, "more comments: transport failure", durationMillis = System.currentTimeMillis() - started, detail = failure.toString())
                Outcome.Failure(ErrorMapper.fromThrowable(host, failure))
            }
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
                var body = response.bodyAsText()
                val status = response.status.value
                val landed = response.call.request.url.toString()
                log.record(
                    kind = kind,
                    url = url,
                    outcome = if (status == 200) "ok" else "http $status",
                    httpStatus = status,
                    bodyBytes = body.length,
                    durationMillis = System.currentTimeMillis() - started,
                    detail = landed.takeIf { it != url }?.let { "redirected to $it" }
                )
                if (status == 200 && RedditParser.isCheckPage(body)) {
                    // Reddit wants to check the browser first: let the phone's
                    // web engine pass it, as a browser would, and read the page
                    // it then shows. Its cookie serves the requests that follow.
                    body = pass(url, kind, started) ?: body
                }
                if (status == 429) throttle.penalise(host, response.headers["Retry-After"]?.toLongOrNull())
                ErrorMapper.fromStatus(host, status, response.headers["Retry-After"]?.toLongOrNull(), body, handle)
                    ?.let { Outcome.Failure(it) }
                    ?: Outcome.Success(body)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                log.record(kind, url, "transport failure", durationMillis = System.currentTimeMillis() - started, detail = failure.toString())
                Outcome.Failure(ErrorMapper.fromThrowable(host, failure))
            }
        }

    /**
     * The page behind Reddit's browser check. One at a time: a Home refresh
     * asks for every sub at once, and once the first has been through the
     * check, the cookie it earned lets the others come straight over HTTP,
     * so they try that before queueing for the web engine.
     */
    private suspend fun pass(url: String, kind: RequestLog.Kind, askedAt: Long): String? = passing.withLock {
        if (passedAt.get() > askedAt) {
            val again = runCatching { client.get(url).bodyAsText() }.getOrNull()
            if (again != null && !RedditParser.isCheckPage(again)) {
                log.record(kind, url, "read directly after the browser check", httpStatus = 200, bodyBytes = again.length)
                return@withLock again
            }
        }
        log.record(kind, url, "browser check page, opening it in the web engine")
        browser.open(url)?.also { passedAt.set(System.currentTimeMillis()) }
    }

    private companion object {
        const val CHECK_GAP_MS = 1_500L
    }
}
