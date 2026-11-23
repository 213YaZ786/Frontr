package com.frontr.app.feature.search

import com.frontr.app.ui.component.GlassSearchField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.frontr.app.ui.component.BarTitle
import com.frontr.app.ui.component.FloatingTopBar
import androidx.compose.material3.TopAppBarDefaults
import com.frontr.app.ui.component.plus
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.core.model.Post
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.feature.media.MediaViewer
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.icon.FrontrIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Search through every post saved on this phone, instantly and offline.
 *
 * Port: https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/search/SearchScreen.kt#SearchScreen
 * LinkedOut's ScreenBanner row is Frontr's FloatingTopBar here (as PostDetailScreen and SavedMediaScreen);
 * not ported: BarAction (LinkedOut's own round action, it bans IconButton; Frontr keeps IconButton in the bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val typed by viewModel.typed.collectAsState()
    val uriHandler = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    viewing?.let { (post, index) ->
        MediaViewer(
            postId = post.id,
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
            onDismiss = { viewing = null }
        )
    }

    // The bar folds away going down and returns going up, the list full screen.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val hint = when {
            !state.loaded -> null
            state.savedCount == 0 -> "Nothing saved yet. Posts you read are kept here and can be searched offline."
            typed.isBlank() -> "Search through ${state.savedCount} posts saved on this phone, offline. " +
                "Words can be in any order."
            state.results.isEmpty() -> "No saved post matches."
            else -> null
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Horizontal padding rather than a narrower list, so a drag in
            // the margins of a wide window scrolls too.
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current
            ).plus(padding)
        ) {
            // The field is the first row of the list, so the whole page scrolls with it.
            item(key = "field") {
                GlassSearchField(
                    value = typed,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search saved posts",
                    trailingIcon = {
                        // Clear fades and widens in once something is typed.
                        AnimatedVisibility(
                            visible = typed.isNotEmpty(),
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(FrontrIcons.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // The hint, or the count of results, under the field.
            if (hint != null || state.results.isNotEmpty()) {
                item(key = "status") {
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    } else {
                        Text(
                            if (state.results.size == 1) "1 post" else "${state.results.size} posts",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            items(state.results, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = {
                        focus.clearFocus()
                        onOpenPost(post)
                    },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = post to index },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
