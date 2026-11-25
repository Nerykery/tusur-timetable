package ru.tusur.timetable.ui.schedule

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.TimelineItem
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.model.computeDayStatus
import ru.tusur.timetable.domain.model.buildTimeline
import ru.tusur.timetable.ui.components.ErrorPanel
import ru.tusur.timetable.ui.components.EmptyDayCard
import ru.tusur.timetable.ui.components.OfflineBanner
import ru.tusur.timetable.ui.picker.PickerSheet
import ru.tusur.timetable.ui.picker.PickerUiState
import java.time.LocalDate

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onSelectDay: (LocalDate) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSourceSelected: (SearchItem) -> Unit,
    onSavedSelected: (SourceSelection) -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onPickerReset: () -> Unit,
    pickerState: PickerUiState,
    pickerSaved: SourceSelection?,
    pickerRecent: List<SearchItem>,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRangeWarningShown: () -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenPerson: (PersonRef) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val rangeMessage = stringResource(R.string.error_bad_week)
    LaunchedEffect(state.rangeWarning) {
        if (state.rangeWarning) {
            snackbarHostState.showSnackbar(rangeMessage)
            onRangeWarningShown()
        }
    }

    val week = state.week
    val days = remember(week, state.monday) { buildDayChips(week, state.monday) }
    val lessons = week?.day(state.selectedDate)?.lessons.orEmpty()
    val status = remember(lessons, state.nowMinutes) { computeDayStatus(lessons, state.nowMinutes) }
    val timeline = remember(lessons) { buildTimeline(lessons) }
    val nearest = remember(week, state.selectedDate) {
        week?.days?.firstOrNull { it.date.isAfter(state.selectedDate) && it.hasLessons }
    }
    val subtitle = remember(state.monday, state.parityLabel) {
        buildString {
            append(TomskTime.formatRange(state.monday))
            state.parityLabel?.let { append(" · ").append(it) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScheduleTopBar(
                subtitle = subtitle,
                refreshing = state.refreshing && week != null,
                onSearch = onOpenPicker,
                onSettings = onOpenSettings,
                onMap = { onOpenMap("") },
                onAbout = onOpenAbout,
                onShare = onShare,
            )
        },
        bottomBar = {
            WeekFooter(
                sourceLabel = state.sourceLabel,
                isToday = state.isShowingToday,
                onPreviousWeek = onPrevWeek,
                onNextWeek = onNextWeek,
                onPickSource = onOpenPicker,
                onToday = onToday,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.isOffline || state.staleAt != null) {
                OfflineBanner(
                    fetchedAt = state.fetchedAt,
                    staleAt = state.staleAt,
                    onRefresh = onRefresh,
                )
            }
            DaySelector(
                days = days,
                selected = state.selectedDate,
                today = state.today,
                onSelect = onSelectDay,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .weekSwipe(
                        enabled = !state.showPicker,
                        onSwipeLeft = onNextDay,
                        onSwipeRight = onPrevDay,
                    ),
            ) {
                when {
                    week == null && state.loading -> LoadingBox()
                    week == null && state.error != null -> ErrorPanel(
                        error = state.error,
                        onRetry = onRetry,
                        onPickSource = onOpenPicker,
                    )

                    week == null -> ChooseSourcePrompt(onOpenPicker = onOpenPicker)
                    else -> DayContent(
                        state = state,
                        lessons = lessons,
                        timeline = timeline,
                        status = status,
                        nearest = nearest,
                        onRefresh = onRefresh,
                        onOpenPerson = onOpenPerson,
                        onOpenMap = onOpenMap,
                        onSelectDay = onSelectDay,
                    )
                }
            }
        }
    }

    if (state.showPicker) {
        LaunchedEffect(Unit) { onPickerReset() }
        PickerSheet(
            state = pickerState,
            saved = pickerSaved,
            recent = pickerRecent,
            onQueryChange = onPickerQueryChange,
            onSelect = onSourceSelected,
            onSelectSaved = onSavedSelected,
            onDismiss = onClosePicker,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DayContent(
    state: ScheduleUiState,
    lessons: List<ru.tusur.timetable.domain.model.Lesson>,
    timeline: List<TimelineItem>,
    status: ru.tusur.timetable.domain.model.DayStatus,
    nearest: ScheduleDay?,
    onRefresh: () -> Unit,
    onOpenPerson: (PersonRef) -> Unit,
    onOpenMap: (String) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.isShowingToday && lessons.isNotEmpty()) {
                item(key = "status") {
                    DayStatusCard(status = status, nowMinutes = state.nowMinutes)
                }
            }
            item(key = "day-header") {
                DayHeader(
                    date = state.selectedDate,
                    isToday = state.isShowingToday,
                )
            }
            if (!state.isShowingToday && lessons.isNotEmpty()) {
                item(key = "summary") {
                    WeekSummary(count = lessons.size)
                }
            }
            items(
                items = timeline,
                key = { item ->
                    when (item) {
                        is TimelineItem.LessonItem -> "lesson-${item.lesson.position}"
                        is TimelineItem.GapItem -> "gap-${item.startMinutes}-${item.endMinutes}"
                    }
                },
            ) { item ->
                when (item) {
                    is TimelineItem.LessonItem -> LessonCard(
                        lesson = item.lesson,
                        phase = item.lesson.phaseAt(state.nowMinutes),
                        onOpenPerson = onOpenPerson,
                        onOpenMap = onOpenMap,
                    )

                    is TimelineItem.GapItem -> GapRow(item = item)
                }
            }
            if (lessons.isEmpty()) {
                item(key = "empty") {
                    EmptyDayCard(
                        nearestTitle = nearest?.let {
                            "${TomskTime.weekdayShort(it.date)}, ${TomskTime.formatHumanDate(it.date)}"
                        },
                        onGoNearest = {
                            nearest?.let { onSelectDay(it.date) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_schedule),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChooseSourcePrompt(onOpenPicker: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.choose_source_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.choose_source_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onOpenPicker) {
            Text(stringResource(R.string.choose_source_action))
        }
    }
}

@Composable
private fun ScheduleTopBar(
    subtitle: String,
    refreshing: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onMap: () -> Unit,
    onAbout: () -> Unit,
    onShare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.schedule_search),
                    )
                }
                if (refreshing) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_map)) },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMap()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_about)) },
                            onClick = {
                                menuOpen = false
                                onAbout()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekFooter(
    sourceLabel: String,
    isToday: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPickSource: () -> Unit,
    onToday: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousWeek, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_week),
                    )
                }
                TextButton(
                    onClick = onPickSource,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = sourceLabel.ifBlank { stringResource(R.string.choose_source_action) },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.change_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onNextWeek, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_week),
                    )
                }
                if (!isToday) {
                    TextButton(onClick = onToday) {
                        Text(stringResource(R.string.today))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun WeekLessonsCaption(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.lessons_count, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun buildDayChips(week: Week?, monday: LocalDate): List<DayChip> {
    if (week != null && week.days.isNotEmpty()) {
        return week.days.map { DayChip(it.date, it.hasLessons) }
    }
    return (0..6).map { offset ->
        DayChip(monday.plusDays(offset.toLong()), hasLessons = false)
    }
}

private fun Modifier.weekSwipe(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -SWIPE_THRESHOLD -> onSwipeLeft()
                    total > SWIPE_THRESHOLD -> onSwipeRight()
                }
            },
        ) { change, dragAmount ->
            total += dragAmount
            change.consume()
        }
    }
}

private const val SWIPE_THRESHOLD = 90f
