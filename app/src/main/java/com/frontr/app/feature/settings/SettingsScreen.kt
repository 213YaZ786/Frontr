package com.frontr.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.frontr.app.BuildConfig
import com.frontr.app.core.system.BatteryExemption
import com.frontr.app.core.update.StoreLink
import com.frontr.app.core.update.UpdateMode
import com.frontr.app.core.update.Updates
import com.frontr.app.data.settings.AutoDownload
import com.frontr.app.data.settings.StartTab
import com.frontr.app.data.settings.ThemeMode
import com.frontr.app.feature.welcome.rememberSetupTiles
import com.frontr.app.navigation.LocalReadableInset
import com.frontr.app.navigation.WidthClass
import com.frontr.app.sync.NewPostNotifier
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.ChoiceDialog
import com.frontr.app.ui.component.FloatingFrame
import com.frontr.app.ui.component.FloatingTop
import com.frontr.app.ui.component.LargeTitle
import com.frontr.app.ui.component.LocalDockPadding
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.ui.component.SettingsActionTile
import com.frontr.app.ui.component.SettingsFoldedSections
import com.frontr.app.ui.component.SettingsHeaderSpacer
import com.frontr.app.ui.component.SettingsListDetail
import com.frontr.app.ui.component.SettingsSection
import com.frontr.app.ui.component.SettingsSectionTitle
import com.frontr.app.ui.component.SettingsSwitchTile
import com.frontr.app.ui.component.SettingsTile
import com.frontr.app.ui.component.SettingsTileDialogs
import com.frontr.app.ui.component.SettingsTileGrid
import com.frontr.app.ui.component.SideDockClearance
import com.frontr.app.ui.component.ZoneAlertDialog
import com.frontr.app.ui.component.leftText
import com.frontr.app.ui.component.rememberHaptics
import com.frontr.app.ui.icon.FrontrIcons
import com.frontr.app.ui.theme.TEXT_SCALES
import com.frontr.app.ui.theme.textScaleLabel
import org.koin.androidx.compose.koinViewModel

private enum class OpenDialog { NONE, THEME, TEXT_SIZE, KEEP, FREQUENCY, CLEAR, START_TAB, AUTO_DOWNLOAD, UPDATES }

/**
 * The groups of settings: one page after another on a phone, a list and the chosen one's page on a wide window.
 * Ported from MTGA's SECTIONS (LinkedOut's commit 6f8bbce took them from there), with Frontr's own groups: "Your list"
 * is Subreddits here, and Privacy (LinkedOut's browsing data) has no counterpart.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#SECTIONS
 */
private val APPEARANCE = SettingsSection("appearance", "Appearance", SettingsIcons.Palette)
private val MEDIA = SettingsSection("media", "Media", SettingsIcons.Image)
private val READING = SettingsSection("reading", "Reading", FrontrIcons.Comment)
private val BACKGROUND = SettingsSection("background", "Background updates", FrontrIcons.Refresh)
private val STORAGE = SettingsSection("storage", "Storage", FrontrIcons.Folder)
private val SUBREDDITS = SettingsSection("list", "Subreddits", FrontrIcons.Person)
private val SUPPORT = SettingsSection("support", "Support", SettingsIcons.Warning)
private val ABOUT = SettingsSection("about", "About", FrontrIcons.Info)
private val SECTIONS = listOf(APPEARANCE, MEDIA, READING, BACKGROUND, STORAGE, SUBREDDITS, SUPPORT, ABOUT)
// not ported: PRIVACY (LinkedOut's browsing data section: Frontr has no cookie clearing to offer)
// not ported: SOURCES (MTGA's sources section: Frontr reads reddit.com alone, there is nothing to choose)

/**
 * A section's tiles: the switches first, then what opens something, as MTGA's page lambda joins them
 * (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#SettingsScreen).
 * About alone starts with what opens something, so that Guide stays its first tile as Dialer's About
 * (https://github.com/213YaZ786/Dialer/blob/main/app/src/main/java/com/yaz/dialer/feature/settings/SettingsScreen.kt#SettingsScreen)
 * ahead of the update source switch. LinkedOut's commit 6f8bbce names this join sectionTiles.
 */
private fun sectionTiles(
    which: SettingsSection,
    tiles: Map<SettingsSection, List<SettingsSwitchTile>>,
    actions: Map<SettingsSection, List<SettingsActionTile>>
): List<SettingsTile> =
    if (which == ABOUT) actions[which].orEmpty() + tiles[which].orEmpty()
    else tiles[which].orEmpty() + actions[which].orEmpty()

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install"
}

private fun autoDownloadLabel(value: AutoDownload): String = when (value) {
    AutoDownload.OFF -> "Never"
    AutoDownload.UNMETERED -> "On Wi-Fi only"
    AutoDownload.ANY -> "On Wi-Fi or mobile data"
}

private val KEEP_DAYS = listOf(7, 30, 90, 365, 0)

private fun keepLabel(days: Int): String = when (days) {
    0 -> "Forever"
    7 -> "One week"
    30 -> "One month"
    90 -> "Three months"
    365 -> "One year"
    else -> "$days days"
}

/**
 * Settings as tiles, ported from MTGA's SettingsScreen (itself from SMS's, which follows Android's Quick Settings
 * tile): https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#SettingsScreen
 * and from LinkedOut's, which took it from there in its commit 6f8bbce
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/settings/SettingsScreen.kt#SettingsScreen
 * Sections, switches, actions and the page are laid out as there; Frontr's Settings is a tab of the dock too,
 * so the header has no back button. The guide tile reads "N left" as Language's does:
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/settings/SettingsScreen.kt#SettingsScreen
 * The "Update by itself" tile is the logic of Launcher's UpdateSourceRow
 * (https://github.com/213YaZ786/Launcher/blob/main/app/src/main/java/com/yaz/launcher/ui/component/UpdateSourceRow.kt#UpdateSourceRow).
 * The kit's UpdateSourceRow is a ListItem row, which does not fit a grid of tiles, so its logic is ported into the tile here.
 * Every setting the former list of rows offered is a tile: appearance, media (Wi-Fi only, saving, playback), start tab,
 * Reddit links, background checks, notifications, keeping posts, saved posts, saved media, cached images, the
 * subreddits file, the activity log, updates and the about lines.
 */
// not ported: UpdateSourceRow (the kit's ListItem row, its logic lives in the "self" tile below)
// not ported: onOpenConnection and the "connection" tile (Frontr has no connection check screen to reach)
// not ported: OpenDialog.COOKIES and the "cookies" tile (LinkedOut's browsing data, Frontr keeps no cookies to clear)
// not ported: the "reads" tile (LinkedOut's one-host line, Frontr's About already says what it reads)
// not ported: Section, SettingRow, SwitchRow (Frontr's former rows, the tiles replace them)
@Composable
fun SettingsScreen(
    onOpenDebugLog: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenSavedMedia: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val storageBytes by viewModel.storageBytes.collectAsState()
    val imageCacheBytes by viewModel.imageCacheBytes.collectAsState()
    val offlineBytes by viewModel.offlineBytes.collectAsState()
    val offlineCount by viewModel.offlineCount.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = rememberHaptics()
    // The guide's tile says how many steps are left, read from the system as the guide reads them.
    val setup = rememberSetupTiles()

    // Changed in system settings, so read again each time the screen returns.
    var redditLinksOn by remember { mutableStateOf(redditLinksEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        redditLinksOn = redditLinksEnabled(context)
        onPauseOrDispose { }
    }
    // Store can be installed while the app is away, so both are read again when the app comes back.
    var present by remember { mutableStateOf(StoreLink.present(context)) }
    var own by remember { mutableStateOf(StoreLink.selfUpdates(context)) }
    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                present = StoreLink.present(context)
                own = StoreLink.selfUpdates(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    // The tile whose long press is open (its id), "" when none.
    var tileId by rememberSaveable { mutableStateOf("") }
    var sectionId by rememberSaveable { mutableStateOf(APPEARANCE.id) }
    val section = SECTIONS.first { it.id == sectionId }
    // On a phone every section starts folded; the one open stays open across a turn of the screen.
    var openId by rememberSaveable { mutableStateOf("") }
    val openSection = SECTIONS.firstOrNull { it.id == openId }
    // The page's scroll, held here so a drag on the header drives it (FloatingFrame's `scroll`): the folded
    // list on a phone, the chosen section's page on a wide window (it starts at the top again for each section).
    val compactScroll = rememberScrollState()
    val detailScroll = rememberSaveable(sectionId, saver = ScrollState.Saver) { ScrollState(0) }

    LaunchedEffect(Unit) { viewModel.measureStorage() }

    // The system file picker, so no storage permission is ever needed.
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportAccounts) }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importAccounts) }

    // Notifications can be blocked in Android at any time, so this is read
    // again whenever the screen comes back, like the Reddit links state.
    val notifier = remember { NewPostNotifier(context) }
    var notificationsAllowed by remember { mutableStateOf(notifier.canNotify()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notifier.canNotify()
        onPauseOrDispose { }
    }
    /*
     * The progress line of automatic saving is a notification like any
     * other, so Android 13 and later want the same permission. Asked at the
     * moment automatic saving is turned on, and a refusal changes nothing to
     * the saving itself, only to seeing how far it is.
     */
    val downloadNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsAllowed = notifier.canNotify() }
    val askForDownloadNotifications: () -> Unit = {
        if (!notifier.canNotify() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            downloadNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Asked at the moment the reader turns notifications on, never before.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = notifier.canNotify()
        if (granted && notificationsAllowed) {
            viewModel.setNotifyNewPosts(true)
        } else {
            // Refused now or earlier. Android will not ask twice, so its own
            // screen is the only way left, and the reader decides there.
            Toast.makeText(context, "Notifications are off for Frontr in Android", Toast.LENGTH_SHORT).show()
            openNotificationSettings(context)
        }
    }
    val onNotifyChange: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> viewModel.setNotifyNewPosts(false)
            notifier.canNotify() -> viewModel.setNotifyNewPosts(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                // Permission granted or not needed, but switched off in Android.
                Toast.makeText(context, "Notifications are off for Frontr in Android", Toast.LENGTH_SHORT).show()
                openNotificationSettings(context)
            }
        }
    }

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    val measuring = "Measuring"
    val storageSize = storageBytes?.let { Formatter.formatShortFileSize(context, it) }
    val imageCacheSize = imageCacheBytes?.let { Formatter.formatShortFileSize(context, it) }
    val mediaSize = offlineBytes?.let { bytes ->
        if (offlineCount == 0) "Nothing saved yet" else "$offlineCount files, ${Formatter.formatShortFileSize(context, bytes)}"
    }

    val tiles: Map<SettingsSection, List<SettingsSwitchTile>> = mapOf(
        APPEARANCE to listOf(
            SettingsSwitchTile(
                id = "black", name = "Pure black", title = "Pure black", icon = SettingsIcons.Night,
                summary = "Deeper blacks in dark mode. Easier on the battery with OLED screens.",
                checked = settings.pureBlack, enabled = settings.themeMode != ThemeMode.LIGHT,
                onChange = viewModel::setPureBlack
            ),
            SettingsSwitchTile(
                id = "glass", name = "Glass effects", title = "Glass effects", icon = SettingsIcons.AutoAwesome,
                summary = "Zones and floating buttons in liquid glass, over a soft light in your wallpaper's colours.",
                checked = settings.glass, onChange = viewModel::setGlass
            ),
            SettingsSwitchTile(
                id = "counts", name = "Counts", title = "Show counts", icon = FrontrIcons.Heart,
                summary = "Comments and votes under each post.",
                checked = settings.showCounts, onChange = viewModel::setShowCounts
            ),
            SettingsSwitchTile(
                id = "compact", name = "Compact posts", title = "Compact posts", icon = SettingsIcons.GridView,
                summary = "Tighter spacing, smaller avatars and shorter pictures. More posts on screen.",
                checked = settings.compactPosts, onChange = viewModel::setCompactPosts
            ),
            SettingsSwitchTile(
                id = "square", name = "Square avatars", title = "Square avatars", icon = FrontrIcons.Person,
                summary = "Rounded squares instead of circles.",
                checked = settings.squareAvatars, onChange = viewModel::setSquareAvatars
            )
        ),
        MEDIA to listOf(
            SettingsSwitchTile(
                id = "mediaWifi", name = "Media on Wi-Fi", title = "Media on Wi-Fi only", icon = SettingsIcons.Wifi,
                summary = "On mobile data, pictures and videos wait for a tap. Avatars still load.",
                checked = settings.mediaOnWifiOnly, onChange = viewModel::setMediaOnWifiOnly
            ),
            SettingsSwitchTile(
                id = "autoplay", name = "Autoplay", title = "Play videos automatically", icon = FrontrIcons.Play,
                summary = "Videos and GIFs play silently in the list as you scroll, and start " +
                    "when opened. Never on mobile data while Wi-Fi only is on.",
                checked = settings.autoplayVideos, onChange = viewModel::setAutoplayVideos
            ),
            SettingsSwitchTile(
                id = "muted", name = "Start muted", title = "Start videos muted", icon = FrontrIcons.VolumeOff,
                summary = "Sound stays off until you tap the speaker.",
                checked = settings.startMuted, onChange = viewModel::setStartMuted
            )
        ),
        BACKGROUND to listOf(
            SettingsSwitchTile(
                id = "sync", name = "New posts", title = "Check for new posts", icon = FrontrIcons.Refresh,
                summary = "Keeps Home up to date and saves new posts, even when the app is closed.",
                checked = settings.backgroundSync,
                onChange = { enabled ->
                    viewModel.setBackgroundSync(enabled)
                    // Asked here and nowhere else: the reader just asked for
                    // work that runs with the app closed. See BatteryExemption.
                    if (enabled) BatteryExemption.request(context)
                }
            ),
            SettingsSwitchTile(
                id = "syncWifi", name = "Wi-Fi only", title = "Wi-Fi only", icon = SettingsIcons.Wifi,
                summary = "Uses no mobile data for background checks.",
                checked = settings.syncOnWifiOnly, enabled = settings.backgroundSync,
                onChange = viewModel::setWifiOnly
            ),
            SettingsSwitchTile(
                id = "notify", name = "Notify", title = "Notify about new posts", icon = FrontrIcons.Bell,
                summary = when {
                    !settings.backgroundSync -> "Needs \"Check for new posts\"."
                    settings.notifyNewPosts && !notificationsAllowed ->
                        "Blocked in Android settings. Tap to allow."
                    else -> "A notification for each new post Home would show, grouped, one sound per check."
                },
                // Shown off while Android blocks it, so a tap then means
                // "make it work" and leads to the permission or Android's page.
                checked = settings.notifyNewPosts && notificationsAllowed, enabled = settings.backgroundSync,
                onChange = onNotifyChange
            )
        ),
        ABOUT to listOf(
            // Without Store the app always updates itself: the switch says so and is locked.
            SettingsSwitchTile(
                id = "self", name = "Update by itself", title = "Update by itself", icon = SettingsIcons.Update,
                summary = when {
                    !present -> "Store is not installed"
                    !own -> "Store updates this app"
                    else -> "Turn off to let Store update Frontr."
                },
                checked = !present || own, enabled = present,
                onChange = { on ->
                    haptics.toggle(on)
                    StoreLink.setSelfUpdates(context, on)
                    own = on
                }
            )
        )
    )

    // What opens a choice, a page or a link: tiles of the same grid, the value as the second line.
    val actions: Map<SettingsSection, List<SettingsActionTile>> = mapOf(
        APPEARANCE to listOf(
            SettingsActionTile(
                id = "theme", name = "Theme", title = "Theme", icon = SettingsIcons.Brightness,
                value = themeLabel(settings.themeMode), summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            ),
            SettingsActionTile(
                id = "textSize", name = "Text size", title = "Text size", icon = SettingsIcons.FormatSize,
                value = textScaleLabel(settings.textScale),
                summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                onClick = { dialog = OpenDialog.TEXT_SIZE }
            )
        ),
        MEDIA to listOf(
            SettingsActionTile(
                id = "autoSave", name = "Save media", title = "Save media automatically", icon = FrontrIcons.Download,
                value = autoDownloadLabel(settings.autoDownloadMedia), summary = autoDownloadLabel(settings.autoDownloadMedia),
                onClick = { dialog = OpenDialog.AUTO_DOWNLOAD }
            )
        ),
        READING to listOf(
            SettingsActionTile(
                id = "startTab", name = "Start on", title = "Start on", icon = FrontrIcons.Home,
                value = startTabLabel(settings.startTab), summary = startTabLabel(settings.startTab),
                onClick = { dialog = OpenDialog.START_TAB }
            ),
            SettingsActionTile(
                id = "redditLinks", name = "Reddit links", title = "Open Reddit links in Frontr", icon = SettingsIcons.Link,
                value = if (redditLinksOn) "On" else "Off",
                summary = if (redditLinksOn) {
                    "On. reddit.com links open here."
                } else {
                    "Off. Turn on \"Open supported links\" and add the reddit.com " +
                        "links. Sharing a link to Frontr works either way."
                },
                onClick = { openLinkSettings(context) }
            )
        ),
        BACKGROUND to listOf(
            SettingsActionTile(
                id = "frequency", name = "Frequency", title = "Frequency", icon = SettingsIcons.Timer,
                value = intervalLabel(settings.syncIntervalMinutes),
                summary = if (settings.backgroundSync) {
                    "Checks run " + intervalLabel(settings.syncIntervalMinutes).replaceFirstChar { it.lowercase() } + "."
                } else {
                    "Needs \"Check for new posts\"."
                },
                // Depends on the switch above: a tap then only explains.
                onClick = if (settings.backgroundSync) ({ dialog = OpenDialog.FREQUENCY }) else ({ tileId = "frequency" })
            )
        ),
        STORAGE to listOf(
            SettingsActionTile(
                id = "keep", name = "Keep posts", title = "Keep posts", icon = SettingsIcons.Timer,
                value = keepLabel(settings.keepPostsDays),
                summary = keepLabel(settings.keepPostsDays) + ". Saved media follows the same limit.",
                onClick = { dialog = OpenDialog.KEEP }
            ),
            SettingsActionTile(
                id = "savedPosts", name = "Saved posts", title = "Saved posts", icon = FrontrIcons.Delete,
                value = storageSize ?: measuring, summary = storageSize?.let { "$it. Tap to delete." } ?: measuring,
                onClick = { dialog = OpenDialog.CLEAR }
            ),
            SettingsActionTile(
                id = "savedMedia", name = "Saved media", title = "Saved media", icon = FrontrIcons.Download,
                value = mediaSize ?: measuring, summary = mediaSize?.let { "$it. Tap to browse and delete." } ?: measuring,
                onClick = onOpenSavedMedia
            ),
            SettingsActionTile(
                id = "cachedImages", name = "Cached images", title = "Cached images", icon = FrontrIcons.Delete,
                value = imageCacheSize ?: measuring, summary = imageCacheSize?.let { "$it. Tap to delete." } ?: measuring,
                onClick = viewModel::clearImageCache
            )
        ),
        SUBREDDITS to listOf(
            SettingsActionTile(
                id = "export", name = "Export", title = "Export subreddits", icon = SettingsIcons.Upload,
                value = "To a file", summary = "Save the subreddits you follow, and their folders, to a file.",
                onClick = { exporter.launch("frontr-subreddits.json") }
            ),
            SettingsActionTile(
                id = "import", name = "Import", title = "Import subreddits", icon = FrontrIcons.Download,
                value = "From a file",
                summary = "From a Frontr export, or a text file of subreddit names or reddit.com links.",
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            )
        ),
        SUPPORT to listOf(
            SettingsActionTile(
                id = "log", name = "Activity log", title = "Activity log", icon = SettingsIcons.Code,
                value = "Open", summary = "What Frontr did lately, to report a problem.",
                onClick = onOpenDebugLog
            )
        ),
        ABOUT to listOf(
            // Dialer's About "Guide" tile (YAZ/Dialer feature/settings/SettingsScreen.kt, id "guide"):
            // the same first tile in every app. The guide opens over Settings and shows the current choices,
            // so welcomeSeen is kept instead of reset as Dialer does.
            SettingsActionTile(
                id = "guide", name = "Guide", title = "Guide", icon = FrontrIcons.Play,
                value = setup.leftText(), summary = "See the welcome pages again",
                onClick = onOpenWelcome
            ),
            SettingsActionTile(
                id = "updates", name = "Updates", title = "Updates", icon = SettingsIcons.Update,
                value = updatesLabel(settings.updates), summary = updatesLabel(settings.updates),
                onClick = { dialog = OpenDialog.UPDATES }
            ),
            SettingsActionTile(
                id = "version", name = "Frontr", title = "Frontr ${BuildConfig.VERSION_NAME}", icon = FrontrIcons.Info,
                value = BuildConfig.VERSION_NAME, summary = "Read public Reddit communities with no account, no tracking and no ads."
            ),
            SettingsActionTile(
                id = "source", name = "Source code", title = "Source code", icon = SettingsIcons.Code,
                value = "github.com/213YaZ786/Frontr", summary = "github.com/213YaZ786/Frontr",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/Frontr") }
            ),
            SettingsActionTile(
                id = "thanks", name = "Thanks", title = "Thanks", icon = FrontrIcons.Heart,
                value = "Reddit", summary = "To the communities that make Reddit worth reading."
            )
        )
    )

    // One section's page: its name, then its tiles: the switches first, then what opens something.
    val page: @Composable ColumnScope.(SettingsSection) -> Unit = { which ->
        SettingsSectionTitle(which.title)
        val list: List<SettingsTile> = sectionTiles(which, tiles, actions)
        if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
    }

    // The window decides: a list and a page side by side from Medium up, as a phone held upright
    // keeps one page. The panes take the whole width, so the readable inset is dropped; the side dock
    // still needs its room when the readable column no longer clears it (see FrontrApp's clearsPill).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = WidthClass.of(maxWidth) != WidthClass.COMPACT
        val inset = LocalReadableInset.current
        val dockRoom = if (wide && inset >= SideDockClearance) SideDockClearance else 0.dp
        val dockPadding = LocalDockPadding.current
        CompositionLocalProvider(LocalReadableInset provides if (wide) 0.dp else inset) {
            Box(Modifier.fillMaxSize().padding(start = dockRoom)) {
                FloatingFrame(
                    bottom = 0.dp,
                    scroll = if (wide) detailScroll else compactScroll,
                    top = { FloatingTop("Settings") },
                    largeTitle = LargeTitle("Settings")
                ) { padding ->
                    if (wide) {
                        SettingsListDetail(
                            padding = padding,
                            sections = SECTIONS,
                            section = section,
                            onSection = { sectionId = it.id },
                            detailScroll = detailScroll,
                            detail = { page(section) }
                        )
                    } else {
                        // Scrolls at full width, rows pushed in by the readable inset, so the
                        // margins of a tablet scroll like the rest. See ReadableScroll.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(compactScroll)
                                .padding(horizontal = LocalReadableInset.current)
                        ) {
                            SettingsHeaderSpacer(padding)
                            Spacer(Modifier.height(24.dp))
                            SettingsFoldedSections(SECTIONS, open = openSection, onOpen = { openId = it?.id.orEmpty() }) { which ->
                                val list: List<SettingsTile> = sectionTiles(which, tiles, actions)
                                if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
                            }
                            Spacer(Modifier.height(24.dp))
                            // The floating dock covers the bottom: the last section stays above it.
                            Spacer(Modifier.height(dockPadding))
                        }
                    }
                }
            }
        }
    }

    (tiles.values.flatten() + actions.values.flatten()).firstOrNull { it.id == tileId }?.let { tile ->
        SettingsTileDialogs(tile, onDismiss = { tileId = "" })
    }

    when (dialog) {
        OpenDialog.START_TAB -> ChoiceDialog(
            title = "Start on",
            options = StartTab.entries.map { it to startTabLabel(it) },
            selected = settings.startTab,
            onPick = viewModel::setStartTab,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onPick = viewModel::setTheme,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = "Text size",
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onPick = viewModel::setTextScale,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = settings.updates,
            onPick = { mode ->
                viewModel.setUpdates(mode)
                // Installing needs Android's leave, asked when chosen.
                if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
            },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.AUTO_DOWNLOAD -> ChoiceDialog(
            title = "Save media automatically",
            options = AutoDownload.entries.map { it to autoDownloadLabel(it) },
            selected = settings.autoDownloadMedia,
            onPick = { choice ->
                viewModel.setAutoDownloadMedia(choice)
                if (choice != AutoDownload.OFF) askForDownloadNotifications()
            },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.KEEP -> ChoiceDialog(
            title = "Keep posts",
            options = KEEP_DAYS.map { it to keepLabel(it) },
            selected = settings.keepPostsDays,
            onPick = viewModel::setKeepPostsDays,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.FREQUENCY -> ChoiceDialog(
            title = "Check for new posts",
            options = INTERVALS.map { it to intervalLabel(it) },
            selected = settings.syncIntervalMinutes,
            onPick = viewModel::setInterval,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.CLEAR -> ZoneAlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Clear saved posts?") },
            text = {
                Text("Posts saved on this phone will be deleted. They load again the next time you open a subreddit.")
            },
            confirmButton = {
                BoldButton(filled = true, onClick = {
                    viewModel.clearSavedPosts()
                    dialog = OpenDialog.NONE
                }) { Text("Clear") }
            },
            dismissButton = {
                QuietButton(onClick = { dialog = OpenDialog.NONE }) { Text("Cancel") }
            }
        )
        OpenDialog.NONE -> Unit
    }
}

private fun startTabLabel(tab: StartTab): String = when (tab) {
    StartTab.HOME -> "Home"
    StartTab.ACCOUNTS -> "Subreddits"
    StartTab.LAST -> "Last tab used"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private val INTERVALS = listOf(15, 30, 60, 180, 360)

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60 -> "Every hour"
    else -> "Every ${minutes / 60} hours"
}

/**
 * True when the reader has allowed Frontr to open Reddit links. The keys of hostToStateMap are the hosts the
 * manifest declares, so this asks for the exact strings written there. Only the reader can switch them on, since
 * only Reddit could verify the domains, so this reads the reader's choice rather than a verification.
 * Ported from MTGA's xLinksEnabled
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SettingsScreen.kt#xLinksEnabled
 * and LinkedOut's linkedInLinksEnabled, which tries every declared host
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/settings/SettingsScreen.kt#linkedInLinksEnabled
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

/** Frontr's own notification page in Android settings. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** The system screen where supported links are switched on for this app. */
private fun openLinkSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
