package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.glass.LocalGlass
import com.frontr.app.ui.glass.LocalGlassBackdrop
import com.frontr.app.ui.glass.glassSource
import com.frontr.app.ui.glass.rememberGlassBackdrop
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
 * DRAFT for the user's yes (requests.md point 20), nothing of app/src/main changes: the pull to refresh back to the app's
 * own animation, no bubble, bigger. pull_original_draft_<state>: LoadingMark (the melting dots) at 72 dp alone, straight
 * in the layer (no disc, no glass, no shadow, no blur, no count, no "Updated"), sliding down from the top edge as
 * IndicatorBox does (translationY = distanceFraction * maxDistance - height, read in the layer), its alpha from the pull
 * (fraction * 2, clamped), held at 1 while refreshing. States: half (0.5), threshold (1.0), refreshing. The mark's
 * `progress` is the pull's fraction, as the kit's PullRevealBox gives it.
 * pull_original_draft_today_<state>: today's pull for comparison, the ring in the middle over the blurred screen as
 * PullDotsDraftScreenshotTest draws it (half and threshold with "Updated" under the dots, refreshing counted 3 of 7).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/pulltorefresh/PullToRefresh.kt#IndicatorBox
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/PullOriginalDraftScreenshotTest.kt#PullOriginalDraftScreenshotTest
 * not ported: IndicatorBox's drawWithContent clipRect, shape, containerColor, background and shadowElevation (the mark has
 * none); PullToRefreshBox's gesture (Modifier.pullToRefresh: the pull is fixed per picture); PullIndicator's haptic effect;
 * MTGA's posts (X posts: the feed is Home's own, HomeScreen with A2Capsule and the subreddit DraftPosts); MTGA's Today
 * (the kit's PullRevealBox now draws the new mark, so today's ring is PullDotsDraftScreenshotTest's FeatherRing); Feed and
 * its FeedBanner, MenuPill, PostCard (Home's HomeScreen takes their place)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PullOriginalDraftScreenshotTest : ScreenshotBase() {

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

    @Test
    fun half() = capture("pull_original_draft_half") { Draft(fraction = 0.5f, refreshing = false) }

    @Test
    fun threshold() = capture("pull_original_draft_threshold") { Draft(fraction = 1f, refreshing = false) }

    @Test
    fun refreshing() = capture("pull_original_draft_refreshing") { Draft(fraction = 1f, refreshing = true) }

    @Test
    fun todayHalf() = capture("pull_original_draft_today_half") { Today(fraction = 0.5f, refreshing = false) }

    @Test
    fun todayThreshold() = capture("pull_original_draft_today_threshold") { Today(fraction = 1f, refreshing = false) }

    @Test
    fun todayRefreshing() = capture("pull_original_draft_today_refreshing") { Today(fraction = 1f, refreshing = true) }

    // The threshold state at 200 % font and at a narrow width.
    @Test
    fun edgeThreshold() = captureEdge("pull_original_draft_threshold") { Draft(fraction = 1f, refreshing = false) }

    // Home's capsule and cards, as home_top_draft draws them.
    @Composable
    private fun Feed() {
        HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) }
    }

    // The proposal: the feed recorded as the glass backdrop, the mark alone over it.
    @Composable
    private fun Draft(fraction: Float, refreshing: Boolean) {
        val look = LocalGlass.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Box(Modifier.fillMaxSize()) {
                // The list stays in place, the dots fall over it.
                Box(Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)) { Feed() }
                IndicatorBox(
                    distanceFraction = { fraction },
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    LoadingMark(size = 72.dp, running = refreshing, progress = { fraction.coerceIn(0f, 1f) })
                }
            }
        }
    }

    // Today: the screen blurred and the ring at the centre, as PullDotsDraftScreenshotTest and its PullScreen draw it.
    @Composable
    private fun Today(fraction: Float, refreshing: Boolean) {
        if (!refreshing) {
            PullScreen(pull = fraction)
        } else {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().blurFeed(blurBackground = true)) { Feed() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FeatherRing(progress = 0.5f) {
                        Text("3 of 7", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        Text("subreddits", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// The distance at which the mark's foot lands: the banner's 64 dp, the mark's 72 dp and 4 dp of air, so at the threshold
// the mark sits just under the folder capsule, over the first post card.
private val PullMaxDistance = 140.dp

// IndicatorBox of PullToRefresh.kt without its shape, background and elevation: the box places itself with a layer,
// translationY = distanceFraction * maxDistance - height, read in the layer and not in composition. The alpha comes from
// the pull (fraction * 2, clamped) and is 1 while refreshing. The size is the mark's own 72 dp (SpinnerContainerSize is
// Material's). The kit's PullMarkBox is private, so the draft carries the same box.
@Composable
private fun IndicatorBox(
    distanceFraction: () -> Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    maxDistance: Dp = PullMaxDistance,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(
                        0,
                        0,
                        layerBlock = {
                            translationY = distanceFraction() * maxDistance.roundToPx() - size.height
                            alpha = if (isRefreshing) 1f else (distanceFraction() * 2f).coerceIn(0f, 1f)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}
