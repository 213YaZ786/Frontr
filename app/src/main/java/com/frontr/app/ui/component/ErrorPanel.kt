package com.frontr.app.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frontr.app.core.common.AppError
import com.frontr.app.core.common.ErrorAction
import com.frontr.app.core.common.present

/**
 * The visible half of the error taxonomy. Every failure gets a name, an
 * explanation and the one action that helps, instead of a shrug.
 */
@Composable
fun ErrorPanel(
    error: AppError,
    onRetry: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = error.present()

    ZoneSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(presentation.headline, style = MaterialTheme.typography.titleMedium)
            Text(
                presentation.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (presentation.action) {
                ErrorAction.RETRY -> BoldButton(onClick = onRetry) { Text("Try again") }
                ErrorAction.OPEN_LOG -> BoldButton(onClick = onOpenLog) { Text("Activity log") }
                ErrorAction.NONE -> Unit
            }
        }
    }
}
