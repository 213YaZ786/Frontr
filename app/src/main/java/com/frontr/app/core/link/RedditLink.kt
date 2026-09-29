package com.frontr.app.core.link

/**
 * What a Reddit link points at. Pure, no Android.
 *
 * Reads reddit.com/r/<sub>, reddit.com/r/<sub>/comments/<id>/..., the bare
 * reddit.com/comments/<id>, and redd.it/<id>, on every Reddit host (www, old,
 * new, m, np). A post read without its sub, from a short link, is still
 * opened: Reddit answers /comments/<id> for any post. Users, share links and
 * anything else are left to the browser.
 */
sealed interface RedditLink {
    data class Sub(val name: String) : RedditLink

    data class Post(val sub: String?, val id: String) : RedditLink {
        /** The post's id in Frontr, Reddit's own thing id. */
        val thingId: String get() = "t3_$id"
    }

    companion object {

        fun parse(raw: String): RedditLink? {
            val trimmed = raw.trim()
            val scheme = trimmed.substringBefore("://", "").lowercase()
            if (scheme != "https" && scheme != "http") return null
            val rest = trimmed.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore('?').lowercase()
            val segments = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
                .split('/').filter { it.isNotEmpty() }
            if (host == "redd.it") {
                return segments.singleOrNull()?.takeIf(::isPostId)?.let { Post(null, it.lowercase()) }
            }
            if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
            return when {
                segments.size >= 2 && segments[0].lowercase() == "r" && isSubreddit(segments[1]) -> {
                    val sub = segments[1]
                    if (segments.size >= 4 && segments[2] == "comments" && isPostId(segments[3])) {
                        Post(sub, segments[3].lowercase())
                    } else if (segments.size == 2) {
                        Sub(sub)
                    } else {
                        null
                    }
                }
                segments.size >= 2 && segments[0] == "comments" && isPostId(segments[1]) -> Post(null, segments[1].lowercase())
                else -> null
            }
        }

        /** The post id inside a thing id, t3_abc123 becomes abc123. */
        fun postIdOf(thingId: String): String = thingId.removePrefix("t3_")

        /** The first http or https URL inside shared text, for the share sheet. */
        fun firstUrlIn(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

        fun subUrl(name: String): String = "https://www.reddit.com/r/$name/"

        /** The web address of a post from its thing id. Reddit redirects it to the full permalink. */
        fun postUrl(thingId: String): String = "https://www.reddit.com/comments/${postIdOf(thingId)}/"

        /** Reddit's own rule: 2 to 21 letters, digits or underscores, not starting with an underscore. */
        fun isSubreddit(value: String): Boolean = SUB.matches(value)

        private fun isPostId(value: String) = POST_ID.matches(value)

        private val SUB = Regex("^[A-Za-z0-9][A-Za-z0-9_]{1,20}$")
        private val POST_ID = Regex("^[A-Za-z0-9]{3,12}$")
        private val URL = Regex("""https?://\S+""")
    }
}
