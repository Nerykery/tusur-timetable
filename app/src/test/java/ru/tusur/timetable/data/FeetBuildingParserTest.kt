package ru.tusur.timetable.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.data.map.FeetBuildingParser
import ru.tusur.timetable.domain.model.RoomCodes
import java.io.File

class FeetBuildingParserTest {

    private val parser = FeetBuildingParser(Json { ignoreUnknownKeys = true })

    private fun parseRealFile() = parser.parse(
        File("src/main/assets/fet.json").readText(),
    )

    @Test
    fun `real fet json gives five floors`() {
        val map = parseRealFile()
        assertEquals("ФЭТ", map.title)
        assertEquals(listOf(0, 1, 2, 3, 4), map.floors)
        assertTrue(map.hallFloors.contains(1))
    }

    @Test
    fun `auditoriums keep internal ids and cyrillic labels`() {
        val map = parseRealFile()
        val room = map.find("331б")
        assertEquals("331b", room?.id)
        assertEquals("331б", room?.displayLabel)
        assertEquals(3, room?.floor)

        val lab = map.find("115б")
        assertEquals("115b", lab?.id)
    }

    @Test
    fun `floor one uses detailed plan and others use corridor slots`() {
        val map = parseRealFile()
        assertTrue(map.rooms(1).size > 20)
        assertTrue(map.rooms(0).size >= 6)
        assertTrue(map.rooms(3).any { it.id == "329" || it.id == "330" })
        assertEquals("005", map.find("005")?.id)
    }

    @Test
    fun `outline has courtyard hole and hall annex only on first floor`() {
        val map = parseRealFile()
        assertEquals(1, map.outlineByFloor[3]?.holes?.size)
        assertTrue(map.outlineByFloor[1]!!.annex.isNotEmpty())
        assertTrue(map.outlineByFloor[2]!!.annex.isEmpty())
    }

    @Test
    fun `room codes normalize user input`() {
        assertEquals("331b", RoomCodes.normalize("331б"))
        assertEquals("331b", RoomCodes.normalize("ф 331Б"))
        assertEquals("005", RoomCodes.normalize("005"))
        assertEquals("331б", RoomCodes.display("331b"))
    }
}
