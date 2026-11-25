package ru.tusur.timetable.core.format

import kotlin.math.abs

/**
 * Русская плюрализация для текстов, которые строятся вне Compose
 * (уведомления, подписи виджетов): 1 пара, 2 пары, 5 пар.
 */
object RussianPlurals {

    fun pairs(count: Int): String = plural(count, "пара", "пары", "пар")

    fun lessons(count: Int): String = plural(count, "занятие", "занятия", "занятий")

    fun minutes(count: Int): String = plural(count, "минута", "минуты", "минут")

    fun hours(count: Int): String = plural(count, "час", "часа", "часов")

    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = abs(count) % 100
        val mod10 = abs(count) % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    /** «ещё 25 мин», «через 1 ч 10 мин» — компактная форма для виджетов. */
    fun shortDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours == 0 -> "$mins мин"
            mins == 0 -> "$hours ч"
            else -> "$hours ч $mins мин"
        }
    }

    /** «3-я пара» / «пара 3». */
    fun pairOrdinal(pair: Int): String = "$pair-я пара"
}
