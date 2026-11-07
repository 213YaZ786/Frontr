package com.frontr.app.screenshot

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.feature.settings.SettingsScreen
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
 * Settings as drawn on a phone and a tablet, by day and by night, with the settings a first run has. Ported from
 * Language's SettingsScreenshotTest, as LinkedOut's (its commit 6f8bbce) is
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/SettingsScreenshotTest.kt#SettingsScreenshotTest
 * which is written as SMS's PersonPageScreenshotTest (the capture itself is ScreenshotBase's): as it opens (the
 * sections folded on a phone, the first one's page beside the list on a tablet), then each of two other sections
 * opened by a tap on its row, as Compose's testing guide taps a node found by its text
 * https://developer.android.com/develop/ui/compose/testing/interactions
 *
 * Language splits its screen into a content function that takes its settings. Frontr's screen reads its settings
 * through a view model, so Koin is started here with the app's own module instead, as the app starts it: the stores
 * are read from an empty files folder, which gives the defaults.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class SettingsScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // A row is found by its text: on a tablet the open section's name is on its row and on its page, so the first.
    private fun ComposeTestRule.tap(text: String) {
        onAllNodesWithText(text).onFirst().performClick()
        waitForIdle()
    }

    private fun screen(name: String, open: String? = null) = capture(name, interact = {
        if (open != null) compose.tap(open)
    }) {
        SettingsScreen(onOpenDebugLog = {}, onOpenWelcome = {}, onOpenSavedMedia = {})
    }

    @Test
    fun folded() = screen("settings")

    @Test
    fun appearance() = screen("settings_appearance", open = "Appearance")

    @Test
    fun about() = screen("settings_about", open = "About")
}
