package ru.tusur.timetable.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.ui.theme.tabular
import java.time.Instant
import java.time.LocalDateTime

@Composable
fun TabularText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.tabular,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@StringRes
fun TimetableError.messageRes(): Int = when (kind) {
    TimetableErrorKind.UNKNOWN_TARGET -> R.string.error_unknown_target
    TimetableErrorKind.BAD_WEEK -> R.string.error_bad_week
    TimetableErrorKind.RATE_LIMITED -> R.string.error_rate_limited
    TimetableErrorKind.NOT_FOUND -> R.string.error_not_found
    TimetableErrorKind.UNAVAILABLE -> R.string.error_unavailable
    TimetableErrorKind.NETWORK, TimetableErrorKind.TIMEOUT -> R.string.error_network
    TimetableErrorKind.MAPPING, TimetableErrorKind.UNKNOWN -> R.string.error_unknown
}

@Composable
fun ErrorPanel(
    error: TimetableError,
    onRetry: () -> Unit,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.error_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
            if (error.isSourceGone || error.kind == TimetableErrorKind.BAD_WEEK) {
                OutlinedButton(onClick = onPickSource) {
                    Text(stringResource(R.string.choose_again))
                }
            }
        }
    }
}

@Composable
fun OfflineBanner(
    fetchedAt: Instant?,
    staleAt: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when {
        !staleAt.isNullOrBlank() -> stringResource(R.string.stale_banner, staleAt)
        fetchedAt != null -> {
            val local = LocalDateTime.ofInstant(fetchedAt, TomskTime.ZONE)
            stringResource(
                R.string.offline_banner,
                String.format(java.util.Locale.ROOT, "%02d:%02d", local.hour, local.minute),
            )
        }

        else -> stringResource(R.string.offline_banner_no_time)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
fun EmptyDayCard(
    nearestTitle: String?,
    onGoNearest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.empty_day_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_day_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (nearestTitle != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGoNearest) {
                    Text(stringResource(R.string.empty_day_nearest, nearestTitle))
                }
            }
        }
    }
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, androidx.compose.foundation.shape.CircleShape),
    )
}
