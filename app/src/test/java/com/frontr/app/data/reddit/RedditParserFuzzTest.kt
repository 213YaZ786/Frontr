package com.frontr.app.data.reddit

import com.frontr.app.core.model.CommentLine
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A fixed seed and 5,000 mutations of each golden Reddit page, in the normal test run. Every input goes to the
 * parser's entry points and either reads or is refused as null or empty: an exception out of [RedditParser] is a
 * failure (RedditApi's guard would turn it into a failed read, but the parser must not throw), and none allocates
 * far beyond its own size or takes long.
 * https://github.com/palchrb/vibb-launcher/blob/main/launcher/app/src/test/java/com/kidslauncher/mdm/sms/mms/PduFuzzTest.kt#PduFuzzTest
 * (GPL-3.0, vibb-launcher; credited in the README), through SMS's adaptation of it
 * (YAZ/SMS core/mms/pdu/PduFuzzTest.kt), here over text: the mutations of a String, the pages of RedditParserTest.
 * not ported: MmsCodec.parseNotification / parseRetrieveConf / parseSendConf and MmsParseResult/MmsParseFailure
 * (vibb's MMS API; here the entry points are RedditParser's, and a read returns a model, null or an empty list),
 * not ported: the seeds built by PduBytes and PduVectors and MmsCodec.composeSendReq (MMS only: the seeds are Reddit's
 * pages), the INTERNAL check of a typed failure (here any Throwable fails), the REJECTED check of an empty input.
 */
class RedditParserFuzzTest {

    /** The comments partial a "more comments" call answers (RedditParserTest, depth 1). */
    private val moreComments = """
        <shreddit-comment author="eps" depth="1" permalink="/r/Examples/comments/eee555/comment/c5/" score="3">
          <div id="t1_c5-comment-rtjson-content" slot="comment"><div><p>Loaded later</p></div></div>
          <shreddit-comment author="zeta" depth="2" permalink="/r/Examples/comments/eee555/comment/c6/" score="1">
            <div id="t1_c6-comment-rtjson-content" slot="comment"><div><p>And its answer</p></div></div>
          </shreddit-comment>
        </shreddit-comment>
    """.trimIndent()

    /** The faceplate partial that continues Popular in a country (RedditParserTest). */
    private val nextPage = "<faceplate-partial src=\"/svc/shreddit/feeds/popular-feed?after=dDNf&amp;cursor=dDNf&amp;sort=TOP&amp;t=DAY&amp;geo_filter=FR\"></faceplate-partial>"

    private val seeds: List<String> = listOf(
        // A sub page with its header, its banner and its posts.
        SUB_HEADER.replace("BANNER_STYLE", "--small-banner:url(https://styles.redditmedia.com/t5_1/styles/banner_s.png);" +
            " --large-banner:url(&quot;https://styles.redditmedia.com/t5_1/styles/banner_l.png&quot;)") + RedditParserTest.LISTING,
        RedditParserTest.LISTING,
        RedditParserTest.POST_PAGE,
        moreComments,
        nextPage,
    )

    /** The line a "more comments" answer is read for, as RedditParserTest takes it from the post page. */
    private val moreAt: CommentLine.More =RedditParser.conversation(RedditParserTest.POST_PAGE)!!.comments[3] as CommentLine.More

    private val interesting = charArrayOf('\u0000', '<', '>', '"', '\'', '=', '/', '\\', '&', '#', ';', ' ', '\n', Char.MAX_VALUE, '\uD800')

    private val tagNames = listOf(
        "shreddit-post", "shreddit-comment", "faceplate-partial", "shreddit-subreddit-header", "reddit-page-data",
        "shreddit-player", "img", "input", "div"
    )

    private val mutationNames = listOf(
        "flipped bit", "interesting char", "random char", "insertion", "deletion", "cut", "run of U+FFFF", "splice",
        "unclosed tag", "unclosed quote", "nested divs", "attribute flood", "very long attribute"
    )

    /** The mutations applied to the last input, by name, so a failure says which one made it slow. */
    private val applied = ArrayList<String>()

    private fun mutate(random: Random, input: String): String {
        var text = input
        applied.clear()
        repeat(1 + random.nextInt(4)) {
            if (text.isEmpty()) text = random.nextInt(256).toChar().toString()
            val at = random.nextInt(text.length)
            val kind = random.nextInt(13)
            applied.add("${mutationNames[kind]} at $at")
            text = when (kind) {
                0 -> text.replaceRange(at, at + 1, (text[at].code xor (1 shl random.nextInt(16))).toChar().toString())
                1 -> text.replaceRange(at, at + 1, interesting[random.nextInt(interesting.size)].toString())
                2 -> text.replaceRange(at, at + 1, random.nextInt(256).toChar().toString())
                3 -> text.substring(0, at) + String(CharArray(1 + random.nextInt(8)) { random.nextInt(256).toChar() }) + text.substring(at)
                4 -> text.substring(0, at) + text.substring(minOf(text.length, at + 1 + random.nextInt(16)))
                5 -> text.substring(0, at)
                // A run of U+FFFF, as vibb's run of 0xFF (continuation octets): a char that is no text.
                6 -> text.substring(0, at) + Char.MAX_VALUE.toString().repeat(1 + random.nextInt(6)) + text.substring(at)
                7 -> {
                    val other = seeds[random.nextInt(seeds.size)]
                    val from = random.nextInt(other.length)
                    text.substring(0, at) + other.substring(from, minOf(other.length, from + 1 + random.nextInt(64)))
                }
                // An unclosed tag: the start of a Reddit element with a few attributes and no ">".
                8 -> text.substring(0, at) + "<" + tagNames[random.nextInt(tagNames.size)] +
                    " id=\"t3_x\" depth=\"0\" permalink=\"/r/x/comments/y/comment/z/\" src=\"/svc/shreddit/more-comments/" + text.substring(at)
                // A quote opened and never closed.
                9 -> text.substring(0, at) + (if (random.nextBoolean()) "\"" else "'") + text.substring(at)
                // Nested divs a thousand deep, opened inside a rich text block when the page has one.
                10 -> {
                    val block = text.indexOf("-rtjson-content\"").let { i -> if (i < 0) -1 else text.indexOf('>', i) }
                    val inside = if (block < 0) at else block + 1
                    text.substring(0, inside) + "<div>".repeat(1_000) + (if (random.nextBoolean()) "</div>".repeat(1_000) else "") + text.substring(inside)
                }
                // An attribute flood: many quoted values with spaces in a tag with no end.
                11 -> text.substring(0, at) + "<" + tagNames[random.nextInt(tagNames.size)] + " a=\"b c d\"".repeat(20 + random.nextInt(60)) + text.substring(at)
                // A very long attribute, rare (one in eight) and kept to a few hundred KB: it makes every call scan it.
                else -> if (text.length < 200_000 && random.nextInt(8) == 0) {
                    text.substring(0, at) + " x=\"" + "A".repeat(10_000 + random.nextInt(100_000)) + (if (random.nextBoolean()) "\"" else "") + text.substring(at)
                } else {
                    text.substring(0, at) + " x=\"" + text.substring(at)
                }
            }
        }
        return text
    }

    // HotSpot's per-thread allocation counter, by reflection: unit tests compile against android.jar, which has no
    // java.lang.management. -1 where the JVM has none (the time and exception checks still run).
    private val allocatedBytesOf: (() -> Long)? = runCatching {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val method = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
        val read = { method.invoke(bean) as Long }
        read.takeIf { it() >= 0 }
    }.getOrNull()

    private fun allocatedBytes(): Long = allocatedBytesOf?.invoke() ?: -1

    /** The start of an input, escaped, to find it again: the run is deterministic, so the iteration number is the key. */
    private fun excerpt(input: String) = "${input.length} chars, starts \"" +
        input.take(300).map { if (it.code in 32..126) it.toString() else "\\u%04x".format(it.code) }.joinToString("") + "\""

    /** Every entry point of the parser on one input, and what it read. */
    // The per call timing is vibb's `slowest` and `took` of PduFuzzTest, kept per entry point and named in the failure:
    // https://github.com/palchrb/vibb-launcher/blob/main/launcher/app/src/test/java/com/kidslauncher/mdm/sms/mms/PduFuzzTest.kt#PduFuzzTest
    private val spent = LinkedHashMap<String, Long>()
    private var current = ""

    // Bytes allocated by each call of the last input, from the counter of vibb's `allocatedBytes` (same file as above, #PduFuzzTest).
    private val allocatedBy = LinkedHashMap<String, Long>()

    private fun <T> call(name: String, read: () -> T): T {
        current = name
        val before = allocatedBytes()
        val t0 = System.nanoTime()
        try {
            return read()
        } finally {
            spent[name] = (spent[name] ?: 0L) + System.nanoTime() - t0
            if (before >= 0) allocatedBy[name] = (allocatedBy[name] ?: 0L) + allocatedBytes() - before
        }
    }

    /** The slowest calls of the last input, in ms. */
    private fun slowestCalls() = spent.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key} ${it.value / 1_000_000} ms" }

    // The calls that allocated most on the last input, in KB, as vibb's `allocated` of PduFuzzTest, kept per call:
    // https://github.com/palchrb/vibb-launcher/blob/main/launcher/app/src/test/java/com/kidslauncher/mdm/sms/mms/PduFuzzTest.kt#PduFuzzTest
    private fun biggestAllocations() = allocatedBy.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key} ${it.value / 1_000} KB" }

    private fun parse(input: String): List<String> {
        spent.clear()
        allocatedBy.clear()
        val feed = call("feed") { RedditParser.feed(input, "examples", nowMillis = 1_000) }
        val conversation = call("conversation") { RedditParser.conversation(input) }
        val comments = call("comments") { RedditParser.comments(input, "examples") }
        call("comments at") { RedditParser.comments(input, "examples", moreAt) }
        val next = call("nextPage") { RedditParser.nextPage(input) }
        call("describe") { RedditParser.describe(input) }
        call("headerNote") { RedditParser.headerNote(input) }
        call("bodyMissing") { RedditParser.bodyMissing(input) }
        call("isCheckPage") { RedditParser.isCheckPage(input) }
        call("postIds") { RedditParser.postIds(input) }
        return listOfNotNull(
            if (feed.posts.isNotEmpty()) "posts" else null,
            if (conversation != null) "conversation" else null,
            if (comments.isNotEmpty()) "comments" else null,
            if (next != null) "next page" else null,
        ).ifEmpty { listOf("nothing") }
    }

    @Test(timeout = 900_000)
    fun `5,000 seeded mutations of each seed read or are refused, in bounded memory and time`() {
        // Each seed is valid as its own page.
        assertTrue(parse(seeds[0]).containsAll(listOf("posts", "next page")))
        assertEquals(4, RedditParser.feed(seeds[1], "examples", nowMillis = 1_000).posts.size)
        assertNotNull(RedditParser.conversation(seeds[2]))
        assertEquals(2, RedditParser.comments(seeds[3], "examples", moreAt).size)
        assertNotNull(RedditParser.nextPage(seeds[4]))
        // The first run loads the classes and compiles the patterns: not counted against an input.
        seeds.forEach { parse(it) }

        val random = Random(77)
        val outcomes = HashMap<String, Int>()
        var slowest = 0L
        val started = System.nanoTime()
        repeat(PER_SEED * seeds.size) { i ->
            val input = mutate(random, seeds[i % seeds.size])
            val before = allocatedBytes()
            val t0 = System.nanoTime()
            val result = try {
                parse(input)
            } catch (e: Throwable) {
                fail("iteration $i: ${e.javaClass.name} ${e.message?.take(100)} in $current after ${applied.joinToString()} for ${excerpt(input)}")
                emptyList()
            }
            val took = System.nanoTime() - t0
            val allocated = if (before >= 0) allocatedBytes() - before else 0
            slowest = maxOf(slowest, took)
            for (key in result) outcomes[key] = (outcomes[key] ?: 0) + 1
            // One read copies its input a few times at most: never an attacker-sized string.
            assertTrue("iteration $i allocated $allocated bytes for ${input.length} (most allocated by ${biggestAllocations()}; slowest calls ${slowestCalls()}) after ${applied.joinToString()}: ${excerpt(input)}", allocated < 64L * input.length + 4_000_000)
            assertTrue("iteration $i took ${took / 1_000_000} ms, slowest calls ${slowestCalls()}, after ${applied.joinToString()}: ${excerpt(input)}", took < 2_000_000_000L)
        }
        assertTrue("the allocation check must run on the test JVM", allocatedBytesOf != null)
        val seconds = (System.nanoTime() - started) / 1e9
        println("RedditParserFuzzTest: ${PER_SEED * seeds.size} inputs in %.2f s, slowest %.1f ms, outcomes %s".format(seconds, slowest / 1e6, outcomes.toSortedMap()))
        // The fuzzer reaches deep: some mutations still read, and some pages give nothing.
        assertTrue((outcomes["nothing"] ?: 0) > 0)
        assertTrue(outcomes.keys.size >= 4)
    }

    @Test
    fun `random strings of every short length read or are refused`() {
        val random = Random(9)
        repeat(5_000) {
            val input = String(CharArray(random.nextInt(48)) { random.nextInt(256).toChar() })
            try {
                parse(input)
            } catch (e: Throwable) {
                fail("${e.javaClass.name} ${e.message?.take(100)} for ${excerpt(input)}")
            }
        }
        assertEquals(listOf("nothing"), parse(""))
        assertNull(RedditParser.conversation(""))
        assertNull(RedditParser.nextPage(""))
    }

    private companion object {
        const val PER_SEED = 5_000
    }
}
