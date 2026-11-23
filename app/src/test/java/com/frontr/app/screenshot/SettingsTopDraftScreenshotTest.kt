package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.navigation.WidthClass
import com.frontr.app.ui.component.FloatingFrame
import com.frontr.app.ui.component.FloatingTop
import com.frontr.app.ui.component.LargeTitle
import com.frontr.app.ui.component.SettingsFoldedSections
import com.frontr.app.ui.component.SettingsHeaderSpacer
import com.frontr.app.ui.component.SettingsListDetail
import com.frontr.app.ui.component.SettingsSection
import com.frontr.app.ui.component.SettingsSectionTitle
import com.frontr.app.ui.component.SettingsSwitchTile
import com.frontr.app.ui.component.SettingsTileGrid
import com.frontr.app.ui.component.SideDockClearance
import com.frontr.app.ui.icon.FrontrIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 8), nothing of app/src/main changes: the top of Settings.
 * settings_top_now is today's: FloatingFrame with LargeTitle("Settings") and the list starting under SettingsHeaderSpacer
 * and 24 dp ("settings commence bas sur l'ecran c'est bizarre"). settings_top_draft has no large title: "Settings" small,
 * bold and centred on the top line, as FloatingTop draws it, and the sections' list right under it, 16 dp below (24 dp
 * today). On a wide window SettingsListDetail keeps its own 24 dp spacer, so the draft gives it a header 8 dp shorter
 * to show the 16 dp.
 * The screen is SettingsScreen's frame (SettingsScreen.kt, its groups and its tiles are private there: a copy with
 * fixed data), the sections folded as the screen opens (a phone) and the first section open (a tablet, its page with
 * Appearance's switches).
 * Port of MTGA's validated draft, with Frontr's own groups and tiles:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/SettingsTopDraftScreenshotTest.kt#Settings
 * not ported: SettingsScreen's view model, every tile but Appearance's switches and its dialogs (ChoiceDialog, the
 * tile explain dialogs), the toast, the storage measures, the dock padding, the section state saved across a turn
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class SettingsTopDraftScreenshotTest : ScreenshotBase() {

    // The groups of settings, as SettingsScreen lists them (private there).
    private val appearance = SettingsSection("appearance", "Appearance", SettingsIcons.Palette)
    private val sections = listOf(
        appearance,
        SettingsSection("media", "Media", SettingsIcons.Image),
        SettingsSection("reading", "Reading", FrontrIcons.Comment),
        SettingsSection("background", "Background updates", FrontrIcons.Refresh),
        SettingsSection("storage", "Storage", FrontrIcons.Folder),
        SettingsSection("list", "Subreddits", FrontrIcons.Person),
        SettingsSection("support", "Support", SettingsIcons.Warning),
        SettingsSection("about", "About", FrontrIcons.Info)
    )

    // Appearance's switches, as SettingsScreen builds them (fixed values).
    private val tiles = listOf(
        SettingsSwitchTile(
            id = "black", name = "Pure black", title = "Pure black", icon = SettingsIcons.Night,
            summary = "Deeper blacks in dark mode. Easier on the battery with OLED screens.",
            checked = false, onChange = {}
        ),
        SettingsSwitchTile(
            id = "glass", name = "Glass effects", title = "Glass effects", icon = SettingsIcons.AutoAwesome,
            summary = "Zones and floating buttons in liquid glass, over a soft light in your wallpaper's colours.",
            checked = true, onChange = {}
        ),
        SettingsSwitchTile(
            id = "counts", name = "Counts", title = "Show counts", icon = FrontrIcons.Heart,
            summary = "Comments and votes under each post.",
            checked = true, onChange = {}
        ),
        SettingsSwitchTile(
            id = "compact", name = "Compact posts", title = "Compact posts", icon = SettingsIcons.GridView,
            summary = "Tighter spacing, smaller avatars and shorter pictures. More posts on screen.",
            checked = false, onChange = {}
        ),
        SettingsSwitchTile(
            id = "square", name = "Square avatars", title = "Square avatars", icon = FrontrIcons.Person,
            summary = "Rounded squares instead of circles.",
            checked = false, onChange = {}
        )
    )

    @Test
    fun now() = capture("settings_top_now") { Settings(draft = false) }

    @Test
    fun draft() = capture("settings_top_draft") { Settings(draft = true) }

    @Test
    fun edge() = captureEdge("settings_top_draft") { Settings(draft = true) }

    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/SettingsTopDraftScreenshotTest.kt#Settings
    @Composable
    private fun Settings(draft: Boolean) {
        val compactScroll = rememberScrollState()
        val detailScroll = rememberScrollState()
        // The gap between the header and the list: 24 dp today, 16 dp in the draft.
        val gap: Dp = if (draft) 16.dp else 24.dp
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = WidthClass.of(maxWidth) != WidthClass.COMPACT
            val inset = LocalReadableInset.current
            val dockRoom = if (wide && inset >= SideDockClearance) SideDockClearance else 0.dp
            CompositionLocalProvider(LocalReadableInset provides if (wide) 0.dp else inset) {
                Box(Modifier.fillMaxSize().padding(start = dockRoom)) {
                    FloatingFrame(
                        bottom = 0.dp,
                        scroll = if (wide) detailScroll else compactScroll,
                        top = { FloatingTop("Settings") },
                        largeTitle = if (draft) null else LargeTitle("Settings")
                    ) { padding ->
                        if (wide) {
                            SettingsListDetail(
                                // SettingsListDetail adds its own 24 dp: 8 dp less in the header gives the draft's 16.
                                padding = if (draft) PaddingValues(top = (padding.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp)) else padding,
                                sections = sections,
                                section = appearance,
                                onSection = {},
                                detailScroll = detailScroll,
                                detail = {
                                    SettingsSectionTitle(appearance.title)
                                    SettingsTileGrid(tiles) {}
                                }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(compactScroll)
                                    .padding(horizontal = LocalReadableInset.current)
                            ) {
                                SettingsHeaderSpacer(padding)
                                Spacer(Modifier.height(gap))
                                SettingsFoldedSections(sections, open = null, onOpen = {}) {}
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
