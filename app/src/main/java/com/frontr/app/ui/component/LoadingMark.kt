package com.frontr.app.ui.component

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Frontr's loading mark, the launcher icon in motion: its three dots, far,
 * mid and near, drift together until they melt into one, then part again.
 *
 * [progress] from 0 to 1 draws the dots from their places in the icon towards
 * the centre, for a gesture that is still being made. While [running] they
 * loop on their own and turn slowly around the centre.
 *
 * The melting is a blur followed by a sharp cut on opacity, applied to the
 * layer the flat dots are drawn in: where two blurred dots overlap their
 * opacity adds up past the cut and they join, exactly like the drops in the
 * icon. Then each dot is drawn again over the melted shape, and only inside
 * it, as a soft sphere lit from the upper left: the dots get the icon's
 * relief, the bridges between them keep their flat colour. The dots turn,
 * the light does not. Depth is tint, as in the icon: the near dot is the
 * accent, the others sink towards the surface.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) = LoadingMark(modifier = modifier, size = size, running = running, progress = { progress })

/**
 * The same mark with [progress] read in the draw phase (a pull's distanceFraction, the ring's fill): a change redraws
 * the dots without recomposing.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: () -> Float
) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val colours = listOf(lerp(accent, surface, 0.5f), lerp(accent, surface, 0.3f), accent)

    // One time source for both ways, as MTGA's mark: the clock continues from where it stands, and a gesture's progress
    // glides from there, so the dots never jump when `running` changes. Nothing runs at rest. One turn of the clock is
    // TURN_MILLIS: the dots gather three times in it (phase) and turn once (angle).
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
    // (not ported: the feathers, FEATHERS, FALL_MILLIS, drawFeather, FeatherInk, FeatherArt: Frontr draws its three dots)
    val currentProgress by rememberUpdatedState(progress)
    val clock = remember { Animatable(if (running) 0f else pullClock(currentProgress())) }
    LaunchedEffect(running) {
        if (running) {
            // The infinite frame clock, as InfiniteTransition runs on: a test waiting for idle does not wait for it.
            var last = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val now = withInfiniteAnimationFrameMillis { it }
                val next = clock.value + (now - last).toFloat() / TURN_MILLIS
                clock.snapTo(if (next >= 1f) next - 1f else next)
                last = now
            }
        } else {
            // A gesture's progress is the clock's phase that gathers the dots as far.
            snapshotFlow { pullClock(currentProgress()) }.collectLatest { clock.animateTo(it, tween(90, easing = LinearEasing)) }
        }
    }

    /** Where each dot is and how large, in half sizes from the centre; read only while drawing. */
    fun dots(): List<Pair<Offset, Float>> {
        val time = clock.value
        // Together halfway through each gathering, apart at both ends.
        val pull = (1f - cos(2f * PI.toFloat() * ((time * GATHERINGS) % 1f))) / 2f
        val angle = 2f * PI.toFloat() * time
        return DOTS.map { (home, radius) ->
            val at = home * (1f - pull * GATHER)
            Offset(at.x * cos(angle) - at.y * sin(angle), at.x * sin(angle) + at.y * cos(angle)) to radius
        }
    }

    Box(
        modifier
            .size(size)
            // Offscreen, so the spheres drawn after can keep to the melted shape.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val half = this.size.minDimension / 2f
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                dots().forEachIndexed { i, (at, radius) ->
                    val c = centre + at * half
                    val r = radius * half
                    drawCircle(
                        Brush.radialGradient(
                            0f to lerp(colours[i], Color.White, 0.26f),
                            0.6f to colours[i],
                            1f to lerp(colours[i], Color.Black, 0.16f),
                            center = c + Offset(-0.35f * r, -0.4f * r),
                            radius = 1.25f * r
                        ),
                        radius = r,
                        center = c,
                        blendMode = BlendMode.SrcAtop
                    )
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = melt(blurPx = this.size.minDimension * BLUR) }) {
            val half = this.size.minDimension / 2f
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            dots().forEachIndexed { i, (at, radius) ->
                drawCircle(colours[i], radius = radius * half, center = centre + at * half)
            }
        }
    }
}

private fun melt(blurPx: Float) = RenderEffect.createColorFilterEffect(
    // Opacity times 20, minus 9 steps of 255: a blurred edge above roughly
    // 45 % becomes solid, below it vanishes, so the dots keep crisp edges.
    ColorMatrixColorFilter(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 20f, -9f * 255f
        )
    ),
    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
).asComposeRenderEffect()

// Where each dot sits in the icon, in half sizes from the centre, and its
// radius, far to near. At rest the mid and near dots already touch through
// the blur, as in the icon.
private val DOTS = listOf(
    Offset(0.5f, -0.45f) to 0.26f,
    Offset(-0.45f, -0.25f) to 0.32f,
    Offset(0.05f, 0.38f) to 0.4f
)

/** How far towards the centre the dots travel when fully gathered. */
private const val GATHER = 0.8f
private const val BLUR = 0.08f
private const val TURN_MILLIS = 4800
private const val GATHERINGS = 3f

/**
 * The clock's value whose phase has the dots gathered by [progress] (0 apart, 1 together): acos(1 - 2p) / 2pi, over the
 * gatherings. MTGA's `currentProgress() / 3f` for its feathers, here the inverse of the dots' own gathering curve.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
 */
private fun pullClock(progress: Float): Float =
    acos(1f - 2f * progress.coerceIn(0f, 1f)) / (2f * PI.toFloat()) / GATHERINGS
