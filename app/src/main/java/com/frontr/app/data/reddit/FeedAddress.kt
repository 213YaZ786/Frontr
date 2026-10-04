package com.frontr.app.data.reddit

import com.frontr.app.core.model.FeedSort

/**
 * The address of a sub's first page in the order the reader chose. Its
 * further pages are the partials the page itself names, which keep the order
 * (and Popular's country) on their own.
 */
object FeedAddress {

    /**
     * [name] as Reddit spells it: asked in another case, Reddit answers a
     * frame without posts that only a browser follows to the right page.
     */
    fun firstPage(name: String, sort: FeedSort, country: String? = null): String {
        val query = listOfNotNull(sort.period?.let { "t=$it" }, country?.let { "geo_filter=$it" })
        return "https://${RedditParser.HOST}/r/$name/${sort.path}/" + query.joinToString("&", prefix = "?").takeIf { query.isNotEmpty() }.orEmpty()
    }
}
