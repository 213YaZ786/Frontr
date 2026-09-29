package com.frontr.app.core.network

import com.frontr.app.core.common.AppError
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns transport exceptions and HTTP statuses into the named cases in
 * AppError. This is the only place in Frontr that is allowed to look at raw
 * exceptions, everything above it deals in Outcome and AppError.
 */
object ErrorMapper {

    fun fromThrowable(host: String, t: Throwable): AppError = when (t) {
        is UnknownHostException -> AppError.DnsFailure(host)
        is SSLException -> AppError.TlsFailure(host, t.message)
        is HttpRequestTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is SocketTimeoutException -> AppError.Timeout(host, HttpClientFactory.REQUEST_TIMEOUT_MS)
        is ConnectException -> AppError.ServerError(host, 0)
        else -> AppError.Unknown("${t::class.java.simpleName}: ${t.message}")
    }

    /**
     * Returns null when the answer is usable.
     *
     * The public API answers a missing or hidden account with a 400 and a
     * JSON error naming it, not with a 404, so [body] is read for that.
     */
    fun fromStatus(
        host: String,
        code: Int,
        retryAfterSeconds: Long?,
        body: String? = null,
        handle: String? = null
    ): AppError? {
        if (code in 200..299) return null
        if (code == 429) return AppError.RateLimited(host, retryAfterSeconds)
        val lower = body.orEmpty().lowercase()
        if (code == 400 && ("not found" in lower || "could not find" in lower)) {
            return AppError.AccountNotFound(handle ?: host)
        }
        if (code == 400 && ("takedown" in lower || "deactivated" in lower || "suspended" in lower)) {
            return AppError.AccountUnavailable(handle ?: host, "This subreddit is banned or closed")
        }
        if (code == 403 && "private" in lower) {
            return AppError.AccountUnavailable(handle ?: host, "This community is private")
        }
        if (code == 403 && "banned" in lower) {
            return AppError.AccountUnavailable(handle ?: host, "This community is banned")
        }
        if (code == 404 || code == 410) return AppError.AccountNotFound(handle ?: host)
        if (code >= 500) return AppError.ServerError(host, code)
        return AppError.ClientRefused(host, code)
    }
}
