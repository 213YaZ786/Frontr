package com.frontr.app.data.reddit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RedditHost.owns, which the cursor of a next page and the web engine's main frame both pass. Cases of the
 * address forms Ktor's parser reads differently from Android's Uri: a user name before an "@", a host that
 * only starts with the right name, "\" as a delimiter, a missing scheme.
 * https://github.com/ktorio/ktor/blob/main/ktor-http/common/src/io/ktor/http/URLParser.kt#takeFromUnsafe
 */
class RedditHostTest {

    @Test
    fun `Reddit's own https addresses are owned`() {
        listOf(
            "https://www.reddit.com/",
            "https://www.reddit.com",
            "https://www.reddit.com/r/Examples/",
            "https://www.reddit.com/svc/shreddit/community-more-posts/best/?after=dDNf&t=DAY&name=Examples",
            "https://WWW.Reddit.com/x",
            "https://www.reddit.com:443/x",
            "https://www.reddit.com/x#fragment"
        ).forEach { assertTrue(it, RedditHost.owns(it)) }
    }

    @Test
    fun `another scheme, host, port or a user name is refused`() {
        listOf(
            "http://www.reddit.com/",
            "https://www.reddit.com@evil.example/svc/shreddit/feeds/x?after=1",
            "https://www.reddit.com.evil.example/svc/shreddit/feeds/x?after=1",
            "https://evil.example/www.reddit.com",
            "https://evil.example/?www.reddit.com",
            "https://user@www.reddit.com/",
            "https://user:password@www.reddit.com/",
            "https://old.reddit.com/",
            "https://reddit.com/",
            "https://www.reddit.com./",
            "https://www.reddit.com:8080/",
            "https://www.reddit.com:99999999999/",
            "//www.reddit.com/",
            "/svc/shreddit/feeds/x?after=1",
            "www.reddit.com/",
            "javascript:alert(1)",
            "data:text/html,www.reddit.com",
            "https://",
            ""
        ).forEach { assertFalse(it, RedditHost.owns(it)) }
    }

    @Test
    fun `only one slash and no backslash or control character is a path`() {
        listOf("/svc/shreddit/feeds/x?after=1", "/", "/a/b?c=d&e=f#g").forEach { assertTrue(it, RedditHost.isPath(it)) }
        listOf(
            "", "svc/x", "//evil.example/x", "///x", "@evil.example/x", ".evil.example/x",
            "/a\\b", "/\\evil.example", "/a\nb", "/a\tb", "/a\u0000b", "/a\u007Fb"
        ).forEach { assertFalse(it, RedditHost.isPath(it)) }
    }
}
