package com.frontr.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.welcome.WelcomeContent
import com.frontr.app.ui.component.DropdownMenuGroup
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.SelectableDropdownMenuItem
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.component.SetupTileGrid
import com.frontr.app.ui.icon.FrontrIcons
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 9), nothing of app/src/main changes: Frontr's guide as MTGA's validated
 * layout: the original LoadingMark (160 dp, Frontr's dots) on top, "Reddit reader" in headlineMedium under it (no app
 * name, no Welcome, no "N left"), the tiles of Frontr's real choices (Save media, a tile that opens a GlassMenu of two
 * items, "Images and videos" and "Posts only"; Updates; New posts; Reddit links), the block centred in the height, Start
 * as an 80 dp cookie under the tiles, 40 dp below them (arrow_forward in onPrimary on the primary).
 * Pictures: welcome_guide_draft, welcome_guide_media_menu_draft (the menu open under the Save media tile),
 * welcome_guide_refused_draft (Updates "Installs not allowed", New posts "Notifications blocked", in the error colour),
 * welcome_guide_pressed_draft (the cookie melting into a circle, sunk to 0.86, the arrow upright), welcome_guide_now
 * (today's WelcomeContent, WelcomeScreen.kt: "Frontr" in displaySmall, "N left", a small Start); the drafts with their
 * 200 % text and 360 dp edges. The GlassMenu is a Popup that Robolectric does not draw: MenuOverScreen stands for it
 * (HomeTopDraftScreenshotTest).
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeGuideDraftScreenshotTest.kt#WelcomeGuideDraft
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#CookieStartDraft
 * which take the layout from WhatsNewKit
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * not ported: WelcomeGuideDraft's pinned footer (the cookie is part of the centred block, as the cookie variant of MTGA
 * keeps it), the plain Start button; WhatsNewView's footer band, secondary action sheet, version store, title and
 * feature helper views (the app's title and SetupTileGrid take their place)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeGuideDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, menu: (@Composable () -> Unit)? = null
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = {}, menu = menu
    )

    private val mediaSummary = "Posts only keeps the text and the links of what you read on the phone. Images and videos " +
        "also saves the pictures and the videos, which spends your data. Tap to choose."
    private val updatesSummary = "When a new version is out, Frontr can install it when you leave the app. " +
        "Android's permission to install is needed once."
    private val notifySummary = "A notification when the subreddits you follow get new posts. Tap to turn on."
    private val linksSummary = "reddit.com links tapped anywhere open in Frontr once Android's \"Open supported links\" " +
        "is on for it. Tap to open that page."

    // Nothing refused: the best presets already set.
    private fun tilesDraft(media: (@Composable () -> Unit)? = null) = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Posts only", mediaSummary, done = true, menu = media),
        tile("updates", "Updates", SettingsIcons.Update, "Install", updatesSummary, done = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Off", notifySummary, done = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off", linksSummary, done = false)
    )

    // Android refused two things: each on its own tile, in the error colour, no message line.
    private val tilesRefused = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Posts only", mediaSummary, done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Installs not allowed",
            "Android does not let Frontr install updates. Tap to open its install page.", done = false, error = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Notifications blocked",
            "Android blocks this app's notifications. Tap to open its notification page.", done = false, error = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off", linksSummary, done = false)
    )

    // Today's guide, the tiles WelcomeScreen builds on a first run.
    private val tilesNow = listOf(
        tile("media", "Save media", FrontrIcons.Download, "Never", "", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", "", done = true),
        tile("notify", "New posts", FrontrIcons.Bell, "Off", "", done = true),
        tile("links", "Reddit links", SettingsIcons.Link, "Off", "", done = false)
    )

    @Test
    fun draft() = capture("welcome_guide_draft") { WelcomeGuideDraft(tilesDraft(), onStart = {}) }

    @Test
    fun mediaMenu() = capture("welcome_guide_media_menu_draft") { MediaMenu() }

    @Test
    fun refused() = capture("welcome_guide_refused_draft") { WelcomeGuideDraft(tilesRefused, onStart = {}) }

    @Test
    fun pressed() = capture("welcome_guide_pressed_draft") {
        WelcomeGuideDraft(tilesDraft(), onStart = {}, morphProgress = 1f, scale = 0.86f, rotation = 90f)
    }

    @Test
    fun now() = capture("welcome_guide_now") { WelcomeContent(tilesNow, reopened = false, onStart = {}) }

    @Test
    fun edge() = captureEdge("welcome_guide_draft") { WelcomeGuideDraft(tilesDraft(), onStart = {}) }

    @Test
    fun edgeRefused() = captureEdge("welcome_guide_refused_draft") { WelcomeGuideDraft(tilesRefused, onStart = {}) }

    @Test
    fun edgeMediaMenu() = captureEdge("welcome_guide_media_menu_draft") { MediaMenu() }

    // The menu of the Save media tile, open under it: the tile's own menu slot gives the point under its middle.
    @Composable
    private fun MediaMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        val media: @Composable () -> Unit = {
            Box(
                Modifier.fillMaxSize().onGloballyPositioned {
                    val at = it.positionInRoot()
                    anchor = IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt())
                }
            )
        }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            // GlassMenu.kt's MenuContentOffset (private): the kit's default, this menu is anchored on a tile
            contentOffset = DpOffset(0.dp, 8.dp),
            screen = { WelcomeGuideDraft(tilesDraft(media), onStart = {}) }
        ) {
            DropdownMenuGroup(index = 0, count = 1) {
                SelectableDropdownMenuItem(selected = false, onClick = {}, text = "Images and videos")
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "Posts only")
            }
        }
    }
}

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#FabMediumContainerSize
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (the cookie here), ContainerWidth, container colours, focus, hover, pressed and icon
 * colours, elevations (a draft).
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * arrow_forward, Material Symbols Rounded 24px: viewport 960, mirrored in right-to-left (autoMirror).
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#ArrowForward
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml#arrow_forward
 * not ported: the tint attribute (the icon takes the colour of its use).
 */
private val ArrowForward: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M647,520L200,520Q183,520 171.5,508.5Q160,497 160,480Q160,463 171.5,451.5Q183,440 200,440L647,440" +
                    "L451,244Q439,232 439.5,216Q440,200 452,188Q464,177 480,176.5Q496,176 508,188L772,452" +
                    "Q778,458 780.5,465Q783,472 783,480Q783,488 780.5,495Q778,502 772,508L508,772" +
                    "Q497,783 480.5,783Q464,783 452,772Q440,760 440,743.5Q440,727 452,715L647,520Z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

/**
 * Material 3 Expressive's Cookie9Sided: a nine pointed star, inner radius .8, rounding .5, turned -90 degrees, then
 * normalized as MaterialShapes' getter does. Built from graphics-shapes (MaterialShapes is experimental in material3).
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#Cookie9Sided
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#cookie9
 * not ported: the other shapes of MaterialShapes and customPolygon (only the cookie and the circle are drawn).
 */
private val Cookie9Sided: RoundedPolygon by lazy {
    RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .8f, rounding = CornerRounding(radius = .5f))
        .transformed { x, y -> TransformResult(y, -x) }
        .normalized()
}

/**
 * MaterialShapes' circle, ten vertices as its default.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#CircleShape
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#circle
 */
private val CircleShape: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 10).normalized() }

/**
 * A morph between two polygons as a clip shape at a progress, scaled to the size it is given.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#MorphShape
 * https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/MorphShape.kt#MorphShape
 */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = Matrix()
    private val bounds = morph.calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress).asComposePath()
        matrix.reset()
        val maxDimension = max(bounds.width, bounds.height)
        matrix.scale(size.width / maxDimension, size.height / maxDimension)
        matrix.translate(-bounds.left, -bounds.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Voice's PlayButton: an 80 dp cookie that morphs to the circle at `morphProgress`, shrinks to `scale` and turns by
 * `rotation`, the arrow turning back against it. Fixed values here (the picture is still). A clickable Box with a
 * ripple, not a Material control; the haptic is Voice's ToggleOn.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#CookieStartDraft
 * https://github.com/PaulWoitaschek/Voice/blob/main/core/ui/src/main/kotlin/voice/core/ui/PlayButton.kt#PlayButton
 * not ported: the animateFloatAsState springs (morph .6 / MediumLow, scale .4 / Medium, turn .55 / Low),
 * collectIsPressedAsState, the turns counter, animationsEnabled, PlayPauseIcon and rememberPlayPausePainter (Start has
 * one icon, ArrowForward), the size parameter (FabMediumContainerSize).
 */
@Composable
private fun CookieStartDraft(
    onClick: () -> Unit,
    description: String,
    morphProgress: Float,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val morph = remember { Morph(Cookie9Sided, CircleShape) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(FabMediumContainerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .clip(MorphShape(morph, morphProgress.coerceIn(0f, 1f)))
            .background(colors.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClickLabel = description
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier.size(FabMediumIconSize).graphicsLayer { rotationZ = -rotation }
        )
    }
}

/**
 * WhatsNewView's body without the pinned footer: the cookie ends the content block, which is centred when shorter than
 * the screen, with no bottom reserve.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeGuideDraftScreenshotTest.kt#WelcomeGuideDraft
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#WelcomeCookieStartDraft
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeGuideDraft(
    tiles: List<SetupTile>,
    onStart: () -> Unit,
    morphProgress: Float = 0f,
    scale: Float = 1f,
    rotation: Float = 0f
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
                CookieStartDraft(
                    onClick = onStart, description = "Start",
                    morphProgress = morphProgress, scale = scale, rotation = rotation
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
