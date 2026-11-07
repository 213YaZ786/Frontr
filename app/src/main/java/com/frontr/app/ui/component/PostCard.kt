package com.frontr.app.ui.component

import androidx.compose.foundation.combinedClickable
import com.frontr.app.ui.theme.innerZone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import com.frontr.app.ui.theme.LocalDisplayPrefs
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frontr.app.core.media.rememberMediaSource
import com.frontr.app.core.model.LinkCard
import com.frontr.app.core.model.MediaItem
import com.frontr.app.core.model.MediaType
import com.frontr.app.core.model.Post
import com.frontr.app.core.model.PostKind
import androidx.compose.ui.graphics.vector.ImageVector
import com.frontr.app.ui.icon.FrontrIcons
import java.util.concurrent.TimeUnit

/**
 * A post, laid out the way a reader expects one: who, when, what, then the
 * media, then the numbers. Media carries its own download control, since
 * saving a picture is the single most common thing people want from a client
 * like this and burying it in a long press is hostile.
 */
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onOpenMedia: (index: Int) -> Unit = {},
    showStats: Boolean = true,
    /** Arrived since the last visit and not yet scrolled past. */
    unread: Boolean = false,
    modifier: Modifier = Modifier
) {
    val compact = LocalDisplayPrefs.current.compact
    // A post is an object, so it gets a container of its own with air around
    // it, the same rounded zone the settings sections use. The hairline that
    // used to separate two posts is gone with it: two zones with a gap read as
    // two things without needing a line drawn between them.
    val shape = ShapeDefaults.ExtraLarge
    val tap = rememberHaptics()
    // A tap opens the post, a long press the suite's long press menu, see PostActions.
    val actions = rememberPostActions(post, onDownload)
    // Ported from https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/PostCard.kt#PostCard
    // (local checkout, commit 6f8bbce and after) and MTGA's PostCard: the zone is drawn twice, itself as SMS's
    // ThreadScreen.kt draws its bubble: in place, and again as the held copy without click handlers. [outer] places
    // it, [clicks] is what the column answers to. The copy flies no avatar to the opened post (one shared key, one
    // element) and plays no film of its own.
    val zone: @Composable (Modifier, Modifier, Boolean) -> Unit = { outer, clicks, copy ->
    ZoneSurface(
        modifier = outer,
        shape = shape
    ) {
        Column(
            clicks.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            post.contextLine()?.let { ContextLine(it, icon = if (post.isPinned) FrontrIcons.Pin else null) }

            // LinkedIn's post: the avatar and the author in a header, then the content on the whole width of the card
            // (the layout validated on a mockup, 2026-11-07).
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    post.avatarUrl,
                    post.authorName,
                    size = if (compact) 36.dp else 44.dp,
                    sharedKey = if (copy) null else post.id
                )

                NameRow(post, unread, Modifier.weight(1f))
            }

            // Reddit's title on its own line, full width, above the content, as Infinity's item_post_text.xml
            // (https://raw.githubusercontent.com/Docile-Alligator/Infinity-For-Reddit/master/app/src/main/res/layout/item_post_text.xml,
            // title_text_view_item_post_text_type, then content_text_view_item_post_text_type). The parser puts
            // the title as the first paragraph of a post's text (RedditParser.post: title, a blank line, body);
            // a comment's text stays whole.
            val isPost = post.id.startsWith("t3_")
            val title = if (isPost) post.text.substringBefore("\n\n").trim() else ""
            val body = if (isPost) post.text.substringAfter("\n\n", "").trim() else post.text
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }

            if (post.media.isNotEmpty()) {
                MediaBlock(post = post, onDownload = onDownload, onOpen = onOpenMedia)
            }

            post.card?.let { card ->
                LinkCardBlock(card = card, onClick = { card.url?.let(onOpenLink) })
            }

            post.quoted?.let { quote ->
                QuoteBlock(
                    handle = quote.handle,
                    name = quote.name,
                    text = quote.text,
                    onClick = { onOpenLink(quote.permalink) }
                )
            }

            if (showStats) post.stats?.let { StatsRow(it) }
        }
    }
    }
    Box(modifier) {
        // The tracker sits on the zone itself, after the padding, so its bounds are the zone HoldMenu lifts.
        zone(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp)
                .then(actions.tracker),
            Modifier.combinedClickable(onClick = { tap.tick(); onClick() }, onLongClick = actions::open),
            false
        )
        // The copy plays no film of its own: the inline player stays in the card below it.
        PostActionsOverlay(actions) {
            CompositionLocalProvider(LocalInlinePlaying provides null) { zone(Modifier.fillMaxWidth(), Modifier, true) }
        }
    }
}

@Composable
internal fun Avatar(
    url: String?,
    name: String,
    size: Dp = 44.dp,
    /** Post id when this avatar should fly to the opened post, null otherwise. */
    sharedKey: String? = null
) {
    val shape = if (LocalDisplayPrefs.current.squareAvatars) RoundedCornerShape(size * 0.22f) else CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .let { if (sharedKey != null) it.sharedPostElement("avatar-$sharedKey") else it }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NameRow(post: Post, unread: Boolean, modifier: Modifier = Modifier) {
    // Ported from https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/PostCard.kt#NameRow
    // The header's text column, name over author. The sub is the name, u/author and the time sit under it. A comment
    // names its author alone, so only the time stays.
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                post.authorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            // Arrived since the last visit and not yet scrolled past. The dot sits on
            // the line it marks, centred on it, as Fossify Messages' unread_count_badge (https://github.com/FossifyOrg/Messages/blob/main/app/src/main/res/layout/item_conversation.xml).
            NewDot(unread, Modifier.padding(start = 8.dp))
        }
        // Author and time joined with a comma, as AOSP Dialer's CallLogEntryText.java (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
        val time = relativeTime(post.publishedAtMillis)
        Text(
            post.relatedHandle?.let { author -> "u/$author, $time" } ?: time,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ContextLine(text: String, icon: ImageVector? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Stands in for a picture or video that waits for a tap on mobile data. */
@Composable
private fun HeldMedia(type: MediaType, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (type == MediaType.PHOTO) FrontrIcons.Download else FrontrIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            when (type) {
                MediaType.PHOTO -> "Photo, tap to load"
                MediaType.GIF -> "GIF, tap to load"
                MediaType.VIDEO -> "Video, tap to load"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Mobile data, Wi-Fi only is on",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MediaBlock(post: Post, onDownload: (MediaItem) -> Unit, onOpen: (Int) -> Unit) {
    // Compact trades some picture for a list that moves faster.
    val ratio = if (LocalDisplayPrefs.current.compact) 2f else 16f / 9f
    val hold = rememberMediaPolicy().hold
    // Only the first video or GIF of the post chosen by the list plays inline.
    val inlineIndex = if (LocalInlinePlaying.current == post.id) {
        post.media.indexOfFirst { it.type != MediaType.PHOTO }
    } else {
        -1
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        post.media.forEachIndexed { index, item ->
            // Wi-Fi only on a metered network: nothing is fetched until the
            // reader taps. One tap loads the preview, the next opens it.
            var revealed by remember(item.previewUrl) { mutableStateOf(false) }
            val waiting = hold && !revealed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (waiting) revealed = true else onOpen(index) }
            ) {
                if (waiting) {
                    HeldMedia(item.type, Modifier.fillMaxWidth().aspectRatio(ratio))
                } else {
                    // A saved copy wins over the network, which is the whole
                    // point of saving it.
                    val source = rememberMediaSource(post.id, index, item)
                    AsyncImage(
                        model = source.preview,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                    )
                    if (index == inlineIndex) {
                        InlineVideo(
                            url = source.playback,
                            onClick = { onOpen(index) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (item.type != MediaType.PHOTO) {
                    Text(
                        if (item.type == MediaType.GIF) "GIF" else "Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val tap = rememberHaptics()
                IconButton(
                    onClick = {
                        // The file will land somewhere the reader cannot see,
                        // so the phone says it has been asked for.
                        tap.done()
                        onDownload(item)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        FrontrIcons.Download,
                        contentDescription = "Save to Downloads",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuoteBlock(
    handle: String,
    name: String,
    text: String,
    onClick: () -> Unit
) {
    val tap = rememberHaptics()
    Surface(
        onClick = { tap.tick(); onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.innerZone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (name.isBlank()) "@$handle" else "$name  @$handle",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A link preview, its picture on top. The picture follows the media policy: on a
 * metered network with Wi-Fi only on, nothing is fetched until the reader
 * taps the placeholder, and a tap on the text still opens the link.
 */
@Composable
internal fun LinkCardBlock(card: LinkCard, onClick: () -> Unit) {
    val hold = rememberMediaPolicy().hold
    var revealed by remember(card.imageUrl) { mutableStateOf(false) }
    val image = card.imageUrl
    val waiting = hold && !revealed

    val tap = rememberHaptics()
    Surface(
        onClick = { tap.tick(); onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.innerZone,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Reddit's own preview of an article is small: drawn at its size
        // beside the text it stays sharp, stretched across the card it blurs.
        if (image != null && isSmallPreview(image)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 112.dp, height = 63.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (waiting) {
                        Icon(
                            FrontrIcons.Download,
                            contentDescription = "Load the preview image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).clickable { revealed = true }
                        )
                    } else {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                LinkCardText(card, Modifier.weight(1f).padding(start = 12.dp))
            }
            return@Surface
        }
        Column {
            if (image != null) {
                val ratio = if (LocalDisplayPrefs.current.compact) 2.4f else 1.91f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (waiting) {
                        HeldCardImage(Modifier.matchParentSize().clickable { revealed = true })
                    } else {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
            LinkCardText(card, Modifier.padding(12.dp))
        }
    }
}

/** A preview Reddit made at thumbnail size, which a larger size would only blur. */
private fun isSmallPreview(url: String): Boolean {
    if (url.startsWith("https://b.thumbs.redditmedia.com/")) return true
    val width = Regex("[?&]width=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return false
    return width <= 320
}

@Composable
private fun LinkCardText(card: LinkCard, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        card.destination?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            card.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        card.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeldCardImage(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            FrontrIcons.Download,
            contentDescription = "Load the preview image",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            "Preview, tap to load",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun StatsRow(stats: com.frontr.app.core.model.PostStats) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        stats.replies?.let { Stat(FrontrIcons.Comment, it) }
        stats.reposts?.let { Stat(FrontrIcons.Repost, it) }
        stats.likes?.let { Stat(FrontrIcons.Heart, it) }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            compactCount(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun Post.contextLine(): String? = when {
    isPinned -> "Pinned"
    kind == PostKind.REPOST -> relatedHandle?.let { "Reposted by $it" } ?: "Repost"
    kind == PostKind.REPLY -> relatedHandle?.let { "Replying to @$it" } ?: "Reply"
    else -> null
}

internal fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10.0}M"
    value >= 1_000 -> "${value / 100 / 10.0}K"
    else -> value.toString()
}

/** Compact relative time. Absolute weeks take over past a week. */
internal fun relativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val delta = System.currentTimeMillis() - millis
    if (delta < 0) return "now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
