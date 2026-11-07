package com.frontr.app.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.welcome.WelcomeContent
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.icon.FrontrIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The welcome guide as drawn on a phone and a tablet, by day and by night, with fixed tiles. Ported from the same
 * test of Language, as LinkedOut's (its commit 6f8bbce) is
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/WelcomeScreenshotTest.kt#WelcomeScreenshotTest
 * (the capture itself is ScreenshotBase's): the first run, with the best settings already set, and everything set,
 * reopened from Settings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String,
        done: Boolean, required: Boolean, error: Boolean = false, progress: Float? = null
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
        required = required, error = error, progress = progress, onClick = {}
    )

    // The first run: the best settings already set, the system's two still to do.
    private val firstRun = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Never", done = true, required = false),
        tile("updates", "Updates", SettingsIcons.Update, "Install", done = true, required = false),
        tile("notify", "New posts", FrontrIcons.Bell, "Off", done = true, required = false),
        tile("links", "Reddit links", SettingsIcons.Link, "Off", done = false, required = false)
    )

    // Everything done, the guide reopened from Settings.
    private val allSet = listOf(
        tile("media", "Save media", FrontrIcons.Download, "On Wi-Fi only", done = true, required = false),
        tile("updates", "Updates", SettingsIcons.Update, "Install", done = true, required = false),
        tile("notify", "New posts", FrontrIcons.Bell, "Notify me", done = true, required = false),
        tile("links", "Reddit links", SettingsIcons.Link, "On", done = true, required = false)
    )

    @Test
    fun firstRun() = capture("welcome_first_run") { WelcomeContent(firstRun, reopened = false, onStart = {}) }

    @Test
    fun allSet() = capture("welcome_all_set") { WelcomeContent(allSet, reopened = true, onStart = {}) }
}
