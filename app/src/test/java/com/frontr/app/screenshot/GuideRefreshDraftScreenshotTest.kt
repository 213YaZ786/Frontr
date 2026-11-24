package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.welcome.WelcomeContent
import com.frontr.app.ui.component.GlassSlider
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.component.SyncFrequency
import com.frontr.app.ui.component.ZoneAlertDialog
import com.frontr.app.ui.component.defaultSyncFrequency
import com.frontr.app.ui.icon.FrontrIcons
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 22), nothing of app/src/main changes: the welcome guide as the app draws
 * it today (WelcomeContent: the mark, "Reddit reader", the tiles, the round Start) with one more tile, "Refresh at launch",
 * the Settings tile's icon (SettingsIcons.Timer), its state the stop's tile label ("1 h"), a preset so done, not
 * required. Frontr's guide has no Sources tile (X only, as the other readers' own sources): the tile comes right after
 * Save media. guide_refresh_draft_dialog shows the dialog the tile opens, the Settings one
 * (LaunchRefreshDraftScreenshotTest's RefreshDialog) over the guide, at 1 h. Both with their 200 % text and 360 dp edges.
 * The stops are Feeder's SyncFrequency (the app holds it as ui/component/LaunchRefresh.kt SyncFrequency):
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/GuideRefreshDraftScreenshotTest.kt#GuideRefreshDraftScreenshotTest
 * not ported: the test's Stop enum (the kit's SyncFrequency takes its place), GuideContent (Frontr's own WelcomeContent
 * draws its guide, with the Start cookie in place of the pinned BoldButton), MTGA's Sources and X links tiles (X only),
 * SyncFrequency's stringId (plain text here), setSyncFrequency's storage and schedulePeriodicRssSync (a draft keeps
 * nothing, nothing runs in the background)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class GuideRefreshDraftScreenshotTest : ScreenshotBase() {

    // The tiles read the app's marks and settings through Koin, as in PullDotsDraftScreenshotTest.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = onClick
    )

    // WelcomeGuideDraftScreenshotTest's tiles, Frontr's real choices, the new one after Save media.
    private val tiles = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Posts only",
            "Posts only keeps the text and the links of what you read on the phone. Images and videos " +
                "also saves the pictures and the videos, which spends your data. Tap to choose.", done = true),
        tile("refresh", "Refresh at launch", SettingsIcons.Timer, defaultSyncFrequency.tile,
            "When Frontr opens, a subreddit is read again once its last read is older than this. Tap to choose.", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install",
            "When a new version is out, Frontr can install it when you leave the app. " +
                "Android's permission to install is needed once.", done = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Off",
            "A notification when the subreddits you follow get new posts. Tap to turn on.", done = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off",
            "reddit.com links tapped anywhere open in Frontr once Android's \"Open supported links\" " +
                "is on for it. Tap to open that page.", done = false)
    )

    @Test
    fun draft() = capture("guide_refresh_draft") { Guide(open = false) }

    @Test
    fun dialog() = capture("guide_refresh_draft_dialog", window = true) { Guide(open = true) }

    @Test
    fun edge() = captureEdge("guide_refresh_draft") { Guide(open = false) }

    // ScreenshotBase's captureEdge shoots the root, which leaves a dialog's window out: the dialog is taken here, in the
    // step before it, with Roborazzi's captureScreenRoboImage as capture(window = true) does; the root picture the base
    // then writes (the guide behind the dialog) goes to its own name.
    @Test
    fun edgeDialog() {
        val edges = listOf("font200", "narrow")
        var shot = 0
        captureEdge(
            "guide_refresh_draft_dialog_behind",
            interact = {
                captureScreenRoboImage(
                    "src/test/screenshots/guide_refresh_draft_dialog_edge_${edges[shot++]}.png",
                    roborazziOptions = RoborazziOptions(
                        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
                        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
                    )
                )
            }
        ) { Guide(open = true) }
    }

    @Composable
    private fun Guide(open: Boolean) {
        WelcomeContent(tiles, reopened = false, onStart = {})
        if (open) RefreshDialog(defaultSyncFrequency)
    }

    // ChoiceDialog's frame: ZoneAlertDialog with the title, a centred text and its one button; the slider is the choice.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/GuideRefreshDraftScreenshotTest.kt#RefreshDialog
    @Composable
    private fun RefreshDialog(start: SyncFrequency) {
        var index by remember { mutableFloatStateOf(start.ordinal.toFloat()) }
        val current = SyncFrequency.entries[index.toInt().coerceIn(0, SyncFrequency.entries.lastIndex)]
        ZoneAlertDialog(
            onDismissRequest = {},
            title = { Text("Refresh at launch") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        current.line,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassSlider(
                        value = index,
                        onValueChange = { index = it },
                        valueRange = 0f..(SyncFrequency.entries.size - 1).toFloat(),
                        steps = SyncFrequency.entries.size - 2,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
            },
            confirmButton = { QuietButton(onClick = {}) { Text("Done") } }
        )
    }
}
