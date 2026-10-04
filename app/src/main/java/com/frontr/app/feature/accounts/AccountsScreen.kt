package com.frontr.app.feature.accounts

import com.frontr.app.ui.component.GlassSearchField
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.core.link.LinkCleaner
import com.frontr.app.ui.component.CleanLinkEffect
import com.frontr.app.ui.component.rememberHaptics
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import com.frontr.app.core.link.PastedText
import android.widget.Toast
import android.content.ClipboardManager
import com.frontr.app.ui.component.BannerAction
import com.frontr.app.ui.component.ScreenBanner
import com.frontr.app.ui.component.EmptyZone
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.FolderDialog
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.AssistChip
import com.frontr.app.ui.component.LocalDockPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.relativeTime
import com.frontr.app.ui.icon.FrontrIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Subreddits and search merged. One pill search bar on top: it filters the
 * subreddits you follow, and when the text is a sub name or a link, a card
 * offers to open that sub or follow it. Reddit has no search for logged out
 * readers that Frontr could use, so a sub is typed or pasted.
 * Typing never follows anyone.
 * Unfollowing happens on the profile, which keeps it away from a stray tap.
 */
@Composable
fun AccountsScreen(
    onOpenFeed: (String) -> Unit,
    onOpenFolders: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var filing by remember { mutableStateOf<AccountRow?>(null) }

    filing?.let { row ->
        FolderDialog(
            title = "File r/${row.handle}",
            folders = folders,
            selected = row.folder,
            everything = null,
            onSelect = { name ->
                viewModel.setFolder(row.handle, name.orEmpty())
                filing = null
            },
            onDismiss = { filing = null }
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val haptics = rememberHaptics()


    // Coming back from a profile may have brought new posts or an avatar.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val candidate = AccountsViewModel.asHandle(query)
    // A pasted link filters by the handle it names, so a link to an account
    // already followed finds that account instead of matching nothing.
    val trimmed = if ('/' in query) candidate.orEmpty() else query.trim().removePrefix("@")
    val alreadyFollowed = candidate != null &&
        rows.any { it.handle.equals(candidate, ignoreCase = true) }
    val visible = if (trimmed.isEmpty()) {
        rows
    } else {
        rows.filter {
            it.handle.contains(trimmed, ignoreCase = true) ||
                it.name?.contains(trimmed, ignoreCase = true) == true
        }
    }

    fun open(handle: String) {
        focus.clearFocus()
        onOpenFeed(handle)
    }

    // A detour link in the field, from a search engine or another site,
    // becomes the address it stands for; a pasted one then opens like any
    // other paste.
    var openWhenClean by remember { mutableStateOf(false) }
    CleanLinkEffect(
        text = query,
        onClean = { clean ->
            query = clean
            if (openWhenClean) {
                openWhenClean = false
                AccountsViewModel.asHandle(clean)?.let { open(it) }
            }
        },
        onUnreadable = {
            openWhenClean = false
            haptics.reject()
            Toast.makeText(context, "Google did not say where this link leads. Open it and copy the page address.", Toast.LENGTH_SHORT).show()
        }
    )

    /**
     * One tap: read the clipboard, put what it holds in the field, and open
     * the profile it names. A shared sentence goes through PastedText first,
     * so the link inside it is what lands in the field.
     */
    fun paste() {
        val clip = context.getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        val text = PastedText.query(clip) { "reddit.com/" in it || "redd.it/" in it }
        if (text.isEmpty()) {
            haptics.reject()
            Toast.makeText(context, "Nothing to paste. Copy a subreddit or a post link first.", Toast.LENGTH_SHORT).show()
            return
        }
        query = text
        if (LinkCleaner.needsResolving(text)) {
            // Opened once Google has said where it leads, see CleanLinkEffect.
            openWhenClean = true
            focus.clearFocus()
            return
        }
        AccountsViewModel.asHandle(text)?.let { open(it) } ?: focus.clearFocus()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The banner and the search field are rows of the list, so the
            // whole screen scrolls, under the status bar too.
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                end = 16.dp + LocalReadableInset.current,
                bottom = 16.dp + LocalDockPadding.current
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "banner") {
                // The screen's name centred in its zone, the folders one tap away in
                // the same zone, as on every screen that opens with a banner.
                Box(Modifier.fullBleed(16.dp)) {
                    ScreenBanner(
                        title = "Subreddits",
                        subtitle = if (rows.isEmpty()) null else "${rows.size} followed",
                        trailing = {
                            BannerAction(
                                icon = FrontrIcons.Folder,
                                label = "Folders",
                                onClick = onOpenFolders
                            )
                        }
                    )
                }
            }
            item(key = "search") {
                GlassSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Type a subreddit or paste a link",
                    leadingIcon = { Icon(FrontrIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(FrontrIcons.Close, contentDescription = "Clear")
                                }
                            }
                            // Always there, and the clipboard is read only on a tap:
                            // reading it to decide whether to show the button would be
                            // a read too, and Android raises its notice on every one.
                            IconButton(onClick = { paste() }) {
                                Icon(FrontrIcons.Paste, contentDescription = "Paste a link")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        when {
                            candidate != null && !alreadyFollowed -> open(candidate)
                            visible.size == 1 -> open(visible.first().handle)
                            else -> focus.clearFocus()
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
            if (candidate != null && !alreadyFollowed) {
                item(key = "candidate") {
                    CandidateCard(
                        handle = candidate,
                        onOpen = { open(candidate) },
                        onFollow = { haptics.done(); viewModel.follow(candidate) }
                    )
                }
            } else if (trimmed.isNotEmpty() && candidate == null && visible.isEmpty()) {
                item(key = "invalid") {
                    Hint("Not a subreddit name. Type it as it appears after r/, like Android, or paste a link.")
                }
            }

            items(visible, key = { it.handle }) { row ->
                AccountCard(
                    row = row,
                    onClick = { open(row.handle) },
                    // Only once the reader has made a folder. With Main alone
                    // the chip would name the one place everything is.
                    onFile = if (folders.size > 1) ({ filing = row }) else null
                )
            }

            if (rows.isEmpty() && trimmed.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.fillParentMaxHeight(0.7f)) }
            }
        }
    }
}

@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit, onFile: (() -> Unit)?) {
    ZoneSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    row.name ?: "r/${row.handle}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The folder is a control, not a label: tapping the card opens
                // the account, tapping this files it.
                if (onFile != null) {
                    AssistChip(
                        onClick = onFile,
                        label = { Text(row.folder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(FrontrIcons.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(handle: String, onOpen: () -> Unit, onFollow: () -> Unit) {
    ZoneSurface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        accent = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Avatar(url = null, name = handle, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "r/$handle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Tap to read the profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            BoldButton(onClick = onFollow, filled = true) { Text("Follow") }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    EmptyZone(
        title = "No subreddits yet",
        message = "Type a subreddit name or paste a link above, then follow to build your front page. " +
            "The list stays on this phone. Only the reads go to Reddit.",
        icon = FrontrIcons.Person,
        modifier = modifier
    )
}

/**
 * Undoes the list's side padding for a row that brings its own, the banner,
 * so it keeps the width it has on every other screen.
 */
private fun Modifier.fullBleed(side: Dp) = layout { measurable, constraints ->
    val extra = side.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
