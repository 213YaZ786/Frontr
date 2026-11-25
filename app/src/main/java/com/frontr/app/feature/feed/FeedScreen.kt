package com.frontr.app.feature.feed

import com.frontr.app.ui.component.ZoneAlertDialog
import com.frontr.app.ui.component.QuietButton
import androidx.compose.ui.graphics.Color
import com.frontr.app.ui.component.RejectOnFailure
import com.frontr.app.ui.component.rememberHaptics
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.BoldButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.core.model.MediaItem
import com.frontr.app.core.model.MediaType
import com.frontr.app.core.model.Post
import com.frontr.app.feature.media.MediaViewer
import com.frontr.app.ui.component.ErrorPanel
import com.frontr.app.ui.component.LocalDockPadding
import com.frontr.app.ui.component.LocalInlinePlaying
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.PullRevealBox
import com.frontr.app.ui.component.rememberInlineTarget
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One sub: a profile header (ProfileHeader.kt), then its posts.
 *
 * The header is always there, even before anything loaded, so you can follow
 * a sub whose feed is momentarily unreachable. No bar: the pull down
 * refreshes, back is the system's.
 *
 * Port: https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
 * not ported: loadHead, keepPaging, fillGapsShown (MTGA's Nitter source handling; Frontr reads one source per sub)
 * not ported: onOpenDiagnostics, ChallengePill and solvableChallenge, viewModel.verify (X's browser check, not Reddit's)
 * not ported: FeedRepository.total, instances, archives (X sources: no count, the ring runs on its own)
 * not ported: VerifiedType on the header (X only), originalAvatar and bannerFull (X's picture sizes)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberHaptics()
    var confirmUnfollow by remember { mutableStateOf(false) }
    if (confirmUnfollow) {
        ZoneAlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            // Drops touches while another app's window covers the dialog (filterTouchesWhenObscured, the kit's ZoneDialog):
            // https://github.com/super-productivity/super-productivity/blob/master/android/app/src/main/java/com/superproductivity/superproductivity/webview/WebViewBlockActivity.kt#WebViewBlockActivity
            refuseObscuredTaps = true,
            title = { Text("Unfollow?") },
            confirmButton = {
                QuietButton(onClick = {
                    confirmUnfollow = false
                    haptics.done()
                    viewModel.toggleFollow()
                }) { Text("Unfollow") }
            },
            dismissButton = { QuietButton(onClick = { confirmUnfollow = false }) { Text("Cancel") } }
        )
    }
    RejectOnFailure(state.error)
    val followed by viewModel.followed.collectAsState()
    val isFollowing = followed.any { it.handle.equals(handle, ignoreCase = true) }
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = rememberLazyListState()
    // Post id, attachments, start index. The id is what lets the viewer find
    // a saved copy, and it is empty for the avatar, which belongs to no post.
    var viewing by remember { mutableStateOf<Triple<String, List<MediaItem>, Int>?>(null) }

    val feed = state.feed
    val name = feed?.displayName?.takeIf { it.isNotBlank() && it != handle }

    viewing?.let { (postId, media, index) ->
        MediaViewer(
            postId = postId,
            media = media,
            startIndex = index,
            // The viewer holds the post's id only (empty for the avatar, which
            // belongs to no post): its address is looked up in the feed.
            onDownload = {
                downloader.download(it, handle, feed?.posts?.firstOrNull { post -> post.id == postId }?.permalink)
            },
            onDismiss = { viewing = null }
        )
    }

    LaunchedEffect(handle) { viewModel.load(handle) }

    val shown = feed?.posts.orEmpty()
    val canMore = state.canLoadMore
    val pagingFailed = state.pagingFailed
    val loadingMore = state.loadingMore

    val shouldLoadMore by remember(shown.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            shown.isNotEmpty() && last >= shown.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, canMore, pagingFailed) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // No bar on a sub: back is the system's, refresh is the pull down. [onBack] stays for the navigation host.
    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val readable = LocalReadableInset.current
        val inline = rememberInlineTarget(
            listState = listState,
            posts = shown,
            keyOf = { it.id },
            paused = viewing != null
        )
        // Every load (the first one, a retry, the pull) shows the app's mark falling over the list, nothing counted.
        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen
        // not ported: MTGA's ChallengePill (X only)
        PullRevealBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
        CompositionLocalProvider(LocalInlinePlaying provides inline) {
        LazyColumn(
            state = listState,
            // The header starts at the window's top, its banner under the status bar, so the top inset is not padded.
            // The readable inset is on the rows, not the list, so a drag in the margins of a wide window scrolls too
            // and the banner stays edge to edge.
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                bottom = LocalDockPadding.current + padding.calculateBottomPadding()
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                ProfileHeader(
                    handle = handle,
                    name = name,
                    feed = feed,
                    isFollowing = isFollowing,
                    onToggleFollow = {
                        // Following answers at once; unfollowing asks first, a stray
                        // tap on "Following" must not drop a sub.
                        if (isFollowing) {
                            confirmUnfollow = true
                        } else {
                            haptics.done()
                            viewModel.toggleFollow()
                        }
                    },
                    onOpenAvatar = { small ->
                        viewing = Triple(
                            "",
                            listOf(
                                MediaItem(
                                    previewUrl = small,
                                    downloadUrl = small,
                                    type = MediaType.PHOTO
                                )
                            ),
                            0
                        )
                    },
                    // The viewer gets the banner as Reddit serves it.
                    onOpenBanner = { small ->
                        viewing = Triple(
                            "",
                            listOf(
                                MediaItem(
                                    previewUrl = small,
                                    downloadUrl = small,
                                    type = MediaType.PHOTO
                                )
                            ),
                            0
                        )
                    }
                )
            }

            state.error?.let {
                item(key = "error") {
                    ErrorPanel(
                        modifier = Modifier.padding(horizontal = readable).padding(16.dp),
                        error = it,
                        onRetry = viewModel::refresh
                    )
                }
            }

            // The list's own 96 dp mark is gone: the pull's ring above the header shows the load.
            if (shown.isEmpty()) return@LazyColumn

            items(shown, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = { onOpenPost(post) },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle, post.permalink) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = Triple(post.id, post.media, index) },
                    modifier = Modifier.padding(horizontal = readable).animateItem()
                )
            }

            item(key = "footer") {
                // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#FeedScreen
                // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
                // The footer's states (mark, button, text) cross through nothing, as the draft shows (no Crossfade: it makes the
                // height jump): animateContentSize is the one animator of the height, so AnimatedContent's own size change snaps.
                // not ported: FeedScreen (the screen is this file's own, only the footer is taken)
                // not ported: simpleAnimatedContentSample (the sample's function; its AnimatedContent is used here, inline)
                // not ported: foo, bar, baz (the sample's own demo functions)
                val footer = when {
                    loadingMore -> FeedFooter.Loading
                    canMore -> if (pagingFailed) FeedFooter.Retry else FeedFooter.More
                    else -> FeedFooter.End
                }
                Box(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = readable).animateContentSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = footer,
                        transitionSpec = {
                            (fadeIn(tween(210, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        },
                        label = "feedFooter"
                    ) { shown ->
                        when (shown) {
                            FeedFooter.Loading -> LoadingMark(size = 28.dp)
                            FeedFooter.Retry, FeedFooter.More -> BoldButton(onClick = { viewModel.loadMore(manual = true) }) {
                                Text(if (shown == FeedFooter.Retry) "Try again" else "Load older posts")
                            }
                            FeedFooter.End -> Text(
                                "No older posts available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        }
        }
        }
    }
}

// The footer's states, as LinkedOut's FeedScreen footer (its FeedFooter enum, local to that file).
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#FeedScreen
private enum class FeedFooter { Loading, Retry, More, End }
