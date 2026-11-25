package ru.tusur.timetable.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.tusur.timetable.core.time.TomskTime
import ru.tusur.timetable.ui.MainActivity
import java.time.LocalDate

@Composable
internal fun GlanceModifier.widgetSurface(): GlanceModifier = this
    .fillMaxSize()
    .background(GlanceTheme.colors.surface)
    .cornerRadius(24.dp)
    .padding(14.dp)

@Composable
internal fun WidgetHeader(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = GlanceTheme.colors.onSurfaceVariant,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetTime(text: String, size: Int = 24) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            color = GlanceTheme.colors.onSurface,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetBody(text: String, maxLines: Int = 2) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 12.sp,
            color = GlanceTheme.colors.onSurface,
        ),
        maxLines = maxLines,
    )
}

@Composable
internal fun WidgetMuted(text: String, maxLines: Int = 2) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 11.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
        ),
        maxLines = maxLines,
    )
}

internal fun openAppIntent(
    context: Context,
    date: LocalDate? = null,
    sourceKey: String? = null,
): Intent {
    val uri = deepLink(date = date, sourceKey = sourceKey)
    return Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(uri)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

internal fun deepLink(date: LocalDate?, sourceKey: String?): String {
    val base = when {
        sourceKey == null -> "timetable://day"
        sourceKey.startsWith("group:") -> "timetable://group/" + sourceKey.removePrefix("group:")
        sourceKey.startsWith("teacher:") -> "timetable://teacher/" + sourceKey.removePrefix("teacher:")
        else -> "timetable://day"
    }
    return when {
        date == null -> base
        base == "timetable://day" -> "$base/$date"
        else -> "$base?date=$date"
    }
}

internal fun dayCaption(date: LocalDate): String =
    "${TomskTime.weekdayShort(date)}, ${date.dayOfMonth} ${TomskTime.monthName(date.month)}"

internal fun pluralPairs(count: Int): String = ru.tusur.timetable.core.format.RussianPlurals.pairs(count)
