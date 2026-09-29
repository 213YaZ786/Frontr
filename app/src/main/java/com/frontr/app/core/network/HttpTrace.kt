package com.frontr.app.core.network

import com.frontr.app.core.debug.RequestLog
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Records every exchange with Reddit's own hosts as it happens on the wire,
 * redirects included, one entry per hop: the headers actually sent, the
 * status and headers that came back, the protocol and the TLS session. What
 * a page looked like is not enough to tell a refusal apart from a changed
 * page; this is.
 *
 * Cookie values are replaced by an ellipsis, only their names are kept.
 * Pictures and videos, on other hosts, are left out.
 */
class HttpTrace(private val log: RequestLog) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (host != "reddit.com" && !host.endsWith(".reddit.com")) return chain.proceed(request)
        val started = System.currentTimeMillis()
        val response = chain.proceed(request)
        val tls = chain.connection()?.handshake()?.let { "${it.tlsVersion.javaName} ${it.cipherSuite.javaName}" }
        log.record(
            kind = RequestLog.Kind.HTTP,
            url = request.url.toString(),
            outcome = "${request.method} ${response.code} ${response.protocol}",
            httpStatus = response.code,
            durationMillis = System.currentTimeMillis() - started,
            detail = buildList {
                add("sent:")
                addAll(lines(request.headers))
                add("received:")
                addAll(lines(response.headers))
                tls?.let { add("tls: $it") }
            }.joinToString("\n")
        )
        return response
    }

    private fun lines(headers: Headers): List<String> =
        headers.names().sorted().map { name ->
            val values = headers.values(name)
            val shown = if (name.equals("cookie", true) || name.equals("set-cookie", true)) {
                values.flatMap { it.split(';').take(if (name.equals("cookie", true)) Int.MAX_VALUE else 1) }
                    .joinToString("; ") { it.trim().substringBefore('=') + "=…" }
            } else {
                values.joinToString(", ")
            }
            "  $name: $shown"
        }
}
