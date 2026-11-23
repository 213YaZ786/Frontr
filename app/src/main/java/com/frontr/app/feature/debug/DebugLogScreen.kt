package com.frontr.app.feature.debug

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.ui.component.rememberHaptics
import com.frontr.app.ui.component.BarTitle
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.plus
import android.os.Build
import com.frontr.app.BuildConfig

import com.frontr.app.ui.component.BoldButton
import com.frontr.app.navigation.LocalReadableInset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.frontr.app.core.debug.LogExporter
import com.frontr.app.core.debug.RequestLog
import com.frontr.app.ui.icon.FrontrIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every request and its result, exportable as a text file.
 *
 * The point is that a bug report can carry evidence rather than a description.
 * Nothing here leaves the device unless you export it and send it yourself.
 *
 * Port: https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/debug/DebugLogScreen.kt#DebugLogScreen
 * LinkedOut's ScreenBanner row is Frontr's FloatingTopBar here (as PostDetailScreen and SavedMediaScreen);
 * not ported: BarAction (LinkedOut's own round action, it bans IconButton; Frontr keeps IconButton in the bar);
 * not ported: lastBody, "lastpage" and Save page (Frontr's RequestLog keeps no last body, its pages go into Save to Downloads).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val log: RequestLog = koinInject()
    val exporter: LogExporter = koinInject()
    val entries by log.entries.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val stamp = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    // A stable key per entry: an entry carries no id, so equal entries are told apart by
    // their order of arrival (counted from the oldest), newest shown first.
    val keyed = remember(entries) {
        val seen = HashMap<RequestLog.Entry, Int>()
        entries.map { entry ->
            val n = (seen[entry] ?: 0) + 1
            seen[entry] = n
            "${entry.atMillis}-${entry.kind}-$n-${entry.url.hashCode()}" to entry
        }.asReversed()
    }

    // The bar folds away going down and returns going up, the list full screen.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Activity log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = log::clear) {
                        Icon(FrontrIcons.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        // The whole page is one list, the actions its first row.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                bottom = 12.dp
            ).plus(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BoldButton(onClick = {
                        clipboard.setText(AnnotatedString(log.render()))
                        haptics.done()
                        scope.launch { snackbar.showSnackbar("Log copied") }
                    }) { Text("Copy all") }

                    BoldButton(onClick = {
                        scope.launch {
                            val name = "frontr-log-${System.currentTimeMillis()}.txt"
                            val heading = "Frontr ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, " +
                                "language ${java.util.Locale.getDefault().toLanguageTag()}"
                            exporter.exportText(name, log.render(withPages = true, heading = heading))
                                .onSuccess { snackbar.showSnackbar("Saved to $it") }
                                .onFailure { snackbar.showSnackbar("Could not save the file") }
                        }
                    }) { Text("Save to Downloads") }
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
                items(keyed, key = { it.first }) { (_, entry) ->
                    ZoneSurface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).animateItem(),
                        shape = RoundedCornerShape(16.dp),
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
                                // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents.
                                // not ported: buildPrimaryText, buildSecondaryTextForBottomSheet, buildSecondaryTextForEntries,
                                // buildSecondaryTextListForEntries, numberTypeLabel (call log texts, a request log has no calls).
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
