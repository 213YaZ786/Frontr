package com.frontr.app.data.reddit

import com.frontr.app.core.link.RedditLink
import com.frontr.app.core.model.Conversation
import com.frontr.app.core.model.Feed
import com.frontr.app.core.model.LinkCard
import com.frontr.app.core.model.MediaItem
import com.frontr.app.core.model.MediaType
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Turns the pages Reddit serves a logged out browser into Frontr's models.
 * Pure, tested on pages saved from a phone (September 2026).
 *
 * Reddit's pages carry their data as attributes of their own elements: a post
 * is a `<shreddit-post>` whose attributes hold the title, the author, the
 * score, the counts and the date, a video is the `<shreddit-player>` beside
 * it, a comment is a `<shreddit-comment>`. Text bodies are the only part read
 * from markup, from the block Reddit marks as the post's or the comment's
 * rich text. Nothing else of the page is relied on.
 */
internal object RedditParser {

    const val HOST = "www.reddit.com"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A sub's posts from its page or from a further page, and where the next
     * page starts. [name] is the sub as followed.
     */
    fun feed(html: String, name: String, nowMillis: Long): Feed {
        val posts = posts(html)
        val display = posts.firstOrNull()?.authorName
            ?: tags(html, "shreddit-subreddit-header").firstOrNull()?.get("prefixed-name")
            ?: "r/$name"
        return Feed(
            handle = name.lowercase(),
            displayName = display,
            posts = posts,
            fetchedFromHost = HOST,
            fetchedAtMillis = nowMillis,
            nextCursor = nextPage(html)
        )
    }

    fun posts(html: String): List<Post> = tags(html, "shreddit-post").mapNotNull { post(it, html, body = null) }

    /**
     * Where the next page of posts is loaded from: the partial a browser
     * fetches when the reader nears the end. Null at the end of the sub.
     */
    fun nextPage(html: String): String? =
        tags(html, "faceplate-partial")
            .mapNotNull { it["src"] }
            .firstOrNull { ("more-posts" in it || "/feeds/" in it) && "after=" in it && "right-rail" !in it }

    /** A post and its first comments, from the post's own page. */
    fun conversation(html: String): Conversation? {
        val tag = tags(html, "shreddit-post").firstOrNull() ?: return null
        val id = tag["id"] ?: return null
        val main = post(tag, html, body = richText(html, "$id-post-rtjson-content")) ?: return null
        val sub = tag["subreddit-name"].orEmpty().lowercase()
        val chains = mutableListOf<MutableList<Post>>()
        for (comment in tags(html, "shreddit-comment")) {
            val depth = comment["depth"]?.toIntOrNull() ?: continue
            val reply = comment(comment, html, sub) ?: continue
            // One comment and the first answer to it, the way a thread is
            // shown in small chains rather than as a full tree.
            when {
                depth == 0 -> chains.add(mutableListOf(reply))
                depth == 1 && chains.lastOrNull()?.size == 1 -> chains.last().add(reply)
            }
        }
        return Conversation(ancestors = emptyList(), main = main, continuation = emptyList(), replies = chains, host = HOST)
    }

    private fun post(a: Map<String, String>, html: String, body: String?): Post? {
        val id = a["id"]?.takeIf { it.startsWith("t3_") } ?: return null
        val sub = a["subreddit-name"] ?: return null
        val title = a["post-title"].orEmpty()
        val type = a["post-type"].orEmpty()
        val href = a["content-href"]
        val domain = a["domain"]
        val media = when {
            type == "video" -> video(html, id)
            type == "image" || type == "gif" || (href != null && isImage(href)) -> href?.let { listOf(MediaItem(it, it, MediaType.PHOTO)) }.orEmpty()
            else -> emptyList()
        }
        val card = if (type == "link" && href != null && media.isEmpty()) {
            LinkCard(
                title = href.substringAfter("://").removePrefix("www.").take(TITLE_LIMIT),
                description = null,
                destination = domain?.removePrefix("www."),
                imageUrl = a["thumbnail-url"]?.takeIf { it.startsWith("https://") },
                url = href
            )
        } else {
            null
        }
        return Post(
            id = id,
            authorHandle = sub.lowercase(),
            authorName = a["subreddit-prefixed-name"] ?: "r/$sub",
            text = listOfNotNull(title.takeIf { it.isNotBlank() }, body?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            links = listOfNotNull(href?.takeIf { type == "link" }),
            publishedAtMillis = a["created-timestamp"]?.let(::millis) ?: 0L,
            permalink = a["permalink"]?.let { "https://$HOST$it" } ?: RedditLink.postUrl(id),
            // The author is shown beside the sub, see PostCard.
            relatedHandle = a["author"]?.takeIf { it.isNotBlank() },
            media = media,
            card = card,
            stats = PostStats(replies = a["comment-count"]?.toIntOrNull(), likes = a["score"]?.toIntOrNull())
        )
    }

    private fun comment(a: Map<String, String>, html: String, sub: String): Post? {
        val permalink = a["permalink"] ?: return null
        val id = permalink.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val author = a["author"].orEmpty()
        return Post(
            id = "t1_$id",
            authorHandle = sub,
            authorName = if (author.isBlank()) "[deleted]" else "u/$author",
            text = richText(html, "t1_$id-comment-rtjson-content").orEmpty(),
            links = richLinks(html, "t1_$id-comment-rtjson-content"),
            publishedAtMillis = a["created"]?.let(::millis) ?: 0L,
            permalink = "https://$HOST$permalink",
            stats = PostStats(likes = a["score"]?.toIntOrNull())
        )
    }

    /**
     * A video: Reddit's player element names the poster and a list of MP4
     * files, one per size. The largest plays and is what gets saved. Reddit
     * signs those addresses for a limited time, so a video in an old saved
     * post may no longer play; the post opens on Reddit then.
     */
    private fun video(html: String, id: String): List<MediaItem> {
        val player = tags(html, "shreddit-player").firstOrNull { it["post-id"] == id } ?: return emptyList()
        val mp4 = player["packaged-media-json"]?.let(::largestMp4)
        val file = mp4 ?: player["src"] ?: return emptyList()
        return listOf(MediaItem(previewUrl = player["poster"] ?: file, downloadUrl = file, type = MediaType.VIDEO))
    }

    private fun largestMp4(packaged: String): String? {
        val root = runCatching { json.parseToJsonElement(packaged) as? JsonObject }.getOrNull() ?: return null
        val permutations = ((root["playbackMp4s"] as? JsonObject)?.get("permutations") as? JsonArray).orEmpty()
        return permutations.mapNotNull { it as? JsonObject }
            .mapNotNull { perm ->
                val source = perm["source"] as? JsonObject ?: return@mapNotNull null
                val url = (source["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val height = ((source["dimensions"] as? JsonObject)?.get("height") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                height to url
            }
            .maxByOrNull { it.first }?.second
    }

    /** The text of a rich text block, paragraphs kept, markup dropped. */
    private fun richText(html: String, blockId: String): String? {
        val inner = divInner(html, blockId) ?: return null
        val text = inner
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|li|blockquote|h[1-6]|pre)>"), "\n\n")
            .replace(Regex("(?i)<li[^>]*>"), "• ")
            .replace(Regex("<[^>]+>"), "")
        return decode(text).lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun richLinks(html: String, blockId: String): List<String> {
        val inner = divInner(html, blockId) ?: return emptyList()
        return Regex("""<a\b[^>]*href="(https?://[^"]+)"""").findAll(inner).map { decode(it.groupValues[1]) }.distinct().toList()
    }

    /** What sits inside the div with this id, nested divs balanced. */
    private fun divInner(html: String, id: String): String? {
        val open = html.indexOf("id=\"$id\"").takeIf { it >= 0 } ?: return null
        val start = html.indexOf('>', open) + 1
        var depth = 1
        var i = start
        val tag = Regex("<(/?)div\\b", RegexOption.IGNORE_CASE)
        while (depth > 0) {
            val m = tag.find(html, i) ?: return null
            depth += if (m.groupValues[1] == "/") -1 else 1
            i = m.range.last + 1
            if (depth == 0) return html.substring(start, m.range.first)
        }
        return null
    }

    /** Every opening tag with this name, as its decoded attributes. */
    fun tags(html: String, name: String): List<Map<String, String>> =
        Regex("<$name(\\s[^>]*)?>", RegexOption.IGNORE_CASE).findAll(html).map { attributes(it.groupValues[1]) }.toList()

    private fun attributes(raw: String): Map<String, String> =
        ATTRIBUTE.findAll(raw).associate { it.groupValues[1].lowercase() to decode(it.groupValues[2]) }

    private fun isImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return IMAGE_EXTENSIONS.any { path.endsWith(it) }
    }

    fun decode(text: String): String = ENTITY.replace(text) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> NAMED[e] ?: m.value
        }
    }

    private fun millis(iso: String): Long? = runCatching { OffsetDateTime.parse(iso, TIME).toInstant().toEpochMilli() }.getOrNull()

    private val TIME = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
        .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
        .toFormatter()

    private val ATTRIBUTE = Regex("""([a-zA-Z][a-zA-Z0-9:-]*)="([^"]*)"""")
    private val ENTITY = Regex("&(#?[a-zA-Z0-9]+);")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    private const val TITLE_LIMIT = 80
}
