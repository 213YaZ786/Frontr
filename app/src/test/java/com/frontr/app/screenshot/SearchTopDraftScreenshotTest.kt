package com.frontr.app.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.BarTitle
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.GlassSearchField
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
 * DRAFT for the user's yes (requests.md point 15), nothing of app/src/main changes: Search under the floating capsule.
 * Today (SearchScreen.kt) a Material TopAppBar carries a bare back button and the search field as its title. The draft
 * is LinkedOut's: the capsule (FloatingTopBar, BarTitle "Search", the back arrow, folding with Material's
 * enterAlwaysScrollBehavior as PostDetailScreen's does) and the page as one list under it, full screen: the glass
 * field the first item, then the hint or the count of posts, then the results as cards. Pictures: search_top_draft
 * (empty field and the hint), search_typed_draft (text typed, the Clear icon in the field, the count and the cards),
 * search_typed_scrolled_draft (the list scrolled, its rows running under the capsule, which is still there as on a
 * first upward scroll); the _edge_ pictures are the first two at 200 % text and on a 360 dp phone.
 * Ported from LinkedOut's SearchScreen, its screen and its state (hint, count, Clear) kept, a typed query and a result
 * list given as fixed data in place of its view model:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/search/SearchScreen.kt#SearchScreen
 * not ported: SearchViewModel (state, setQuery), the focus request and the keyboard's Search action (no keyboard on
 * the JVM, the options are kept), the media viewer, the settings' counts switch, the banner (ScreenBanner and its
 * BannerAction: the capsule takes their place), navigationBarBottom and statusBarTop (Scaffold's padding)
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class SearchTopDraftScreenshotTest : ScreenshotBase() {

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

    // The bar's height is measured after the first frame: frames are given before the picture.
    private fun settle() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Test
    fun empty() = capture("search_top_draft", interact = ::settle) { Search(typed = "") }

    @Test
    fun typed() = capture("search_typed_draft", interact = ::settle) { Search(typed = "the") }

    @Test
    fun typedScrolled() = capture("search_typed_scrolled_draft", interact = ::settle) { Search(typed = "the", scrolled = true) }

    @Test
    fun emptyEdge() = captureEdge("search_top_draft", interact = ::settle) { Search(typed = "") }

    @Test
    fun typedEdge() = captureEdge("search_typed_draft", interact = ::settle) { Search(typed = "the") }

    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/search/SearchScreen.kt#SearchScreen
    @Composable
    private fun Search(typed: String, scrolled: Boolean = false) {
        // Fixed data in place of the view model's state: 312 saved posts, the three sample cards as results.
        val savedCount = 312
        val results = if (typed.isBlank()) emptyList() else DraftPosts
        val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = if (scrolled) 1 else 0,
            initialFirstVisibleItemScrollOffset = if (scrolled) 40 else 0
        )

        Scaffold(
            // The page's ground is painted under the whole app, see MainActivity.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
            topBar = {
                FloatingTopBar(
                    scrollBehavior = behavior,
                    title = { BarTitle("Search") },
                    navigationIcon = {
                        // The 48 dp target of the other drafts' bars, as IconButton draws it.
                        Box(Modifier.size(48.dp).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                            Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            val hint = when {
                savedCount == 0 -> "Nothing saved yet. Posts you read are kept here and can be searched offline."
                typed.isBlank() -> "Search through $savedCount posts saved on this phone, offline. " +
                    "Words can be in any order."
                results.isEmpty() -> "No saved post matches."
                else -> null
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Horizontal padding rather than a narrower list, so a drag in the margins of a wide window scrolls too.
                // The top is the capsule's height: the first row starts under it, and the rows scroll under it.
                contentPadding = PaddingValues(
                    start = LocalReadableInset.current,
                    end = LocalReadableInset.current,
                    top = padding.calculateTopPadding(),
                    bottom = 24.dp
                )
            ) {
                // The glass field, the first row of the list
                item(key = "field") {
                    GlassSearchField(
                        value = typed,
                        onValueChange = {},
                        placeholder = "Search saved posts",
                        trailingIcon = {
                            if (typed.isNotEmpty()) {
                                // A tap target of 48 dp in the field's glass, without a Material icon button.
                                Icon(
                                    FrontrIcons.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button) { }
                                        .padding(12.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }

                if (hint != null) {
                    item(key = "hint") {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                } else if (results.isNotEmpty()) {
                    item(key = "count") {
                        Text(
                            if (results.size == 1) "1 post" else "${results.size} posts",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(results.size, key = { results[it].id }) { index ->
                    PostCard(
                        post = results[index],
                        onClick = {},
                        onOpenLink = {},
                        onDownload = {}
                    )
                }
            }
        }
    }
}
