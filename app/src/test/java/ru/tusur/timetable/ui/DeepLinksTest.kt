package ru.tusur.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.tusur.timetable.ui.navigation.AppLink
import ru.tusur.timetable.ui.navigation.DeepLinks

class DeepLinksTest {

    @Test
    fun `day link`() {
        assertEquals(
            AppLink.Day("2026-11-24"),
            DeepLinks.parse("timetable://day/2026-11-24"),
        )
        assertEquals("schedule?date=2026-11-24", DeepLinks.routeFor(AppLink.Day("2026-11-24")))
    }

    @Test
    fun `group link keeps faculty`() {
        assertEquals(
            AppLink.Source("group:fit/023", null),
            DeepLinks.parse("timetable://group/fit/023"),
        )
        assertEquals(
            "schedule?source=group%3Afit%2F023",
            DeepLinks.routeFor(AppLink.Source("group:fit/023")),
        )
    }

    @Test
    fun `teacher link with date`() {
        val link = DeepLinks.parse("timetable://teacher/35663?date=2026-11-24")
        assertEquals(AppLink.Source("teacher:35663", "2026-11-24"), link)
        assertEquals(
            "schedule?source=teacher%3A35663&date=2026-11-24",
            DeepLinks.routeFor(link!!),
        )
    }

    @Test
    fun `map link decodes room`() {
        assertEquals(AppLink.Map("331б"), DeepLinks.parse("timetable://map?r=331%D0%B1"))
        assertEquals("map?room=331%D0%B1", DeepLinks.routeFor(AppLink.Map("331б")))
    }

    @Test
    fun `foreign and broken links are ignored`() {
        assertNull(DeepLinks.parse("https://timetable.iptvik.ru/?g=023"))
        assertNull(DeepLinks.parse("timetable://unknown/1"))
        assertNull(DeepLinks.parse(null))
        assertNull(DeepLinks.parse(""))
    }
}
