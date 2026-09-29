package com.frontr.app.ui.component

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val shape = RoundedCornerShape(24.dp)
    val tap = rememberHaptics()
    Box(modifier) {
    ZoneSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 5.dp),
        shape = shape
    ) {
        Column(
            Modifier.clickable { tap.tick(); onClick() }
                .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            post.contextLine()?.let { ContextLine(it, icon = if (post.isPinned) FrontrIcons.Pin else null) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    post.avatarUrl,
                    post.authorName,
                    size = if (compact) 36.dp else 44.dp,
                    sharedKey = post.id
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    NameRow(post)

                    if (post.text.isNotBlank()) {
                        Text(post.text, style = MaterialTheme.typography.bodyLarge)
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
    }
    // Arrived since the last visit and not yet scrolled past, see NewDot.
    NewDot(unread, Modifier.align(Alignment.TopEnd).padding(top = if (compact) 11.dp else 13.dp, end = 26.dp))
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
private fun NameRow(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            post.authorName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // The sub is the name, the author sits beside it. A comment names
        // its author alone.
        post.relatedHandle?.let { author ->
        Text(
            " u/$author",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        }
        Text(
            " · ${relativeTime(post.publishedAtMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
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
