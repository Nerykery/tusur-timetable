package ru.tusur.timetable.data.map

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.tusur.timetable.domain.model.BuildingMap
import ru.tusur.timetable.domain.model.FloorOutline
import ru.tusur.timetable.domain.model.MapRoom
import ru.tusur.timetable.domain.model.MapRoomKind
import ru.tusur.timetable.domain.model.PointF
import ru.tusur.timetable.domain.model.RoomCodes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Порт геометрии из /map.js сайта: кольцо коридора вокруг двора, помещения
 * с обеих сторон. Первый этаж — готовый план из fet.json.
 */
@Singleton
class FeetBuildingParser @Inject constructor(
    private val json: Json,
) {

    fun parse(raw: String): BuildingMap {
        val dto = json.decodeFromString<FeetPlanDto>(raw)
        val floors = dto.floors.keys.mapNotNull { it.toIntOrNull() }.sorted()
        val roomsByFloor = mutableMapOf<Int, List<MapRoom>>()
        val outlineByFloor = mutableMapOf<Int, FloorOutline>()

        floors.forEach { floor ->
            val layout = dto.floors[floor.toString()] ?: return@forEach
            val rooms = buildList {
                if (layout.plan != null) {
                    addAll(planRooms(floor, layout.plan))
                } else {
                    addAll(corridorRooms(floor, layout))
                }
                addAll(zoneRooms(floor, dto.zones[floor.toString()].orEmpty()))
            }
            roomsByFloor[floor] = rooms
            val hall = floor in dto.hallFloors
            outlineByFloor[floor] = FloorOutline(
                building = rectPolygon(0f, 0f, 600f, 600f),
                holes = listOf(rectPolygon(200f, 165f, 210f, 285f)),
                annex = if (hall) listOf(annexPolygon()) else emptyList(),
            )
        }

        return BuildingMap(
            title = dto.title,
            address = dto.address,
            building = dto.building,
            floors = floors,
            hallFloors = dto.hallFloors.toSet(),
            roomsByFloor = roomsByFloor,
            outlineByFloor = outlineByFloor,
        )
    }

    private fun corridorRooms(floor: Int, layout: FeetFloorDto): List<MapRoom> {
        val result = mutableListOf<MapRoom>()
        SIDES.forEach { side ->
            val slots = layout.side(side.name) ?: return@forEach
            val cells = slotRects(side, slots)
            var i = 0
            while (i < cells.size) {
                val token = cells[i].room
                if (token.isNullOrBlank() || token == "#" || token == "!pass") {
                    i++
                    continue
                }
                var rect = cells[i].rect
                var last = i
                while (last + 1 < cells.size && cells[last + 1].room == token) {
                    rect = union(rect, cells[last + 1].rect)
                    last++
                }
                result += MapRoom(
                    id = token,
                    label = if (isRealRoom(token)) RoomCodes.display(token) else null,
                    floor = floor,
                    polygon = rectPolygon(rect.x, rect.y, rect.w, rect.h),
                    kind = kindOf(token),
                )
                i = last + 1
            }
        }
        return result
    }

    private fun planRooms(floor: Int, plan: List<FeetRoomDto>): List<MapRoom> =
        plan.mapNotNull { item ->
            val polygon = when {
                item.p != null -> item.p.mapNotNull { pair ->
                    if (pair.size >= 2) PointF(pair[0], pair[1]) else null
                }

                item.r != null && item.r.size >= 4 ->
                    rectPolygon(item.r[0], item.r[1], item.r[2], item.r[3])

                else -> null
            } ?: return@mapNotNull null

            val kind = when {
                item.i != null -> kindOf(item.i)
                item.n == null -> MapRoomKind.ZONE
                else -> MapRoomKind.ROOM
            }
            val id = item.n ?: item.i ?: "zone-$floor-${item.r?.take(2)?.joinToString("-")}"
            MapRoom(
                id = id,
                label = item.t ?: item.n?.let(RoomCodes::display),
                floor = floor,
                polygon = polygon,
                kind = kind,
            )
        }

    private fun zoneRooms(floor: Int, zones: List<FeetZoneDto>): List<MapRoom> =
        zones.mapIndexed { index, zone ->
            MapRoom(
                id = "zone-$floor-$index",
                label = zone.name.takeIf { it.isNotBlank() },
                floor = floor,
                polygon = rectPolygon(zone.x, zone.y, zone.w, zone.h),
                kind = when {
                    zone.icon == "!stairs" -> MapRoomKind.STAIRS
                    zone.icon == "!entry" -> MapRoomKind.ENTRY
                    zone.icon == "!atm" -> MapRoomKind.ATM
                    zone.kind == "hall" -> MapRoomKind.HALL
                    else -> MapRoomKind.ZONE
                },
                info = zone.info,
            )
        }

    private fun isRealRoom(token: String): Boolean =
        token.isNotEmpty() && token.first().isDigit()

    private fun kindOf(token: String): MapRoomKind = when {
        token == "!stairs" -> MapRoomKind.STAIRS
        token.startsWith("!wcf") || token.startsWith("!wcm") -> MapRoomKind.WC
        token == "!entry" -> MapRoomKind.ENTRY
        token == "!atm" -> MapRoomKind.ATM
        token.startsWith("!") -> MapRoomKind.UNKNOWN
        else -> MapRoomKind.ROOM
    }

    // ---------- геометрия ----------

    private data class SideDef(
        val name: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val vertical: Boolean,
        val reversed: Boolean,
        val cornerA: Float = 0f,
        val cornerB: Float = 0f,
    )

    private data class Cell(val room: String?, val rect: RectF)

    private data class RectF(val x: Float, val y: Float, val w: Float, val h: Float)

    private fun slotRects(side: SideDef, rooms: List<String?>): List<Cell> {
        val n = rooms.size
        if (n == 0) return emptyList()
        val length = if (side.vertical) side.h else side.w

        fun edge(k: Int): Float {
            if (side.cornerA == 0f && side.cornerB == 0f) return k * length / n
            return when (k) {
                0 -> 0f
                n -> length
                else -> side.cornerA + (k - 1) * (length - side.cornerA - side.cornerB) /
                    (n - 2).coerceAtLeast(1)
            }
        }

        return rooms.mapIndexed { index, room ->
            val k = if (side.reversed) n - 1 - index else index
            val start = edge(k)
            val size = edge(k + 1) - start
            val rect = if (!side.vertical) {
                RectF(side.x + start, side.y, size, side.h)
            } else {
                RectF(side.x, side.y + start, side.w, size)
            }
            Cell(room, rect)
        }
    }

    private fun union(a: RectF, b: RectF): RectF {
        val x = minOf(a.x, b.x)
        val y = minOf(a.y, b.y)
        return RectF(
            x = x,
            y = y,
            w = maxOf(a.x + a.w, b.x + b.w) - x,
            h = maxOf(a.y + a.h, b.y + b.h) - y,
        )
    }

    private fun rectPolygon(x: Float, y: Float, w: Float, h: Float): List<PointF> = listOf(
        PointF(x, y),
        PointF(x + w, y),
        PointF(x + w, y + h),
        PointF(x, y + h),
    )

    private fun annexPolygon(): List<PointF> = listOf(
        PointF(600f, 364f),
        PointF(880f, 364f),
        PointF(880f, 500f),
        PointF(830f, 500f),
        PointF(830f, 545f),
        PointF(730f, 545f),
        PointF(730f, 500f),
        PointF(600f, 500f),
    )

    private companion object {
        val SIDES = listOf(
            SideDef("n_out", x = 0f, y = 0f, w = 600f, h = 65f, vertical = false, reversed = false, cornerA = 125f, cornerB = 116f),
            SideDef("e_out", x = 525f, y = 65f, w = 75f, h = 480f, vertical = true, reversed = false),
            SideDef("s_out", x = 0f, y = 545f, w = 600f, h = 55f, vertical = false, reversed = true, cornerA = 125f, cornerB = 116f),
            SideDef("w_out", x = 0f, y = 65f, w = 80f, h = 480f, vertical = true, reversed = true),
            SideDef("n_in", x = 120f, y = 100f, w = 365f, h = 65f, vertical = false, reversed = false),
            SideDef("e_in", x = 410f, y = 165f, w = 75f, h = 285f, vertical = true, reversed = false),
            SideDef("s_in", x = 120f, y = 450f, w = 365f, h = 60f, vertical = false, reversed = true),
            SideDef("w_in", x = 120f, y = 165f, w = 80f, h = 285f, vertical = true, reversed = true),
        )
    }
}
