package ru.tusur.timetable.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.widget.deepLink

class WidgetDeepLinkTest {

    @Test
    fun `day deep link for widget tap`() {
        assertEquals(
            "timetable://day/2026-11-24",
            deepLink(java.time.LocalDate.of(2026, 11, 24), null),
        )
    }

    @Test
    fun `group deep link carries date`() {
        assertEquals(
            "timetable://group/fit/023?date=2026-11-24",
            deepLink(java.time.LocalDate.of(2026, 11, 24), "group:fit/023"),
        )
    }

    @Test
    fun `teacher deep link carries date`() {
        assertEquals(
            "timetable://teacher/35612?date=2026-11-24",
            deepLink(java.time.LocalDate.of(2026, 11, 24), "teacher:35612"),
        )
    }

    @Test
    fun `widget without data is empty`() {
        assertTrue(WidgetSnapshot().isEmpty)
        assertTrue(WidgetSnapshot(hasSource = true, hasCache = false).isEmpty)
        assertTrue(!WidgetSnapshot(hasSource = true, hasCache = true).isEmpty)
    }
}
