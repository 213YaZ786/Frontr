package com.frontr.app.ui.component

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlin.math.PI
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
) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val colours = listOf(lerp(accent, surface, 0.5f), lerp(accent, surface, 0.3f), accent)

    val transition = rememberInfiniteTransition(label = "melting dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "gather"
    )
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "turn"
    )
    // Together halfway through the loop, apart at both ends.
    val pull = if (running) (1f - cos(2f * PI.toFloat() * phase)) / 2f else progress.coerceIn(0f, 1f)
    val angle = if (running) turn else 0f

    /** Where each dot is and how large, in half sizes from the centre. */
    fun dots(): List<Pair<Offset, Float>> = DOTS.map { (home, radius) ->
        val at = home * (1f - pull * GATHER)
        Offset(at.x * cos(angle) - at.y * sin(angle), at.x * sin(angle) + at.y * cos(angle)) to radius
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
private const val LOOP_MILLIS = 1600
private const val TURN_MILLIS = 4800
