package com.frontr.app.data.reddit

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom

/**
 * Whether an address stays on Reddit's own site: https, www.reddit.com, its
 * default port and no user name. A cursor stored by an old version or read
 * from a page, and a page the web engine is sent to, must pass it.
 *
 * The address is read by Ktor's own parser, the one that sends the request:
 * Android's Uri and java.net.URI split "https://www.reddit.com@evil.example/"
 * by other rules, so a check made with them could say yes where the client
 * calls evil.example.
 *
 * Ports https://github.com/ktorio/ktor/blob/main/ktor-http/common/src/io/ktor/http/URLParser.kt#takeFrom
 * by calling it (URLBuilder.takeFrom throws URLParserException on an address
 * it cannot read, as takeFrom there). Its reading of the user name (the
 * userinfo before an "@", and "\" as a delimiter) is what decides here.
 * not ported: takeFromUnsafe, parseFile, parseMailto, parseQuery,
 * parseFragment, fillHost, findScheme, indexOfColonInHostPort, isLetter
 * (the parser's own steps, called through takeFrom, not copied).
 */
internal object RedditHost {

    fun owns(urlString: String): Boolean {
        val builder = try {
            URLBuilder().takeFrom(urlString)
        } catch (cause: URLParserException) {
            return false
        }
        return builder.protocolOrNull == URLProtocol.HTTPS &&
            builder.host.equals(RedditParser.HOST, ignoreCase = true) &&
            (builder.port == DEFAULT_PORT || builder.port == URLProtocol.HTTPS.defaultPort) &&
            builder.encodedUser == null &&
            builder.encodedPassword == null
    }

    /**
     * A path on the host the address is joined to: takeFromUnsafe counts the
     * slashes after the scheme (`count`) and two or more open an authority,
     * and it ends a host at any of "@/\?#" (`indexOfAny`), the backslash
     * included. One slash and no backslash leave a path; no control character.
     * https://github.com/ktorio/ktor/blob/main/ktor-http/common/src/io/ktor/http/URLParser.kt#count
     */
    fun isPath(urlString: String): Boolean {
        val startIndex = 0
        val endIndex = urlString.length
        val slashCount = count(urlString, startIndex, endIndex, '/')
        return slashCount == 1 &&
            urlString.indexOfAny("\\".toCharArray(), startIndex).takeIf { it >= 0 } == null &&
            urlString.none { it.isISOControl() }
    }

    private fun count(urlString: String, startIndex: Int, endIndex: Int, char: Char): Int {
        var result = 0
        while (startIndex + result < endIndex) {
            if (urlString[startIndex + result] != char) break
            result++
        }

        return result
    }
}
