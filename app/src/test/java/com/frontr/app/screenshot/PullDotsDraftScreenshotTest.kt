package com.frontr.app.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.ui.component.LoadingMark
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
 * DRAFT for the user's yes (requests.md point 5), nothing of app/src/main changes: the pull to refresh as MTGA's validated
 * ring (the count in the middle, Home blurred behind), but with Frontr's own melting dots in place of the feathers
 * (the user, 2026-11-23). The ring is Material's determinate indicator's frame (168 dp, coercedProgress,
 * ProgressBarRangeInfo) with LoadingMark drawn in it:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 * Neko's PullRefresh blurs the content itself while it refreshes (16 dp):
 * https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
 * Pictures: pull_dots_draft (loading, counted, 3 of 7), pull_dots_early_draft (1 of 7), pull_dots_pulling_draft (the pull
 * before release: the dots smaller and fainter, "Updated" over "3 min ago" under them), pull_dots_gathered_draft (the
 * dots at the middle of their loop, joined) and pull_dots_apart_draft (at the loop's ends, apart): LoadingMark's
 * `progress` draws the dots from their places toward the centre, so both states of their linking are seen; the
 * dots at rest touch through the blur by design (LoadingMark.kt), and the melting itself is the app's.
 * not ported: CircularProgressIndicator's indeterminate overload, drawCircularIndicator and drawDeterminateCircularIndicator
 * (arc strokes: the dots take their place), strokeCap and gapSize (no stroke); Neko's PullRefresh PullToRefreshBox, its
 * state, WavyLinearIndicator (the user refused a bar), the black dim under Android 12 (this app starts at Android 12 and
 * draws no dim without blur); the old feathers (FeatherRing is the test's own, drawing the dots)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullDotsDraftScreenshotTest : ScreenshotBase() {

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

    // Loading, 3 of the 7 subreddits read, the dots halfway through their loop.
    @Test
    fun late() = capture("pull_dots_draft") { Screen(progress = 0.5f, count = 3, total = 7) }

    // The first one read.
    @Test
    fun early() = capture("pull_dots_early_draft") { Screen(progress = 0.5f, count = 1, total = 7) }

    // The pull before release: no count yet, the last update under the dots.
    @Test
    fun pulling() = capture("pull_dots_pulling_draft") { PullScreen(pull = 0.6f) }

    // The dots joined, at the middle of their loop (progress 1).
    @Test
    fun gathered() = capture("pull_dots_gathered_draft") { Screen(progress = 1f, count = 3, total = 7) }

    // The dots apart, at the ends of their loop (progress 0).
    @Test
    fun apart() = capture("pull_dots_apart_draft") { Screen(progress = 0f, count = 3, total = 7) }

    @Test
    fun edge() = captureEdge("pull_dots_draft") { Screen(progress = 0.5f, count = 3, total = 7) }

    @Test
    fun edgePulling() = captureEdge("pull_dots_pulling_draft") { PullScreen(pull = 0.6f) }

    /**
     * The screen of PullRefresh while refreshing: the content blurred, and over it the ring at the centre.
     * https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
     */
    @Composable
    private fun Screen(progress: Float, count: Int, total: Int) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().blurFeed(blurBackground = true)) { Feed() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeatherRing(progress = progress) {
                    Text("$count of $total", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text("subreddits", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }

    // The feed of home_top_draft, not moved: the capsule and the cards.
    @Composable
    private fun Feed() {
        HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) }
    }
}

// Neko's `blurBackground`: the content blurred and made untouchable while it refreshes, 16 dp (Size.medium).
// https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/components/PullRefresh.kt#PullRefresh
internal fun Modifier.blurFeed(blurBackground: Boolean): Modifier =
    if (blurBackground) this.blur(Size.medium).clickable(enabled = false) {} else this

/** Neko's Size.medium. https://github.com/nekomangaorg/Neko/blob/main/app/src/main/java/org/nekomanga/presentation/theme/Constants.kt#Size */
private object Size {
    val medium = 16.dp
}

/**
 * CircularProgressIndicator's determinate frame: 168 dp, [progress] coerced to 0..1 and given to TalkBack as a
 * ProgressBarRangeInfo, the ring's drawing being Frontr's LoadingMark (not the feathers of MTGA's): its dots run no loop
 * here (running = false) and sit where [progress] puts them, from their places in the icon (0) to the centre (1);
 * [scale] and [alpha] make them smaller and fainter for a pull that has not gone far. The count, or the last update,
 * is [centre], under them.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ProgressIndicator.kt#CircularProgressIndicator
 */
@Composable
internal fun FeatherRing(
    progress: Float,
    scale: Float = 1f,
    alpha: Float = 1f,
    centre: @Composable ColumnScope.() -> Unit
) {
    val coercedProgress = progress.coerceIn(0f, 1f)
    Column(
        Modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress.takeUnless { it.isNaN() } ?: 0f, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingMark(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
            size = 168.dp,
            running = false,
            progress = coercedProgress
        )
        centre()
    }
}
