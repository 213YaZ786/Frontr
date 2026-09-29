package com.frontr.app.core.network

import com.frontr.app.core.debug.RequestLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import okhttp3.CookieJar

/**
 * The single HTTP client for the app.
 *
 * expectSuccess stays false on purpose. A 429 or a 403 is information we want
 * to classify and report, not an exception thrown from deep inside a plugin.
 *
 * Frontr reads Reddit as a logged out mobile browser: it names itself with
 * the identity of the phone's own web engine, the one that also runs Reddit's
 * browser check (see WebPages), and keeps the cookies Reddit sets for a
 * visitor, in the store that engine uses. Presenting as a browser was the
 * reader's own choice. Frontr reads only what a logged out visitor is shown,
 * and nothing that names the reader is sent.
 */
object HttpClientFactory {

    /** Used only where the phone's web engine cannot tell its own identity. */
    const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val REQUEST_TIMEOUT_MS = 15_000L

    fun create(log: RequestLog, userAgent: String, cookies: CookieJar = CookieJar.NO_COOKIES): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true

        engine {
            config { cookieJar(cookies) }
            // The browser identity is set here rather than as a default header:
            // Ktor appends default headers to a request's own, so a request
            // naming another identity would send both.
            addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") != null) request
                    else request.newBuilder().header("User-Agent", userAgent).build()
                )
            }
            addNetworkInterceptor(HttpTrace(log))
        }

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        defaultRequest {
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", java.util.Locale.getDefault().toLanguageTag() + ",en;q=0.5")
        }
    }
}
