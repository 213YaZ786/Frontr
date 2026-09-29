package com.frontr.app.data.cache

import com.frontr.app.core.common.writeTextAtomically
import android.content.Context
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.core.model.Feed
import com.frontr.app.core.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * On disk cache of the last fetched feed per account.
 *
 * One JSON file per handle rather than a database. For a few hundred posts this
 * is faster to read than opening a database, adds no code generation to the
 * build, and keeps everything the app stores inspectable. Room earns its place
 * when full text search across the archive arrives, not before.
 *
 * Lives in filesDir, not cacheDir, because the system is free to delete cacheDir
 * under storage pressure and an offline reader that loses its content when the
 * phone gets full is not much of an offline reader.
 */
class FeedCache(
    context: Context,
    private val log: RequestLog,
    /** Days to keep saved posts, 0 for no limit. Read at each write. */
    private val retentionDays: () -> Int = { 0 }
) {

    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Posts the reader scrolled back to in this session that the disk does
     * not keep: older than "Keep posts", or past the per account cap. Memory
     * only, per lowercased handle, gone when the app closes.
     *
     * Dropped at the write that fetched them, a page of older posts would
     * arrive and vanish at once. Home and profiles would see no new posts,
     * take it as a failure and stop loading older ones. Kept
     * here, they show until the app closes, which is what the setting
     * promises: older posts can be read, they are simply not kept.
     */
    private val scrolledBack = ConcurrentHashMap<String, List<Post>>()

    suspend fun read(handle: String): Feed? = withContext(Dispatchers.IO) {
        val file = fileFor(handle)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull()
            ?.let(::canonical)
            ?.let(::withScrolledBack)
    }

    suspend fun write(feed: Feed) = withContext(Dispatchers.IO) {
        val kept = trim(feed)
        if (kept !== feed) {
            val keptIds = kept.posts.mapTo(HashSet()) { it.id }
            remember(feed.handle, feed.posts.filterNot { it.id in keptIds })
        }
        runCatching {
            fileFor(feed.handle).writeTextAtomically(json.encodeToString(kept))
        }
        Unit
    }

    /** Adds what the disk dropped to this session's memory, newest version first. */
    private fun remember(handle: String, dropped: List<Post>) {
        if (dropped.isEmpty()) return
        scrolledBack.merge(handle.lowercase(), dropped) { old, new ->
            (new + old).distinctBy { it.id }
                .sortedByDescending { it.publishedAtMillis }
                .take(MAX_SCROLLED_BACK_PER_ACCOUNT)
        }
    }

    /** The stored feed plus the posts of this session the disk does not keep. */
    private fun withScrolledBack(feed: Feed): Feed {
        val extra = scrolledBack[feed.handle.lowercase()].orEmpty()
        if (extra.isEmpty()) return feed
        val known = feed.posts.mapTo(HashSet()) { it.id }
        val missing = extra.filterNot { it.id in known }
        if (missing.isEmpty()) return feed
        return feed.copy(posts = (feed.posts + missing).sortedWith(PROFILE_ORDER))
    }

    /**
     * Appends a newly fetched page to what is already stored, keeping the newer
     * cursor. Deduplicated by post id, because a refresh overlaps what is stored
     * and a repeated post breaks LazyColumn's key contract.
     */
    suspend fun append(page: Feed, isPagedFetch: Boolean = false): Feed = withContext(Dispatchers.IO) {
        val incoming = canonical(page)
        val existing = read(incoming.handle)
        if (existing == null) {
            log.record(
                kind = RequestLog.Kind.CACHE,
                url = "cache/${incoming.handle}",
                outcome = "first",
                detail = "nothing stored yet | in: ${incoming.posts.size} | " +
                    "in newest: ${incoming.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}" +
                    " oldest: ${incoming.posts.minByOrNull { it.publishedAtMillis }?.id ?: "NONE"}"
            )
            write(incoming)
            return@withContext incoming
        }

        val known = existing.posts.map { it.id }.toSet()
        val newPosts = incoming.posts.filterNot { it.id in known }

        // A post already stored comes back with fresher counts. Keep the
        // stored post and take what moved.
        //
        // Pin status is the exception to "the stored post keeps its identity".
        // Only page one of a profile states it with authority: getAuthorFeed
        // puts the pin at the top of that page, and its absence there means
        // the author unpinned. mergedWith leaves isPinned alone, so a post
        // stored before its author pinned it could otherwise never become
        // pinned.
        val statesPins = !isPagedFetch
        val seenAgain = incoming.posts.filter { it.id in known }.associateBy { it.id }
        val refreshed = if (seenAgain.isEmpty()) existing.posts else existing.posts.map { post ->
            seenAgain[post.id]?.let { fresh ->
                val merged = post.mergedWith(fresh)
                if (statesPins && merged.isPinned != fresh.isPinned) merged.copy(isPinned = fresh.isPinned) else merged
            } ?: post
        }

        // A paged fetch that brings back nothing new means the cursor did not
        // advance. Continuing would loop forever on the same page, so stop
        // offering to load more rather than spinning against the server.
        val exhausted = isPagedFetch && newPosts.isEmpty()

        // A refresh reads the first page again. When that page meets posts
        // already stored, what is stored runs on from the head without a
        // hole, so the deeper cursor kept from earlier paging is still where
        // to continue. Taking the page's own cursor instead, the next scroll
        // would ask for page two, find only known posts, and end paging for
        // that account.
        val keepDeeperCursor = !isPagedFetch &&
            seenAgain.isNotEmpty() &&
            existing.nextCursor != null

        // Only a sub's first page names its icon: posts from further pages
        // wear the one kept.
        val icon = incoming.avatarUrl ?: existing.avatarUrl
        val combined = incoming.copy(
            posts = (refreshed + newPosts).sortedWith(PROFILE_ORDER)
                .map { if (icon != null && it.avatarUrl == null) it.copy(avatarUrl = icon) else it },
            displayName = incoming.displayName.ifBlank { existing.displayName },
            avatarUrl = icon,
            bio = incoming.bio ?: existing.bio,
            bannerUrl = incoming.bannerUrl ?: existing.bannerUrl,
            joined = incoming.joined ?: existing.joined,
            stats = incoming.stats ?: existing.stats,
            nextCursor = when {
                exhausted -> null
                keepDeeperCursor -> existing.nextCursor
                else -> incoming.nextCursor ?: existing.nextCursor
            }
        )
        // Everything needed to tell a post that was dropped from a post that
        // was never fetched. A refresh whose page meets nothing already stored
        // has landed clear of the store, which leaves a hole between the two.
        val newestStored = existing.posts.maxOfOrNull { it.publishedAtMillis } ?: 0L
        val oldestIncoming = incoming.posts.minOfOrNull { it.publishedAtMillis } ?: 0L
        val hole = !isPagedFetch && seenAgain.isEmpty() &&
            existing.posts.isNotEmpty() && incoming.posts.isNotEmpty() &&
            oldestIncoming > newestStored
        log.record(
            kind = RequestLog.Kind.CACHE,
            url = "cache/${incoming.handle}",
            outcome = if (hole) "gap" else "ok",
            detail = buildString {
                append(if (isPagedFetch) "paged" else "refresh")
                append(" | in: ${incoming.posts.size}")
                append(" | new: ${newPosts.size}")
                append(" | seen again: ${seenAgain.size}")
                append(" | stored was: ${existing.posts.size} now: ${combined.posts.size}")
                append(" | in newest: ${incoming.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" oldest: ${incoming.posts.minByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" | stored newest was: ${existing.posts.maxByOrNull { it.publishedAtMillis }?.id ?: "NONE"}")
                append(" | pins in: ${incoming.posts.count { it.isPinned }} stored: ${combined.posts.count { it.isPinned }}")
                append(" | pin authority: ${if (statesPins) "yes" else "no"}")
                append(
                    " | cursor: " + when {
                        exhausted -> "STOPPED, page brought nothing new"
                        keepDeeperCursor -> "kept deeper"
                        incoming.nextCursor != null -> "from this page"
                        existing.nextCursor != null -> "kept existing"
                        else -> "none"
                    }
                )
                if (hole) append(" | GAP, posts between the two are not stored and nothing will fetch them")
            }
        )
        write(combined)
        combined
    }

    /**
     * Finds one stored post. [hint] is the account whose file most likely holds
     * it. Otherwise every file is scanned, which is a few local reads and only
     * happens when a post is opened from a place that could not tell.
     */
    suspend fun find(id: String, hint: String?): Post? = withContext(Dispatchers.IO) {
        hint?.let { read(it) }?.posts?.firstOrNull { it.id == id }?.let { return@withContext it }
        feedFiles().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .map(::canonical)
            .firstNotNullOfOrNull { feed -> feed.posts.firstOrNull { it.id == id } }
    }

    /** Every saved post across all accounts, each once, newest first. */
    suspend fun allPosts(): List<Post> = withContext(Dispatchers.IO) {
        feedFiles().asSequence()
            .mapNotNull { file -> runCatching { json.decodeFromString<Feed>(file.readText()) }.getOrNull() }
            .flatMap { canonical(it).posts.asSequence() }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAtMillis }
            .toList()
    }

    suspend fun forget(handle: String) = withContext(Dispatchers.IO) {
        scrolledBack.remove(handle.lowercase())
        runCatching { fileFor(handle).delete() }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        scrolledBack.clear()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Drops duplicate posts, which would break LazyColumn's key contract. */
    private fun canonical(feed: Feed): Feed {
        val posts = feed.posts.distinctBy { it.id }
        return if (posts == feed.posts) feed else feed.copy(posts = posts)
    }

    /**
     * Drops posts older than the reader's retention, and nothing else.
     *
     * There used to be a hard cap of 300 posts per account here. On an account
     * that posts twenty times in two hours that is barely three days of
     * history, and it could never be passed: each page fetched deeper pushed
     * the oldest posts straight back out at the next write, so scrolling back
     * looked like an upstream failure when it was this line. Depth is now the
     * reader's own business, governed by "Keep posts" alone.
     */
    private fun trim(feed: Feed): Feed {
        val days = retentionDays()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * DAY_MS else Long.MIN_VALUE
        val kept = feed.posts
            // A pin survives the retention window. It is the author's own
            // statement about their profile, not a post that happens to be old.
            .filter { it.isPinned || it.publishedAtMillis <= 0L || it.publishedAtMillis >= cutoff }
        return if (kept.size == feed.posts.size) feed else feed.copy(posts = kept)
    }

    /**
     * Applies the retention to every saved account now, rather than at each
     * account's next fetch. Run at launch and when the setting changes.
     */
    suspend fun applyRetention() = withContext(Dispatchers.IO) {
        if (retentionDays() <= 0) return@withContext
        feedFiles().forEach { file ->
            runCatching {
                val feed = json.decodeFromString<Feed>(file.readText())
                val trimmed = trim(feed)
                if (trimmed !== feed) file.writeTextAtomically(json.encodeToString(trimmed))
            }
        }
    }

    /**
     * Every post id the disk still holds.
     *
     * Read by the media store so a saved picture never outlives the post it
     * belongs to. Retention is set once, in "Keep posts", and it governs the
     * files as well as the text.
     */
    suspend fun storedPostIds(): Set<String> = withContext(Dispatchers.IO) {
        feedFiles().flatMapTo(HashSet()) { file ->
            runCatching { json.decodeFromString<Feed>(file.readText()).posts.map { it.id } }
                .getOrDefault(emptyList())
        }
    }

    private fun fileFor(handle: String) = File(directory, "${handle.lowercase()}.json")

    /**
     * The feed files, and only those. A write goes through a temporary file
     * beside the real one, and a crash between the two can leave it behind:
     * read as a feed, it would bring back posts retention already dropped.
     */
    private fun feedFiles(): List<File> = directory.listFiles().orEmpty().filter { it.name.endsWith(".json") }

    private companion object {
        /** Scrolling back further than this in one session drops the oldest again. */
        const val MAX_SCROLLED_BACK_PER_ACCOUNT = 1_000
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * A profile's own order: the pinned post first, then newest first, which
         * is how Reddit draws it. Sorting by date alone parked a pin that was a
         * week old at the very bottom of the feed, where it also became the
         * oldest post on screen, so paging behaved as though the account had
         * already been read back a week.
         */
        val PROFILE_ORDER: Comparator<Post> = compareByDescending<Post> { it.isPinned }
            .thenByDescending { it.publishedAtMillis }
    }
}
