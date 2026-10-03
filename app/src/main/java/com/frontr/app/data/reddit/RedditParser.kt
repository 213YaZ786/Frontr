package com.frontr.app.data.reddit

import com.frontr.app.core.link.RedditLink
import com.frontr.app.core.model.CommentLine
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
        val header = tags(html, "shreddit-subreddit-header").firstOrNull()
        val icon = subIcon(html)
        // Every post of a sub's page is the sub's, so each wears its icon.
        val posts = posts(html).map { if (icon != null && it.avatarUrl == null) it.copy(avatarUrl = icon) else it }
        val display = posts.firstOrNull()?.authorName
            ?: header?.get("prefixed-name")
            ?: "r/$name"
        return Feed(
            handle = name.lowercase(),
            displayName = display,
            posts = posts,
            fetchedFromHost = HOST,
            fetchedAtMillis = nowMillis,
            avatarUrl = icon,
            bio = header?.get("description")?.trim()?.takeIf { it.isNotEmpty() },
            nextCursor = nextPage(html),
            bannerUrl = subBanner(html)
        )
    }

    /**
     * The sub's icon, as its page names it: in the page data Reddit keeps
     * for its own scripts, else on the icon picture of the sub's header.
     * Only a sub's first page has it, further pages do not.
     */
    fun subIcon(html: String): String? {
        val data = tags(html, "reddit-page-data").firstNotNullOfOrNull { it["data"] }
        val fromData = data?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?.let { it["subreddit"] as? JsonObject }
            ?.let { (it["communityIcon"] as? JsonPrimitive)?.content }
        // A listing of many subs shows each post's sub icon with the same
        // class: only the one marked with this sub's own id is its icon.
        val fromHeader = subId(html)?.let { id ->
            tags(html, "img").firstOrNull { "community-icon-$id" in it["class"].orEmpty().split(' ') }?.get("src")
        }
        return (fromData ?: fromHeader)?.takeIf { it.startsWith("https://") }
    }

    /**
     * The sub's banner: the picture the header block's style points at,
     * several sizes named as custom properties, the largest taken. The style
     * is empty on a sub without a banner. Else a banner file of this very sub
     * named anywhere in the page, where Reddit's own data keeps it,
     * without the size query, which only works signed.
     */
    fun subBanner(html: String): String? {
        val urls = BANNER_URL.findAll(bannerStyle(html).orEmpty()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val styled = BANNER_SIZES.firstNotNullOfOrNull { size -> urls.firstOrNull { it.first == size }?.second }
            ?: urls.firstOrNull()?.second
        val named = subId(html)?.let { id -> Regex("https://styles\\.redditmedia\\.com/$id/styles/bannerBackgroundImage_[^\"'&\\s)?]+").find(html)?.value }
        return (styled?.trim('\'', '"', ' ') ?: named)?.takeIf { it.startsWith("https://") }
    }

    /** What a sub's page gave for its header, for the activity log. */
    fun headerNote(html: String): String =
        "icon ${if (subIcon(html) != null) "found" else "missing"}, " +
            "banner ${subBanner(html) ?: "none"}, banner style \"${bannerStyle(html)?.take(TEXT_LIMIT) ?: "no banner block"}\""

    private fun bannerStyle(html: String): String? {
        val at = html.indexOf("id=\"subreddit-banner-img\"").takeIf { it >= 0 } ?: return null
        val open = html.lastIndexOf('<', at)
        val close = html.indexOf('>', at).takeIf { it > 0 } ?: return null
        return attributes(html.substring(open, close))["style"]
    }

    private fun subId(html: String): String? =
        tags(html, "shreddit-subreddit-header").firstNotNullOfOrNull { it["subreddit-id"] }?.takeIf { it.matches(Regex("t5_[a-z0-9]+")) }

    fun posts(html: String): List<Post> = tags(html, "shreddit-post").mapNotNull { post(it, html, body = null) }

    /**
     * Where the next page of posts is loaded from: the partial a browser
     * fetches when the reader nears the end. Null at the end of the sub.
     */
    fun nextPage(html: String): String? =
        tags(html, "faceplate-partial")
            .mapNotNull { it["src"] }
            .firstOrNull { ("more-posts" in it || "/feeds/" in it) && "after=" in it && "right-rail" !in it }

    /**
     * A short account of a page that gave no posts, for the activity log:
     * its title, how many post elements and Reddit elements it holds, and the
     * start of its visible text, which is where a check page or a notice says
     * what it is. Nothing of the reader is in it.
     */
    fun describe(html: String): String {
        val title = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.get(1)?.let(::decode)?.trim().orEmpty()
        val posts = Regex("<shreddit-post[\\s>]").findAll(html).count()
        val reddit = Regex("<(shreddit|faceplate)-").findAll(html).count()
        val text = decode(
            html.replace(Regex("(?is)<(script|style|template)\\b.*?</\\1>"), " ")
                .replace(Regex("<[^>]+>"), " ")
        ).replace(Regex("\\s+"), " ").trim()
        return "title \"${title.take(TITLE_LIMIT)}\", $posts post elements, $reddit Reddit elements, " +
            "text \"${text.take(TEXT_LIMIT)}\""
    }

    /**
     * Whether this is the page Reddit sends a browser it wants to check
     * before showing anything: a form its script fills in and submits.
     */
    fun isCheckPage(html: String): Boolean = "name=\"js_challenge\"" in html

    /**
     * How many different post ids a page of any kind mentions: the new
     * site, old Reddit, the RSS feed or the JSON listing. Tells a page that
     * carries posts Frontr does not read yet from one that carries none.
     */
    fun postIds(body: String): Int = Regex("t3_[a-z0-9]{4,12}\\b").findAll(body).map { it.value }.distinct().count()

    /** A post and its comments, from the post's own page. */
    fun conversation(html: String): Conversation? {
        val tag = tags(html, "shreddit-post").firstOrNull() ?: return null
        val id = tag["id"] ?: return null
        val main = post(tag, html, body = richText(html, "$id-post-rtjson-content"))?.let { it.copy(avatarUrl = it.avatarUrl ?: subIcon(html)) } ?: return null
        val sub = tag["subreddit-name"].orEmpty().lowercase()
        return Conversation(ancestors = emptyList(), main = main, continuation = emptyList(), comments = comments(html, sub), host = HOST)
    }

    /**
     * The comment tree of a post's page, or of what a "more comments" call
     * answers, in reading order. Reddit nests each comment's answers inside
     * it, and puts the block that loads a thread's missing answers after the
     * answers it shows, so the page's own order is the tree's.
     *
     * [at] is the line the page answers, when it is one: Reddit may count
     * depth from that point rather than from the post, which puts the
     * answers back under the thread they belong to.
     */
    fun comments(html: String, sub: String, at: CommentLine.More? = null): List<CommentLine> {
        val lines = mutableListOf<CommentLine>()
        // A page read back from the web engine may hold a part twice.
        val seen = mutableSetOf<String>()
        for (m in TREE_TAG.findAll(html)) {
            val a = attributes(m.groupValues[2])
            if (m.groupValues[1].equals("shreddit-comment", ignoreCase = true)) {
                val depth = a["depth"]?.toIntOrNull() ?: at?.depth ?: 0
                comment(a, html, sub)?.takeIf { seen.add(it.id) }?.let { lines.add(CommentLine.Reply(it, depth)) }
            } else {
                more(a, html, m.range.last)?.takeIf { seen.add(it.cursor) }?.let(lines::add)
            }
        }
        if (at == null) return lines
        val shallowest = lines.minOfOrNull { it.depth } ?: return lines
        val shift = (at.depth - shallowest).coerceAtLeast(0)
        return if (shift == 0) lines else lines.map {
            when (it) {
                is CommentLine.Reply -> it.copy(depth = it.depth + shift)
                is CommentLine.More -> it.copy(depth = it.depth + shift)
            }
        }
    }

    /**
     * A block that loads comments left out of the page: its address, and the
     * cursor its hidden field holds, which the browser sends back with it.
     */
    private fun more(a: Map<String, String>, html: String, end: Int): CommentLine.More? {
        val src = a["src"]?.takeIf { it.startsWith("/svc/shreddit/more-comments/") } ?: return null
        val close = html.indexOf("</faceplate-partial>", end).takeIf { it > 0 } ?: html.length
        val cursor = tags(html.substring(end + 1, close), "input").firstOrNull { it["name"] == "cursor" }?.get("value") ?: return null
        val query = src.substringAfter('?', "").split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val topLevel = query["top-level"] == "1"
        return CommentLine.More(
            path = src,
            cursor = cursor,
            depth = if (topLevel) 0 else query["startingDepth"]?.toIntOrNull() ?: 1,
            topLevel = topLevel,
            remaining = query["comments-remaining"]?.toIntOrNull()
        )
    }

    /**
     * Whether the page's post is a text post whose body is not in the page:
     * its text block is absent or empty. A text post with nothing but a title
     * has no block either, and the web engine then finds none as well.
     */
    fun bodyMissing(html: String): Boolean {
        val tag = tags(html, "shreddit-post").firstOrNull() ?: return false
        if (tag["post-type"] != "text") return false
        val id = tag["id"] ?: return false
        return richText(html, "$id-post-rtjson-content").isNullOrBlank()
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
                imageUrl = a["thumbnail-url"]?.takeIf { it.startsWith("https://") } ?: thumbnail(html, id),
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
     * A link post's picture: Reddit no longer names it on
     * the post element but draws it as an image inside it, a small preview
     * of the article's own picture. Reddit signs it at that size, a larger
     * one is refused.
     */
    private fun thumbnail(html: String, id: String): String? {
        val at = html.indexOf("id=\"$id\"").takeIf { it >= 0 } ?: return null
        val end = html.indexOf("</shreddit-post>", at).takeIf { it > 0 } ?: html.length
        return Regex("""<img\b[^>]*\bsrc="(https://(?:external-preview\.redd\.it|preview\.redd\.it|b\.thumbs\.redditmedia\.com)/[^"]+)"""")
            .find(html.substring(at, end))?.groupValues?.get(1)?.let(::decode)
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

    /**
     * Every opening tag with this name, as its decoded attributes. Quoted
     * values may hold a raw >, as a page read back from the web engine can.
     */
    fun tags(html: String, name: String): List<Map<String, String>> =
        Regex("<$name((?:\\s+[^\\s=>/]+(?:=(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*)\\s*/?>", RegexOption.IGNORE_CASE)
            .findAll(html).map { attributes(it.groupValues[1]) }.toList()

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

    /** A url(...) in a style, with the property it is set on, if any. */
    private val BANNER_URL = Regex("""(?:(--[a-z-]+)\s*:\s*)?url\(([^)]+)\)""")
    private val BANNER_SIZES = listOf("--large-banner", "--x-large-banner", "--medium-banner", "--small-banner")
    private val TREE_TAG = Regex(
        "<(shreddit-comment|faceplate-partial)((?:\\s+[^\\s=>/]+(?:=(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*)\\s*/?>",
        RegexOption.IGNORE_CASE
    )
    private val ATTRIBUTE = Regex("""([a-zA-Z][a-zA-Z0-9:-]*)="([^"]*)"""")
    private val ENTITY = Regex("&(#?[a-zA-Z0-9]+);")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    private const val TITLE_LIMIT = 80
    private const val TEXT_LIMIT = 200
}
