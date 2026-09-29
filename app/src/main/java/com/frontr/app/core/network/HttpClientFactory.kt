package com.frontr.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * The single HTTP client for the app.
 *
 * expectSuccess stays false on purpose. A 429 or a 403 is information we want
 * to classify and report, not an exception thrown from deep inside a plugin.
 *
 * No cookies, and the requests of a mobile browser: Reddit refuses scripted
 * clients and answers a logged out browser with the same public pages anyone
 * sees. Presenting as a browser was the reader's own choice. Frontr reads only
 * what a logged out visitor is shown, and nothing that names the reader is sent.
 */
object HttpClientFactory {

    const val USER_AGENT = "Mozilla/5.0 (Android 16; Mobile; rv:150.0) Gecko/150.0 Firefox/150.0"

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val REQUEST_TIMEOUT_MS = 15_000L

    fun create(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }

        defaultRequest {
            header("User-Agent", USER_AGENT)
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", java.util.Locale.getDefault().toLanguageTag() + ",en;q=0.5")
        }
    }
}
