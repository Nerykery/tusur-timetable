package ru.tusur.timetable.core.room

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Ссылки на аудитории корпуса ФЭТ. Сайт отдаёт `/map?r=331%D0%B1` — здесь
 * извлекаем номер в человеческом виде и строим deep link обратно.
 */
object RoomRefs {

    private val roomToken = Regex("""\d{2,4}[а-яa-z]?""", RegexOption.IGNORE_CASE)

    /** "/map?r=331%D0%B1" -> "331б". */
    fun roomFromMapPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val query = path.substringAfter('?', "")
        val raw = query.split('&')
            .firstOrNull { it.startsWith("r=") }
            ?.substringAfter("r=")
            ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** "331б" -> "/map?r=331%D0%B1". */
    fun mapPathFor(room: String): String {
        val encoded = runCatching { URLEncoder.encode(room, "UTF-8") }.getOrDefault(room)
        return "/map?r=$encoded"
    }

    /**
     * Первая аудитория корпуса ФЭТ в строке места: «ф 331б, улк 503, ф 224» -> «331б».
     * Аудитории других корпусов сайт в схему не отдаёт.
     */
    fun roomFromPlace(place: String?): String? {
        val value = place?.trim() ?: return null
        val parts = value.split(',')
        parts.forEach { part ->
            val chunk = part.trim()
            if (chunk.startsWith("ф") || chunk.startsWith("Ф")) {
                val token = roomToken.find(chunk.removePrefix("ф").removePrefix("Ф"))?.value
                if (token != null) return token.trim()
            }
        }
        val token = roomToken.find(value)?.value
        return token?.trim()
    }

    /** «331б» -> 3, «005» -> 0. */
    fun floorFromRoom(room: String?): Int? {
        val value = room?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val first = value.firstOrNull()?.digitToIntOrNull() ?: return null
        return first
    }

    /** Готовая ссылка deep link для карты: timetable://map?r=331%D0%B1. */
    fun deepLinkFor(room: String): String = "timetable://map?r=" + runCatching {
        URLEncoder.encode(room, "UTF-8")
    }.getOrDefault(room)
}
