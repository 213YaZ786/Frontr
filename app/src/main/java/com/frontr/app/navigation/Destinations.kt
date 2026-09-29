package com.frontr.app.navigation

import android.net.Uri

import androidx.compose.ui.graphics.vector.ImageVector
import com.frontr.app.ui.icon.FrontrIcons

/** The tabs, in dock order. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("timeline", "Home", FrontrIcons.Home),
    ACCOUNTS("accounts", "Subreddits", FrontrIcons.Person),
    SETTINGS("settings", "Settings", FrontrIcons.Settings)
}

/** Destinations pushed on top, not part of the bar. */
object Routes {
    /** The three tabs, hosted together in one pager. */
    const val MAIN = "main"
    const val FEED_PATTERN = "feed/{handle}"
    const val DEBUG_LOG = "debuglog"
    const val SEARCH = "search"
    const val SAVED_MEDIA = "savedmedia"
    const val FOLDERS = "folders"

    const val POST_PATTERN = "post/{id}?from={from}"

    fun feed(handle: String): String = "feed/$handle"

    /**
     * [id] is the post's thing id, encoded like any value in a route, so it is
     * encoded to stay one path segment. [from] is the account whose cache
     * holds the post, a lookup hint.
     */
    fun post(id: String, from: String?): String {
        val encoded = Uri.encode(id)
        return if (from.isNullOrBlank()) "post/$encoded" else "post/$encoded?from=${Uri.encode(from)}"
    }
}
