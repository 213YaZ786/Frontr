package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.welcome.WelcomeContent
import com.frontr.app.ui.component.SettingsActionTile
import com.frontr.app.ui.component.SettingsSectionTitle
import com.frontr.app.ui.component.SettingsSwitchTile
import com.frontr.app.ui.component.SettingsTile
import com.frontr.app.ui.component.SettingsTileGrid
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.icon.FrontrIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 23), nothing of app/src/main changes: one font size in the tiles of the
 * guide and of Settings. The state line (TileFace's second line) takes the name's style at the name's size (both shrink
 * together), told apart by colour only: onSurfaceVariant, the error colour when the tile says so; the name keeps the
 * content colour. Frontr's TileFace, SetupTileGrid and SettingsTileGrid already carry that change (the synced kit), so
 * the draft draws them as they are, with fixed data: the guide's tiles are the four of WelcomeScreen.kt's rememberSetup
 * (Save media, Updates, New posts, Reddit links) in their states, and the Settings sections are Background and About of
 * SettingsScreen.kt (the switches first, then what opens something, as sectionTiles lays them).
 * labelLarge for both lines: Material 3's type scale
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Typography.kt#Typography
 * Pictures: tile_text_size_draft_guide_* and tile_text_size_draft_settings_*, with edge_font200 and edge_narrow.
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/TileTextSizeDraftScreenshotTest.kt#TileTextSizeDraftScreenshotTest
 * not ported: Typography's other styles (unused here); DraftTileGrid, DraftTile and DraftTileFace (MTGA's copies of
 * SettingsTileGrid and TileFace with the state changed: Frontr's own SettingsTileGrid, SetupTileGrid and TileFace have the
 * change, so no copy is needed); the Guide's own layout (WelcomeContent is that layout, drawn as the app draws it, with its
 * StartCookie in place of MTGA's pinned Start button); TileFace's haptics, long press, dimming, semantics and the guide's
 * check and progress under the state (not part of the change).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TileTextSizeDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, on: Boolean = false, error: Boolean = false
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = on,
        required = false, error = error, onClick = {}
    )

    // The guide on a first run, the best presets already set.
    private val guide = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Images and videos", on = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", on = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Off", on = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off")
    )

    // Android refused three things: each state in the error colour, the long one on two lines.
    private val guideRefused = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Saved, no progress bar", on = true, error = true),
        tile("updates", "Updates", SettingsIcons.Update, "Installs not allowed", error = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Notifications off", error = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off")
    )

    // The Background section (two switches on, one off, the frequency), then About (the long value on two lines).
    private val background: List<SettingsTile> = listOf(
        SettingsSwitchTile(
            id = "sync", name = "New posts", title = "Check for new posts", icon = FrontrIcons.Refresh,
            summary = "", checked = true, onChange = {}
        ),
        SettingsSwitchTile(
            id = "syncWifi", name = "Wi-Fi only", title = "Wi-Fi only", icon = SettingsIcons.Wifi,
            summary = "", checked = true, onChange = {}
        ),
        SettingsSwitchTile(
            id = "notify", name = "Notify", title = "Notify about new posts", icon = FrontrIcons.Bell,
            summary = "", checked = false, onChange = {}
        ),
        SettingsActionTile(
            id = "frequency", name = "Frequency", title = "Frequency", icon = SettingsIcons.Timer,
            value = "Every 30 minutes", summary = ""
        )
    )

    private val about: List<SettingsTile> = listOf(
        SettingsActionTile(
            id = "guide", name = "Guide", title = "Guide", icon = FrontrIcons.Play,
            value = "All set", summary = ""
        ),
        SettingsActionTile(
            id = "updates", name = "Updates", title = "Updates", icon = SettingsIcons.Update,
            value = "Notify me", summary = ""
        ),
        SettingsActionTile(
            id = "source", name = "Source code", title = "Source code", icon = SettingsIcons.Code,
            value = "github.com/213YaZ786/Frontr", summary = ""
        ),
        SettingsActionTile(
            id = "thanks", name = "Thanks", title = "Thanks", icon = FrontrIcons.Heart,
            value = "Reddit", summary = ""
        )
    )

    @Test
    fun guideTiles() = capture("tile_text_size_draft_guide") { Guide(guide) }

    @Test
    fun settingsTiles() = capture("tile_text_size_draft_settings") { Settings() }

    @Test
    fun guideEdge() = captureEdge("tile_text_size_draft_guide") { Guide(guideRefused) }

    @Test
    fun settingsEdge() = captureEdge("tile_text_size_draft_settings") { Settings() }

    @Composable
    private fun Settings() {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            SettingsSectionTitle("Background")
            SettingsTileGrid(background) {}
            SettingsSectionTitle("About")
            SettingsTileGrid(about) {}
        }
    }

    // The guide as the app draws it (the title and Start unchanged), the tiles with the one size.
    @Composable
    private fun Guide(items: List<SetupTile>) {
        WelcomeContent(items, reopened = false, onStart = {})
    }
}
