package com.frontr.app.core.common

/**
 * Every failure the app can surface, named precisely.
 *
 * Design rule: no generic "something went wrong". Each case carries enough
 * context to say why Frontr cannot show something and whether the phone,
 * the network, Reddit or the app is at fault.
 */
sealed interface AppError {

    /** Who or what is responsible. Drives the tone of the message shown. */
    val blame: Blame

    /** Whether retrying the exact same call could plausibly succeed. */
    val retryable: Boolean

    /** No network transport available at all. */
    data object Offline : AppError {
        override val blame = Blame.DEVICE
        override val retryable = true
    }

    /** Host name did not resolve. DNS blocked, or no connection behind the Wi-Fi. */
    data class DnsFailure(val host: String) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = true
    }

    /** TLS handshake failed. Possible interception or an expired certificate. */
    data class TlsFailure(val host: String, val detail: String?) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = false
    }

    /** Connected but no answer in time. */
    data class Timeout(val host: String, val millis: Long) : AppError {
        override val blame = Blame.NETWORK
        override val retryable = true
    }

    /** The service refused the request, an HTTP 4xx other than the named ones. */
    data class ClientRefused(val host: String, val status: Int) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = false
    }

    /** Asked to slow down, with the wait the service named when it did. */
    data class RateLimited(val host: String, val retryAfterSeconds: Long?) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = true
    }

    /** The service itself failed, an HTTP 5xx or a refused connection. */
    data class ServerError(val host: String, val status: Int) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = true
    }

    /** No account by that handle. */
    data class AccountNotFound(val handle: String) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = false
    }

    /** The account exists but cannot be shown: suspended, deactivated, hidden. */
    data class AccountUnavailable(val handle: String, val reason: String?) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = false
    }

    /** The post is deleted, or hidden from logged out readers. */
    data class PostUnavailable(val host: String, val reason: String?) : AppError {
        override val blame = Blame.SERVICE
        override val retryable = false
    }

    /** Local storage could not be read or written. */
    data class StorageFailure(val detail: String?) : AppError {
        override val blame = Blame.DEVICE
        override val retryable = false
    }

    /** Nothing above fits. [detail] goes to the activity log, never to the reader. */
    data class Unknown(val detail: String?) : AppError {
        override val blame = Blame.APP
        override val retryable = true
    }
}

enum class Blame { DEVICE, NETWORK, SERVICE, APP }
