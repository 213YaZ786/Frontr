package com.frontr.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.present
import com.frontr.app.di.appModule
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.ui.component.ZoneAlertDialog
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.ui.component.failureMessage
import com.frontr.app.ui.glass.LocalGlass
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
 * DRAFT for the user's yes (requests.md point 4), nothing of app/src/main changes: Home after a tap on the red triangle.
 * alert_now is the dialog as Frontr has it (TimelineScreen.kt's FailureDialog, private there, so a faithful copy of it:
 * the failed subreddits listed with their explanation), the two others are the planned one, as MTGA's validated draft:
 * ONE pane of the post card's glass centred both ways over Home blurred behind it, one message naming where the trouble
 * comes from, no subreddit listed. In the app it is the reader kit's FailureDialog (ZoneAlertDialog: BasicAlertDialog,
 * BlurBehind and rememberCrossWindowBlur, AOSP's window blur) with its pane drawn like the post card. The dialog's width is
 * Material's (280 to 560 dp), the padding 24 dp, the content centred:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#BasicAlertDialog
 *
 * Robolectric draws no window blur, so the draft does what the window does: Home blurred (Modifier.blur, the kit's
 * BlurBehind radius, 16 dp by day and 8 by night), dimmed by BlurBehind's dimAmount (0.23 by day, 0.56 by night), and the
 * pane drawn over it. alert_now needs none of that: its dialog is a real window, taken as Roborazzi's WindowCaptureTest does
 * (ScreenshotBase's window).
 * https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/WindowCaptureTest.kt#WindowCaptureTest
 *
 * The message is the kit's failureMessage (ui/component/FailureDialog.kt), chosen by the blame shared by most failed
 * subreddits (core/common/AppError.kt, Blame), site = "Reddit". Pictures: alert_draft_host (Blame.SERVICE, "Reddit is holding
 * back guests") and alert_draft_offline (Blame.DEVICE, "You are offline"). The capsule behind it, with the alert as a
 * BannerAction tinted colorScheme.error and Material Symbols Rounded's warning, fill 1, is the one of HomeTopDraftScreenshotTest.
 * not ported: MTGA's own blur routine (Modifier.blur does it), its AlertAction copy (HomeTopDraftScreenshotTest's), the other
 * blames' pictures (NETWORK, APP: the same pane with another text), the silent downloads text
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AlertDraftScreenshotTest : ScreenshotBase() {

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

    // Three of the twelve followed subreddits failed. Blame.SERVICE: Reddit refused or asked to slow down.
    private val hostErrors = listOf(
        AppError.RateLimited(host = "www.reddit.com", retryAfterSeconds = 60),
        AppError.ClientRefused(host = "www.reddit.com", status = 403),
        AppError.ServerError(host = "www.reddit.com", status = 503)
    )

    // Blame.DEVICE: no network at all, no subreddit is to blame.
    private val offlineErrors = listOf(AppError.Offline, AppError.Offline, AppError.Offline)

    // The subreddits the failures belong to, for alert_now.
    private val failedHandles = listOf("android", "grapheneos", "linux")

    // The dialog as it is today, in its own window.
    @Test
    fun now() = capture("alert_now", window = true) {
        HomeBehind()
        NowDialog(failed = failedHandles.zip(hostErrors), onRetry = {}, onDismiss = {})
    }

    @Test
    fun host() = capture("alert_draft_host") {
        val (title, text) = failureMessage(hostErrors, 12, "Reddit")!!
        AlertOverScreen(title, text)
    }

    @Test
    fun offline() = capture("alert_draft_offline") {
        val (title, text) = failureMessage(offlineErrors, 12, "Reddit")!!
        AlertOverScreen(title, text)
    }

    @Test
    fun edgeHost() = captureEdge("alert_draft_host") {
        val (title, text) = failureMessage(hostErrors, 12, "Reddit")!!
        AlertOverScreen(title, text)
    }

    @Test
    fun edgeOffline() = captureEdge("alert_draft_offline") {
        val (title, text) = failureMessage(offlineErrors, 12, "Reddit")!!
        AlertOverScreen(title, text)
    }

    // The screen blurred as the window blurs it, its dim, and the pane over it.
    @Composable
    private fun AlertOverScreen(title: String, text: String) {
        val dark = LocalGlass.current?.dark ?: false
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().blur(if (dark) 8.dp else 16.dp)) { Screen() }
            // BlurBehind's dimAmount with the blur on.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.56f else 0.23f)))
            Box(Modifier.fillMaxSize().padding(horizontal = DialogMargin), contentAlignment = Alignment.Center) {
                AlertPane(title, text)
            }
        }
    }

    // The post card's glass (ZoneSurface, its shape and fill), Material's dialog width and 24 dp padding, centred
    // content: the icon, the title (headlineSmall as ZoneAlertContent), the text (bodyMedium), the two buttons.
    @Composable
    private fun AlertPane(title: String, text: String) {
        ZoneSurface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.widthIn(min = DialogMinWidth, max = DialogMaxWidth)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    WarningRounded,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    QuietButton(onClick = {}) { Text("Close") }
                    BoldButton(onClick = {}, filled = true) { Text("Try again") }
                }
            }
        }
    }

    // The capsule with the alert, then the posts: HomeTopDraftScreenshotTest's.
    @Composable
    private fun Screen() {
        HomeScreen { A2Capsule("All", HomeHints, alert = true, onSwitch = {}) }
    }

    // What lies behind alert_now: Home as it is today, its title and two cards.
    @Composable
    private fun HomeBehind() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Home", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PostCard(post = DraftPosts[0], onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
                PostCard(post = DraftPosts[1], onClick = {}, onOpenLink = {}, onDownload = {})
            }
        }
    }

    // A faithful copy of FailureDialog (TimelineScreen.kt, private): the subreddits that failed, each with its explanation.
    @Composable
    private fun NowDialog(
        failed: List<Pair<String, AppError>>,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        ZoneAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (failed.size == 1) "1 subreddit could not be updated" else "${failed.size} subreddits could not be updated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    failed.take(FAILURES_SHOWN).forEach { (handle, error) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("r/$handle", style = MaterialTheme.typography.titleSmall)
                            Text(
                                error.present().explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (failed.size > FAILURES_SHOWN) {
                        Text(
                            "and ${failed.size - FAILURES_SHOWN} more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { QuietButton(onClick = onRetry) { Text("Try again") } },
            dismissButton = { QuietButton(onClick = onDismiss) { Text("Close") } }
        )
    }

    // TimelineScreen's FAILURES_SHOWN (private there).
    private val FAILURES_SHOWN = 5

    // Material's dialog: 280 to 560 dp wide (BasicAlertDialog's DialogMinWidth and DialogMaxWidth), 24 dp from the sides.
    private val DialogMargin = 24.dp
    private val DialogMinWidth = 280.dp
    private val DialogMaxWidth = 560.dp
}
