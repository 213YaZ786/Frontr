package com.frontr.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.welcome.StartCookie
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.component.SetupTileGrid
import com.frontr.app.ui.component.StartRevealOverlay
import com.frontr.app.ui.component.StartRevealState
import com.frontr.app.ui.component.startRevealHole
import com.frontr.app.ui.glass.LocalGlass
import com.frontr.app.ui.glass.glassGround
import com.frontr.app.ui.glass.rememberGlassLook
import com.frontr.app.ui.icon.FrontrIcons
import com.frontr.app.ui.theme.FrontrTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 24), nothing of app/src/main changes: after the guide's Start the app opens
 * from the Start button outward to the screen's edges, with SystemUI's sparkle ripple drawn by its real AGSL shader. The
 * shader and its animation are the kit's ui/component/StartReveal.kt as it is (StartRevealState, StartRevealOverlay,
 * startRevealHole); the draft only lays the guide over Home and starts the ripple. A filmstrip as
 * AnimationFilmstripDraftScreenshotTest makes them (the test clock stopped, advanced to each instant, each frame captured
 * with captureToImage, frames side by side with their time): 0, 150, 350, 600, 900, 1250, 1750 ms, phone size,
 * start_reveal_draft_light.png and start_reveal_draft_dark.png, and one full size frame at 600 ms,
 * start_reveal_draft_detail_light.png. The guide (Frontr's WelcomeContent layout, its StartCookie) lies over Home (the
 * home_top_draft screen, HomeScreen and A2Capsule); the hole cut in the guide is centred on the Start button and its
 * radius is the first ripple's radius, Home shows through it.
 *
 * The state is played by StartRevealState.startRipple on the test clock, as the app plays it after Start: the origin is the
 * Start button's centre, the waves are the kit's (the two more ripples 150 ms and 300 ms after the first are OUR choice,
 * SystemUI sets none).
 *
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/StartRevealDraftScreenshotTest.kt#StartRevealDraftScreenshotTest
 * not ported: Waves, MultiRippleView, MultiRippleController, RippleAnimationConfig, RippleAnimation, ShaderUtilLibrary,
 * SdfShaderLibrary, RippleShader with its FadeParams, SizeAtProgress and RippleSize, RIPPLE_DURATION, WAVE_DELAYS and
 * WAVE_TOTAL, Color0 (MTGA's test carries a copy of the SystemUI shader: Frontr's kit file StartReveal.kt has all of it,
 * and the draft's Reveal calls the kit's StartRevealState, StartRevealOverlay and startRevealHole, which draw the ripple
 * and cut the hole); the draft's own Animatable clock and its drawWithContent hole and drawBehind ripple (the kit's
 * overlay and hole do them); the sample posts and PostCard column of Feed (Frontr's HomeScreen draws Home's cards);
 * SetupTileGrid's explanation (a draft).
 * The kit exposes what the draft needs: StartRevealState's origin and currentPlayTime and its startRipple are public,
 * the shader pieces are private to the kit and never touched here.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class StartRevealDraftScreenshotTest : ScreenshotBase() {

    // PostCard reads the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private val times = listOf(0, 150, 350, 600, 900, 1250, 1750)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    private fun tile(id: String, name: String, icon: ImageVector, state: String, done: Boolean) =
        SetupTile(
            id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
            required = false, error = false, onClick = {}
        )

    // The four tiles of WelcomeScreen's rememberSetup on a first run.
    private val tiles = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Images and videos", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", done = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Off", done = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off", done = false)
    )

    // One test per mode, each a Roborazzi test as in https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/ManualTest.kt#captureRoboImage
    // (a second setContent in the same test kept the first row's state).
    @Test
    fun filmstripLight() = filmstrip(false)

    @Test
    fun filmstripDark() = filmstrip(true)

    private fun filmstrip(night: Boolean) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        val frames = row(night)
        sheet(night, frames)
        if (!night) {
            frames[times.indexOf(600)].captureRoboImage(
                "src/test/screenshots/start_reveal_draft_detail_light.png",
                roborazziOptions = options
            )
        }
    }

    // One row: the guide over Home, the reveal started ([go] true), a frame at each instant on the stopped clock.
    private fun row(night: Boolean): List<Bitmap> {
        val go = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                FrontrTheme(darkTheme = night, dynamicColor = false) {
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        Box(
                            Modifier
                                .testTag("stage")
                                .fillMaxSize()
                                .clipToBounds()
                                .glassGround(look, MaterialTheme.colorScheme.background)
                        ) { Reveal(go.value) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { go.value = true }
        val frames = mutableListOf<Bitmap>()
        var elapsed = 0
        times.forEach { t ->
            compose.mainClock.advanceTimeBy((t - elapsed).toLong())
            elapsed = t
            frames += compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }
        compose.mainClock.autoAdvance = true
        return frames
    }

    // The frames joined, the ms under each.
    private fun sheet(night: Boolean, frames: List<Bitmap>) {
        val scale = 0.6f
        val w = (frames[0].width * scale).toInt()
        val h = (frames[0].height * scale).toInt()
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap((w + gap) * times.size + gap, h + ms + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(); textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        frames.forEachIndexed { i, frame ->
            val left = gap + i * (w + gap)
            canvas.drawBitmap(frame, null, AndroidRect(left, gap, left + w, gap + h), smooth)
            canvas.drawText("${times[i]} ms", left + 4f, gap + h + 28f, text)
        }
        sheet.captureRoboImage(
            "src/test/screenshots/start_reveal_draft_${if (night) "dark" else "light"}.png",
            roborazziOptions = options
        )
    }

    // Home under, the guide over it with the ripple's hole, the ripple over both.
    @Composable
    private fun Reveal(go: Boolean) {
        val reveal = remember { StartRevealState() }
        var centre by remember { mutableStateOf(Offset.Unspecified) }
        // What the app does at Start's tap: the origin is the button's centre, then the ripple plays on the clock.
        LaunchedEffect(go) {
            if (go) {
                reveal.origin = centre
                reveal.startRipple {}
            }
        }
        Box(Modifier.fillMaxSize()) {
            Feed()
            Box(Modifier.fillMaxSize().startRevealHole(reveal)) {
                Guide(onStartCentre = { centre = it })
            }
            StartRevealOverlay(reveal)
        }
    }

    // Home's screen, as home_top_draft draws it: the capsule and the cards.
    @Composable
    private fun Feed() {
        HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) }
    }

    // WelcomeContent's layout (the app's guide), on the ground, reporting the Start button's centre.
    @Composable
    private fun Guide(onStartCentre: (Offset) -> Unit) {
        val look = LocalGlass.current
        val ground = MaterialTheme.colorScheme.background
        BoxWithConstraints(Modifier.fillMaxSize().glassGround(look, ground)) {
            val screen = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                        .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
                ) {
                    LoadingMark(size = 160.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Reddit reader",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    SetupTileGrid(tiles) {}
                    Spacer(Modifier.height(40.dp))
                    StartCookie(
                        description = "Start",
                        onClick = {},
                        modifier = Modifier.onGloballyPositioned { onStartCentre(it.boundsInRoot().center) }
                    )
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}
