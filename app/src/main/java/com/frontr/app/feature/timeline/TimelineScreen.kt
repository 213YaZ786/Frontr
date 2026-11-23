package com.frontr.app.feature.timeline

import com.frontr.app.data.marks.PostMarks
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.frontr.app.data.accounts.AccountStore
import com.frontr.app.ui.component.AppBarWithSearch
import com.frontr.app.ui.component.BannerAction
import com.frontr.app.ui.component.CapsuleMenuOffset
import com.frontr.app.ui.component.DropdownMenuGroup
import com.frontr.app.ui.component.FailureDialog
import com.frontr.app.ui.component.FolderSwitch
import com.frontr.app.ui.component.GlassMenu
import com.frontr.app.ui.component.PullRevealBox
import com.frontr.app.ui.component.ScrollUpButton
import com.frontr.app.ui.component.SearchCapsuleHint
import com.frontr.app.ui.component.SelectableDropdownMenuItem
import androidx.compose.ui.graphics.Color
import com.frontr.app.ui.glass.rememberGlassBackdrop
import com.frontr.app.ui.glass.glassSource
import com.frontr.app.ui.glass.LocalGlassBackdrop
import com.frontr.app.ui.glass.LocalGlass
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.ui.component.rememberRefreshHaptics
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.frontr.app.ui.component.LoadingMark
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.border
import com.frontr.app.ui.component.EmptyZone
import com.frontr.app.ui.component.boldBorder
import com.frontr.app.ui.component.BoldIconButton
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.LocalDockPadding
import com.frontr.app.ui.component.LocalInlinePlaying
import com.frontr.app.ui.component.rememberInlineTarget
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frontr.app.data.read.ReadMarks
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.core.model.Post
import com.frontr.app.feature.media.MediaViewer
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.relativeTime
import com.frontr.app.ui.icon.FrontrIcons
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Home: everything you follow in one stream, newest first.
 *
 * Pull down to refresh. A post that arrived since the last visit wears a
 * dot until you have scrolled past it. When an
 * account fails to load, a warning in the title says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenAccounts: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: TimelineViewModel = koinViewModel()
) {
    val loaded by viewModel.state.collectAsState()
    // Liked and Archived are views picked from Home's folder menu, not
    // folders: their posts come from PostMarks, and archived posts leave the
    // other views.
    val postMarks: PostMarks = koinInject()
    val marked by postMarks.marks.collectAsState()
    var special by rememberSaveable { mutableStateOf<String?>(null) }
    val state = remember(loaded, marked, special) {
        when (special) {
            LIKED -> loaded.copy(posts = marked.liked.sortedByDescending { it.publishedAtMillis }, folder = LIKED, canLoadMore = false)
            ARCHIVED -> loaded.copy(posts = marked.archived.sortedByDescending { it.publishedAtMillis }, folder = ARCHIVED, canLoadMore = false)
            else -> if (marked.archived.isEmpty()) loaded else loaded.copy(posts = loaded.posts.filterNot { marked.isArchived(it.id) })
        }
    }
    // The threshold is felt in the indicator itself; this answers the end of
    // a refresh the reader pulled: done, or a refusal when nothing came.
    val askedRefresh = rememberRefreshHaptics(
        refreshing = state.loading,
        failed = state.errors.isNotEmpty() && (state.posts.isEmpty() || state.errors.size >= state.followedCount)
    )
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }

    viewing?.let { (post, index) ->
        MediaViewer(
            postId = post.id,
            media = post.media,
            startIndex = index,
            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
            onDismiss = { viewing = null }
        )
    }


    var showFailures by remember { mutableStateOf(false) }
    // The failure message names the network and where the fault likely lies. Frontr has no silent downloads state to add
    // to it, so silentDownloads stays false.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    // not ported: TimelineScreen's ChallengePill, solvableChallenge, verify and onOpenDiagnostics (X's challenge only)
    if (showFailures) {
        FailureDialog(
            failed = state.errors.values.toList(),
            followed = state.followedCount,
            silentDownloads = false,
            site = "Reddit",
            onRetry = {
                showFailures = false
                viewModel.refresh()
            },
            onDismiss = { showFailures = false }
        )
    }

    // The feed choice, in the capsule's start: the folder switch (folder icon, the feed's name and a chevron) opens a menu
    // under it: All and the folders, then Liked and Archived, the shown one checked (Material 3 Expressive's grouped menu
    // in glass, GlassMenu). Picking does what the folder dialog did.
    // The capsule is an item of the list that records listBackdrop: it is drawn with no backdrop (AppBarWithSearch), and
    // the menu lives in a window of its own.
    var feedsOpen by remember { mutableStateOf(false) }
    val showFeed: (String?) -> Unit = { name ->
        feedsOpen = false
        // Liked and Archived are views of the marks, not folders. Any other name is a folder that exists already.
        if (name == LIKED || name == ARCHIVED) {
            special = name
        } else {
            special = null
            viewModel.showFolder(name)
        }
    }
    // Each folder row with its count of subreddits (Gmail's labels), from the followed list.
    // not ported: TimelineScreen's feedPill and LocalBannerProbe (the capsule's folder switch takes its place everywhere,
    // nobody followed included, as the draft shows), NavigationDrawerItem (SelectableDropdownMenuItem carries its count)
    val followed by koinInject<AccountStore>().accounts.collectAsState()
    val feedMenu: @Composable () -> Unit = {
        // contentOffset: the menu opens 8 dp under the capsule, not on its edge (requests point 18; MTGA has none).
        GlassMenu(expanded = feedsOpen, onDismissRequest = { feedsOpen = false }, contentOffset = CapsuleMenuOffset) {
            // No Clear row: a feed is chosen, not cleared.
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = state.folder == null, onClick = { showFeed(null) }, text = "All", count = followed.size)
                state.folders.forEach { name ->
                    SelectableDropdownMenuItem(
                        selected = state.folder == name,
                        onClick = { showFeed(name) },
                        text = name,
                        count = followed.count { it.folder == name }
                    )
                }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                listOf(LIKED, ARCHIVED).forEach { name ->
                    SelectableDropdownMenuItem(selected = state.folder == name, onClick = { showFeed(name) }, text = name)
                }
            }
        }
    }
    // The capsule's navigation icon: the folder switch with the same menu, one composition.
    val feedSwitch: @Composable () -> Unit = {
        Box(Modifier.semantics { heading(); contentDescription = "Home" }) {
            Box {
                FolderSwitch(
                    icon = FrontrIcons.Folder,
                    trailingIcon = FrontrIcons.ExpandMore,
                    label = state.folder ?: "All",
                    expanded = feedsOpen,
                    onClick = { feedsOpen = true }
                )
                feedMenu()
            }
        }
    }

    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    // The alert pops in and out (scale and fade) as the failures come and go.
    // not ported: AnimatedVisibilityWithBooleanVisibleParamNoReceiver's Box, its fixed size, background and the toggle
    // button around the visible flag
    val alert: (@Composable () -> Unit) = {
        AnimatedVisibility(
            visible = state.errors.isNotEmpty(),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            BannerAction(
                icon = FrontrIcons.Warning,
                label = "What went wrong",
                onClick = { showFailures = true },
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    val banner: @Composable () -> Unit = {
        // One glass capsule, nothing under it: the folder switch, "Search", the alert on a failure only. Nobody followed:
        // the same capsule, the folder switch "All" and its menu (requests point 3).
        // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
        // not ported: AppBarWithSearch itself (the kit's AppBarWithSearch, ui/component/FeedBanner.kt, is called as it is)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AppBarWithSearch(
                inputField = { SearchCapsuleHint(FrontrIcons.Search, listOf("Search"), onOpenSearch) },
                navigationIcon = feedSwitch,
                actions = { alert() }
            )
        }
    }

    // A post is read once it has gone past the top of the screen. Everything
    // before the first visible row has, by definition, and one pass of the
    // list costs one write rather than one per card.
    val marks: ReadMarks = koinInject()
    val readIds by marks.read.collectAsState()
    val marksReady by marks.readyState.collectAsState()

    // No top bar at all. A bar that folds away and comes back still owns a
    // band of the screen the whole time it is on it, which on a phone is
    // several lines of a post. The title and its two actions are the first
    // zone of the list instead: they scroll away with everything else, one
    // flick or the back to top button brings them back, and the reading area
    // is the whole window.
    val pull = rememberPullToRefreshState()
    // In glass, the list is recorded as it scrolls, for the controls that
    // float over it (the refresh disc, the folder and back to top buttons)
    // to bend it.
    val look = LocalGlass.current
    val listBackdrop = rememberGlassBackdrop()
    // The pull's ring fills with the subreddits the refresh has finished; the count shows once it is known.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
    val refreshProgress: () -> Float = { state.refreshFill }
    val refreshCount = if (state.refreshTotal > 0) state.refreshDone to state.refreshTotal else null
    // The page's ground is painted under the whole app, see MainActivity.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        CompositionLocalProvider(LocalGlassBackdrop provides listBackdrop.takeIf { look != null }) {
        // The check pill lives outside the when, so it is on screen whether
        // Home is empty, failed or full, and whatever the scroll position.
        Box(Modifier.fillMaxSize()) {
        when {
            state.followedCount == 0 -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = LocalReadableInset.current)
            ) {
                banner()
                // The weight gives it the rest of the screen, so it sits in the middle of what is left under the
                // banner rather than against it.
                EmptyState(
                    title = "Nothing followed yet",
                    message = "Follow a few subreddits and they will all appear here, on one front page.",
                    actionLabel = "Open Subreddits",
                    onAction = onOpenAccounts,
                    modifier = Modifier.weight(1f)
                )
            }

            // Pull works here too, so a failed refresh at launch does not
            // leave Home stuck until a restart.
            // The list pulled down uncovers the mark, under the status bar, where the list starts.
            state.isEmpty && state.errors.isNotEmpty() -> PullRevealBox(
                isRefreshing = state.loading,
                onRefresh = {
                    askedRefresh()
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
                state = pull,
                progress = refreshProgress,
                count = refreshCount,
                countLabel = "subreddits",
                lastUpdated = subtitle(state)
            ) {
                // A list, because the pull gesture needs something scrollable.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        top = padding.calculateTopPadding(),
                        bottom = LocalDockPadding.current
                    )
                ) {
                    // The banner stays, so a folder whose subreddits all failed
                    // is not a dead end: the reader can still switch away.
                    item(key = "home-header") { banner() }
                    item(key = "nothing") {
                        EmptyState(
                            title = "Nothing could be loaded",
                            message = "None of your subreddits could be loaded.",
                            actionLabel = "Try again",
                            onAction = { viewModel.refresh() },
                            modifier = Modifier.fillParentMaxHeight(0.8f)
                        )
                    }
                }
            }

            else -> PullRevealBox(
                isRefreshing = state.loading,
                onRefresh = {
                    askedRefresh()
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
                state = pull,
                progress = refreshProgress,
                count = refreshCount,
                countLabel = "subreddits",
                lastUpdated = subtitle(state)
            ) {
                // Prefetch a page before the reader actually hits the bottom,
                // so scrolling stays continuous instead of stalling.
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
                    }
                }
                LaunchedEffect(shouldLoadMore, state.canLoadMore, state.pagingFailed) {
                    if (shouldLoadMore) viewModel.loadMore()
                }

                val inline = rememberInlineTarget(
                    listState = listState,
                    posts = state.posts,
                    keyOf = { it.id },
                    paused = viewing != null
                )
                CompositionLocalProvider(LocalInlinePlaying provides inline) {
                // The Home zone is the one row drawn above the posts, so an
                // index in the list turns back into an index in the posts by
                // subtracting it.
                val headerCount = 1

                LaunchedEffect(listState, state.posts, headerCount) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { first ->
                            val past = first - headerCount
                            if (past > 0) {
                                marks.markRead(state.posts.take(past).map { it.id })
                            }
                        }
                }
                LazyColumn(
                    state = listState,
                    // Horizontal padding rather than a narrower list, so a
                    // drag in the margins of a wide window scrolls too.
                    // The list starts under the status bar and scrolls beneath it.
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current,
                        end = LocalReadableInset.current,
                        top = padding.calculateTopPadding(),
                        bottom = LocalDockPadding.current
                    ),
                    modifier = Modifier.fillMaxSize().then(if (look != null) Modifier.glassSource(listBackdrop, look) else Modifier)
                ) {
                    item(key = "home-header") { banner() }

                    if (state.posts.isEmpty() && !state.loading && state.folder != null) {
                        item(key = "folder-empty") {
                            EmptyState(
                                title = "Nothing in ${state.folder} yet",
                                message = "File subreddits here from Subreddits, or show every subreddit.",
                                actionLabel = "Show every subreddit",
                                onAction = { viewModel.showFolder(null) },
                                // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
                                modifier = Modifier.fillParentMaxHeight(0.7f).animateItem()
                            )
                        }
                    }

                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            // Unknown until the file is read, and unknown
                            // means read: a border flashed on every card for
                            // one frame at launch would be worse than none.
                            unread = marksReady && post.id !in readIds,
                            onClick = { onOpenPost(post) },
                            onOpenLink = { uriHandler.openUri(it) },
                            onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                            showStats = settings.showCounts,
                            onOpenMedia = { index -> viewing = post to index },
                            // Posts arrive and merge while the list is on
                            // screen. Animating placement means a card slides
                            // into its row instead of teleporting.
                            modifier = Modifier.animateItem()
                        )
                    }

                    item(key = "footer") {
                        TimelineFooter(
                            state = state,
                            onLoadMore = { viewModel.loadMore(manual = true) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                }

                // The way back to the top shows only once it is a real chore.
                // The feed is chosen from the banner now, so nothing stacks with it.
                // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
                val showBackToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex >= BACK_TO_TOP_AFTER }
                }
                ScrollUpButton(
                    visible = showBackToTop,
                    icon = FrontrIcons.ArrowUp,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp + LocalReadableInset.current, bottom = LocalDockPadding.current + 16.dp)
                )
            }
        }
        }
        }
    }
}

// The age the pull shows in its ring before release (PullRevealBox's lastUpdated, "Updated" over "3m ago"); null while
// loading or when nothing was read yet. Was the banner's subtitle, now in the pull.
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/timeline/TimelineScreen.kt#TimelineScreen
private fun subtitle(state: TimelineUiState): String? = when {
    state.followedCount == 0 -> null
    state.loading -> null
    state.lastUpdatedMillis != null -> relativeTime(state.lastUpdatedMillis)
    else -> null
}

private enum class FooterKind { Loading, Failed, More, End }

// The footer's states (mark, failure, button, text) cross through nothing, as the draft shows (no Crossfade: it makes the
// height jump): animateContentSize is the one animator of the height, so AnimatedContent's own size change snaps. The
// footer is an item of the list: LazyItemScope.animateItem places it.
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineFooter
// https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
// not ported: FooterKind's doc and the TimelineScreen (the screen is this file's own, only the footer's states are taken)
// not ported: simpleAnimatedContentSample (the sample's function; its AnimatedContent is used here, inline)
// not ported: foo, bar, baz (the sample's own demo functions)
@Composable
private fun TimelineFooter(state: TimelineUiState, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    val kind = when {
        state.loadingMore -> FooterKind.Loading
        state.pagingFailed -> FooterKind.Failed
        state.canLoadMore -> FooterKind.More
        else -> FooterKind.End
    }
    Box(
        modifier = modifier.fillMaxWidth().animateContentSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "timelineFooter"
        ) { shown ->
            when (shown) {
                FooterKind.Loading -> LoadingMark(size = 28.dp)
                FooterKind.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Couldn't load older posts. Reddit is busy, " +
                            "which usually clears in a minute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BoldButton(onClick = onLoadMore) { Text("Try again") }
                }
                FooterKind.More -> BoldButton(onClick = onLoadMore) { Text("Load older posts") }
                FooterKind.End -> Text(
                    "No older posts available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5

/** Posts scrolled past before the way back becomes worth a button. */
private const val BACK_TO_TOP_AFTER = 5

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A zone centred in the space, like every empty screen, so an empty
    // Home still carries the wallpaper's colour.
    EmptyZone(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier.fillMaxSize()
    )
}

private const val LIKED = "Liked"
private const val ARCHIVED = "Archived"
