package ru.tusur.timetable.widget.quick

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.tusur.timetable.core.format.RussianPlurals
import ru.tusur.timetable.widget.WidgetBody
import ru.tusur.timetable.widget.WidgetDisplayMode
import ru.tusur.timetable.widget.WidgetHeader
import ru.tusur.timetable.widget.WidgetMuted
import ru.tusur.timetable.widget.WidgetSnapshot
import ru.tusur.timetable.widget.WidgetTime
import ru.tusur.timetable.widget.openAppIntent
import ru.tusur.timetable.widget.widgetEntryPoint
import ru.tusur.timetable.widget.widgetSurface

class QuickPickWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, mode) = widgetEntryPoint(context)
            .snapshotProvider()
            .forWidget(id.toString())
        val intent = openAppIntent(context, snapshot.today, snapshot.sourceKey)
        provideContent {
            GlanceTheme {
                QuickPickContent(snapshot = snapshot, mode = mode, openIntent = intent)
            }
        }
    }
}

@Composable
fun QuickPickContent(snapshot: WidgetSnapshot, mode: WidgetDisplayMode, openIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .widgetSurface()
            .clickable(actionStartActivity(openIntent)),
    ) {
        WidgetHeader(snapshot.sourceTitle.ifBlank { "Быстрый выбор" })
        Spacer(GlanceModifier.height(4.dp))

        when {
            !snapshot.hasSource -> WidgetBody(
                "Настройте виджет: выберите группу или преподавателя в конфигурации",
                maxLines = 4,
            )

            !snapshot.hasCache -> WidgetBody(
                "Откройте приложение и обновите расписание",
                maxLines = 3,
            )

            mode == WidgetDisplayMode.TODAY -> {
                if (snapshot.lessons.isEmpty()) {
                    WidgetBody("Сегодня пар нет", maxLines = 2)
                } else {
                    snapshot.lessons.take(3).forEach { lesson ->
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            WidgetTime(lesson.startLabel, size = 12)
                            Spacer(GlanceModifier.width(6.dp))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = lesson.title,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = GlanceTheme.colors.onSurface,
                                    ),
                                    maxLines = 1,
                                )
                                WidgetMuted(lesson.place.orEmpty(), maxLines = 1)
                            }
                        }
                    }
                }
            }

            snapshot.status.live != null -> {
                val lesson = snapshot.status.live
                Text(
                    text = "Идёт ${lesson.pairLabel ?: "пара"}",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                    maxLines = 1,
                )
                WidgetTime("ещё ${snapshot.status.minutesLeft ?: 0} мин", size = 22)
                WidgetBody(lesson.title, maxLines = 1)
                lesson.place?.let { WidgetMuted(it, maxLines = 1) }
            }

            snapshot.status.next != null -> {
                val lesson = snapshot.status.next
                WidgetMuted(
                    "Следующая через ${RussianPlurals.shortDuration(snapshot.status.minutesToNext ?: 0)}",
                    maxLines = 1,
                )
                WidgetTime(lesson.startLabel, size = 24)
                WidgetBody(lesson.title, maxLines = 1)
                lesson.place?.let { WidgetMuted(it, maxLines = 1) }
            }

            else -> WidgetBody("Пары на сегодня закончились", maxLines = 3)
        }
    }
}

class QuickPickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickPickWidget()
}
