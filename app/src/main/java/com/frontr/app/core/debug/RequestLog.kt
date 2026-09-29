package com.frontr.app.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An in memory record of every request Frontr makes and what came back.
 *
 * Exists so a failure can be reported with evidence instead of a description.
 * Bounded to [CAPACITY] entries, kept in memory only, never written anywhere
 * unless you explicitly export it, and it stores no identifiers beyond the URLs
 * the app itself requested.
 */
class RequestLog {

    data class Entry(
        val atMillis: Long,
        val kind: Kind,
        val url: String,
        val httpStatus: Int? = null,
        val bodyBytes: Int? = null,
        val durationMillis: Long? = null,
        val outcome: String,
        val detail: String? = null
    )

    enum class Kind { PROBE, PROFILE, PAGE, RSS, PARSE, THREAD, LIST, CACHE, MEDIA, HTTP }

    /** A whole page Reddit sent that Frontr could not read, kept to be looked at. */
    data class Page(val atMillis: Long, val label: String, val body: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _pages = MutableStateFlow<List<Page>>(emptyList())

    fun record(entry: Entry) {
        _entries.value = (_entries.value + entry).takeLast(CAPACITY)
    }

    fun record(
        kind: Kind,
        url: String,
        outcome: String,
        httpStatus: Int? = null,
        bodyBytes: Int? = null,
        durationMillis: Long? = null,
        detail: String? = null
    ) = record(
        Entry(
            atMillis = System.currentTimeMillis(),
            kind = kind,
            url = url,
            httpStatus = httpStatus,
            bodyBytes = bodyBytes,
            durationMillis = durationMillis,
            outcome = outcome,
            detail = detail
        )
    )

    /**
     * Keeps a page Reddit sent that gave no posts, whole up to [PAGE_LIMIT]
     * characters, so an exported log shows exactly what arrived. Only the
     * last [PAGE_CAPACITY] are kept.
     */
    fun keepPage(label: String, body: String) {
        _pages.value = (_pages.value + Page(System.currentTimeMillis(), label, body.take(PAGE_LIMIT))).takeLast(PAGE_CAPACITY)
    }

    fun clear() {
        _entries.value = emptyList()
        _pages.value = emptyList()
    }

    /**
     * Plain text, newest last, suitable for pasting anywhere. [withPages] adds
     * the pages kept for diagnosis, too large for the clipboard but right for
     * a saved file. [heading] goes first, the app and system versions.
     */
    fun render(withPages: Boolean = false, heading: String? = null): String {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("Frontr request log")
            heading?.let { appendLine(it) }
            appendLine("entries: ${_entries.value.size}")
            appendLine("=".repeat(60))
            _entries.value.forEach { e ->
                appendLine("[${stamp.format(Date(e.atMillis))}] ${e.kind}  ${e.outcome}")
                appendLine("  url: ${e.url}")
                val meta = buildList {
                    e.httpStatus?.let { add("http $it") }
                    e.durationMillis?.let { add("${it}ms") }
                    e.bodyBytes?.let { add("$it bytes") }
                }
                if (meta.isNotEmpty()) appendLine("  ${meta.joinToString("  ")}")
                e.detail?.lines()?.forEach { appendLine("  $it") }
                appendLine()
            }
            if (withPages && _pages.value.isNotEmpty()) {
                appendLine("=".repeat(60))
                appendLine("Pages kept for diagnosis: ${_pages.value.size}")
                _pages.value.forEach { page ->
                    appendLine()
                    appendLine("----- [${stamp.format(Date(page.atMillis))}] ${page.label}, ${page.body.length} characters -----")
                    appendLine(page.body)
                    appendLine("----- end of page -----")
                }
            }
        }
    }

    private companion object {
        const val CAPACITY = 300
        const val PAGE_CAPACITY = 8
        const val PAGE_LIMIT = 400_000
    }
}
