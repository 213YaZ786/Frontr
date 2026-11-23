package com.frontr.app.screenshot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostStats
import com.frontr.app.di.appModule
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.icon.FrontrIcons
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
 * DRAFT for the user's yes (requests.md points 6 and 7), nothing of app/src/main changes: a sub page as MTGA's validated
 * profile header, for a subreddit. The banner (a STAND-IN, a vertical Brush of primary to tertiary: no picture is
 * fetched in a test) 150 dp edge to edge from the window's top with the status bar shadow, no top bar and no back
 * button (the system gesture), the round sub icon 90 dp over the banner's edge in a 2 dp ring, Follow at the end of
 * that band (Material 3 Expressive's medium filled button, "Following" outlined with a check), under them the name
 * (two lines at most), "r/android" and the description, nothing else: no members, no created date (Reddit's page gives
 * neither, point 17), no source line. Then the posts.
 * Pictures: sub_header_draft, sub_header_following_draft, sub_header_long_draft (a long name and description),
 * sub_header_now (today's FeedScreen header, FeedScreen.kt ProfileHeader, with its bar), sub_top_now (today's bar while
 * it loads: the mark inside the capsule's Refresh button, point 6) and sub_pull_draft (the sub page pulled: the
 * melting dots at the centre over the blurred page, "2 of 4" over "sources", the ring of PullDotsDraftScreenshotTest);
 * the drafts with their 200 % text and 360 dp edges.
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileHeaderDraftScreenshotTest.kt#DraftHeader
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileTopDraftScreenshotTest.kt#ProfileTopDraftScreenshotTest
 * which take the header from Bluesky's
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Shell.tsx#ProfileHeaderShell
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/layout.ts#PROFILE_BANNER_HEIGHT
 * https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/StatusBarShadow.tsx#StatusBarShadow
 * not ported: ProfileHeaderMetrics, MetricsItem, ProfileItem, LocationOnRounded, CalendarMonthRounded, LinkRounded (a
 * subreddit gives no counts, place or date), the verified mark, the website, LongContent's counts; the banner click and
 * its Coil picture (a stand-in here); the other header parts of Bluesky's files
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class SubHeaderDraftScreenshotTest : ScreenshotBase() {

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

    // The sub's posts: Reddit's way, the title, a blank line, the body.
    private val posts = listOf(
        DraftPosts[0],
        Post(
            id = "t3_1klmno", authorHandle = "android", authorName = "r/Android", relatedHandle = "tablet_guy",
            text = "Is the new gesture bar worth keeping?\n\nI have used it for a week and cannot decide.",
            publishedAtMillis = FIXED_NOW - 5 * 60 * 60 * 1000,
            permalink = "https://www.reddit.com/r/Android/comments/1klmno/is_the_new_gesture_bar_worth_keeping/",
            stats = PostStats(replies = 63, likes = 840)
        )
    )

    @Test
    fun draft() = capture("sub_header_draft") { Screen(following = false) }

    @Test
    fun followingState() = capture("sub_header_following_draft") { Screen(following = true) }

    @Test
    fun long() = capture("sub_header_long_draft") { Screen(following = false, content = LongSub) }

    // Today's FeedScreen: the bar with Back and Refresh, then the centred header with its source line.
    @Test
    fun now() = capture("sub_header_now") { NowScreen(loading = false) }

    // Today's bar while it loads: the mark in the capsule's Refresh button.
    @Test
    fun topNow() = capture("sub_top_now") { NowScreen(loading = true) }

    // The sub page pulled: the page blurred, the dots at the centre, "2 of 4" over "sources".
    @Test
    fun pull() = capture("sub_pull_draft") { PullSub() }

    @Test
    fun edge() = captureEdge("sub_header_draft") { Screen(following = false) }

    @Test
    fun edgeLong() = captureEdge("sub_header_long_draft") { Screen(following = false, content = LongSub) }

    @Test
    fun edgePull() = captureEdge("sub_pull_draft") { PullSub() }

    @Composable
    private fun Screen(following: Boolean, content: SubContent = ShortSub) {
        Column(Modifier.fillMaxSize()) {
            DraftHeader(following, content)
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, showStats = true) }
            }
        }
    }

    /**
     * ProfileTopDraftScreenshotTest#DraftProfile's pulled frame: the page blurred (Neko's PullRefresh, 16 dp), over it
     * the ring at the centre with the count and its word.
     */
    @Composable
    private fun PullSub() {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().blurFeed(blurBackground = true)) { Screen(following = false) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeatherRing(progress = 0.5f) {
                    Text("2 of 4", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text("sources", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }

    /**
     * ProfileTopDraftScreenshotTest#now: FeedScreen's floating bar, Back, no title (the header is on screen), the
     * Refresh button holding the 22 dp mark while it loads, then today's header (a faithful copy of FeedScreen's private
     * ProfileHeader for r/Android).
     */
    @Composable
    private fun NowScreen(loading: Boolean) {
        Column(Modifier.fillMaxSize()) {
            FloatingTopBar(
                title = {},
                navigationIcon = {
                    Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                        Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (loading) LoadingMark(size = 22.dp, running = false, progress = 1f)
                        else Icon(FrontrIcons.Refresh, contentDescription = "Refresh")
                    }
                }
            )
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { NowHeader(ShortSub) }
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, showStats = true) }
            }
        }
    }
}

private class SubContent(val name: String, val handle: String, val description: String)

private val ShortSub = SubContent(
    "Android", "r/android",
    "News, reviews, tips and discussion about the Android operating system and its apps."
)

private val LongSub = SubContent(
    "The Linux Kernel Mailing List, Systems Programming and Everything Around Open Source Operating Systems",
    "r/linux_kernel_and_systems_programming_discussion",
    "Patches, reviews, release notes and long threads from every corner of the kernel. Read before you post, search " +
        "before you ask, and be kind to the people who maintain what the rest of us run."
)

/**
 * A faithful copy of today's ProfileHeader (FeedScreen.kt, private) for a sub the reader has read: the banner inset and
 * rounded (a stand-in Brush), the icon 88 dp, the name centred, the description, Follow, the source line.
 */
@Composable
private fun NowHeader(content: SubContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f).clip(ShapeDefaults.Large)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
        )
        Avatar(url = null, name = content.name, size = 88.dp)
        Text(
            "r/${content.name}",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            content.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp)
        )
        com.frontr.app.ui.component.BoldButton(filled = true, onClick = {}, modifier = Modifier.padding(top = 8.dp)) { Text("Follow") }
        Text(
            "Read via www.reddit.com, updated 3 min ago",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }
}

// https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileHeaderDraftScreenshotTest.kt#DraftHeader
// The banner behind (ProfileBannerHeight), the icon at top 104 and left 10 inside its ring, Follow at the end of the band
// below the banner (px_lg, pt_md), then the name, the handle and the description at the start (px_lg).
@Composable
private fun DraftHeader(following: Boolean, content: SubContent) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth()) {
        // A stand-in for the banner picture: a vertical Brush of primary to tertiary, no Coil picture here.
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                )
        )
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalReadableInset.current)) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, size 90: a ring of the page's colour around the icon.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Sub icon" },
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = null, name = content.name, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 8 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = ProfileBannerHeight + 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = following, onClick = {})
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // DisplayName.tsx: the name wraps, capped here at 2 lines.
                    Text(
                        content.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Handle.tsx ProfileHeaderHandle: numberOfLines={1}.
                    Text(
                        content.handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(content.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileHeaderDraftScreenshotTest.kt#FollowButton
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Box, no Material control. Filled
// (ContainerColor Primary, IconColor and LabelTextColor OnPrimary) or, once followed, outlined.
// The height is a minimum, so a 200 % label never breaks it.
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
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
    val shape = RoundedCornerShape(with(density) { corner.toDp() })
    val contentColor = if (following) colors.primary else colors.onPrimary
    Row(
        Modifier
            .heightIn(min = height)
            .clip(shape)
            .then(
                if (following) Modifier.border(ButtonMediumOutlineWidth, colors.outline, shape)
                else Modifier.background(colors.primary, shape)
            )
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
        Icon(
            if (following) CheckRounded else PersonAddRounded,
            contentDescription = null,
            modifier = Modifier.size(ButtonMediumIconSize),
            tint = contentColor
        )
        Spacer(Modifier.width(ButtonMediumIconLabelSpace))
        // textStyleFor: titleMedium from the medium height up.
        Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp
private val ButtonMediumOutlineWidth: Dp = 1.dp

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

// layout.ts PROFILE_BANNER_HEIGHT, Shell.tsx GrowableAvatar (top 104, left 10, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

private fun symbol(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = autoMirror)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
