/*
 * The welcome guide as tiles, after FlorisBoard's SetupScreen: every step is read from the system or the stores
 * each time the screen comes back, shown done or not, and the tiles are Modules' SetupTiles. Ported from the same
 * guide of Language, the first app whose guide is tiles, as LinkedOut's (its commits 6f8bbce and a58c325) is
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt
 *
 * Copyright (C) 2021-2025 The FlorisBoard Contributors, Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0), translated from
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 *
 * not ported: SetupScreen (it is WelcomeScreen and WelcomeContent here), SetupLinks (FlorisBoard's own links to its
 * site), awaitTransition (the wait for the keyboard to be enabled, which this guide has no step for), Flow (the type
 * awaitTransition extends)
 * not ported: WelcomePage, PAGES (Frontr's former pager: what Frontr is, find a subreddit, follow a subreddit, sort
 * into folders, then the three choices; the tiles keep the choices and the Subreddits tab explains the rest)
 * not ported: PageContent, Point, Dots, accentDisc, LinkExample (the pages' layout and the r/ example, no page left)
 * not ported: MediaChoice, Choices, Option (the radio rows of the pager's choice pages, ChoiceDialog replaces them)
 * not ported: the first-run lock on Save media (it was required until chosen; none of the four tiles is required now)
 */
package com.frontr.app.feature.welcome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.frontr.app.core.system.BatteryExemption
import com.frontr.app.core.update.StoreLink
import com.frontr.app.core.update.UpdateMode
import com.frontr.app.core.update.Updates
import com.frontr.app.data.accounts.AccountStore
import com.frontr.app.data.settings.AutoDownload
import com.frontr.app.data.settings.Settings
import com.frontr.app.data.settings.SettingsStore
import com.frontr.app.feature.settings.SettingsIcons
import com.frontr.app.feature.settings.SettingsViewModel
import com.frontr.app.sync.NewPostNotifier
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.ChoiceDialog
import com.frontr.app.ui.component.LoadingMark
import com.frontr.app.ui.component.SetupTile
import com.frontr.app.ui.component.SetupTileDialog
import com.frontr.app.ui.component.SetupTileGrid
import com.frontr.app.ui.component.leftText
import com.frontr.app.ui.component.requiredDone
import com.frontr.app.ui.component.requiredLeft
import com.frontr.app.ui.icon.FrontrIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** The choice dialog the screen has open, as Settings' OpenDialog does. */
private enum class OpenDialog { NONE, MEDIA, UPDATES, NOTIFY }

/**
 * What the tiles and the screen share: the tiles, the dialog a tile opened, and the picks that dialog sends back.
 * Kept apart from [rememberSetupTiles] so that Settings can ask for the tiles alone, as it counts what is left.
 * Ported from LinkedOut's Setup, which Language's rememberSetupTiles is split into.
 */
private class Setup(
    val tiles: List<SetupTile>,
    val dialog: MutableState<OpenDialog>,
    val settings: Settings,
    val chooseMedia: (AutoDownload) -> Unit,
    val chooseUpdates: (UpdateMode) -> Unit,
    val chooseNotify: (Boolean) -> Unit,
    val notifyOn: Boolean
)

/**
 * The few choices worth making, the best one already set, as tiles: each is read from the stores and the system
 * again each time the screen comes back (SetupScreen reads isFlorisBoardEnabled, isFlorisBoardSelected and
 * hasNotificationPermission, and sets the current step from them), and turns done by itself.
 * Ported from rememberSetupTiles of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 * Only the choices that weigh on the reader are tiles (the user, 2026-11-06): what spends their data (saving
 * media), a permission (installing updates), work with the app closed (new posts), a role of the phone (Reddit
 * links). Each starts on the best setting (Never, Install, Off, as the system has it), so none is required and
 * Start never waits. Following a subreddit is an action of the Subreddits tab, not a choice, so it is no tile.
 */
@Composable
fun rememberSetupTiles(): List<SetupTile> = rememberSetup().tiles

@Composable
private fun rememberSetup(): Setup {
    val context = LocalContext.current
    val store: SettingsStore = koinInject()
    val sync: SettingsViewModel = koinViewModel()
    val current by store.settings.collectAsState()
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val dialog = rememberSaveable { mutableStateOf(OpenDialog.NONE) }

    val canInstall = remember(checks) { Updates.canInstall(context) }
    val viaStore = remember(checks) { StoreLink.viaStore(context) }
    val canNotify = remember(checks) { NewPostNotifier(context).canNotify() }
    val linksOn = remember(checks) { redditLinksEnabled(context) }
    val notifyOn = current.backgroundSync && current.notifyNewPosts
    val hasNotificationPermission = remember(checks) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val askDownloadNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }

    val chooseMedia: (AutoDownload) -> Unit = { choice ->
        // The same call as Settings' dialog: the watermark restarts, so what is on screen now is saved on the
        // next refresh.
        sync.setAutoDownloadMedia(choice)
        // The progress of a download is a notification like any other, and Android 13 and later want the
        // permission for it. Asked here because here is where the reader asked for downloads.
        if (choice != AutoDownload.OFF && !hasNotificationPermission) {
            askDownloadNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val chooseUpdates: (UpdateMode) -> Unit = { mode ->
        store.update { it.copy(updates = mode) }
        if (mode == UpdateMode.INSTALL && !canInstall) Updates.allowInstalls(context)
    }
    val turnOnNotify = {
        sync.setBackgroundSync(true)
        sync.setNotifyNewPosts(true)
        // Checks run with the app closed: see BatteryExemption.
        BatteryExemption.request(context)
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOnNotify()
        checks++
    }
    val chooseNotify: (Boolean) -> Unit = { on ->
        when {
            !on -> sync.setNotifyNewPosts(false)
            !hasNotificationPermission -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> turnOnNotify()
        }
    }

    val tiles = listOf(
        SetupTile(
            id = "media", name = "Save media", title = "Save media", icon = FrontrIcons.Download,
            state = autoDownloadLabel(current.autoDownloadMedia),
            summary = "Posts saved on the phone open again offline. It is the one setting that spends your data, " +
                "off until you choose otherwise.",
            done = true, required = false,
            onClick = { dialog.value = OpenDialog.MEDIA }
        ),
        SetupTile(
            id = "updates", name = "Updates", title = "Updates", icon = SettingsIcons.Update,
            state = when {
                viaStore -> "Through Store"
                current.updates == UpdateMode.INSTALL && !canInstall -> "Installs not allowed"
                else -> updatesLabel(current.updates)
            },
            summary = "When a new version is out, Frontr can tell you, or install it when you leave the app. " +
                "Android's permission to install is needed once.",
            done = viaStore || current.updates != UpdateMode.INSTALL || canInstall, required = false,
            error = !viaStore && current.updates == UpdateMode.INSTALL && !canInstall,
            onClick = {
                if (!viaStore && current.updates == UpdateMode.INSTALL && !canInstall) Updates.allowInstalls(context)
                else dialog.value = OpenDialog.UPDATES
            }
        ),
        SetupTile(
            id = "notify", name = "New posts", title = "New posts", icon = FrontrIcons.Bell,
            state = when {
                !notifyOn -> "Off"
                !canNotify -> "Notifications off"
                else -> "Notify me"
            },
            summary = "A notification when the subreddits you follow get new posts. Frontr then checks in the " +
                "background, which costs battery.",
            done = !notifyOn || canNotify, required = false,
            error = notifyOn && !canNotify,
            onClick = { dialog.value = OpenDialog.NOTIFY }
        ),
        SetupTile(
            id = "links", name = "Reddit links", title = "Reddit links", icon = SettingsIcons.Link,
            state = if (linksOn) "On" else "Off",
            summary = "reddit.com links tapped anywhere open in Frontr once Android's \"Open supported links\" " +
                "is on for it. Tap to open that page.",
            done = linksOn, required = false,
            onClick = { openLinkSettings(context) }
        )
    )
    return Setup(tiles, dialog, current, chooseMedia, chooseUpdates, chooseNotify, notifyOn)
}

/** The labels of Settings' own dialogs, so a choice reads the same here. */
private fun autoDownloadLabel(choice: AutoDownload): String = when (choice) {
    AutoDownload.OFF -> "Never"
    AutoDownload.UNMETERED -> "On Wi-Fi only"
    AutoDownload.ANY -> "On Wi-Fi or mobile data"
}

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install"
}

/**
 * The guide: the logo, the title, how many steps are left, the tiles, then Start (Done when reopened from Settings),
 * which waits for the required ones. Laid out as SetupScreen: header, steps, footer with the one button.
 * [onFinish] receives true when the reader asks to go to Subreddits.
 * Ported from WelcomeScreen of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 * and from SetupScreen#SetupScreen of
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 */
@Composable
fun WelcomeScreen(onFinish: (openAccounts: Boolean) -> Unit) {
    val store: SettingsStore = koinInject()
    val followed: AccountStore = koinInject()
    // Read once: closing the guide sets welcomeSeen, and the button must not turn to Done while it leaves.
    val reopened = remember { store.current.welcomeSeen }
    val setup = rememberSetup()
    val steps = setup.tiles
    // The tile whose long press is open (its id), "" when none.
    var explained by rememberSaveable { mutableStateOf("") }
    val close = { setup.dialog.value = OpenDialog.NONE }

    // Start lands on Subreddits while nobody is followed, where a name is typed (the old guide's last Next).
    WelcomeContent(steps, reopened, onStart = { onFinish(followed.accounts.value.isEmpty()) }) { explained = it.id }

    steps.firstOrNull { it.id == explained }?.let { tile ->
        SetupTileDialog(tile, onDismiss = { explained = "" })
    }

    when (setup.dialog.value) {
        OpenDialog.NONE -> Unit
        OpenDialog.MEDIA -> ChoiceDialog(
            title = "Save media",
            options = AutoDownload.entries.map { it to autoDownloadLabel(it) },
            selected = setup.settings.autoDownloadMedia,
            onPick = { close(); setup.chooseMedia(it) },
            onDismiss = close
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = setup.settings.updates,
            onPick = { close(); setup.chooseUpdates(it) },
            onDismiss = close
        )
        OpenDialog.NOTIFY -> ChoiceDialog(
            title = "New posts",
            options = listOf(false to "Off", true to "Notify me"),
            selected = setup.notifyOn,
            onPick = { close(); setup.chooseNotify(it) },
            onDismiss = close
        )
    }
}

/**
 * What the guide draws from the tiles it is given: the layout of SetupScreen (header, steps, footer with the one
 * button), split from [WelcomeScreen] so that a picture can be taken with fixed tiles, without the system checks.
 * Ported from WelcomeContent of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeContent
 */
@Composable
internal fun WelcomeContent(tiles: List<SetupTile>, reopened: Boolean, onStart: () -> Unit, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
    // Drawn over the whole window, so it keeps clear of the system bars itself.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(40.dp))
            // The icon's own animation, as the guide played it before the tiles (the user, 2026-11-07).
            LoadingMark(size = 160.dp)
            Spacer(Modifier.height(20.dp))
            // Every title in the accent (the user, 2026-10-31), as Modules' SettingsSectionTitle.
            Text("Frontr", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(steps.leftText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            SetupTileGrid(steps, onExplain)

            Spacer(Modifier.height(32.dp))
            val ready = steps.requiredDone()
            BoldButton(filled = true, enabled = ready, onClick = onStart) { Text(if (reopened) "Done" else "Start") }
            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Still needed: " + steps.requiredLeft().joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * True when the reader has allowed Frontr to open Reddit links. The keys of hostToStateMap are the hosts the manifest
 * declares, so this asks for the exact strings written there. A private copy of Settings' redditLinksEnabled, as
 * LinkedOut keeps one of its linkedInLinksEnabled here.
 */
private fun redditLinksEnabled(context: Context): Boolean {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }
        .getOrNull() ?: return false
    if (!state.isLinkHandlingAllowed) return false
    return listOf("reddit.com", "www.reddit.com", "old.reddit.com", "new.reddit.com", "m.reddit.com", "redd.it").any { host ->
        val hostState = state.hostToStateMap[host]
        hostState == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            hostState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }
}

/** The system screen where supported links are switched on for this app. A private copy of Settings' openLinkSettings. */
private fun openLinkSettings(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
