package com.frontr.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pull's diameter, as in MTGA. */
private val CircularIndicatorDiameter = 168.dp

/**
 * CircularProgressIndicator's determinate frame: [size] wide (168 dp in the pull), [progress] coerced to 0..1 and given
 * to TalkBack as a ProgressBarRangeInfo, the ring's drawing being Frontr's LoadingMark (not the feathers of MTGA's):
 * its dots run no loop here (running = false) and sit where [progress] puts them, from their places in the icon (0) to
 * the centre (1). The count, or the last update, is [centre], under them.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/FeatherRing.kt#FeatherRing
 * not ported: CircularProgressIndicator's stroke, track and colours (the dots are LoadingMark's), color and trackColor,
 * FeatherSlots, startAngle, sweep, drawCircularIndicator, drawCircularIndicatorTrack,
 * drawDeterminateCircularIndicator, rememberRingInk (the feathers' drawing, here the dots').
 */
@Composable
fun FeatherRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = CircularIndicatorDiameter,
    centre: @Composable ColumnScope.() -> Unit = {}
) {
    val coercedProgress = { progress().coerceIn(0f, 1f) }
    Column(
        modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(coercedProgress().takeUnless { it.isNaN() } ?: 0f, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingMark(size = size, running = false, progress = coercedProgress)
        centre()
    }
}

/**
 * CircularProgressIndicator's indeterminate form: the dots run their own loop (gather, melt, part, turning slowly),
 * continuous, no restart jump. [intro] is how far the mark has come in, 0 to 1, read while drawing: the dots rise from
 * nothing when the ring starts to run.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/FeatherRing.kt#FeatherRing
 * not ported: CircularProgressIndicator's infinite transitions of the global rotation, the additional rotation and the
 * growing arc (CircularGlobalRotationDegreesTarget, CircularAnimationAdditionalRotation*, CircularIndeterminate*Progress,
 * the easings: LoadingMark's own loop and turn do it), color and trackColor.
 */
@Composable
fun FeatherRing(
    modifier: Modifier = Modifier,
    size: Dp = CircularIndicatorDiameter,
    intro: () -> Float = { 1f },
    centre: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingMark(
            modifier = Modifier.graphicsLayer { alpha = intro().coerceIn(0f, 1f) },
            size = size,
            running = true,
            progress = { 0f }
        )
        centre()
    }
}
