package ru.tusur.timetable.widget.today

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
import ru.tusur.timetable.widget.WidgetBody
import ru.tusur.timetable.widget.WidgetHeader
import ru.tusur.timetable.widget.WidgetMuted
import ru.tusur.timetable.widget.WidgetSnapshot
import ru.tusur.timetable.widget.WidgetTime
import ru.tusur.timetable.widget.dayCaption
import ru.tusur.timetable.widget.openAppIntent
import ru.tusur.timetable.widget.widgetEntryPoint
import ru.tusur.timetable.widget.widgetSurface

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = widgetEntryPoint(context).snapshotProvider().forApp()
        val intent = openAppIntent(context, snapshot.today, snapshot.sourceKey)
        provideContent {
            GlanceTheme {
                TodayContent(snapshot = snapshot, openIntent = intent)
            }
        }
    }
}

@Composable
fun TodayContent(snapshot: WidgetSnapshot, openIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .widgetSurface()
            .clickable(actionStartActivity(openIntent)),
    ) {
        Text(
            text = "Сегодня · ${dayCaption(snapshot.today)}",
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

            snapshot.lessons.isEmpty() -> WidgetBody("Сегодня пар нет", maxLines = 2)

            else -> {
                val visible = snapshot.lessons.take(MAX_ROWS)
                visible.forEach { lesson ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(openIntent)),
                    ) {
                        WidgetTime(lesson.startLabel, size = 12)
                        Spacer(GlanceModifier.width(8.dp))
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
                val hidden = snapshot.lessons.size - visible.size
                if (hidden > 0) {
                    Spacer(GlanceModifier.height(2.dp))
                    WidgetMuted("+$hidden ещё", maxLines = 1)
                }
            }
        }
    }
}

private const val MAX_ROWS = 5

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
