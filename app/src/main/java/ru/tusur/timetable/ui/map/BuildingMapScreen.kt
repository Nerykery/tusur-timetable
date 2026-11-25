package ru.tusur.timetable.ui.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.MapRoom
import ru.tusur.timetable.domain.model.RoomCodes
import ru.tusur.timetable.domain.model.RoomOccupancy
import ru.tusur.timetable.domain.model.RoomStatus
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.ui.components.ErrorPanel
import ru.tusur.timetable.ui.theme.LocalLessonColors
import ru.tusur.timetable.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingMapScreen(
    state: BuildingMapUiState,
    onBack: () -> Unit,
    onFloor: (Int) -> Unit,
    onSelectRoom: (MapRoom) -> Unit,
    onClearRoom: () -> Unit,
    onQuery: (String) -> Unit,
    onDay: (Int) -> Unit,
    onPair: (Int) -> Unit,
    onFollowNow: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val map = state.map
    val rooms = map?.roomsByFloor?.get(state.floor).orEmpty()
    val outline = map?.outlineByFloor?.get(state.floor)

    val statuses = remember(state.load, state.dayIndex, state.pairIndex, rooms) {
        rooms.filter { it.searchable }.associate { room ->
            room.id to RoomOccupancy.status(state.load, room.id, state.dayIndex, state.pairIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.map_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = map?.let { "${it.title} · ${it.address}" }
                                    ?: stringResource(R.string.map_address),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.map_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.picker_clear),
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
            )

            if (state.query.isNotEmpty()) {
                SearchResults(
                    query = state.query,
                    results = state.results,
                    onSelect = onSelectRoom,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.map_floor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                (map?.floors ?: (0..4).toList()).forEach { floor ->
                    FilterChip(
                        selected = state.floor == floor,
                        onClick = { onFloor(floor) },
                        label = { Text(floor.toString()) },
                    )
                }
            }

            Legend()

            if (map == null && state.mapError != null) {
                ErrorPanel(
                    error = state.mapError!!,
                    onRetry = onRetry,
                    onPickSource = onBack,
                    modifier = Modifier.weight(1f),
                )
            } else if (map == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.map_loading))
                }
            } else {
                FloorPlan(
                    floor = state.floor,
                    rooms = rooms,
                    outline = outline,
                    statuses = statuses,
                    selectedId = state.selectedRoomId,
                    onSelect = onSelectRoom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            if (state.loadError != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.map_load_error),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.map_disclaimer),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val selected = state.selectedRoom
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onClearRoom,
            sheetState = sheetState,
        ) {
            RoomSheet(
                room = selected,
                load = state.load,
                loadError = state.loadError,
                dayIndex = state.dayIndex,
                pairIndex = state.pairIndex,
                autoSlot = state.autoSlot,
                onDay = onDay,
                onPair = onPair,
                onFollowNow = onFollowNow,
                onRetry = onRetry,
                onOpenSource = onOpenSource,
            )
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<MapRoom>,
    onSelect: (MapRoom) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_room_not_on_scheme, query),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                results.take(6).forEach { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = room.displayLabel,
                            style = MaterialTheme.typography.bodyLarge.tabular,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.map_floor_format, room.floor),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onSelect(room) }) {
                            Text(stringResource(R.string.map_show))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val freeColor = LocalLessonColors.current.color(LessonType.PRACTICE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = freeColor, label = stringResource(R.string.map_legend_free))
        LegendItem(
            color = MaterialTheme.colorScheme.error,
            label = stringResource(R.string.map_legend_busy),
        )
        LegendItem(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.map_legend_selected),
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.width(10.dp).height(10.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoomSheet(
    room: MapRoom,
    load: BuildingLoad?,
    loadError: TimetableError?,
    dayIndex: Int,
    pairIndex: Int,
    autoSlot: Boolean,
    onDay: (Int) -> Unit,
    onPair: (Int) -> Unit,
    onFollowNow: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val status = RoomOccupancy.status(load, room.id, dayIndex, pairIndex)
    val href = load?.rooms?.entries
        ?.firstOrNull { RoomCodes.normalize(it.key) == RoomCodes.normalize(room.id) }
        ?.value?.href

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = room.displayLabel,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.map_floor_format, room.floor),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        RoomStatusBlock(status = status, load = load, loadError = loadError, onRetry = onRetry)

        val slot = load?.pairs?.getOrNull(pairIndex)
        if (load != null && slot != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.map_slot_format,
                        pairIndex + 1,
                        TomskTime.formatMinutes(slot.startMinutes),
                        TomskTime.formatMinutes(slot.endMinutes),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!autoSlot) {
                    TextButton(onClick = onFollowNow) {
                        Text(stringResource(R.string.map_follow_now))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.map_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (day in 0..5) {
                    val date = load.monday.plusDays(day.toLong())
                    FilterChip(
                        selected = dayIndex == day,
                        onClick = { onDay(day) },
                        label = {
                            Text(
                                text = "${TomskTime.weekdayShort(date)} ${date.dayOfMonth}",
                                style = MaterialTheme.typography.labelMedium.tabular,
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                load.pairs.forEachIndexed { index, _ ->
                    FilterChip(
                        selected = pairIndex == index,
                        onClick = { onPair(index) },
                        label = { Text((index + 1).toString()) },
                    )
                }
            }
        }

        if (status is RoomStatus.Busy && status.lessons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.map_busy_lessons),
                style = MaterialTheme.typography.titleSmall,
            )
            status.lessons.forEach { lesson ->
                Text(
                    text = buildString {
                        append(lesson.title)
                        if (lesson.group.isNotBlank()) append(" · ").append(lesson.group)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (lesson.who.isNotBlank()) {
                    Text(
                        text = lesson.who,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (href != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { onOpenSource(href) }) {
                Text(stringResource(R.string.map_open_source))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.map_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoomStatusBlock(
    status: RoomStatus,
    load: BuildingLoad?,
    loadError: TimetableError?,
    onRetry: () -> Unit,
) {
    val freeColor = LocalLessonColors.current.color(LessonType.PRACTICE)
    val container = when (status) {
        is RoomStatus.Free -> freeColor.copy(alpha = 0.14f)
        is RoomStatus.Busy -> MaterialTheme.colorScheme.errorContainer
        RoomStatus.NotInSchedule -> MaterialTheme.colorScheme.surfaceVariant
    }
    val text = when {
        load == null && loadError != null -> stringResource(R.string.map_load_error)
        load == null -> stringResource(R.string.map_loading)
        status is RoomStatus.Free -> status.untilMinutes?.let {
            stringResource(R.string.room_free_until, TomskTime.formatMinutes(it))
        } ?: stringResource(R.string.room_free_all_day)

        status is RoomStatus.Busy -> status.untilMinutes?.let {
            stringResource(R.string.room_busy_until, TomskTime.formatMinutes(it))
        } ?: stringResource(R.string.room_busy_all_day)

        else -> stringResource(R.string.room_not_in_schedule)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (load == null && loadError != null) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
