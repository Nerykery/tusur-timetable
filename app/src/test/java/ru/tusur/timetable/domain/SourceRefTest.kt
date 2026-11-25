package ru.tusur.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tusur.timetable.domain.model.PersonRef
import ru.tusur.timetable.domain.model.SourceRef

class SourceRefTest {

    @Test
    fun `parses stored keys`() {
        assertEquals(SourceRef.group("fit/023"), SourceRef.parse("group:fit/023"))
        assertEquals(SourceRef.teacher("35612"), SourceRef.parse("teacher:35612"))
        assertNull(SourceRef.parse(""))
        assertNull(SourceRef.parse(null))
        assertNull(SourceRef.parse("023"))
    }

    @Test
    fun `key roundtrip`() {
        assertEquals("group:fit/023", SourceRef.group("fit/023").key)
        assertEquals("teacher:35612", SourceRef.teacher("35612").key)
    }

    @Test
    fun `group query and official id differ`() {
        val ref = SourceRef.group("fit/023")
        assertEquals("fit/023", ref.groupQuery)
        assertEquals("023", ref.officialGroupId)
        assertEquals("https://timetable.iptvik.ru/?g=fit/023", ref.webUrl)
    }

    @Test
    fun `teacher official name comes from label`() {
        val ref = SourceRef.teacher("35612", "Костина Мария Алексеевна")
        assertEquals("Костина", ref.officialTeacherName)
        assertEquals("https://timetable.iptvik.ru/?t=35612", ref.webUrl)
        assertNull(SourceRef.teacher("35612").officialTeacherName)
    }

    @Test
    fun `dashboards label falls back to group number`() {
        assertEquals("гр. 023", SourceRef.group("fit/023").fallbackLabel)
        assertEquals("гр. 023", SourceRef.parse("group:fit/023")?.fallbackLabel)
    }

    @Test
    fun `person refs build source keys and short names`() {
        val teacher = PersonRef("Костина Мария Алексеевна", teacherId = "35612")
        assertEquals("teacher:35612", teacher.sourceKey)
        assertEquals("Костина М.А.", teacher.shortLabel)
        assertEquals("Костина", teacher.surname)

        val group = PersonRef("023", groupKey = "fit/023")
        assertEquals("group:fit/023", group.sourceKey)
        assertTrue(group.shortLabel == "023")
    }
}
