package com.frontr.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostStats
import com.frontr.app.di.appModule
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.AppBarWithSearch
import com.frontr.app.ui.component.BannerAction
import com.frontr.app.ui.component.CapsuleMenuOffset
import com.frontr.app.ui.component.DropdownMenuGroup
import com.frontr.app.ui.component.DropdownMenuItem
import com.frontr.app.ui.component.EmptyZone
import com.frontr.app.ui.component.FolderSwitch
import com.frontr.app.ui.component.GroupSpacing
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.SearchCapsuleHint
import com.frontr.app.ui.component.SelectableDropdownMenuItem
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
 * DRAFT for the user's yes (requests.md points 1 and 3), nothing of app/src/main changes: Home's top as MTGA's validated
 * variant A2, on Frontr's own kit. One glass capsule the width of the page, nothing under it: the folder switch leading
 * ("All", its GlassMenu), "Search" in the middle, the alert trailing only when something failed; Frontr's post cards
 * (Reddit's title on its own line) right below.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * Pictures: home_top_draft, home_top_alert_draft (the alert), home_top_menu_draft (the GlassMenu open: All 12, the
 * folders with counts, then Liked and Archived), home_top_pull_draft (pulled, a centre saying "Updated" over "3 min
 * ago"), home_empty_draft (nobody followed: the capsule with the pill "All", then the empty state); each with its 200 %
 * text and 360 dp edges.
 * The GlassMenu is a Popup that Robolectric does not draw: MenuOverScreen stands for it.
 * not ported: TopArrangementDraftScreenshotTest's B variant, LargeTitleTop, LargeFlexibleTopAppBar, TodayHome (today's
 * top is told by requests.md point 1), SearchCapsule (the first A, with its status line), the hand blur (Modifier.blur
 * does it), CountedMenuItem (the kit's SelectableDropdownMenuItem carries the count itself)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeTopDraftScreenshotTest : ScreenshotBase() {

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
    fun homeA2() = capture("home_top_draft") { HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) } }

    @Test
    fun homeA2Alert() = capture("home_top_alert_draft") { HomeScreen { A2Capsule("All", HomeHints, alert = true, onSwitch = {}) } }

    @Test
    fun homeA2Menu() = capture("home_top_menu_draft") { HomeMenu() }

    @Test
    fun homeA2Pull() = capture("home_top_pull_draft") { PullScreen(pull = 0.6f) }

    @Test
    fun homeEmpty() = capture("home_empty_draft") { EmptyHome() }

    @Test
    fun edgeHomeA2() = captureEdge("home_top_draft") { HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) } }

    @Test
    fun edgeHomeA2Alert() = captureEdge("home_top_alert_draft") { HomeScreen { A2Capsule("All", HomeHints, alert = true, onSwitch = {}) } }

    @Test
    fun edgeHomeA2Menu() = captureEdge("home_top_menu_draft") { HomeMenu() }

    @Test
    fun edgeHomeA2Pull() = captureEdge("home_top_pull_draft") { PullScreen(pull = 0.6f) }

    @Test
    fun edgeHomeEmpty() = captureEdge("home_empty_draft") { EmptyHome() }

    @Composable
    private fun HomeMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            contentOffset = CapsuleMenuOffset,
            screen = { HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = { anchor = it }) } }
        ) {
            DropdownMenuGroup(index = 0, count = 2) { FolderRows() }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf("Liked", "Archived").forEach { DropdownMenuItem(text = it, onClick = {}) }
            }
        }
    }

    // Nobody followed: the capsule stays, the pill "All" in it, and the empty state under it (TimelineScreen's words).
    @Composable
    private fun EmptyHome() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) {
                Column { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) }
            }
            EmptyZone(
                title = "Nothing followed yet",
                message = "Follow a few subreddits and they will all appear here, on one front page.",
                icon = FrontrIcons.Person,
                actionLabel = "Open Subreddits",
                onAction = {},
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Home's one word in the capsule, and Subreddits' (AccountsTopDraftScreenshotTest) shrinking pair.
internal val HomeHints = listOf("Search")

// The sample posts of every draft, dated from the fixed clock so the cards' ages never move: Reddit's way, the title,
// a blank line, the body (PostCardScreenshotTest's).
internal val DraftPosts = listOf(
    Post(
        id = "t3_1abcde", authorHandle = "android", authorName = "r/Android", relatedHandle = "pixelfan",
        text = "Android 17 beta 2 is out\n\nThe second beta brings the new lock screen clock and fixes the battery drain " +
            "some of you reported on the first one.",
        publishedAtMillis = FIXED_NOW - 2 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/Android/comments/1abcde/android_17_beta_2_is_out/",
        stats = PostStats(replies = 128, likes = 9_300)
    ),
    Post(
        id = "t3_1fghij", authorHandle = "grapheneos", authorName = "r/GrapheneOS", relatedHandle = "strcat",
        text = "Release notes for the latest build",
        publishedAtMillis = FIXED_NOW - 26 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/GrapheneOS/comments/1fghij/release_notes_for_the_latest_build/",
        stats = PostStats(replies = 41, likes = 2_100)
    ),
    Post(
        id = "t3_1pqrst", authorHandle = "linux", authorName = "r/linux", relatedHandle = "maintainer",
        text = "A good week for the people building in the open\n\nThank you to every maintainer.",
        publishedAtMillis = FIXED_NOW - 3 * 24 * 60 * 60 * 1000,
        permalink = "https://www.reddit.com/r/linux/comments/1pqrst/a_good_week_for_the_people_building_in_the_open/",
        stats = PostStats(replies = 20, likes = 1_450)
    )
)

// The screen under the top: the sample cards, which carry their own 16 dp margin (as PostCardScreenshotTest draws them),
// so they line up with the capsule's edges.
@Composable
internal fun HomeScreen(top: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Column { top() } }
        Column(
            Modifier.padding(horizontal = LocalReadableInset.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DraftPosts.forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}, unread = it === DraftPosts.first()) }
        }
    }
}

// The folders of the menu, each with its count at the end (Gmail's labels): All is the sum of the others.
@Composable
internal fun FolderRows() {
    SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All", count = 12)
    SelectableDropdownMenuItem(selected = false, onClick = {}, text = "Tech", count = 5)
    SelectableDropdownMenuItem(selected = false, onClick = {}, text = "News", count = 4)
    SelectableDropdownMenuItem(selected = false, onClick = {}, text = "Fun", count = 3)
}

// The alert of the capsule: a round action tinted with the error colour, Material Symbols Rounded's warning, fill 1.
@Composable
internal fun AlertAction() {
    BannerAction(
        icon = WarningRounded,
        label = "What went wrong",
        onClick = {},
        tint = MaterialTheme.colorScheme.error
    )
}

// A's capsule exactly, nothing under it; the field shrinks before it cuts, then drops to the next shorter form (the
// kit's SearchCapsuleHint does both). [onSwitch] gives the point under the folder switch, where the menu opens.
@Composable
internal fun A2Capsule(folder: String, hints: List<String>, alert: Boolean, onSwitch: (IntOffset) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBarWithSearch(
            inputField = { SearchCapsuleHint(FrontrIcons.Search, hints, onClick = {}) },
            navigationIcon = {
                FolderSwitch(
                    icon = FrontrIcons.Folder,
                    trailingIcon = ExpandMore,
                    label = folder,
                    expanded = false,
                    onClick = {},
                    modifier = Modifier.onGloballyPositioned {
                        val at = it.positionInRoot()
                        onSwitch(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                    }
                )
            },
            actions = if (alert) ({ AlertAction() }) else null
        )
    }
}

/**
 * The pull before release: Home blurred behind (Neko's PullRefresh blurs the content while it works), at the centre the
 * test's own ring, Frontr's melting dots smaller and fainter the less the pull has gone, the age of the last update
 * under them as MJRefresh puts the last update beside its state.
 * https://github.com/CoderMJLee/MJRefresh/blob/master/MJRefresh/Custom/Header/MJRefreshStateHeader.m#lastUpdatedTimeLabel
 * not ported: MJRefreshStateHeader's stateLabel and its titles per state, lastUpdatedTimeText's date formats (the app's
 * own "Updated N ago" is the text), the NSUserDefaults key of the last time, placeSubviews and its arrow and loading
 * views, setState, prepare and the localized strings
 */
@Composable
internal fun PullScreen(pull: Float) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blurFeed(blurBackground = true)) {
            HomeScreen { A2Capsule("All", HomeHints, alert = false, onSwitch = {}) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FeatherRing(progress = pull, scale = 0.5f + 0.5f * pull, alpha = pull) {
                Text("Updated", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("3 min ago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Material Symbols "expand_more", as the kit's FolderSwitch takes it (GlassMenu.kt keeps its icons private).
// https://github.com/google/material-design-icons/blob/master/symbols/android/expand_more/materialsymbolsoutlined/expand_more_24px.xml
internal val ExpandMore: ImageVector by lazy {
    val pathData = "M480,615L240,375L296,319L480,503L664,319L720,375L480,615Z"
    ImageVector.Builder("ExpandMore", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

// Material Symbols "chevron_right", for the "Manage folders" row (GlassMenu.kt keeps its chevron private).
// https://github.com/google/material-design-icons/blob/master/symbols/android/chevron_right/materialsymbolsoutlined/chevron_right_24px.xml
internal val ChevronRight: ImageVector by lazy {
    val pathData = "M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"
    ImageVector.Builder("ChevronRight", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = true)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

// Material Symbols "tune", the "Manage folders" row's icon.
// https://github.com/google/material-design-icons/blob/master/symbols/android/tune/materialsymbolsoutlined/tune_24px.xml
internal val Tune: ImageVector by lazy {
    val pathData = "M440,840L440,600L520,600L520,680L840,680L840,760L520,760L520,840L440,840ZM120,760L120,680L360,680L360,760L120,760Z" +
        "M280,600L280,520L120,520L120,440L280,440L280,360L360,360L360,600L280,600ZM440,520L440,440L840,440L840,520L440,520Z" +
        "M600,360L600,120L680,120L680,200L840,200L840,280L680,280L680,360L600,360ZM120,280L120,200L520,200L520,280L120,280Z"
    ImageVector.Builder("Tune", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

// Material Symbols Rounded "warning", fill 1: rounded corners, the failure mark of the capsule and of the dialog.
// https://github.com/google/material-design-icons/blob/master/symbols/android/warning/materialsymbolsrounded/warning_fill1_24px.xml
internal val WarningRounded: ImageVector by lazy {
    val pathData = "M109,840Q98,840 89,834.5Q80,829 75,820Q70,811 69.5,800.5Q69,790 75,780L445,140Q451,130 460.5,125" +
        "Q470,120 480,120Q490,120 499.5,125Q509,130 515,140L885,780Q891,790 890.5,800.5Q890,811 885,820" +
        "Q880,829 871,834.5Q862,840 851,840L109,840ZM480,720Q497,720 508.5,708.5Q520,697 520,680Q520,663 508.5,651.5" +
        "Q497,640 480,640Q463,640 451.5,651.5Q440,663 440,680Q440,697 451.5,708.5Q463,720 480,720Z" +
        "M480,600Q497,600 508.5,588.5Q520,577 520,560L520,440Q520,423 508.5,411.5Q497,400 480,400Q463,400 451.5,411.5" +
        "Q440,423 440,440L440,560Q440,577 451.5,588.5Q463,600 480,600Z"
    ImageVector.Builder("Warning", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()
}

private val MenuOverWidth: Dp = 280.dp

// The screen with the menu over it: a scrim on the screen, then the menu under the pill that opened it (a Popup in the
// app, Menu.kt's DropdownMenu, which Robolectric does not draw). [anchor] is the point under the pill's middle, in the
// window; the menu is kept 12 dp from the screen's sides. Ported from MTGA's MenuOverScreen
// https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/HomeTopDraftScreenshotTest.kt#MenuOverScreen
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenu
// not ported: the GlassMenu's own DropdownMenuGroup, MenuItemRow and SelectableDropdownMenuItem copies (the app's
// GlassMenu.kt has them, used as they are)
@Composable
internal fun MenuOverScreen(
    open: Boolean,
    anchor: IntOffset,
    contentOffset: DpOffset,
    screen: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit
) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            val at = it.positionInRoot()
            origin = IntOffset(at.x.toInt(), at.y.toInt())
            width = it.size.width
        }
    ) {
        screen()
        if (open) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
            val menuWidth = with(density) { MenuOverWidth.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            // The vertical placement of DropdownMenuPositionProvider: the menu's top at the anchor's bottom (topToAnchorBottom)
            // plus the content offset (contentOffsetY); the app passes CapsuleMenuOffset for the capsule's pill (16 dp: the
            // pill's bottom to the capsule's bottom, 8 dp, plus an 8 dp gap).
            // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/MenuPosition.kt#DropdownMenuPositionProvider
            // not ported: its candidates, flipping and margins (the stand-in does not need them)
            val gap = with(density) { contentOffset.y.roundToPx() }
            // The menu is centred under the pill: [anchor].x is the pill's middle.
            val x = (anchor.x - origin.x - menuWidth / 2).coerceIn(margin, (width - menuWidth - margin).coerceAtLeast(margin))
            Column(
                Modifier.offset { IntOffset(x, anchor.y - origin.y + gap) }.width(MenuOverWidth),
                verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                content = menu
            )
        }
    }
}
