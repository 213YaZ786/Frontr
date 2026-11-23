package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostStats
import com.frontr.app.di.appModule
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.BarTitle
import com.frontr.app.ui.component.FloatingFrame
import com.frontr.app.ui.component.FloatingTop
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.LargeTitle
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.SettingsFoldedSections
import com.frontr.app.ui.component.SettingsHeaderSpacer
import com.frontr.app.ui.component.SettingsSection
import com.frontr.app.ui.component.SettingsSwitchTile
import com.frontr.app.ui.component.SettingsTileGrid
import com.frontr.app.ui.component.ZoneSurface
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
 * DRAFT for the user's yes (requests.md point 10), nothing of app/src/main changes: the content scrolls on the whole
 * screen with no band at the top, and the top capsule folds away while scrolling down and comes back on the first
 * upward scroll: Material's enterAlwaysScrollBehavior (EnterAlwaysScrollBehavior, its nestedScrollConnection and
 * heightOffset), which FloatingTopBar already supports through its scrollBehavior, as FeedScreen uses it:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#enterAlwaysScrollBehavior
 * Three screens, each scrolled two items down: the post page (PostDetailScreen's pinned bar today), Saved media
 * (SavedMediaScreen's) and Settings (its large title and faded top today). Pictures: scroll_full_screen_draft (the post
 * page, the capsule fully folded: heightOffset = heightOffsetLimit, rows up to the window's top, under the status bar),
 * scroll_full_screen_back_draft (the capsule coming back, half the offset, a scroll up), scroll_full_screen_now (the
 * pinned bar, the list running under it); the same three with _media_ and _settings_ in the name for the other two
 * screens. Settings has no scrollBehavior (FloatingFrame's top is no TopAppBar): its top row is drawn translated up by
 * the same fraction, as the draft of the way it would fold; the Settings page is the phone's folded list on every width.
 * Port of MTGA's validated draft:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ScrollFullScreenDraftScreenshotTest.kt#ScrollFullScreenDraftScreenshotTest
 * not ported: EnterAlwaysScrollBehavior's flingAnimationSpec and snapAnimationSpec (a still picture, no fling);
 * DiagnosticsDraftScreen (MTGA's screen, here the three Frontr screens); Settings' list and detail on a wide window
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ScrollFullScreenDraftScreenshotTest : ScreenshotBase() {

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

    private enum class Page { Post, Media, Settings }

    private enum class Mode { Now, Folded, Back }

    // The bar's height is measured after the first frame, and the offset follows it one frame later: frames are given.
    private fun settle() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    // Today's Settings fades what scrolled under its header from the scroll events themselves: a real swipe up gives it.
    private fun swipe() {
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    @Test
    fun folded() = capture("scroll_full_screen_draft", interact = ::settle) { ListPage(Page.Post, Mode.Folded) }

    @Test
    fun back() = capture("scroll_full_screen_back_draft", interact = ::settle) { ListPage(Page.Post, Mode.Back) }

    @Test
    fun now() = capture("scroll_full_screen_now") { ListPage(Page.Post, Mode.Now) }

    @Test
    fun mediaFolded() = capture("scroll_full_screen_media_draft", interact = ::settle) { ListPage(Page.Media, Mode.Folded) }

    @Test
    fun mediaBack() = capture("scroll_full_screen_media_back_draft", interact = ::settle) { ListPage(Page.Media, Mode.Back) }

    @Test
    fun mediaNow() = capture("scroll_full_screen_media_now") { ListPage(Page.Media, Mode.Now) }

    @Test
    fun settingsFolded() = capture("scroll_full_screen_settings_draft", interact = ::settle) { SettingsPage(Mode.Folded) }

    @Test
    fun settingsBack() = capture("scroll_full_screen_settings_back_draft", interact = ::settle) { SettingsPage(Mode.Back) }

    @Test
    fun settingsNow() = capture("scroll_full_screen_settings_now", interact = ::swipe) { SettingsPage(Mode.Now) }

    // Edge: the capsule fully folded, at 200 % text and on a 360 dp phone.
    @Test
    fun foldedEdge() = captureEdge("scroll_full_screen_draft", interact = ::settle) { ListPage(Page.Post, Mode.Folded) }

    @Test
    fun mediaFoldedEdge() = captureEdge("scroll_full_screen_media_draft", interact = ::settle) { ListPage(Page.Media, Mode.Folded) }

    @Test
    fun settingsFoldedEdge() = captureEdge("scroll_full_screen_settings_draft", interact = ::settle) { SettingsPage(Mode.Folded) }

    /**
     * enterAlwaysScrollBehavior's state moved to the fraction of the fold the frame shows, once the bar has measured
     * itself (heightOffsetLimit, set by FloatingTopBar's onSizeChanged); the list sits two items down. Now keeps the
     * bar pinned, as PostDetailScreen and SavedMediaScreen build it.
     * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ScrollFullScreenDraftScreenshotTest.kt#ScrollFullScreenDraftScreenshotTest
     */
    @Composable
    private fun ListPage(page: Page, mode: Mode) {
        val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        if (mode != Mode.Now) {
            val fraction = if (mode == Mode.Folded) 1f else 0.5f
            // The limit is read again each frame until the bar has measured itself, then the offset is set once.
            LaunchedEffect(Unit) {
                while (behavior.state.heightOffsetLimit < -1_000_000f) withFrameNanos { }
                behavior.state.heightOffset = behavior.state.heightOffsetLimit * fraction
            }
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = 2, initialFirstVisibleItemScrollOffset = 60)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = if (mode == Mode.Now) Modifier else Modifier.nestedScroll(behavior.nestedScrollConnection),
            topBar = {
                FloatingTopBar(
                    scrollBehavior = if (mode == Mode.Now) null else behavior,
                    title = { if (page == Page.Post) BarTitle("Post") else BarTitle("Saved media") },
                    navigationIcon = {
                        Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                            Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (page == Page.Post) {
                            Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                                Icon(FrontrIcons.Share, contentDescription = "Share")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    top = padding.calculateTopPadding(),
                    bottom = 24.dp
                )
            ) {
                if (page == Page.Post) postItems() else mediaItems()
            }
        }
    }

    // PostDetailScreen's conversation: the post in full, "Replies", then the comments as cards.
    private fun androidx.compose.foundation.lazy.LazyListScope.postItems() {
        item(key = "main") {
            PostCard(post = DraftPosts[0], onClick = {}, onOpenLink = {}, onDownload = {})
        }
        item(key = "replieslabel") {
            Text(
                "Replies",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
        }
        items(replies.size, key = { replies[it].id }) { index ->
            PostCard(post = replies[index], onClick = {}, onOpenLink = {}, onDownload = {}, showStats = true)
        }
    }

    // SavedMediaScreen's list: the total, centred, then one zone per sub.
    private fun androidx.compose.foundation.lazy.LazyListScope.mediaItems() {
        item(key = "total") {
            Text(
                "58 files, 212 MB",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        }
        items(mediaGroups.size, key = { mediaGroups[it].first }) { index ->
            val (name, count) = mediaGroups[index]
            ZoneSurface(shape = ShapeDefaults.ExtraLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text("$count files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    private val replies = List(6) { n ->
        Post(
            id = "t1_c$n", authorHandle = "pixelfan$n", authorName = "u/pixelfan$n",
            text = "Same here, the second beta fixed the drain on my phone after one night.",
            publishedAtMillis = FIXED_NOW - (n + 1) * 20 * 60 * 1000L,
            permalink = "https://www.reddit.com/r/Android/comments/1abcde/android_17_beta_2_is_out/c$n/",
            stats = PostStats(replies = n, likes = 40 - n * 5)
        )
    }

    private val mediaGroups = listOf(
        "r/Android" to 21, "r/GrapheneOS" to 14, "r/linux" to 9, "r/privacy" to 7, "r/selfhosted" to 4, "r/kde" to 3,
        "r/archlinux" to 2, "r/opensource" to 1
    )

    // Appearance's groups and switches, as SettingsTopDraftScreenshotTest builds them.
    private val appearance = SettingsSection("appearance", "Appearance", SettingsIcons.Palette)
    private val sections = listOf(
        appearance,
        SettingsSection("media", "Media", SettingsIcons.Image),
        SettingsSection("reading", "Reading", FrontrIcons.Comment),
        SettingsSection("background", "Background updates", FrontrIcons.Refresh),
        SettingsSection("storage", "Storage", FrontrIcons.Folder),
        SettingsSection("list", "Subreddits", FrontrIcons.Person),
        SettingsSection("support", "Support", SettingsIcons.Warning),
        SettingsSection("about", "About", FrontrIcons.Info)
    )
    private val tiles = listOf(
        SettingsSwitchTile("black", "Pure black", "Pure black", SettingsIcons.Night, "Deeper blacks in dark mode.", checked = false, onChange = {}),
        SettingsSwitchTile("glass", "Glass effects", "Glass effects", SettingsIcons.AutoAwesome, "Zones and floating buttons in liquid glass.", checked = true, onChange = {}),
        SettingsSwitchTile("counts", "Counts", "Show counts", FrontrIcons.Heart, "Comments and votes under each post.", checked = true, onChange = {}),
        SettingsSwitchTile("compact", "Compact posts", "Compact posts", SettingsIcons.GridView, "Tighter spacing, smaller avatars.", checked = false, onChange = {}),
        SettingsSwitchTile("square", "Square avatars", "Square avatars", FrontrIcons.Person, "Rounded squares instead of circles.", checked = false, onChange = {})
    )

    /**
     * Settings scrolled: today's frame (large title, faded top, a swipe up given by the test) or the draft's (no large
     * title, no fade, the top row translated up by its fold, the sections running to the window's top).
     */
    @Composable
    private fun SettingsPage(mode: Mode) {
        val density = LocalDensity.current
        val scroll = rememberScrollState(initial = with(density) { 220.dp.roundToPx() })
        val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
        val rowPx = with(density) { 64.dp.toPx() }
        val fold = when (mode) {
            Mode.Folded -> 1f
            Mode.Back -> 0.5f
            Mode.Now -> 0f
        }
        FloatingFrame(
            bottom = 0.dp,
            scroll = scroll,
            softEdge = mode == Mode.Now,
            top = {
                FloatingTop(
                    "Settings",
                    modifier = Modifier.graphicsLayer { translationY = -fold * (rowPx + statusTop) }
                )
            },
            largeTitle = if (mode == Mode.Now) LargeTitle("Settings") else null
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = LocalReadableInset.current)
            ) {
                SettingsHeaderSpacer(padding)
                Spacer(Modifier.height(if (mode == Mode.Now) 24.dp else 16.dp))
                SettingsFoldedSections(sections, open = appearance, onOpen = {}) { SettingsTileGrid(tiles) {} }
                Spacer(Modifier.height(24.dp))
                // A long page, so the list has room to scroll on any window.
                Spacer(Modifier.height(240.dp))
            }
        }
    }
}
