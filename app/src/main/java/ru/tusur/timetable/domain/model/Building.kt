package ru.tusur.timetable.domain.model

import ru.tusur.timetable.core.time.TomskTime
import java.time.LocalDate

data class PointF(val x: Float, val y: Float)

enum class MapRoomKind { ROOM, STAIRS, WC, ZONE, HALL, ENTRY, ATM, UNKNOWN }

data class MapRoom(
    val id: String,
    val label: String?,
    val floor: Int,
    val polygon: List<PointF>,
    val kind: MapRoomKind = MapRoomKind.ROOM,
    val info: String? = null,
) {
    val displayLabel: String get() = label ?: id
    val searchable: Boolean get() = kind == MapRoomKind.ROOM && !label.isNullOrBlank()

    val center: PointF
        get() {
            if (polygon.isEmpty()) return PointF(0f, 0f)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            polygon.forEach {
                minX = minOf(minX, it.x)
                minY = minOf(minY, it.y)
                maxX = maxOf(maxX, it.x)
                maxY = maxOf(maxY, it.y)
            }
            return PointF((minX + maxX) / 2f, (minY + maxY) / 2f)
        }

    fun contains(point: PointF): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { it != 0f } ?: 1e-6f) + a.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

data class FloorOutline(
    val building: List<PointF> = emptyList(),
    val holes: List<List<PointF>> = emptyList(),
    val annex: List<List<PointF>> = emptyList(),
)

data class BuildingMap(
    val title: String,
    val address: String,
    val building: String,
    val floors: List<Int>,
    val hallFloors: Set<Int>,
    val roomsByFloor: Map<Int, List<MapRoom>>,
    val outlineByFloor: Map<Int, FloorOutline> = emptyMap(),
) {
    fun rooms(floor: Int): List<MapRoom> = roomsByFloor[floor].orEmpty()

    fun find(roomCode: String?): MapRoom? {
        val code = RoomCodes.normalize(roomCode)
        if (code.isEmpty()) return null
        return roomsByFloor.values.flatten().firstOrNull {
            it.searchable && RoomCodes.normalize(it.id) == code
        }
    }

    fun search(query: String): List<MapRoom> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return roomsByFloor.values.flatten()
            .filter { it.searchable }
            .filter { room ->
                val id = RoomCodes.normalize(room.id)
                val label = room.displayLabel.lowercase()
                id.contains(RoomCodes.normalize(q)) || label.contains(q)
            }
            .sortedBy { it.floor }
    }
}

/**
 * Нормализация номеров аудиторий: в /api/load и fet.json используются латинские
 * суффиксы («331b», «115a»), а пользователь видит и вводит кириллицу («331б»).
 */
object RoomCodes {

    private val toLatin = mapOf('а' to 'a', 'б' to 'b', 'в' to 'v', 'г' to 'g')
    private val toCyrillic = mapOf('a' to 'а', 'b' to 'б', 'v' to 'в')

    fun normalize(raw: String?): String {
        var value = raw?.trim()?.lowercase().orEmpty()
        if (value.startsWith("ф ")) value = value.removePrefix("ф ").trim()
        if (value.startsWith("ф")) value = value.removePrefix("ф").trim()
        value = value.removePrefix("ауд.").trim()
        value = value.removePrefix("ауд").trim()
        if (value.isEmpty()) return ""
        val firstDigit = value.indexOfFirst { it.isDigit() }
        if (firstDigit < 0) return value
        val prefix = value.substring(0, firstDigit)
        val body = value.substring(firstDigit)
        val normalizedBody = buildString {
            body.forEachIndexed { index, ch ->
                append(
                    if (index > 0 && toLatin.containsKey(ch)) toLatin.getValue(ch) else ch,
                )
            }
        }
        return prefix + normalizedBody
    }

    /** «331b» -> «331б», «005» -> «005». */
    fun display(code: String): String {
        if (code.isEmpty()) return code
        return buildString {
            code.forEachIndexed { index, ch ->
                append(
                    if (index > 0 && toCyrillic.containsKey(ch)) toCyrillic.getValue(ch) else ch,
                )
            }
        }
    }
}

// ---------- Занятость аудиторий (/api/load) ----------

data class LoadedPair(
    val index: Int,
    val startMinutes: Int,
    val endMinutes: Int,
)

data class BusyLesson(
    val title: String,
    val group: String,
    val who: String,
    val kind: String,
)

data class RoomBusy(
    val href: String?,
    val cells: Map<Int, List<BusyLesson>>,
)

data class BuildingLoad(
    val monday: LocalDate,
    val parity: String?,
    val pairs: List<LoadedPair>,
    val rooms: Map<String, RoomBusy>,
) {
    val pairCount: Int get() = pairs.size.coerceAtLeast(1)

    fun lessons(room: String, dayIndex: Int, pairIndex: Int): List<BusyLesson>? {
        val roomBusy = rooms[RoomCodes.normalize(room)] ?: return null
        return roomBusy.cells[dayIndex * pairCount + pairIndex].orEmpty()
    }

    /** Текущая или ближайшая пара; null — сегодня пар больше нет. */
    fun currentOrNextSlot(nowMinutes: Int): Slot? {
        if (pairs.isEmpty()) return null
        val index = pairs.indexOfFirst { nowMinutes < it.endMinutes }
        if (index < 0) return null
        return Slot(pairIndex = index, upcoming = nowMinutes < pairs[index].startMinutes)
    }
}

data class Slot(
    val pairIndex: Int,
    val upcoming: Boolean,
) {
    val displayNumber: Int get() = pairIndex + 1
}

sealed interface RoomStatus {
    /** Свободна; until == null — до конца дня. */
    data class Free(val untilMinutes: Int?) : RoomStatus

    /** Занята; until == null — до конца дня. */
    data class Busy(val untilMinutes: Int?, val lessons: List<BusyLesson>) : RoomStatus

    data object NotInSchedule : RoomStatus
}

object RoomOccupancy {

    /**
     * Повторяет логику сайта: состояние тянется, пока следующая пара не изменит
     * занятость; «до» — конец последней занятой/свободной пары или начало следующей.
     */
    fun status(
        load: BuildingLoad?,
        roomCode: String,
        dayIndex: Int,
        pairIndex: Int,
    ): RoomStatus {
        if (load == null) return RoomStatus.NotInSchedule
        val room = load.rooms[RoomCodes.normalize(roomCode)] ?: return RoomStatus.NotInSchedule
        val pairCount = load.pairCount
        val current = room.cells[dayIndex * pairCount + pairIndex].orEmpty()
        val busy = current.isNotEmpty()
        var q = pairIndex
        while (q + 1 < pairCount) {
            val next = room.cells[dayIndex * pairCount + q + 1].orEmpty()
            if (next.isNotEmpty() != busy) break
            q++
        }
        val until = if (busy) {
            load.pairs.getOrNull(q)?.endMinutes
        } else {
            load.pairs.getOrNull(q + 1)?.startMinutes
        }
        return if (busy) RoomStatus.Busy(until, current) else RoomStatus.Free(until)
    }

    /** Ближайшее время, когда аудитория освободится/займётся, для подписи. */
    fun untilLabel(kind: RoomStatus, load: BuildingLoad?): String? = when (kind) {
        is RoomStatus.Free -> kind.untilMinutes?.let(TomskTime::formatMinutes)
        is RoomStatus.Busy -> kind.untilMinutes?.let(TomskTime::formatMinutes)
        RoomStatus.NotInSchedule -> load?.let { null }
    }
}
