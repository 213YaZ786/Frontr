package com.frontr.app.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.data.reddit.RedditHost
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume

/** Opens a page the way a browser does and gives back what it ends up showing. */
fun interface PageBrowser {
    /** The page's HTML once it has settled, or null when it never did. */
    suspend fun open(url: String): String?
}

/**
 * Android's web engine, used as the browser it is, for the pages Reddit
 * will only show after checking the visitor's browser.
 *
 * Reddit answers a visitor it does not know yet with a short page whose
 * script checks the browser and then reloads the real page. The engine runs
 * that script exactly as Chrome would, and Frontr takes the page that
 * follows. The cookie Reddit sets on the way lands in the store the HTTP
 * client shares (see WebCookieJar), so the next pages usually come directly.
 *
 * One hidden view, one page at a time, pictures not loaded. It is let go
 * after a minute unused, so it costs nothing while Frontr only reads.
 */
class WebPages(private val context: Context, private val log: RequestLog) : PageBrowser {

    private val lock = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private val release = Runnable { view?.destroy(); view = null }
    private var view: WebView? = null
    private var finished = false

    // The renderer died (onRenderProcessGone): settle() answers at once, and the read waiting on a script is let go.
    private var engineGone = false
    private var waiting: CancellableContinuation<String?>? = null

    override suspend fun open(url: String): String? = lock.withLock {
        val started = System.currentTimeMillis()
        val html = withContext(Dispatchers.Main) {
            main.removeCallbacks(release)
            val web = view ?: create().also { view = it }
            try {
                withTimeoutOrNull(TIMEOUT_MS) { settle(web, url) }
            } finally {
                // Stops the page's scripts until the next one is needed. A view the renderer's death destroyed is gone.
                if (view === web) web.loadUrl("about:blank")
                main.postDelayed(release, IDLE_MS)
            }
        }
        CookieManager.getInstance().flush()
        log.record(
            kind = RequestLog.Kind.HTTP,
            url = url,
            outcome = if (html != null) "web engine: page read" else "web engine: no page after ${TIMEOUT_MS / 1000} s",
            bodyBytes = html?.length,
            durationMillis = System.currentTimeMillis() - started
        )
        html
    }

    private suspend fun settle(web: WebView, url: String): String? {
        finished = false
        engineGone = false
        web.loadUrl(url)
        while (true) {
            delay(POLL_MS)
            // The renderer died: nothing will finish, no 25 s wait.
            if (engineGone) return null
            // Ready once the latest page has finished loading and it is not
            // the check page.
            if (finished && web.evaluate(STATE) == "ready") {
                val html = web.evaluate(HTML)
                return if (engineGone) null else html.orEmpty()
            }
        }
    }

    /**
     * The view, locked down as MTGA's ChallengeDriver: no file or content access, no geolocation, no windows opened
     * by a page, mixed content never, third party cookies off, downloads dropped, the main frame kept on Reddit.
     * Images stay off (Frontr reads the page whole). A dead renderer: the view destroyed, its reference dropped, true
     * returned (Android's "Handle WebView termination", which ChallengeDriver follows).
     * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/web/ChallengeDriver.kt#ChallengeDriver
     * not ported: poll (settle() polls the page)
     * not ported: inspect (ChallengeDetector's check logic, Frontr reads the page whole)
     * not ported: finish (settle() returns)
     * not ported: decode (evaluate() decodes)
     * not ported: onTaskHost (RedditHost.owns)
     * not ported: release (open() and the idle runnable destroy the view)
     * not ported: shouldInterceptRequest (X's trackers and media lists; images are off here)
     * not ported: onPageStarted (this file's own, it sets finished)
     * not ported: onPageFinished (this file's own, it sets finished)
     * not ported: onReceivedHttpError (this file's own, it logs)
     * not ported: onReceivedSslError (the client's default already cancels the load)
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        setDownloadListener { _, _, _, _, _ -> }
        // Laid out at a phone's size, as if on screen, so the page behaves as it would.
        layout(0, 0, 1080, 2400)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Frames must be free to load: a check may live in an iframe on its own domain. Only the top page is
                // kept on Reddit (https, www.reddit.com, no user name).
                if (!request.isForMainFrame) return false
                return !RedditHost.owns(request.url.toString())
            }

            /** Without this, a crashed renderer takes the whole app down. */
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                log.record(RequestLog.Kind.HTTP, "about:blank", "web engine: stopped", detail = if (detail.didCrash()) "crashed" else "killed")
                engineGone = true
                waiting?.let { if (it.isActive) it.resume(null) }
                waiting = null
                main.removeCallbacks(release)
                view.destroy()
                this@WebPages.view = null
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url == "about:blank") return
                // A new page: wait for this one to finish, the check page's
                // own finish does not count.
                finished = false
                log.record(RequestLog.Kind.HTTP, url, "web engine: loading")
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == "about:blank") return
                finished = true
                log.record(RequestLog.Kind.HTTP, url, "web engine: loaded")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    log.record(RequestLog.Kind.HTTP, request.url.toString(), "web engine: http ${response.statusCode}", httpStatus = response.statusCode)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    log.record(RequestLog.Kind.HTTP, request.url.toString(), "web engine: failed", detail = "${error.errorCode} ${error.description}")
                }
            }
        }
    }

    private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { done ->
        waiting = done
        evaluateJavascript(script) { raw ->
            // The result comes back as JSON: a quoted string, or null. A page
            // past the network's cap is no page either (HttpClientFactory.MAX_BODY_BYTES).
            val value = if ((raw?.length ?: 0) > HttpClientFactory.MAX_BODY_BYTES) null else runCatching { (Json.parseToJsonElement(raw ?: "null") as? JsonPrimitive)?.contentOrNull }.getOrNull()
            if (done.isActive) done.resume(value)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 25_000L
        const val POLL_MS = 400L
        const val IDLE_MS = 60_000L
        const val STATE = """(function(){
            if (document.querySelector('input[name="js_challenge"]')) return 'check';
            return document.readyState === 'loading' ? 'loading' : 'ready';
        })()"""
        const val HTML = "document.documentElement.outerHTML"
    }
}
