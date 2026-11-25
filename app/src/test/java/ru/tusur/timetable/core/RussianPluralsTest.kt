package ru.tusur.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tusur.timetable.core.format.RussianPlurals

class RussianPluralsTest {

    @Test
    fun `pairs plural forms`() {
        assertEquals("пара", RussianPlurals.pairs(1))
        assertEquals("пары", RussianPlurals.pairs(2))
        assertEquals("пары", RussianPlurals.pairs(4))
        assertEquals("пар", RussianPlurals.pairs(5))
        assertEquals("пар", RussianPlurals.pairs(11))
        assertEquals("пар", RussianPlurals.pairs(12))
        assertEquals("пара", RussianPlurals.pairs(21))
        assertEquals("пары", RussianPlurals.pairs(22))
        assertEquals("пар", RussianPlurals.pairs(0))
    }

    @Test
    fun `short duration is compact`() {
        assertEquals("25 мин", RussianPlurals.shortDuration(25))
        assertEquals("1 ч", RussianPlurals.shortDuration(60))
        assertEquals("1 ч 10 мин", RussianPlurals.shortDuration(70))
        assertEquals("0 мин", RussianPlurals.shortDuration(-3))
    }

    @Test
    fun `pair ordinal`() {
        assertEquals("3-я пара", RussianPlurals.pairOrdinal(3))
    }
}
