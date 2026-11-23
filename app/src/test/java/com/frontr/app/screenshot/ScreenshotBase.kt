package com.frontr.app.screenshot

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import android.os.SystemClock
import com.frontr.app.navigation.ReadableScroll
import com.frontr.app.ui.glass.LocalGlass
import com.frontr.app.ui.glass.glassGround
import com.frontr.app.ui.glass.rememberGlassLook
import com.frontr.app.ui.theme.FrontrTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

// The one instant every screenshot test draws at: 2026-11-08T12:00:00Z, the day the pictures were first recorded.
// Ported from MTGA's ScreenshotBase
// https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ScreenshotBase.kt#FIXED_NOW
const val FIXED_NOW = 1_794_139_200_000L

/**
 * What every screenshot test of this app shares: a screen drawn on a phone and a tablet, by day and by night, and
 * compared with the pictures kept in src/test/screenshots. Ported from Language's ScreenshotBase, as LinkedOut's
 * (its commit 6f8bbce) is
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/ScreenshotBase.kt#ScreenshotBase
 * which takes it from Now in Android's captureForDevice
 * (https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt):
 * the screen's size set through Robolectric's qualifiers, inspection mode on, one picture each; and written as SMS's
 * PersonPageScreenshotTest (this family's model). The frame around the screen is MainActivity's (the theme, glass
 * look and ground), which needs an activity's window bars, so it is rebuilt here without them.
 * The fixed clock, [capture]'s window picture and [captureEdge] are MTGA's
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ScreenshotBase.kt#ScreenshotBase
 */
// The clock, ported from Robolectric: ShadowSystem#currentTimeMillis answers SystemClock.uptimeMillis() in the
// classes it instruments (com.frontr.app here, so relativeTime and the other reads in the app answer FIXED_NOW), and the
// paused looper's clock is moved by SystemClock.setCurrentTimeMillis (ShadowPausedSystemClock#setCurrentTimeMillis).
// https://github.com/robolectric/robolectric/blob/master/shadows/framework/src/main/java/org/robolectric/shadows/ShadowSystem.java#currentTimeMillis
// https://github.com/robolectric/robolectric/blob/master/shadows/framework/src/main/java/org/robolectric/shadows/ShadowPausedSystemClock.java#setCurrentTimeMillis
// Not intercepted: java.time's now() reads the JDK's own clock, which Robolectric does not instrument.
@OptIn(ExperimentalRoborazziApi::class)
@Config(instrumentedPackages = ["com.frontr.app"])
abstract class ScreenshotBase {

    // Outer to the compose rule (order 0 runs first), so the clock and the zone are set before the activity starts.
    @get:Rule(order = 0)
    val fixedClock = object : ExternalResource() {
        private var zone: TimeZone? = null

        override fun before() {
            zone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            SystemClock.setCurrentTimeMillis(FIXED_NOW)
        }

        override fun after() {
            TimeZone.setDefault(zone)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Now in Android's DefaultTestDevices, phone and tablet.
    private val devices = listOf(
        "phone" to "w411dp-h914dp-420dpi",
        "tablet" to "w1280dp-h800dp-320dpi"
    )

    private var pictures = 0

    private val options = RoborazziOptions(
        // Pixel-perfect, as Now in Android's DefaultRoborazziOptions: a word changed is a change.
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
    )

    // As captureForDevice: the qualifiers first, then the content set on the activity again; [interact] taps what the
    // picture should show opened, once the screen has settled.
    // [window] takes the picture with Roborazzi's captureScreenRoboImage, which draws a dialog's window too
    // https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/WindowCaptureTest.kt#WindowCaptureTest
    protected fun capture(name: String, interact: () -> Unit = {}, window: Boolean = false, body: @Composable () -> Unit) {
        devices.forEach { (device, qualifiers) ->
            RuntimeEnvironment.setQualifiers(qualifiers)
            listOf(false, true).forEach { night ->
                // The activity keeps the saved state of the screen drawn before (a section opened, a scroll): a key per
                // picture gives each its own, so a tap in one does not decide the next.
                val picture = pictures++
                compose.activity.setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        FrontrTheme(darkTheme = night, dynamicColor = false) {
                            // The app's own look, quiet glass, as MainActivity sets it.
                            val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                            CompositionLocalProvider(LocalGlass provides look, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                key(picture) {
                                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                                        // As FrontrApp wraps every route: the readable column on a wide window.
                                        ReadableScroll { body() }
                                    }
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                interact()
                val path = "src/test/screenshots/${name}_${device}_${if (night) "dark" else "light"}.png"
                if (window) {
                    captureScreenRoboImage(path, roborazziOptions = options)
                } else {
                    compose.onRoot().captureRoboImage(path, roborazziOptions = options)
                }
            }
        }
    }

    // Edge pictures of a draft, phone and day only: the screen at 200 % text and on a 360 dp phone. The font scale is
    // set before the content and put back after, as captureRoboImage does with its fontScale
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // and the device by qualifiers, as captureMultiDevice
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    protected fun captureEdge(name: String, interact: () -> Unit = {}, body: @Composable () -> Unit) {
        val savedQualifiers = RuntimeEnvironment.getQualifiers()
        val savedFontScale = RuntimeEnvironment.getFontScale()
        try {
            listOf(
                Triple("font200", "w411dp-h914dp-420dpi", 2f),
                Triple("narrow", "w360dp-h760dp-xhdpi", 1f)
            ).forEach { (edge, qualifiers, fontScale) ->
                RuntimeEnvironment.setQualifiers(qualifiers)
                RuntimeEnvironment.setFontScale(fontScale)
                val picture = pictures++
                compose.activity.setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        FrontrTheme(darkTheme = false, dynamicColor = false) {
                            val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                            CompositionLocalProvider(LocalGlass provides look, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                key(picture) {
                                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                                        ReadableScroll { body() }
                                    }
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                interact()
                compose.onRoot().captureRoboImage("src/test/screenshots/${name}_edge_${edge}.png", roborazziOptions = options)
            }
        } finally {
            RuntimeEnvironment.setQualifiers(savedQualifiers)
            if (RuntimeEnvironment.getFontScale() != savedFontScale) RuntimeEnvironment.setFontScale(savedFontScale)
        }
    }
}
