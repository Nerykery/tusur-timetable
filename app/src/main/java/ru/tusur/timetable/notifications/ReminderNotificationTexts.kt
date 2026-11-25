package ru.tusur.timetable.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.tusur.timetable.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationTexts @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun title(content: ReminderPlanner.Content): String = when (content) {
        is ReminderPlanner.Content.BeforeClass -> context.resources.getQuantityString(
            R.plurals.reminder_before_title,
            content.minutesBefore,
            content.minutesBefore,
        )

        is ReminderPlanner.Content.Digest -> context.getString(
            if (content.forTomorrow) {
                R.string.reminder_digest_tomorrow
            } else {
                R.string.reminder_digest_today
            },
        )
    }

    fun text(content: ReminderPlanner.Content): String = when (content) {
        is ReminderPlanner.Content.BeforeClass -> {
            val parts = buildList {
                content.pair?.let { add(context.getString(R.string.reminder_pair_format, it)) }
                add(content.startLabel)
                add(content.title)
                content.place?.let { add(it) }
            }
            parts.joinToString(" · ")
        }

        is ReminderPlanner.Content.Digest -> {
            when {
                content.dataMissing -> context.getString(R.string.reminder_digest_no_data)
                content.lessons.isEmpty() -> context.getString(R.string.reminder_no_lessons)
                else -> content.lessons.joinToString("\n") { lesson ->
                    buildString {
                        append(lesson.startLabel)
                        append("  ")
                        append(lesson.title)
                        lesson.place?.let { append(" · ").append(it) }
                    }
                }
            }
        }
    }
}
