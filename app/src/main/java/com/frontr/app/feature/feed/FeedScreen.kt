package com.frontr.app.feature.feed

import com.frontr.app.ui.component.plus
import com.frontr.app.ui.component.BoldButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.frontr.app.ui.component.rememberMediaPolicy
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.core.model.Feed
import com.frontr.app.core.model.MediaItem
import com.frontr.app.core.model.MediaType
import com.frontr.app.core.model.Post
import com.frontr.app.feature.media.MediaViewer
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.ErrorPanel
import com.frontr.app.ui.component.LocalDockPadding
import com.frontr.app.ui.component.LocalInlinePlaying
import com.frontr.app.ui.component.PostCard
import com.frontr.app.ui.component.rememberInlineTarget
import com.frontr.app.ui.component.relativeTime
import com.frontr.app.ui.icon.FrontrIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One account: a centred profile header, then its posts.
 *
 * The header is always there, even before anything loaded, so you can follow
 * an account whose feed is momentarily unreachable. The top bar stays quiet
 * and only shows the name once the header has scrolled away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    handle: String,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
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
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    viewing?.let { (postId, media, index) ->
        MediaViewer(
            postId = postId,
            media = media,
            startIndex = index,
            onDownload = { downloader.download(it, handle) },
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

    // The bar folds away as the reader goes down and comes back on the first
    // upward flick, which returns a band of screen on a phone without hiding
    // the way back. enterAlways rather than a pinned bar, because this list is
    // long and the bar is not needed while reading.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (headerGone) {
                        Text(name ?: "r/$handle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(FrontrIcons.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        val inline = rememberInlineTarget(
            listState = listState,
            posts = shown,
            keyOf = { it.id },
            paused = viewing != null
        )
        CompositionLocalProvider(LocalInlinePlaying provides inline) {
        LazyColumn(
            state = listState,
            // Horizontal padding rather than a narrower list, so a drag in
            // the margins of a wide window scrolls too.
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                bottom = LocalDockPadding.current
            ).plus(padding),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                ProfileHeader(
                    handle = handle,
                    name = name,
                    feed = feed,
                    isFollowing = isFollowing,
                    onToggleFollow = viewModel::toggleFollow,
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
                    }
                )
            }

            state.error?.let {
                item(key = "error") {
                    ErrorPanel(
                        modifier = Modifier.padding(16.dp),
                        error = it,
                        onRetry = viewModel::refresh,
                        onOpenLog = onOpenLog
                    )
                }
            }

            if (shown.isEmpty()) {
                if (feed == null && state.loading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                return@LazyColumn
            }

            items(shown, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = { onOpenPost(post) },
                    onOpenLink = { uriHandler.openUri(it) },
                    onDownload = { downloader.download(it, post.authorHandle) },
                    showStats = settings.showCounts,
                    onOpenMedia = { index -> viewing = Triple(post.id, post.media, index) },
                    modifier = Modifier.animateItem()
                )
            }

            item(key = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadingMore -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        canMore -> BoldButton(onClick = { viewModel.loadMore(manual = true) }) {
                            Text(if (pagingFailed) "Try again" else "Load older posts")
                        }
                        else -> Text(
                            "No older posts available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ProfileHeader(
    handle: String,
    name: String?,
    feed: Feed?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAvatar: (String) -> Unit
) {
    val hold = rememberMediaPolicy().hold
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // The banner is a picture like any other, so Wi-Fi only holds it too.
        val banner = feed?.bannerUrl
        if (banner != null && !hold) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }

        val avatar = feed?.avatarUrl
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(enabled = avatar != null) { avatar?.let(onOpenAvatar) }
        ) {
            Avatar(url = avatar, name = name ?: handle, size = 88.dp)
        }

        // The sub's name alone: r/Android says all that its id would.
        Text(
            name ?: "r/$handle",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        feed?.bio?.let { bio ->
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp)
            )
        }

        feed?.joined?.let { joined ->
            Text(
                joined,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        feed?.stats?.let { stats ->
            val parts = listOfNotNull(
                stats.posts?.let { "${compactCount(it)} posts" },
                stats.following?.let { "${compactCount(it)} following" },
                stats.followers?.let { "${compactCount(it)} followers" }
            )
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (isFollowing) {
            BoldButton(onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Following")
            }
        } else {
            BoldButton(filled = true, onClick = onToggleFollow, modifier = Modifier.padding(top = 8.dp)) {
                Text("Follow")
            }
        }

        feed?.let {
            Text(
                sourceLine(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** 870, 12,345, 566K, 53.2M: exact while short, rounded once it would not fit. */
private fun compactCount(value: Long): String = when {
    value < 10_000 -> String.format(Locale.getDefault(), "%,d", value)
    value < 1_000_000 -> "${value / 1_000}K"
    else -> {
        val millions = value / 100_000 / 10.0
        String.format(Locale.getDefault(), "%.1fM", millions).replace(".0M", "M").replace(",0M", "M")
    }
}

/** Which server answered and how fresh it is. Frontr always names its source. */
private fun sourceLine(feed: Feed): String {
    val age = relativeTime(feed.fetchedAtMillis)
    val freshness = when {
        age.isEmpty() -> ""
        age == "now" -> ", updated just now"
        else -> ", updated $age ago"
    }
    return "Read via ${feed.fetchedFromHost}$freshness"
}
