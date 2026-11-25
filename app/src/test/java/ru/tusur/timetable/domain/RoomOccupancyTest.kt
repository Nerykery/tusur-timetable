package ru.tusur.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.domain.model.BuildingLoad
import ru.tusur.timetable.domain.model.BusyLesson
import ru.tusur.timetable.domain.model.LoadedPair
import ru.tusur.timetable.domain.model.RoomBusy
import ru.tusur.timetable.domain.model.RoomOccupancy
import ru.tusur.timetable.domain.model.RoomStatus
import java.time.LocalDate

class RoomOccupancyTest {

    private val pairs = listOf(
        LoadedPair(0, 8 * 60 + 50, 10 * 60 + 25),
        LoadedPair(1, 10 * 60 + 40, 12 * 60 + 15),
        LoadedPair(2, 13 * 60 + 15, 14 * 60 + 50),
        LoadedPair(3, 15 * 60, 16 * 60 + 35),
        LoadedPair(4, 16 * 60 + 45, 18 * 60 + 20),
        LoadedPair(5, 18 * 60 + 30, 20 * 60 + 5),
        LoadedPair(6, 20 * 60 + 15, 21 * 60 + 50),
    )

    private val busyAt13 = BusyLesson("Программирование ПЛИС", "023", "Костина М.А.", "laboratory")

    private fun load(busy: Map<Int, List<BusyLesson>>) = BuildingLoad(
        monday = LocalDate.of(2026, 11, 23),
        parity = "нечётная",
        pairs = pairs,
        rooms = mapOf("331b" to RoomBusy(href = "https://timetable.tusur.ru", cells = busy)),
    )

    @Test
    fun `busy cells are indexed by day and pair`() {
        // день 0 (Пн), пары 1 и 2 заняты; «Занята до» — конец последней занятой пары
        val load = load(mapOf(1 to listOf(busyAt13), 2 to listOf(busyAt13)))
        val status = RoomOccupancy.status(load, "331б", dayIndex = 0, pairIndex = 1) as RoomStatus.Busy

        assertEquals(14 * 60 + 50, status.untilMinutes)
        assertEquals("Программирование ПЛИС", status.lessons.first().title)
    }

    @Test
    fun `free room reports next busy start`() {
        val load = load(mapOf(2 to listOf(busyAt13)))
        val status = RoomOccupancy.status(load, "331b", dayIndex = 0, pairIndex = 0) as RoomStatus.Free

        assertEquals(13 * 60 + 15, status.untilMinutes)
    }

    @Test
    fun `free room at end of day has no until`() {
        val load = load(mapOf(0 to listOf(busyAt13)))
        val status = RoomOccupancy.status(load, "331b", dayIndex = 0, pairIndex = 6) as RoomStatus.Free
        assertNull(status.untilMinutes)
    }

    @Test
    fun `busy state extends through consecutive pairs`() {
        val load = load(
            mapOf(
                3 to listOf(busyAt13),
                4 to listOf(busyAt13),
                5 to listOf(busyAt13),
            ),
        )
        val status = RoomOccupancy.status(load, "331B", dayIndex = 0, pairIndex = 3) as RoomStatus.Busy
        assertEquals(20 * 60 + 5, status.untilMinutes)
    }

    @Test
    fun `unknown room is not in schedule`() {
        val load = load(emptyMap())
        assertEquals(RoomStatus.NotInSchedule, RoomOccupancy.status(load, "999", 0, 0))
    }

    @Test
    fun `current or next slot skips finished pairs`() {
        val load = load(emptyMap())
        assertEquals(0, load.currentOrNextSlot(8 * 60)?.pairIndex)
        assertEquals(2, load.currentOrNextSlot(13 * 60 + 30)?.pairIndex)
        assertEquals(null, load.currentOrNextSlot(22 * 60))
        assertTrue(load.currentOrNextSlot(13 * 60)?.upcoming == true)
    }

    @Test
    fun `room codes normalize cyrillic suffixes`() {
        assertEquals("331b", ru.tusur.timetable.domain.model.RoomCodes.normalize("ф 331б"))
        assertEquals("331b", ru.tusur.timetable.domain.model.RoomCodes.normalize("331Б"))
        assertEquals("115a", ru.tusur.timetable.domain.model.RoomCodes.normalize("115а"))
        assertEquals("331б", ru.tusur.timetable.domain.model.RoomCodes.display("331b"))
    }
}
