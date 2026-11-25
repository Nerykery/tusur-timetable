package ru.tusur.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.core.time.TomskTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TomskTimeTest {

    @Test
    fun `today uses Tomsk zone regardless of device zone`() {
        // 24.11.2026 20:30 UTC = 25.11.2026 03:30 в Томске (UTC+7)
        val instant = Instant.parse("2026-11-24T20:30:00Z")
        val deviceClock = Clock.fixed(instant, ZoneId.of("America/New_York"))

        assertEquals(LocalDate.of(2026, 11, 25), TomskTime.today(deviceClock))
        assertEquals(3 * 60 + 30, TomskTime.nowMinutes(deviceClock))
    }

    @Test
    fun `monday of any day is monday of its week`() {
        assertEquals(LocalDate.of(2026, 11, 23), TomskTime.mondayOf(LocalDate.of(2026, 11, 23)))
        assertEquals(LocalDate.of(2026, 11, 23), TomskTime.mondayOf(LocalDate.of(2026, 11, 25)))
        assertEquals(LocalDate.of(2026, 11, 23), TomskTime.mondayOf(LocalDate.of(2026, 11, 29)))
    }

    @Test
    fun `week window is plus minus 30 weeks`() {
        val today = LocalDate.of(2026, 11, 25)
        val monday = TomskTime.mondayOf(today)
        assertTrue(TomskTime.isWithinWindow(monday.plusWeeks(30), today))
        assertTrue(TomskTime.isWithinWindow(monday.minusWeeks(30), today))
        assertFalse(TomskTime.isWithinWindow(monday.plusWeeks(31), today))
        assertFalse(TomskTime.isWithinWindow(monday.minusWeeks(31), today))
    }

    @Test
    fun `duration formats hours and minutes`() {
        assertEquals("25 мин", TomskTime.formatDuration(25))
        assertEquals("1 ч", TomskTime.formatDuration(60))
        assertEquals("1 ч 10 мин", TomskTime.formatDuration(70))
        assertEquals("0 мин", TomskTime.formatDuration(-5))
    }

    @Test
    fun `time parsing is tolerant`() {
        assertEquals(16 * 60 + 45, TomskTime.parseTimeToMinutes("16:45"))
        assertEquals(8 * 60 + 5, TomskTime.parseTimeToMinutes(" 08:05 "))
        assertEquals(null, TomskTime.parseTimeToMinutes("25:99"))
        assertEquals(null, TomskTime.parseTimeToMinutes(null))
    }

    @Test
    fun `minute formatting wraps around midnight`() {
        assertEquals("00:00", TomskTime.formatMinutes(0))
        assertEquals("16:45", TomskTime.formatMinutes(16 * 60 + 45))
        assertEquals("00:10", TomskTime.formatMinutes(24 * 60 + 10))
    }

    @Test
    fun `week range crosses month boundary`() {
        val monday = LocalDate.of(2026, 11, 30)
        assertEquals("30 ноября – 6 декабря", TomskTime.formatRange(monday))
    }
}
