package ru.tusur.timetable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.ScheduleDay
import ru.tusur.timetable.domain.model.TimetableError
import ru.tusur.timetable.domain.model.TimetableErrorKind
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.ui.picker.PickerUiState
import ru.tusur.timetable.ui.schedule.ScheduleScreen
import ru.tusur.timetable.ui.schedule.ScheduleUiState
import ru.tusur.timetable.ui.theme.TusurTheme
import java.time.Instant
import java.time.LocalDate

class ScheduleScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 23)
    private val today = LocalDate.of(2026, 11, 25)

    private fun lesson(date: LocalDate, pair: Int, start: Int, end: Int, title: String) = Lesson(
        position = pair,
        date = date,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = title,
        kindRaw = "Лекция",
        type = LessonType.LECTURE,
        place = "ф 331б",
        who = null,
    )

    private fun sampleWeek(): Week = Week(
        sourceKey = "group:fit/023",
        title = "гр. 023",
        isTeacher = false,
        monday = monday,
        today = today,
        parity = "нечётная",
        days = listOf(
            ScheduleDay(monday, listOf(lesson(monday, 5, 16 * 60 + 45, 18 * 60 + 20, "Программирование ПЛИС"))),
            ScheduleDay(monday.plusDays(1), listOf(lesson(monday.plusDays(1), 3, 13 * 60 + 15, 14 * 60 + 50, "Современные проблемы электроники"))),
            ScheduleDay(today, listOf(lesson(today, 3, 13 * 60 + 15, 14 * 60 + 50, "Схемотехника"))),
            ScheduleDay(monday.plusDays(3), emptyList()),
            ScheduleDay(monday.plusDays(4), listOf(lesson(monday.plusDays(4), 2, 10 * 60 + 40, 12 * 60 + 15, "Физика"))),
            ScheduleDay(monday.plusDays(5), emptyList()),
            ScheduleDay(monday.plusDays(6), emptyList()),
        ),
        sourceUrl = null,
        staleAt = null,
        fetchedAt = Instant.parse("2026-11-25T09:00:00Z"),
    )

    private class Recorder {
        var selectedDate: LocalDate? = null
        var retried = false
        var openedPicker = false
    }

    private fun render(state: ScheduleUiState, recorder: Recorder) {
        rule.setContent {
            TusurTheme {
                ScheduleScreen(
                    state = state,
                    onSelectDay = { recorder.selectedDate = it },
                    onPrevDay = {},
                    onNextDay = {},
                    onPrevWeek = {},
                    onNextWeek = {},
                    onToday = {},
                    onOpenPicker = { recorder.openedPicker = true },
                    onClosePicker = {},
                    onSourceSelected = {},
                    onSavedSelected = {},
                    onPickerQueryChange = {},
                    onPickerReset = {},
                    pickerState = PickerUiState(),
                    pickerSaved = null,
                    pickerRecent = emptyList(),
                    onRetry = { recorder.retried = true },
                    onRefresh = {},
                    onRangeWarningShown = {},
                    onOpenMap = {},
                    onOpenPerson = {},
                    onOpenSettings = {},
                    onOpenAbout = {},
                    onShare = {},
                )
            }
        }
    }

    private fun state(
        selected: LocalDate = today,
        week: Week? = sampleWeek(),
        error: TimetableError? = null,
        nowMinutes: Int = 10 * 60,
    ) = ScheduleUiState(
        sourceKey = "group:fit/023",
        sourceLabel = "гр. 023",
        monday = monday,
        selectedDate = selected,
        today = today,
        nowMinutes = nowMinutes,
        week = week,
        loading = week == null && error == null,
        error = error,
        isOffline = error != null && week != null,
    )

    @Test
    fun emptyDayOffersNearestDay() {
        val recorder = Recorder()
        render(state(selected = LocalDate.of(2026, 11, 26)), recorder)

        rule.onNodeWithText("В этот день пар нет").assertIsDisplayed()
        rule.onNodeWithText("Ближайшие пары: пт, 27 ноября").performClick()
        assertEquals(LocalDate.of(2026, 11, 27), recorder.selectedDate)
    }

    @Test
    fun tappingAnotherDaySelectsIt() {
        val recorder = Recorder()
        render(state(selected = today), recorder)

        rule.onNodeWithContentDescription("вторник, 24 ноября, есть пары").performClick()
        assertEquals(LocalDate.of(2026, 11, 24), recorder.selectedDate)
    }

    @Test
    fun livePairIsShownForToday() {
        val recorder = Recorder()
        render(state(selected = today, nowMinutes = 13 * 60 + 30), recorder)

        rule.onNodeWithText("Идёт 3-я пара", substring = true).assertIsDisplayed()
        rule.onNodeWithText("ещё 80 минут", substring = true).assertIsDisplayed()
    }

    @Test
    fun networkErrorOffersRetry() {
        val recorder = Recorder()
        render(
            state(selected = today, week = null, error = TimetableError(TimetableErrorKind.NETWORK)),
            recorder,
        )

        rule.onNodeWithText("Нет связи с интернетом.").assertIsDisplayed()
        rule.onNodeWithText("Повторить").performClick()
        assertTrue(recorder.retried)
    }
}
