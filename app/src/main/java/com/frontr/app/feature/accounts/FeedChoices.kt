package com.frontr.app.feature.accounts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frontr.app.core.model.FeedSort
import com.frontr.app.core.model.PopularCountry
import com.frontr.app.ui.component.ChoiceDialog
import com.frontr.app.ui.component.rememberHaptics
import com.frontr.app.ui.icon.FrontrIcons

/**
 * What a sub's pill lets the reader set: the order its posts come in, the
 * country for Popular, the folder once there are folders. Controls, not
 * labels: they wrap onto a second line rather than scroll sideways.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedChoiceChips(
    handle: String,
    sort: FeedSort,
    country: String?,
    onSort: () -> Unit,
    onCountry: () -> Unit,
    folder: String? = null,
    onFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip(sort.label, FrontrIcons.Sort, onSort)
        if (handle.equals(PopularCountry.FEED, ignoreCase = true)) {
            ChoiceChip(PopularCountry.label(PopularCountry.resolve(country)), FrontrIcons.Globe, onCountry)
        }
        if (onFile != null && folder != null) ChoiceChip(folder, FrontrIcons.Folder, onFile)
    }
}

/** A chip that gives a little under the finger, with a tick: it is the reader's own action. */
@Composable
private fun ChoiceChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "chip")
    AssistChip(
        onClick = { haptics.tick(); onClick() },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        interactionSource = press,
        modifier = Modifier.widthIn(max = 180.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

/** The order of r/[handle]'s posts. */
@Composable
internal fun SortDialog(handle: String, selected: FeedSort, onSelect: (FeedSort) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = "Posts of r/$handle",
        options = FeedSort.entries.map { it to it.label },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

/** Popular's country: the phone's by default, which follows it when the phone moves. */
@Composable
internal fun CountryDialog(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val countries = remember {
        listOf(PopularCountry.EVERYWHERE) + PopularCountry.codes.sortedBy { PopularCountry.label(it) }
    }
    ChoiceDialog(
        title = "Popular in",
        options = listOf<Pair<String?, String>>(null to "This phone's country, ${PopularCountry.label(PopularCountry.ofPhone())}") +
            countries.map { it to PopularCountry.label(it) },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}
