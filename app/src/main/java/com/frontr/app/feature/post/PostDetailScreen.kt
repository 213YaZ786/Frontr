package com.frontr.app.feature.post

import com.frontr.app.ui.component.ZoneSurface
import androidx.compose.ui.graphics.Color
import com.frontr.app.ui.component.RejectOnFailure
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.plus
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.FloatingTopBar
import com.frontr.app.ui.component.PostCard
import com.frontr.app.core.common.present
import com.frontr.app.core.common.AppError
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frontr.app.core.media.MediaDownloader
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.core.link.LinkRouter
import com.frontr.app.core.model.CommentLine
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostStats
import com.frontr.app.feature.media.MediaViewer
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.ContextLine
import com.frontr.app.ui.component.LinkCardBlock
import com.frontr.app.ui.component.MediaBlock
import com.frontr.app.ui.component.QuoteBlock
import com.frontr.app.ui.component.compactCount
import com.frontr.app.ui.component.contextLine
import com.frontr.app.ui.icon.FrontrIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One post, read in full. Everything comes from the local cache, so opening a
 * post is instant and costs no request. Text is selectable, mentions open the
 * sub in the app, links open the real URL, and sharing uses the reddit.com
 * address so the recipient needs nothing special to open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    id: String,
    from: String?,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    viewModel: PostDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    RejectOnFailure((state.thread as? ThreadState.Failed)?.error)
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id, from) }

    val post = state.post

    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FloatingTopBar(
                title = { Text("Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FrontrIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (post != null) {
                        IconButton(onClick = { share(context, viewModel.shareLink(post)) }) {
                            Icon(FrontrIcons.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                post != null -> ConversationView(
                    contentPadding = padding,
                    post = post,
                    thread = state.thread,
                    loadingMore = state.loadingMore,
                    failedMore = state.failedMore,
                    onLoadMore = viewModel::loadMore,
                    onOpenProfile = onOpenProfile,
                    onOpenPost = onOpenPost,
                    onRetry = viewModel::retryThread,
                    shareLink = viewModel.shareLink(post)
                )
                state.missing -> {
                    val error = (state.thread as? ThreadState.Failed)?.error
                        Text(
                            // The server's own reason when it gave one, a deletion or
                            // a suspension, rather than a guess.
                            (error as? AppError.PostUnavailable)
                                ?.present()?.let { "${it.headline}. ${it.explanation}" }
                                ?: "This post can't be shown. It may have been deleted, and it is not saved on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp)
                        )
                }
                else -> LoadingMark(Modifier.align(Alignment.Center), size = 96.dp)
            }
        }
    }
}

/**
 * The post in the middle of its conversation: what it answers above, then
 * the post itself in full, the author's own thread, and the comments as a
 * tree, each answer set in under the comment it answers.
 */
@Composable
private fun ConversationView(
    contentPadding: PaddingValues,
    post: Post,
    thread: ThreadState,
    loadingMore: Set<String>,
    failedMore: Set<String>,
    onLoadMore: (CommentLine.More) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onRetry: () -> Unit,
    shareLink: String
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val downloader: MediaDownloader = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    var viewing by remember { mutableStateOf<Pair<Post, Int>?>(null) }
    val conversation = (thread as? ThreadState.Ready)?.conversation

    viewing?.let { (shown, index) ->
        MediaViewer(
            postId = shown.id,
            media = shown.media,
            startIndex = index,
            onDownload = { downloader.download(it, shown.authorHandle) },
            onDismiss = { viewing = null }
        )
    }

    @Composable
    fun ReplyCard(item: Post, onClick: () -> Unit = { onOpenPost(item) }, modifier: Modifier = Modifier) {
        PostCard(
            post = item,
            onClick = onClick,
            onOpenLink = { uriHandler.openUri(it) },
            onDownload = { downloader.download(it, item.authorHandle) },
            showStats = settings.showCounts,
            onOpenMedia = { index -> viewing = item to index },
            modifier = modifier
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Horizontal padding rather than a narrower list, so a drag in the
        // margins of a wide window scrolls too.
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current
        ).plus(contentPadding)
    ) {
        val ancestors = conversation?.ancestors.orEmpty()
        if (ancestors.isNotEmpty()) {
            item(key = "earlier") { SectionLabel("Earlier in the conversation") }
            items(ancestors, key = { "a" + it.id }) { ReplyCard(it) }
        }

        item(key = "main") {
            PostBody(
                post = post,
                showCounts = settings.showCounts,
                onOpenProfile = onOpenProfile,
                onOpenMedia = { index -> viewing = post to index },
                shareLink = shareLink
            )
        }

        val continuation = conversation?.continuation.orEmpty()
        if (continuation.isNotEmpty()) {
            item(key = "threadlabel") { SectionLabel("Thread") }
            items(continuation, key = { "t" + it.id }) { ReplyCard(it) }
        }

        item(key = "replieslabel") { SectionLabel("Replies") }

        when (thread) {
            ThreadState.Loading -> item(key = "loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingMark(size = 22.dp)
                    Text("Loading replies", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is ThreadState.Failed -> item(key = "failed") {
                val presentation = thread.error.present()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // A saved post that is gone from Reddit: say so plainly.
                    // There are no replies to fetch and retrying changes nothing.
                    val gone = thread.error as? AppError.PostUnavailable
                    Text(
                        if (gone != null) "No longer available on Reddit" else "Comments couldn't be loaded",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (gone != null) presentation.explanation else presentation.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (gone == null) {
                        BoldButton(onClick = onRetry) { Text("Try again") }
                    }
                }
            }

            is ThreadState.Ready -> {
                val lines = thread.conversation.comments
                if (lines.isEmpty()) {
                    item(key = "noreplies") {
                        Text(
                            "No replies yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                }
                lines.forEach { line ->
                    // Answers sit in under what they answer, a few levels deep
                    // at most, so a long exchange still has room to read.
                    val indent = Modifier.padding(start = (INDENT_STEP * line.depth.coerceAtMost(MAX_INDENT)).dp)
                    when (line) {
                        is CommentLine.Reply -> item(key = "c" + line.post.id) {
                            // A comment has no page of its own in Frontr: a tap
                            // opens nothing, its links and pictures still do.
                            ReplyCard(line.post, onClick = {}, modifier = indent)
                        }
                        is CommentLine.More -> item(key = "m" + line.cursor) {
                            MoreComments(
                                more = line,
                                loading = line.cursor in loadingMore,
                                failed = line.cursor in failedMore,
                                onLoad = { onLoadMore(line) },
                                onOpenOnReddit = { LinkRouter.openOutside(context, post.permalink) },
                                modifier = indent
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where Reddit left comments out of the page. The rest of the comments load
 * by themselves as the reader reaches the end, the rest of a thread when
 * the reader asks for it.
 */
@Composable
private fun MoreComments(
    more: CommentLine.More,
    loading: Boolean,
    failed: Boolean,
    onLoad: () -> Unit,
    onOpenOnReddit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (more.topLevel && !failed) LaunchedEffect(more.cursor) { onLoad() }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (more.topLevel) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            failed -> {
                QuietButton(onClick = onLoad) { Text("Try again") }
                if (more.topLevel) BoldButton(onClick = onOpenOnReddit) { Text("See the rest on Reddit") }
            }
            loading || more.topLevel -> {
                LoadingMark(size = 22.dp)
                Text(
                    if (more.topLevel) "Loading more comments" else "Loading replies",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> QuietButton(onClick = onLoad) { Text("More replies") }
        }
    }
}

private const val INDENT_STEP = 14
private const val MAX_INDENT = 5

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostBody(
    post: Post,
    showCounts: Boolean,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (Int) -> Unit,
    shareLink: String
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val downloader: MediaDownloader = koinInject()

    // The same zone a post gets in a list, so the opened post reads as the
    // same object, only larger.
    ZoneSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        post.contextLine()?.let { ContextLine(it, icon = if (post.isPinned) FrontrIcons.Pin else null) }

        Surface(
            onClick = { onOpenProfile(post.authorHandle) },
            shape = RoundedCornerShape(16.dp),
            // See through: this is a tap target on the zone, not a second
            // card inside the first one.
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Avatar(url = post.avatarUrl, name = post.authorName, size = 48.dp, sharedKey = post.id)
                Column {
                    Text(
                        post.authorName.ifBlank { "r/${post.authorHandle}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    post.relatedHandle?.let { author ->
                        Text(
                            "u/$author",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (post.text.isNotBlank()) {
            val linkColor = MaterialTheme.colorScheme.primary
            val annotated = remember(post.id, linkColor) {
                linkify(post.text, post.links, linkColor, onOpenProfile)
            }
            SelectionContainer {
                Text(
                    annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp)
                )
            }
        }

        if (post.media.isNotEmpty()) {
            MediaBlock(
                post = post,
                onDownload = { downloader.download(it, post.authorHandle) },
                onOpen = onOpenMedia
            )
        }

        post.card?.let { card ->
            LinkCardBlock(card = card, onClick = { card.url?.let(uriHandler::openUri) })
        }

        post.quoted?.let { quote ->
            QuoteBlock(
                handle = quote.handle,
                name = quote.name,
                text = quote.text,
                onClick = { uriHandler.openUri(quote.permalink) }
            )
        }

        fullDate(post.publishedAtMillis)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        post.stats?.takeIf { showCounts }?.let { stats ->
            HorizontalDivider()
            StatsLine(stats)
            HorizontalDivider()
        }

        val url = post.permalink
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Straight to the Reddit app or a browser. Through the app's own link handler
            // this would land back on this screen.
            BoldButton(onClick = { LinkRouter.openOutside(context, url) }, filled = true) { Text("Open on Reddit") }
            // Share and copy hand out the post's reddit.com address.
            BoldButton(onClick = { share(context, shareLink) }) { Text("Share") }
            BoldButton(onClick = { copy(context, shareLink) }) { Text("Copy link") }
        }
    }
    }
}

/** Every count the source gave, spelled out. Counts it did not give are not guessed. */
@Composable
private fun StatsLine(stats: PostStats) {
    val parts = listOfNotNull(
        stats.replies?.let { "${compactCount(it)} replies" },
        stats.reposts?.let { "${compactCount(it)} reposts" },
        stats.quotes?.let { "${compactCount(it)} quotes" },
        stats.likes?.let { "${compactCount(it)} likes" }
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   "),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun fullDate(millis: Long): String? {
    if (millis <= 0L) return null
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Android 13 and later confirm the copy themselves, so no toast is added. */
private fun copy(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Post link", url))
}
