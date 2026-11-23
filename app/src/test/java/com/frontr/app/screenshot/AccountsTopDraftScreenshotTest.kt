package com.frontr.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.di.appModule
import com.frontr.app.feature.accounts.AccountRow
import com.frontr.app.feature.accounts.FeedChoiceChips
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.ui.component.AppBarWithSearch
import com.frontr.app.ui.component.Avatar
import com.frontr.app.ui.component.BoldIconButton
import com.frontr.app.ui.component.CapsuleMenuOffset
import com.frontr.app.ui.component.DropdownMenuGroup
import com.frontr.app.ui.component.DropdownMenuItem
import com.frontr.app.ui.component.EmptyZone
import com.frontr.app.ui.component.FolderSwitch
import com.frontr.app.ui.component.SearchCapsuleField
import com.frontr.app.ui.component.ZoneSurface
import com.frontr.app.ui.component.relativeTime
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
 * DRAFT for the user's yes (requests.md points 2 and 3), nothing of app/src/main changes: Subreddits' top as MTGA's
 * validated one, on Frontr's own kit. The capsule of Home with the folder switch leading, the field inside it ("Search 12
 * subreddits or paste a link", shorter at 200 % and narrow: "Search 12 subreddits"), Clear and Paste inside it as
 * BoldIconButton, the subreddit rows below.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * Pictures: accounts_top_draft, accounts_typed_draft (a word typed: Clear appears beside Paste), accounts_menu_draft (the
 * folder menu open, ending with the group "Manage folders"), accounts_empty_draft (nobody followed: the field alone at the
 * very top, no folder to switch); each with its 200 % text and 360 dp edges. The rows are a local copy of AccountsScreen's
 * AccountCard (private there), its corners Material's ExtraLarge.
 * not ported: TopArrangementDraftScreenshotTest's B variant, LargeTitleTop, TodayAccounts (today's top is told by
 * requests.md point 2), GlassSearchField and its Field (the field is the capsule's own), CountedMenuItem (the kit's
 * SelectableDropdownMenuItem carries the count)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AccountsTopDraftScreenshotTest : ScreenshotBase() {

    // Avatar and the cards read the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private val now = FIXED_NOW

    private val rows = listOf(
        AccountRow("android", "Android", null, now - 2 * 60 * 60 * 1000, "Tech"),
        AccountRow("grapheneos", "GrapheneOS", null, now - 26 * 60 * 60 * 1000, "Tech"),
        AccountRow("worldnews", "International news and politics", null, now - 3 * 24 * 60 * 60 * 1000, "News"),
        AccountRow("linux", null, null, null, "Fun")
    )

    @Test
    fun accountsA2() = capture("accounts_top_draft") { AccountsScreen { AccountsCapsule("All", "", onSwitch = {}) } }

    @Test
    fun accountsTyped() = capture("accounts_typed_draft") { AccountsScreen { AccountsCapsule("All", "Android", onSwitch = {}) } }

    @Test
    fun accountsA2Menu() = capture("accounts_menu_draft") { AccountsMenu() }

    @Test
    fun accountsEmpty() = capture("accounts_empty_draft") { EmptyAccounts() }

    @Test
    fun edgeAccountsA2() = captureEdge("accounts_top_draft") { AccountsScreen { AccountsCapsule("All", "", onSwitch = {}) } }

    @Test
    fun edgeAccountsTyped() = captureEdge("accounts_typed_draft") { AccountsScreen { AccountsCapsule("All", "Android", onSwitch = {}) } }

    @Test
    fun edgeAccountsA2Menu() = captureEdge("accounts_menu_draft") { AccountsMenu() }

    @Test
    fun edgeAccountsEmpty() = captureEdge("accounts_empty_draft") { EmptyAccounts() }

    @Composable
    private fun AccountsScreen(top: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) { Column { top() } }
            Column(
                Modifier.padding(horizontal = 16.dp + LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { AccountCard(it) }
            }
        }
    }

    @Composable
    private fun AccountsMenu() {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            contentOffset = CapsuleMenuOffset,
            screen = { AccountsScreen { AccountsCapsule("All", "", onSwitch = { anchor = it }) } }
        ) {
            DropdownMenuGroup(index = 0, count = 2) { FolderRows() }
            DropdownMenuGroup(index = 1, count = 2) {
                DropdownMenuItem(
                    text = "Manage folders",
                    onClick = {},
                    leadingIcon = { Icon(Tune, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    trailingIcon = {
                        Icon(ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
            }
        }
    }

    // Nobody followed: the field alone at the very top of the page, Paste in it, then the empty state (AccountsScreen's words).
    @Composable
    private fun EmptyAccounts() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(horizontal = LocalReadableInset.current)) {
                Column { AccountsCapsule(folder = null, query = "", onSwitch = {}) }
            }
            EmptyZone(
                title = "No subreddits yet",
                message = "Type a subreddit name or paste a link above, then follow to build your front page. " +
                    "The list stays on this phone. Only the reads go to Reddit.",
                icon = FrontrIcons.Person,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // The capsule of Home with the field inside it: the real field, its hint the shrinking pair, Clear (once a word is typed)
    // and Paste at the end as BoldIconButton. [folder] null: no folder to switch, the field alone.
    @Composable
    private fun AccountsCapsule(folder: String?, query: String, onSwitch: (IntOffset) -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppBarWithSearch(
                inputField = {
                    SearchCapsuleField(
                        searchIcon = FrontrIcons.Search,
                        value = query,
                        onValueChange = {},
                        hints = AccountsHints,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions()
                    )
                },
                navigationIcon = folder?.let {
                    {
                        FolderSwitch(
                            icon = FrontrIcons.Folder,
                            trailingIcon = ExpandMore,
                            label = it,
                            expanded = false,
                            onClick = {},
                            modifier = Modifier.onGloballyPositioned { place ->
                                val at = place.positionInRoot()
                                onSwitch(IntOffset((at.x + place.size.width / 2).toInt(), (at.y + place.size.height).toInt()))
                            }
                        )
                    }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        BoldIconButton(onClick = {}) { Icon(FrontrIcons.Close, contentDescription = "Clear") }
                    }
                    BoldIconButton(onClick = {}) { Icon(FrontrIcons.Paste, contentDescription = "Paste a link") }
                }
            )
        }
    }

    // AccountCard of AccountsScreen (private there), the folder as a chip once there are folders.
    @Composable
    private fun AccountCard(row: AccountRow) {
        ZoneSurface(onClick = {}, shape = ShapeDefaults.ExtraLarge, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.name ?: "r/${row.handle}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(
                            row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FeedChoiceChips(
                        handle = row.handle,
                        sort = row.sort,
                        country = row.country,
                        onSort = {},
                        onCountry = {},
                        folder = row.folder,
                        onFile = {}
                    )
                }
            }
        }
    }
}

// The field's words, longest first, as the kit's SearchHint takes them: the text shrinks, then the shorter form.
private val AccountsHints = listOf("Search 12 subreddits or paste a link", "Search 12 subreddits")
