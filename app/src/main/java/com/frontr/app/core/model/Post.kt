package com.frontr.app.core.model

import kotlinx.serialization.Serializable

/**
 * One post, normalised.
 *
 * Every source produces this exact type, which is what lets a source be swapped
 * without the UI knowing. Fields a given source cannot supply stay null rather
 * than being faked, so the UI can hide what it does not have instead of showing
 * a plausible lie.
 */
@Serializable
data class Post(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val avatarUrl: String? = null,
    val text: String,
    val links: List<String> = emptyList(),
    val publishedAtMillis: Long,
    val permalink: String,
    val kind: PostKind = PostKind.ORIGINAL,
    val relatedHandle: String? = null,
    val isPinned: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val quoted: QuotedPost? = null,
    val card: LinkCard? = null,
    val stats: PostStats? = null
) {
    /**
     * The same post seen again. The stored post keeps its identity, which
     * includes how it reached the feed (a repost, a pin), and takes the newer
     * counts, since replies and likes move. A post that was not stored with
     * its avatar or its embeds takes them too.
     */
    fun mergedWith(fresh: Post): Post {
        if (fresh.id != id) return this
        val merged = copy(
            avatarUrl = fresh.avatarUrl ?: avatarUrl,
            media = media.ifEmpty { fresh.media },
            quoted = fresh.quoted ?: quoted,
            card = fresh.card ?: card,
            stats = stats?.let { old -> fresh.stats?.let(old::mergedWith) ?: old } ?: fresh.stats
        )
        return if (merged == this) this else merged
    }
}

@Serializable
enum class PostKind { ORIGINAL, REPOST, REPLY, QUOTE }

@Serializable
enum class MediaType { PHOTO, VIDEO, GIF }

/**
 * [previewUrl] is what gets shown, [downloadUrl] is the full resolution
 * original. Reddit serves both, and conflating them means either a blurry
 * gallery or a very slow timeline. A video's [downloadUrl] is its HLS
 * playlist, resolved to the original file only when it is saved. A GIF is
 * a short video its author sent as one, which loops without sound.
 */
@Serializable
data class MediaItem(
    val previewUrl: String,
    val downloadUrl: String,
    val type: MediaType
)

@Serializable
data class QuotedPost(
    val handle: String,
    val name: String,
    val text: String,
    val permalink: String
)

/** A link preview, as the author's app attached it to the post. */
@Serializable
data class LinkCard(
    val title: String,
    val description: String?,
    val destination: String?,
    val imageUrl: String?,
    val url: String?
)

@Serializable
data class PostStats(
    val replies: Int? = null,
    val reposts: Int? = null,
    val quotes: Int? = null,
    val likes: Int? = null
) {
    /** Newer counts win, a count the fresh answer does not carry is kept. */
    fun mergedWith(fresh: PostStats) = PostStats(
        replies = fresh.replies ?: replies,
        reposts = fresh.reposts ?: reposts,
        quotes = fresh.quotes ?: quotes,
        likes = fresh.likes ?: likes
    )
}

/** A single account's feed, a profile and the posts read from it. */
@Serializable
data class Feed(
    val handle: String,
    val displayName: String,
    val posts: List<Post>,
    val fetchedFromHost: String,
    val fetchedAtMillis: Long,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val nextCursor: String? = null,
    /** Profile card details, from getProfile. */
    val bannerUrl: String? = null,
    /** For example "Joined March 2023". */
    val joined: String? = null,
    val stats: ProfileStats? = null
)

/** The numbers on a profile card. Any may be missing. */
@Serializable
data class ProfileStats(
    val posts: Long? = null,
    val following: Long? = null,
    val followers: Long? = null,
    val likes: Long? = null
)

/**
 * A post with its surroundings, as read from its own page. Not cached: replies
 * change constantly and are only worth reading fresh.
 *
 * [ancestors] are the posts it answers, oldest first. [continuation] is the
 * author's own thread under it. [comments] is the comment tree in reading
 * order, each comment with its depth, and where Reddit left comments out of
 * the page, the line that loads them.
 */
data class Conversation(
    val ancestors: List<Post>,
    val main: Post?,
    val continuation: List<Post>,
    val comments: List<CommentLine>,
    val host: String
)

/** One line of a post's comment tree. */
sealed interface CommentLine {
    val depth: Int

    data class Reply(val post: Post, override val depth: Int) : CommentLine

    /**
     * Comments Reddit leaves out of the page until the reader asks: the rest
     * of the comments at the end ([topLevel]), or the rest of a thread.
     * [path] and [cursor] are what Reddit's own page sends to get them.
     * [remaining] is how many are left, when Reddit says.
     */
    data class More(
        val path: String,
        val cursor: String,
        override val depth: Int,
        val topLevel: Boolean,
        val remaining: Int? = null
    ) : CommentLine
}
