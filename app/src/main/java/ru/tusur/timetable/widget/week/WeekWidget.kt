package ru.tusur.timetable.widget.week

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.widget.WidgetBody
import ru.tusur.timetable.widget.WidgetDay
import ru.tusur.timetable.widget.WidgetHeader
import ru.tusur.timetable.widget.WidgetMuted
import ru.tusur.timetable.widget.WidgetSnapshot
import ru.tusur.timetable.widget.openAppIntent
import ru.tusur.timetable.widget.pluralPairs
import ru.tusur.timetable.widget.widgetEntryPoint
import ru.tusur.timetable.widget.widgetSurface
import java.time.LocalDate
import kotlin.math.min

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = widgetEntryPoint(context).snapshotProvider().forApp()
        provideContent {
            GlanceTheme {
                WeekContent(snapshot = snapshot, context = context)
            }
        }
    }
}

@Composable
fun WeekContent(snapshot: WidgetSnapshot, context: Context) {
    val days = snapshot.week.take(6)
    val wide = LocalSize.current.width >= 280.dp
    val monday = days.firstOrNull()?.date
    val sunday = days.lastOrNull()?.date

    Column(modifier = GlanceModifier.widgetSurface()) {
        Text(
            text = buildString {
                append("Неделя")
                if (monday != null && sunday != null) {
                    append(" · ")
                    append(TomskTime.formatRange(monday))
                }
            },
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
            maxLines = 1,
        )
        WidgetHeader(snapshot.sourceTitle)
        Spacer(GlanceModifier.height(6.dp))

        when {
            !snapshot.hasSource -> WidgetBody(
                "Откройте приложение и выберите группу или преподавателя",
                maxLines = 4,
            )

            !snapshot.hasCache -> WidgetBody(
                "Нет сохранённого расписания. Откройте приложение и обновите данные.",
                maxLines = 4,
            )

            days.isEmpty() -> WidgetBody("Нет данных на эту неделю", maxLines = 3)

            wide -> {
                days.chunked(3).forEach { rowDays ->
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        rowDays.forEach { day ->
                            DayCell(
                                day = day,
                                today = snapshot.today,
                                sourceKey = snapshot.sourceKey,
                                context = context,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                        repeat(3 - rowDays.size) {
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            }

            else -> {
                val visible = days.take(min(days.size, 6))
                visible.forEach { day ->
                    DayCell(
                        day = day,
                        today = snapshot.today,
                        sourceKey = snapshot.sourceKey,
                        context = context,
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: WidgetDay,
    today: LocalDate,
    sourceKey: String?,
    context: Context,
    modifier: GlanceModifier,
) {
    val isToday = day.date == today
    Column(
        modifier = modifier
            .padding(2.dp)
            .background(
                if (isToday) {
                    GlanceTheme.colors.primaryContainer
                } else {
                    GlanceTheme.colors.surfaceVariant
                },
            )
            .cornerRadius(12.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(actionStartActivity(openAppIntent(context, day.date, sourceKey))),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${TomskTime.weekdayShort(day.date)} ${day.date.dayOfMonth}",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant,
            ),
            maxLines = 1,
        )
        Text(
            text = if (day.lessonCount == 0) "нет пар" else "${day.lessonCount} ${pluralPairs(day.lessonCount)}",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = GlanceTheme.colors.onSurface,
            ),
            maxLines = 1,
        )
        WidgetMuted(if (day.lessonCount > 0) "нажмите" else "свободно", maxLines = 1)
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
