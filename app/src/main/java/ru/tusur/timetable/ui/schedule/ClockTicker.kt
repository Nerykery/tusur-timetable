package ru.tusur.timetable.ui.schedule

import kotlinx.coroutines.delay

/**
 * Источник «тика» минут: обновляет «идёт пара / ещё N мин», пока экран открыт.
 * Абстракция нужна тестам — иначе бесконечный цикл не даёт корутинам завершиться.
 */
interface ClockTicker {
    /** Задержка между тиками; 0 или меньше — тикер выключен. */
    val intervalMs: Long

    suspend fun awaitNextTick()
}

class DefaultClockTicker(
    override val intervalMs: Long = 30_000L,
) : ClockTicker {
    override suspend fun awaitNextTick() {
        if (intervalMs <= 0L) return
        delay(intervalMs)
    }
}
