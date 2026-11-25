package ru.tusur.timetable.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.DayStatus
import ru.tusur.timetable.domain.model.GapKind
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonPhase
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.TimelineItem
import ru.tusur.timetable.ui.components.TabularText
import ru.tusur.timetable.ui.theme.LocalLessonColors
import ru.tusur.timetable.ui.theme.tabular

@Composable
fun LessonCard(
    lesson: Lesson,
    phase: LessonPhase,
    onOpenPerson: (PersonRef) -> Unit,
    onOpenMap: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accent = LocalLessonColors.current.color(lesson.type)
    val isLive = phase is LessonPhase.Live
    val isPast = phase is LessonPhase.Past
    val typeLabel = stringResource(lesson.type.labelRes)

    val border = if (isLive) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (isPast) 0.62f else 1f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = border,
        tonalElevation = if (isLive) 2.dp else 0.dp,
    ) {
        Column {
            Row(modifier = Modifier.padding(14.dp)) {
                Column(
                    modifier = Modifier.width(58.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    TabularText(
                        text = lesson.startLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TabularText(
                        text = lesson.endLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lesson.pair?.let {
                        Text(
                            text = stringResource(R.string.pair_number, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(4.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KindPill(label = typeLabel, color = accent)
                        if (isPast) {
                            Text(
                                text = stringResource(R.string.lesson_past),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lesson.place?.let { place ->
                            val clickable = lesson.hasMap
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .then(
                                        if (clickable) {
                                            Modifier.clickable(
                                                role = Role.Button,
                                                onClickLabel = stringResource(R.string.map_show),
                                            ) { onOpenMap(lesson.mapRoom.orEmpty()) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = buildString {
                                        append(place)
                                        lesson.floor?.let { append(" · ").append(it) }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!compact && lesson.people.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            lesson.people.take(3).forEach { person ->
                                PersonChip(person = person, onClick = { onOpenPerson(person) })
                            }
                        }
                    }
                    lesson.note?.let { note ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isLive) {
                val progress = (phase as LessonPhase.Live).progress
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                )
            }
        }
    }
}

@Composable
private fun KindPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PersonChip(person: PersonRef, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = person.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

@Composable
fun GapRow(item: TimelineItem.GapItem, modifier: Modifier = Modifier) {
    val label = when (item.kind) {
        GapKind.BREAK -> stringResource(R.string.gap_break, TomskTime.formatDuration(item.duration))
        GapKind.WINDOW -> stringResource(R.string.gap_window, TomskTime.formatDuration(item.duration))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabularText(
            text = TomskTime.formatMinutes(item.startMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        TabularText(
            text = TomskTime.formatMinutes(item.endMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DayStatusCard(
    status: DayStatus,
    nowMinutes: Int,
    modifier: Modifier = Modifier,
) {
    when {
        status.live != null -> LiveStatusCard(status, nowMinutes, modifier)
        status.next != null -> NextStatusCard(status, modifier)
        status.allFinished -> FinishedStatusCard(modifier)
        else -> Unit
    }
}

@Composable
private fun LiveStatusCard(status: DayStatus, nowMinutes: Int, modifier: Modifier) {
    val lesson = status.live ?: return
    val phase = lesson.phaseAt(nowMinutes)
    val progress = (phase as? LessonPhase.Live)?.progress ?: 0f
    val accent = LocalLessonColors.current.color(lesson.type)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.status_live,
                        lesson.pairLabel ?: stringResource(R.string.lesson_default_pair),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(
                        R.plurals.minutes_short,
                        status.minutesLeft ?: 0,
                        status.minutesLeft ?: 0,
                    ),
                    style = MaterialTheme.typography.labelLarge.tabular,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(text = lesson.title, style = MaterialTheme.typography.bodyLarge)
            val place = listOfNotNull(lesson.place, lesson.floor).joinToString(" · ")
            if (place.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(text = place, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f),
            )
            status.next?.let { next ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.status_then, next.startLabel, next.title),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NextStatusCard(status: DayStatus, modifier: Modifier) {
    val next = status.next ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.status_next_in,
                        TomskTime.formatDuration(status.minutesToNext ?: 0),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(next.startLabel)
                        append(" · ")
                        append(next.title)
                        next.place?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            TabularText(
                text = next.startLabel,
                style = MaterialTheme.typography.titleLarge,
                color = LocalLessonColors.current.color(next.type),
            )
        }
    }
}

@Composable
private fun FinishedStatusCard(modifier: Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.status_finished),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
