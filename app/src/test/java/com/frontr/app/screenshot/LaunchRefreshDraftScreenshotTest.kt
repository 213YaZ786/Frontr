package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.ui.component.GlassSlider
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.ui.component.SettingsActionTile
import com.frontr.app.ui.component.SettingsSectionTitle
import com.frontr.app.ui.component.SettingsTileGrid
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
 * DRAFT for the user's yes (requests.md point 21), nothing of app/src/main changes: the setting "Refresh at launch" as a
 * tile of Settings' Reading section, the same TileFace and size as Start on and Reddit links, its value the tile's state
 * ("1 h", "Every launch", "Never"). A tap opens a dialog as SettingsScreen's other choices do (ZoneAlertDialog,
 * ChoiceDialog's frame: title, the value as text, Cancel replaced by Done) holding the app's GlassSlider with nine stops.
 * Pictures: launch_refresh_draft_tile (the section at the default 1 h), launch_refresh_draft_dialog_1h and
 * launch_refresh_draft_dialog_never (the dialog over the section), and the tile and the 1 h dialog at 200 % text and on a
 * 360 dp phone. The stops are the kit's SyncFrequency (ui/component/LaunchRefresh.kt), Feeder's with Every launch first:
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/LaunchRefreshDraftScreenshotTest.kt#LaunchRefreshDraftScreenshotTest
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/ChoiceDialog.kt#ChoiceDialog
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/GlassSlider.kt#GlassSlider
 * not ported: the test's Stop enum (the kit's SyncFrequency takes its place, with its tile and line texts), the Share as
 * Nitter switch tile and the X links tile (X only; Frontr's Reading section holds Start on and Reddit links), SyncFrequency's
 * stringId (plain text here), setSyncFrequency's storage and schedulePeriodicRssSync (a draft keeps nothing, nothing runs
 * in the background); ChoiceDialog's option capsules, Tick, danger and icon (a slider takes their place)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class LaunchRefreshDraftScreenshotTest : ScreenshotBase() {

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

    // The Reading section's tiles, as SettingsScreen builds them (fixed values), the new one among them.
    private fun tiles(stop: SyncFrequency) = listOf(
        SettingsActionTile(
            id = "startTab", name = "Start on", title = "Start on", icon = FrontrIcons.Home,
            value = "Home", summary = "Home"
        ),
        SettingsActionTile(
            id = "refreshLaunch", name = "Refresh at launch", title = "Refresh at launch", icon = SettingsIcons.Timer,
            value = stop.tile, summary = stop.line
        ),
        SettingsActionTile(
            id = "redditLinks", name = "Reddit links", title = "Open Reddit links in Frontr", icon = SettingsIcons.Link,
            value = "Off",
            summary = "Off. Turn on \"Open supported links\" and add the reddit.com links. " +
                "Sharing a link to Frontr works either way."
        )
    )

    @Test
    fun tile() = capture("launch_refresh_draft_tile") { Reading(defaultSyncFrequency, open = false) }

    @Test
    fun dialogOneHour() = capture("launch_refresh_draft_dialog_1h", window = true) { Reading(defaultSyncFrequency, open = true) }

    @Test
    fun dialogNever() = capture("launch_refresh_draft_dialog_never", window = true) { Reading(SyncFrequency.NEVER, open = true) }

    @Test
    fun edge() = captureEdge("launch_refresh_draft_tile") { Reading(defaultSyncFrequency, open = false) }

    // ScreenshotBase's captureEdge shoots the root, which leaves a dialog's window out: the dialog is taken here, in the
    // step before it, with Roborazzi's captureScreenRoboImage as capture(window = true) does; the root picture the base
    // then writes (the section behind the dialog) goes to its own name.
    @Test
    fun edgeDialog() {
        val edges = listOf("font200", "narrow")
        var shot = 0
        captureEdge(
            "launch_refresh_draft_dialog_1h_behind",
            interact = {
                captureScreenRoboImage(
                    "src/test/screenshots/launch_refresh_draft_dialog_1h_edge_${edges[shot++]}.png",
                    roborazziOptions = RoborazziOptions(
                        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
                        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
                    )
                )
            }
        ) { Reading(defaultSyncFrequency, open = true) }
    }

    // The Reading section, and over it the dialog a tap on the tile opens.
    @Composable
    private fun Reading(stop: SyncFrequency, open: Boolean) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            SettingsSectionTitle("Reading")
            SettingsTileGrid(tiles(stop)) {}
        }
        if (open) RefreshDialog(stop)
    }

    // ChoiceDialog's frame: ZoneAlertDialog with the title, a centred text and its one button; the slider is the choice.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/LaunchRefreshDraftScreenshotTest.kt#RefreshDialog
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
