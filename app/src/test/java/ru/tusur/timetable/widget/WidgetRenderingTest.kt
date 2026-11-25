package ru.tusur.timetable.widget

import android.content.Intent
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.tusur.timetable.domain.model.DayStatus
import ru.tusur.timetable.domain.model.Lesson
import ru.tusur.timetable.domain.model.LessonType
import ru.tusur.timetable.domain.model.computeDayStatus
import ru.tusur.timetable.widget.nextclass.NextClassContent
import ru.tusur.timetable.widget.quick.QuickPickContent
import ru.tusur.timetable.widget.today.TodayContent
import ru.tusur.timetable.widget.week.WeekContent
import java.time.LocalDate

/**
 * Рендер всех четырёх виджетов на реальных состояниях: идёт пара, следующая,
 * пустой день или нет данных. Проверяем то, что пользователь видит на экране.
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRenderingTest {
    private val context: android.content.Context =
        androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val openIntent = Intent()

    private fun lesson(start: Int, end: Int, pair: Int, title: String, place: String) = Lesson(
        position = pair,
        date = today,
        startMinutes = start,
        endMinutes = end,
        pair = pair,
        title = title,
        kindRaw = "Лекция",
        type = LessonType.LECTURE,
        place = place,
        who = null,
    )

    private fun snapshot(
        lessons: List<Lesson> = emptyList(),
        nowMinutes: Int = 17 * 60,
        hasSource: Boolean = true,
        hasCache: Boolean = true,
    ): WidgetSnapshot {
        val status = if (lessons.isEmpty()) {
            DayStatus(null, null, null, null, allFinished = false, isEmpty = true)
        } else {
            computeDayStatus(lessons, nowMinutes)
        }
        return WidgetSnapshot(
            hasSource = hasSource,
            hasCache = hasCache,
            sourceTitle = "гр. 023",
            sourceKey = "group:fit/023",
            today = today,
            nowMinutes = nowMinutes,
            lessons = lessons,
            status = status,
            week = (0..6).map { WidgetDay(today.plusDays(it.toLong()), if (it < 3) 3 else 0) },
        )
    }

    @Test
    fun nextClassShowsLivePair() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            NextClassContent(
                snapshot = snapshot(
                    lessons = listOf(lesson(16 * 60 + 45, 18 * 60 + 20, 5, "Программирование ПЛИС", "ф 331б")),
                ),
                openIntent = openIntent,
            )
        }

        onNode(hasText("Идёт 5-я пара", true)).assertExists()
        onNode(hasText("Программирование ПЛИС", true)).assertExists()
    }

    @Test
    fun nextClassShowsUpcomingPair() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            NextClassContent(
                snapshot = snapshot(
                    lessons = listOf(lesson(18 * 60 + 30, 20 * 60 + 5, 6, "Схемотехника", "ф 414")),
                ),
                openIntent = openIntent,
            )
        }

        onNode(hasText("Следующая через", true)).assertExists()
        onNode(hasText("18:30", true)).assertExists()
    }

    @Test
    fun nextClassShowsFetchPromptWithoutData() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            NextClassContent(
                snapshot = snapshot(hasSource = false, hasCache = false),
                openIntent = openIntent,
            )
        }

        onNode(hasText("Откройте приложение", true)).assertExists()
    }

    @Test
    fun todayShowsLessonRows() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            TodayContent(
                snapshot = snapshot(
                    lessons = listOf(lesson(13 * 60 + 15, 14 * 60 + 50, 3, "Схемотехника", "ф 220")),
                    nowMinutes = 9 * 60,
                ),
                openIntent = openIntent,
            )
        }

        onNode(hasText("Схемотехника", true)).assertExists()
        onNode(hasText("13:15", true)).assertExists()
    }

    @Test
    fun todayShowsEmptyState() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            TodayContent(snapshot = snapshot(lessons = emptyList()), openIntent = openIntent)
        }

        onNode(hasText("Сегодня пар нет", true)).assertExists()
    }

    @Test
    fun weekShowsDaysAndCounts() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            WeekContent(snapshot = snapshot(), context = context)
        }

        onNode(hasText("ср 25", true)).assertExists()
        onAllNodes(hasText("нет пар", true)).assertCountEquals(3)
    }

    @Test
    fun quickPickRespectsTodayMode() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            QuickPickContent(
                snapshot = snapshot(
                    lessons = listOf(
                        lesson(13 * 60 + 15, 14 * 60 + 50, 3, "Современные проблемы электроники", "ф 220"),
                    ),
                    nowMinutes = 9 * 60,
                ),
                mode = WidgetDisplayMode.TODAY,
                openIntent = openIntent,
            )
        }

        onNode(hasText("Современные проблемы электроники", true)).assertExists()
    }

    @Test
    fun quickPickWithoutConfigurationPromptsSetup() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            QuickPickContent(
                snapshot = snapshot(hasSource = false, hasCache = false),
                mode = WidgetDisplayMode.NEXT,
                openIntent = openIntent,
            )
        }

        onNode(hasText("Настройте виджет", true)).assertExists()
    }
}
