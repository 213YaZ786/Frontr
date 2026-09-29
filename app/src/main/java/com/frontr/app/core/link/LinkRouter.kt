package com.frontr.app.core.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.frontr.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a link from outside the app, or from a tap inside it, to the
 * screen that shows it.
 *
 * The activity cannot navigate by itself, the navigation graph lives in
 * Compose. So it drops the link here, and the app consumes it once.
 */
class LinkRouter {

    private val _pending = MutableStateFlow<RedditLink?>(null)
    val pending: StateFlow<RedditLink?> = _pending.asStateFlow()

    /** Returns false when the intent holds no link Frontr can show. */
    fun offer(intent: Intent?): Boolean {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(RedditLink::firstUrlIn)
            else -> null
        } ?: return false
        val link = parse(url) ?: return false
        _pending.value = link
        return true
    }

    fun consume() {
        _pending.value = null
    }

    /** reddit.com sub and post addresses, and redd.it short links. */
    fun parse(url: String): RedditLink? = RedditLink.parse(url)

    companion object {
        /**
         * Opens [url] anywhere but Frontr. "Open on Reddit" must reach the
         * Reddit app or a browser, and once reddit.com links open in Frontr by
         * default, a plain view intent would come straight back here.
         */
        fun openOutside(context: Context, url: String) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val chooser = Intent.createChooser(view, null).apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, MainActivity::class.java))
                )
            }
            // No browser at all leaves nothing sensible to do.
            runCatching { context.startActivity(chooser) }
        }
    }
}
