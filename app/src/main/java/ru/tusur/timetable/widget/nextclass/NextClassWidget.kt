package ru.tusur.timetable.widget.nextclass

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
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.tusur.timetable.core.format.RussianPlurals
import ru.tusur.timetable.widget.WidgetBody
import ru.tusur.timetable.widget.WidgetHeader
import ru.tusur.timetable.widget.WidgetMuted
import ru.tusur.timetable.widget.WidgetSnapshot
import ru.tusur.timetable.widget.WidgetTime
import ru.tusur.timetable.widget.openAppIntent
import ru.tusur.timetable.widget.widgetEntryPoint
import ru.tusur.timetable.widget.widgetSurface

class NextClassWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = widgetEntryPoint(context).snapshotProvider().forApp()
        val intent = openAppIntent(context, snapshot.today, snapshot.sourceKey)
        provideContent {
            GlanceTheme {
                NextClassContent(snapshot = snapshot, openIntent = intent)
            }
        }
    }
}

@Composable
fun NextClassContent(snapshot: WidgetSnapshot, openIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .widgetSurface()
            .clickable(actionStartActivity(openIntent)),
    ) {
        WidgetHeader(snapshot.sourceTitle.ifBlank { "Расписание" })
        Spacer(GlanceModifier.height(6.dp))

        val status = snapshot.status
        when {
            !snapshot.hasSource -> WidgetBody(
                "Откройте приложение и выберите группу или преподавателя",
                maxLines = 4,
            )

            !snapshot.hasCache -> WidgetBody(
                "Нет сохранённого расписания. Откройте приложение и обновите данные.",
                maxLines = 4,
            )

            status.live != null -> {
                val lesson = status.live
                Text(
                    text = "Идёт ${lesson.pairLabel ?: "пара"}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                    maxLines = 1,
                )
                WidgetTime("ещё ${status.minutesLeft ?: 0} мин", size = 22)
                WidgetBody(lesson.title, maxLines = 1)
                lesson.place?.let { WidgetMuted(it, maxLines = 1) }
            }

            status.next != null -> {
                val lesson = status.next
                WidgetMuted(
                    "Следующая через ${RussianPlurals.shortDuration(status.minutesToNext ?: 0)}",
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                WidgetTime(lesson.startLabel, size = 26)
                WidgetBody(lesson.title, maxLines = 1)
                lesson.place?.let { WidgetMuted(it, maxLines = 1) }
            }

            else -> WidgetBody("Пары на сегодня закончились", maxLines = 3)
        }

        if (snapshot.staleAt != null && snapshot.hasCache) {
            Spacer(GlanceModifier.height(4.dp))
            WidgetMuted("Данные от ${snapshot.staleAt}", maxLines = 1)
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}
