package com.frontr.app.core.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Reddit's cookies, shared between the HTTP client and the web engine.
 *
 * Reddit checks a visitor's browser once and remembers it with a cookie.
 * The check runs in the web engine (see WebPages); keeping one cookie store,
 * Android's own, lets the pages that follow come straight over HTTP like a
 * browser's next requests would. Only Reddit's hosts get or give cookies.
 */
class WebCookieJar : CookieJar {

    private val cookies: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isReddit(url)) return
        cookies.forEach { this.cookies.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isReddit(url)) return emptyList()
        val raw = cookies.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    private fun isReddit(url: HttpUrl) = url.host == "reddit.com" || url.host.endsWith(".reddit.com")
}
