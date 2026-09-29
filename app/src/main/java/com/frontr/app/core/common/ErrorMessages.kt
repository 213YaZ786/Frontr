package com.frontr.app.core.common

/**
 * Turns an AppError into something a human reads, plus the action that
 * actually helps. Kept out of the UI so it can be unit tested and localised.
 */
data class ErrorPresentation(
    val headline: String,
    val explanation: String,
    val action: ErrorAction
)

enum class ErrorAction { RETRY, OPEN_LOG, NONE }

fun AppError.present(): ErrorPresentation = when (this) {
    AppError.Offline -> ErrorPresentation(
        headline = "No internet connection",
        explanation = "Saved posts are still readable. New ones load as soon as you are back online.",
        action = ErrorAction.RETRY
    )
    is AppError.DnsFailure -> ErrorPresentation(
        headline = "Can't reach Reddit",
        explanation = "$host can't be found from this network right now. Saved posts are still readable.",
        action = ErrorAction.RETRY
    )
    is AppError.TlsFailure -> ErrorPresentation(
        headline = "The connection is not secure",
        explanation = "The connection to $host could not be verified, so Frontr stopped rather than take a risk. " +
            "A public Wi-Fi that intercepts traffic does this.",
        action = ErrorAction.OPEN_LOG
    )
    is AppError.Timeout -> ErrorPresentation(
        headline = "Reddit is slow to answer",
        explanation = "No answer from $host in ${millis / 1000} seconds. Try again in a moment.",
        action = ErrorAction.RETRY
    )
    is AppError.ClientRefused -> ErrorPresentation(
        headline = if (status == 200) "Reddit's page couldn't be read" else "Reddit refused the request",
        explanation = if (status == 200) {
            "Reddit sent a page with no posts Frontr can read, often a check it shows to automated visitors. " +
                "The activity log shows what came back."
        } else {
            "$host answered $status. The activity log shows what was asked."
        },
        action = ErrorAction.OPEN_LOG
    )
    is AppError.RateLimited -> ErrorPresentation(
        headline = "Asked to slow down",
        explanation = retryAfterSeconds?.let { "Reddit asked Frontr to wait $it seconds. It tries again on its own." }
            ?: "Reddit asked Frontr to slow down. It tries again on its own.",
        action = ErrorAction.NONE
    )
    is AppError.ServerError -> ErrorPresentation(
        headline = "Reddit is having trouble",
        explanation = "The service failed, not your phone or your connection. Try again later.",
        action = ErrorAction.RETRY
    )
    is AppError.AccountNotFound -> ErrorPresentation(
        headline = "r/$handle doesn't exist",
        explanation = "Reddit has no subreddit by this name. Check its spelling in Subreddits.",
        action = ErrorAction.NONE
    )
    is AppError.AccountUnavailable -> ErrorPresentation(
        headline = "Can't show r/$handle",
        explanation = reason ?: "This subreddit can't be shown to logged out readers.",
        action = ErrorAction.NONE
    )
    is AppError.PostUnavailable -> ErrorPresentation(
        headline = "This post can't be shown",
        explanation = reason ?: "It was deleted or removed, or Reddit hides it from logged out readers.",
        action = ErrorAction.NONE
    )
    is AppError.StorageFailure -> ErrorPresentation(
        headline = "Saved posts couldn't be used",
        explanation = "Frontr could not read or write its saved posts. Check that the phone has free space.",
        action = ErrorAction.NONE
    )
    is AppError.Unknown -> ErrorPresentation(
        headline = "Something unexpected happened",
        explanation = "The activity log in Settings has the details.",
        action = ErrorAction.OPEN_LOG
    )
}
