package com.frontr.app.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.BarTitle
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.ui.icon.FrontrIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DRAFT for the user's yes (requests.md point 15), nothing of app/src/main changes: the Activity log under the
 * floating capsule. Today (DebugLogScreen.kt) a Material TopAppBar carries the title and its two icons, and the
 * Copy and Save buttons sit in a column above a separate list. The draft is LinkedOut's: the capsule (FloatingTopBar,
 * BarTitle "Activity log", the back arrow, the Delete action, folding with Material's enterAlwaysScrollBehavior) and
 * the whole page as one list under it, full screen: the Copy and Save row the first item, then the entries as zones
 * (the time, the kind and the outcome, the address in monospace, the numbers, the detail), or the empty sentence.
 * Pictures: debug_log_top_draft (entries), debug_log_empty_draft (nothing recorded); the _edge_ pictures are both at
 * 200 % text and on a 360 dp phone.
 * Ported from LinkedOut's DebugLogScreen, its screen kept, the entries given as fixed data in place of RequestLog's
 * flow:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/debug/DebugLogScreen.kt#DebugLogScreen
 * not ported: the clipboard, the export and the snackbar (the buttons do nothing in a picture), the haptics, the
 * "Save page" button and the "Last page received" line (LinkedOut's lastBody: Frontr's Save to Downloads carries its
 * pages itself), navigationBarBottom and statusBarTop (Scaffold's padding)
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class DebugLogTopDraftScreenshotTest : ScreenshotBase() {

    // The bar's height is measured after the first frame: frames are given before the picture.
    private fun settle() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    // What the log holds after a launch: Reddit's browser check passed, a sub read, a thread, a failure.
    private val entries = listOf(
        RequestLog.Entry(
            FIXED_NOW - 95_000, RequestLog.Kind.PAGE, "https://www.reddit.com/r/Android/",
            httpStatus = 200, bodyBytes = 412_880, durationMillis = 2_140, outcome = "ok", detail = "25 posts read"
        ),
        RequestLog.Entry(
            FIXED_NOW - 62_000, RequestLog.Kind.PROFILE, "https://www.reddit.com/r/GrapheneOS/",
            httpStatus = 200, bodyBytes = 388_015, durationMillis = 1_870, outcome = "ok", detail = "sub header: icon found, banner found"
        ),
        RequestLog.Entry(
            FIXED_NOW - 41_000, RequestLog.Kind.THREAD, "https://www.reddit.com/r/linux/comments/1pqrst/a_good_week_for_the_people_building_in_the_open/",
            httpStatus = 200, bodyBytes = 96_402, durationMillis = 940, outcome = "ok", detail = "20 comments, 3 more to load"
        ),
        RequestLog.Entry(
            FIXED_NOW - 12_000, RequestLog.Kind.LIST, "https://www.reddit.com/svc/shreddit/community-more-posts/best/?name=privacy",
            httpStatus = 429, durationMillis = 310, outcome = "failed", detail = "Reddit asked to slow down"
        )
    )

    @Test
    fun entriesPage() = capture("debug_log_top_draft", interact = ::settle) { DebugLog(entries) }

    @Test
    fun emptyPage() = capture("debug_log_empty_draft", interact = ::settle) { DebugLog(emptyList()) }

    @Test
    fun entriesEdge() = captureEdge("debug_log_top_draft", interact = ::settle) { DebugLog(entries) }

    @Test
    fun emptyEdge() = captureEdge("debug_log_empty_draft", interact = ::settle) { DebugLog(emptyList()) }

    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/debug/DebugLogScreen.kt#DebugLogScreen
    @Composable
    private fun DebugLog(entries: List<RequestLog.Entry>) {
        val stamp = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
        val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

        Scaffold(
            // The page's ground is painted under the whole app, see MainActivity.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
            topBar = {
                FloatingTopBar(
                    scrollBehavior = behavior,
                    title = { BarTitle("Activity log") },
                    navigationIcon = {
                        // The 48 dp target of the other drafts' bars.
                        Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                            Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                            Icon(FrontrIcons.Delete, contentDescription = "Clear")
                        }
                    }
                )
            }
        ) { padding ->
            // The whole page is one list, the capsule's height its top padding
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    top = padding.calculateTopPadding(),
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BoldButton(onClick = {}) { Text("Copy all") }
                        BoldButton(onClick = {}) { Text("Save to Downloads") }
                    }
                }

                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nothing recorded yet. Open an account or scroll for older posts, then " +
                                "come back here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                        )
                    }
                } else {
                    items(entries.reversed()) { entry ->
                        ZoneSurface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = ShapeDefaults.Large,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${stamp.format(Date(entry.atMillis))}  ${entry.kind}  ${entry.outcome}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    entry.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val meta = buildList {
                                    entry.httpStatus?.let { add("http $it") }
                                    entry.durationMillis?.let { add("${it}ms") }
                                    entry.bodyBytes?.let { add("$it bytes") }
                                }
                                if (meta.isNotEmpty()) {
                                    // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
                                    Text(
                                        meta.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                entry.detail?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
