package com.frontr.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.glass.LocalGlass
import com.frontr.app.ui.glass.glassGround
import com.frontr.app.ui.glass.rememberGlassLook
import com.frontr.app.ui.theme.FrontrTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 13, motion), nothing of app/src/main changes: the footer of Home's list
 * changing kind (TimelineFooter: the loading mark while older posts load, the "Load older posts" button, the end
 * line), shown as a filmstrip, the motion in a still: a frame at 0, 50, 100, 150 and 250 ms with its time under it.
 * Per change, a row "today" (TimelineFooter's plain `when`: the new kind replaces the old one on the next frame and
 * the footer's height jumps) and a row "proposed": AnimatedContent with a fade through (the old kind out in 90 ms,
 * the new one in during 210 ms after 90 ms, Material's fade through) and `animateContentSize()` as the only animator
 * of the height, `SizeTransform { snap() }` so AnimatedContent does not animate the size a second time. No Crossfade:
 * it measures the box at the larger of the two contents, then drops to the smaller, a jump. The thin line under the
 * footer in every frame is what comes after it in the list: it must glide, not jump.
 * Frames are taken with the test clock stopped (mainClock.autoAdvance = false), advanced to each instant, captured with
 * captureToImage, as the compose testing guide does for animations
 * https://developer.android.com/develop/ui/compose/testing/time-manipulation
 * and joined in one bitmap saved with Roborazzi's Bitmap capture, as MTGA's filmstrip test does.
 * Ports (names kept):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineFooter
 * (the footer's three kinds: the mark at 28 dp, the BoldButton, the end line; Box with 24 dp around, centred)
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/AnimationFilmstripDraftScreenshotTest.kt#follow
 * (the filmstrip harness: row, sheet, film; AnimatedContent with SizeTransform on a state)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
 * not ported: TimelineFooter's pagingFailed column ("Couldn't load older posts", Try again: a fourth kind, the same
 * mechanism), MTGA's other cases (like, hold menu, new dot, zoom, video bubble: not Frontr's piece), its tablet and
 * its today/proposed pair for night only (here day and night, a phone).
 * Not testable on the JVM: the LoadingMark clock change (one Animatable, drawn in the draw phase), as LinkedOut says:
 * the test rule cancels infinite animations, so the mark is a still frame here; what the filmstrip shows is the change
 * of kind and of height, not the mark's own turning.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AnimationFilmstripDraftScreenshotTest : ScreenshotBase() {

    private val times = listOf(0, 50, 100, 150, 250)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    private var stages = 0

    private enum class Kind { Loading, More, End }

    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/AnimationFilmstripDraftScreenshotTest.kt#follow
    // One row: the footer composed on a fixed stage, the change triggered ([go] true), a frame at each instant.
    private fun row(night: Boolean, width: Int, height: Int, content: @Composable (go: Boolean) -> Unit): List<Bitmap> {
        val go = mutableStateOf(false)
        val picture = stages++
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                FrontrTheme(darkTheme = night, dynamicColor = false) {
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        key(picture) {
                            Box(
                                Modifier
                                    .testTag("stage")
                                    .size(width.dp, height.dp)
                                    .clipToBounds()
                                    .glassGround(look, MaterialTheme.colorScheme.background)
                            ) { content(go.value) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // The first composition and its enter animations settle on the stopped clock before the change.
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

    // The rows joined, two lines of label per row, the ms under each frame.
    private fun sheet(name: String, night: Boolean, rows: List<Triple<String, String, List<Bitmap>>>) {
        val scale = 0.6f
        val w = (rows[0].third[0].width * scale).toInt()
        val h = (rows[0].third[0].height * scale).toInt()
        val labelW = 300
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap(labelW + (w + gap) * times.size + gap, (h + ms) * rows.size + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val ink = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        rows.forEachIndexed { r, (change, label, frames) ->
            val top = r * (h + ms) + gap
            canvas.drawText(change, 10f, top + h / 2f - 8f, text)
            canvas.drawText(label, 10f, top + h / 2f + 28f, text)
            frames.forEachIndexed { i, frame ->
                val left = labelW + gap + i * (w + gap)
                canvas.drawBitmap(frame, null, AndroidRect(left, top, left + w, top + h), smooth)
                canvas.drawText("${times[i]} ms", left + 4f, top + h + 28f, text)
            }
        }
        sheet.captureRoboImage("src/test/screenshots/${name}_phone_${if (night) "dark" else "light"}.png", roborazziOptions = options)
    }

    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineFooter
    // The footer's three kinds, as TimelineFooter draws them (the failed kind not ported).
    @Composable
    private fun FooterContent(kind: Kind) {
        when (kind) {
            Kind.Loading -> LoadingMark(size = 28.dp)
            Kind.More -> BoldButton(onClick = {}) { Text("Load older posts") }
            Kind.End -> Text(
                "No older posts available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // The footer in its box, then the line that follows it in the list. A thin border shows the box's own height.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
    @Composable
    private fun Footer(from: Kind, to: Kind, go: Boolean, proposed: Boolean) {
        val kind = if (go) to else from
        Column(Modifier.fillMaxSize()) {
            if (!proposed) {
                // Today: TimelineFooter's `when`, the new kind at once.
                Box(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, ShapeDefaults.Small).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { FooterContent(kind) }
            } else {
                // The proposal: animateContentSize is the only height animator, the content fades through.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ShapeDefaults.Small)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = kind,
                        transitionSpec = {
                            (fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform { _, _ -> snap() })
                        },
                        label = "footerContent"
                    ) { shown -> FooterContent(shown) }
                }
            }
            // What follows the footer in the list: its top edge shows any jump of the height above.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp).background(MaterialTheme.colorScheme.primary))
        }
    }

    @Test
    fun footer() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(false, true).forEach { night ->
            val rows = listOf(
                Triple("loading to Load more", "today", row(night, 240, 150) { Footer(Kind.Loading, Kind.More, it, proposed = false) }),
                Triple("loading to Load more", "proposed", row(night, 240, 150) { Footer(Kind.Loading, Kind.More, it, proposed = true) }),
                Triple("Load more to the end", "today", row(night, 240, 150) { Footer(Kind.More, Kind.End, it, proposed = false) }),
                Triple("Load more to the end", "proposed", row(night, 240, 150) { Footer(Kind.More, Kind.End, it, proposed = true) })
            )
            sheet("footer_filmstrip_draft", night, rows)
        }
    }
}
