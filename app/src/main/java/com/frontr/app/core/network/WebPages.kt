package com.frontr.app.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frontr.app.core.debug.RequestLog
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

    override suspend fun open(url: String): String? = lock.withLock {
        val started = System.currentTimeMillis()
        val html = withContext(Dispatchers.Main) {
            main.removeCallbacks(release)
            val web = view ?: create().also { view = it }
            try {
                withTimeoutOrNull(TIMEOUT_MS) { settle(web, url) }
            } finally {
                // Stops the page's scripts until the next one is needed.
                web.loadUrl("about:blank")
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

    private suspend fun settle(web: WebView, url: String): String {
        finished = false
        web.loadUrl(url)
        while (true) {
            delay(POLL_MS)
            // Ready once the latest page has finished loading and it is not
            // the check page.
            if (finished && web.evaluate(STATE) == "ready") return web.evaluate(HTML).orEmpty()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.mediaPlaybackRequiresUserGesture = true
        // Laid out at a phone's size, as if on screen, so the page behaves as it would.
        layout(0, 0, 1080, 2400)
        webViewClient = object : WebViewClient() {
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
        evaluateJavascript(script) { raw ->
            // The result comes back as JSON: a quoted string, or null.
            val value = runCatching { (Json.parseToJsonElement(raw ?: "null") as? JsonPrimitive)?.contentOrNull }.getOrNull()
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
