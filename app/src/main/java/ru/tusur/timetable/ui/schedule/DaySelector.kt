package ru.tusur.timetable.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.ui.components.TabularText
import ru.tusur.timetable.ui.theme.tabular
import java.time.LocalDate

@Composable
fun DaySelector(
    days: List<DayChip>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val hasLessonsLabel = stringResource(R.string.day_has_lessons)
        val selectedTodayLabel = stringResource(R.string.day_selected_today)
        val selectedLabel = stringResource(R.string.day_selected)
        val todayLabel = stringResource(R.string.day_today)
        val notSelectedLabel = stringResource(R.string.day_not_selected)

        days.forEach { chip ->
            val isSelected = chip.date == selected
            val isToday = chip.date == today
            val borderColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(chip.date) },
                    )
                    .semantics {
                        contentDescription = buildString {
                            append(TomskTime.weekdayFull(chip.date))
                            append(", ")
                            append(chip.date.dayOfMonth)
                            append(" ")
                            append(TomskTime.monthName(chip.date.month))
                            if (chip.hasLessons) append(", ").append(hasLessonsLabel)
                        }
                        stateDescription = when {
                            isSelected && isToday -> selectedTodayLabel
                            isSelected -> selectedLabel
                            isToday -> todayLabel
                            else -> notSelectedLabel
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = TomskTime.weekdayShort(chip.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = chip.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = when {
                                !chip.hasLessons -> Color.Transparent
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
fun WeekSummary(count: Int, modifier: Modifier = Modifier) {
    TabularText(
        text = pluralStringResource(R.plurals.lessons_count, count, count),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun DayHeader(date: LocalDate, isToday: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = buildString {
            append(TomskTime.weekdayFull(date))
            if (isToday) append(", сегодня")
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}
