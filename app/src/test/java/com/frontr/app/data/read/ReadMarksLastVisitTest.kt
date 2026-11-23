package com.frontr.app.data.read

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The previous visit decides what is new. MTGA has no test of its ReadMarks, so the cases follow its comments:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/read/ReadMarks.kt#markReadIfOld
 * The folder is JUnit 4's TemporaryFolder rule, as ReadMarksTrimTest's neighbours use it.
 */
class ReadMarksLastVisitTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val visit = 1_000L

    private suspend fun store(previousVisit: Long? = visit): ReadMarks {
        if (previousVisit != null) folder.root.resolve("last-visit.txt").writeText(previousVisit.toString())
        return ReadMarks(folder.root).also { it.load() }
    }

    private fun ReadMarks.isUnread(id: String) = ready && id !in read.value

    @Test
    fun `a post published before the previous visit is read`() = runBlocking {
        val reads = store()
        reads.markReadBeforeLastVisit(emptyMap())

        reads.markReadIfOld(listOf("old" to visit - 1))

        assertFalse(reads.isUnread("old"))
    }

    @Test
    fun `a post published after the previous visit is not`() = runBlocking {
        val reads = store()
        reads.markReadBeforeLastVisit(emptyMap())

        reads.markReadIfOld(listOf("new" to visit + 1))

        assertTrue(reads.isUnread("new"))
    }

    @Test
    fun `at launch only the old ones are marked`() = runBlocking {
        val reads = store()

        reads.markReadBeforeLastVisit(mapOf("old" to visit - 1, "same" to visit, "new" to visit + 1))

        assertFalse(reads.isUnread("old"))
        assertFalse(reads.isUnread("same"))
        assertTrue(reads.isUnread("new"))
    }

    @Test
    fun `the first launch has no previous visit and nothing is new`() = runBlocking {
        val reads = store(previousVisit = null)

        reads.markReadBeforeLastVisit(mapOf("a" to 5L, "b" to Long.MAX_VALUE - 1))

        assertFalse(reads.isUnread("a"))
        assertFalse(reads.isUnread("b"))
    }

    @Test
    fun `the launch writes this visit for the next one`() = runBlocking {
        val reads = store()

        reads.markReadBeforeLastVisit(emptyMap())

        val written = folder.root.resolve("last-visit.txt").readText().trim().toLong()
        assertTrue(written > visit)
    }
}
