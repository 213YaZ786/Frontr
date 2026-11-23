package com.frontr.app.feature.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frontr.app.core.model.Feed
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.rememberMediaPolicy

/**
 * A sub's header, compact as Bluesky's: the banner edge to edge from the window's top (status bar shadow kept),
 * the icon at the start overlapping it in a ring of the page's colour, Follow at the end below the banner, then at
 * the start the name, "r/name" and the description as bio.
 * The banner obeys the media policy (held: its ground colour only). [LocalReadableInset] narrows the text part only.
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#ProfileHeader
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/ProfileHeaderStandard.tsx#ProfileHeaderStandard
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#ProfileHeaderShell
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#onPressBanner
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/StatusBarShadow.tsx#StatusBarShadow
 * not ported: VerifiedType and VerifiedIcon (X only, Reddit has no verified mark)
 * not ported: ProfileHeaderMetrics, MetricsItem (X only, a Reddit page gives no member count, point 17 waits for a saved page)
 * not ported: ProfileItem and the website, location and joined line with their four icons LinkRounded, LocationOnRounded,
 *   CalendarMonthRounded (X only, a Reddit page gives none of them, point 17)
 * not ported: compactCount (only the metrics used it)
 * not ported: AvatarVariant, BannerWithSize, originalAvatar, bannerFull (X's pbs.twimg.com sizes; a Reddit picture is opened as it is)
 * not ported: Shell.tsx's live status card, moderation blur, analytics metric, banner growth on overscroll, haptics, web offsets
 * not ported: ProfileHeaderStandard's labeler, block, mute, subscribe and message buttons (a reader has no account)
 * not ported: measureScrollMarginTop, watchScrollMarginTop, pinInitialHash (web scroll offsets and hash)
 * not ported: onPressFollow (the follow runs through onToggleFollow), onPressUnfollow (FeedScreen's Unfollow dialog asks first)
 * not ported: onRequestHide, unblockAccount, pin (moderation and account actions, a reader has no account)
 * not ported: headerStandardButtons (FollowButton is its one button)
 * not ported: shouldUseProfileLightStatusBar (the status bar's icons are the app's, set in MainActivity)
 * not ported: profileBannerSafeAreaCoverHeight (web safe area, the inset is read from WindowInsets.statusBars)
 * not ported: ProfileHeaderDisplayName, ProfileHeaderHandle, StatusBarShadow (drawn inline in ProfileHeader)
 */
@Composable
internal fun ProfileHeader(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit,
    onOpenBanner: (String) -> Unit
) {
    val hold = rememberMediaPolicy().hold
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val readable = LocalReadableInset.current
    val banner = feed?.bannerUrl
    val avatar = feed?.avatarUrl
    Box(Modifier.fillMaxWidth()) {
        // Shell.tsx onPressBanner: only a banner that exists opens the lightbox (here MediaViewer).
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .clickable(enabled = banner != null, role = Role.Image) { banner?.let(onOpenBanner) }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // The banner is a picture like any other, so Wi-Fi only holds it too.
            if (banner != null && !hold) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(ProfileBannerHeight)
                )
            }
        }
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, width 94, size 90: a ring of the page's colour around the icon.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft + readable)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Sub icon" }
                        // Shell.tsx onPressAvi: only an icon that exists opens the lightbox.
                        .clickable(enabled = avatar != null, role = Role.Image) { avatar?.let(onOpenAvatar) },
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = avatar, name = name ?: handle, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 8 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = ProfileBannerHeight + 8.dp, end = 16.dp + readable),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = isFollowing, onClick = onToggleFollow)
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.padding(start = 16.dp + readable, end = 16.dp + readable),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // DisplayName.tsx: the name wraps, capped here at 2 lines.
                    Text(
                        name ?: "r/$handle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Handle.tsx ProfileHeaderHandle: numberOfLines={1}.
                    if (name != null) {
                        Text(
                            "r/$handle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                feed?.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#FollowButton
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Button.kt#textStyleFor
// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Box, no Material control.
// Filled (ContainerColor Primary, IconColor and LabelTextColor OnPrimary) or, once followed, outlined (OutlinedOutlineWidth).
// The height is a minimum, so a 200 % label never breaks it.
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
// not ported: textStyleFor (its one case here, titleMedium from the medium height up, is the Text's style below)
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val height = ButtonMediumContainerHeight
    // ContainerShapeRound (CornerFull) morphs to PressedContainerShape (CornerMedium = ShapeDefaults.Medium) with a spring.
    val full = with(density) { height.toPx() / 2 }
    val medium = ShapeDefaults.Medium.topStart.toPx(Size(full * 2, full * 2), density)
    val corner by animateFloatAsState(
        targetValue = if (pressed) medium else full,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "followCorner"
    )
    // Same hue, alpha only: a fade to Color.Transparent would pass through grey.
    val container by animateColorAsState(if (following) colors.primary.copy(alpha = 0f) else colors.primary, tween(200), label = "followContainer")
    val line by animateColorAsState(if (following) colors.outline else colors.outline.copy(alpha = 0f), tween(200), label = "followLine")
    val contentColor by animateColorAsState(if (following) colors.primary else colors.onPrimary, tween(200), label = "followContent")
    Row(
        Modifier
            .heightIn(min = height)
            // The clip is drawn through the layer: its block reads the corner (snapshot observed, no recomposition per
            // frame) and builds the shape from it, a new one each time, so the outline is made again for certain.
            .graphicsLayer {
                this.shape = RoundedCornerShape(corner)
                clip = true
            }
            // Container and outline drawn in the draw block (snapshot observed): the corner read here redraws them each
            // frame of the press spring, no recomposition. The stroke is twice the width: the clip keeps its inner half.
            .drawBehind {
                val outline = RoundedCornerShape(corner).createOutline(size, layoutDirection, this)
                drawOutline(outline, container)
                drawOutline(outline, line, style = Stroke(ButtonMediumOutlineWidth.toPx() * 2f))
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = ButtonMediumLeadingSpace),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
        // not ported: foo, bar, baz (the sample's own demo functions), simpleAnimatedContentSample (used here, inline)
        // Icon and label fade through, the width follows without clipping (SizeTransform(clip = false)).
        AnimatedContent(
            targetState = following,
            transitionSpec = {
                (fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false))
            },
            label = "followLabel"
        ) { isFollowing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isFollowing) CheckRounded else PersonAddRounded,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonMediumIconSize),
                    tint = contentColor
                )
                Spacer(Modifier.width(ButtonMediumIconLabelSpace))
                // textStyleFor: titleMedium from the medium height up.
                Text(if (isFollowing) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
            }
        }
    }
}

// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp
private val ButtonMediumOutlineWidth: Dp = 1.dp

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/layout.ts#PROFILE_BANNER_HEIGHT
// Shell.tsx GrowableAvatar (top 104, left 10, width 94, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

private fun symbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()

// Material Symbols Rounded "person_add".
// https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml
private val PersonAddRounded: ImageVector by lazy {
    symbol(
        "PersonAdd",
        "M720,440L640,440Q623,440 611.5,428.5Q600,417 600,400Q600,383 611.5,371.5Q623,360 640,360L720,360L720,280" +
            "Q720,263 731.5,251.5Q743,240 760,240Q777,240 788.5,251.5Q800,263 800,280L800,360L880,360Q897,360 908.5,371.5" +
            "Q920,383 920,400Q920,417 908.5,428.5Q897,440 880,440L800,440L800,520Q800,537 788.5,548.5Q777,560 760,560" +
            "Q743,560 731.5,548.5Q720,537 720,520L720,440ZM360,480Q294,480 247,433Q200,386 200,320Q200,254 247,207" +
            "Q294,160 360,160Q426,160 473,207Q520,254 520,320Q520,386 473,433Q426,480 360,480ZM40,720L40,688" +
            "Q40,654 57.5,625.5Q75,597 104,582Q166,551 230,535.5Q294,520 360,520Q426,520 490,535.5Q554,551 616,582" +
            "Q645,597 662.5,625.5Q680,654 680,688L680,720Q680,753 656.5,776.5Q633,800 600,800L120,800Q87,800 63.5,776.5" +
            "Q40,753 40,720ZM120,720L600,720L600,688Q600,677 594.5,668Q589,659 580,654Q526,627 471,613.5Q416,600 360,600" +
            "Q304,600 249,613.5Q194,627 140,654Q131,659 125.5,668Q120,677 120,688L120,720ZM360,400Q393,400 416.5,376.5" +
            "Q440,353 440,320Q440,287 416.5,263.5Q393,240 360,240Q327,240 303.5,263.5Q280,287 280,320Q280,353 303.5,376.5" +
            "Q327,400 360,400Z"
    )
}

// Material Symbols Rounded "check".
// https://github.com/google/material-design-icons/blob/master/symbols/android/check/materialsymbolsrounded/check_24px.xml
private val CheckRounded: ImageVector by lazy {
    symbol(
        "Check",
        "M382,606L721,267Q733,255 749,255Q765,255 777,267Q789,279 789,295.5Q789,312 777,324L410,692Q398,704 382,704" +
            "Q366,704 354,692L182,520Q170,508 170.5,491.5Q171,475 183,463Q195,451 211.5,451Q228,451 240,463L382,606Z"
    )
}
