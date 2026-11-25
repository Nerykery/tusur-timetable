package ru.tusur.timetable.data.map

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /fet.json сайта: реальная схема корпуса ФЭТ. Этаж 1 задан готовым планом,
 * остальные — восемью сторонами коридора (внешний и внутренний ряд помещений).
 */
@Serializable
data class FeetPlanDto(
    val building: String = "ф",
    val title: String = "ФЭТ",
    val address: String = "",
    @SerialName("hall_floors") val hallFloors: List<Int> = emptyList(),
    val floors: Map<String, FeetFloorDto> = emptyMap(),
    val zones: Map<String, List<FeetZoneDto>> = emptyMap(),
)

@Serializable
data class FeetFloorDto(
    val outline: String? = null,
    val plan: List<FeetRoomDto>? = null,
    @SerialName("n_out") val nOut: List<String?>? = null,
    @SerialName("e_out") val eOut: List<String?>? = null,
    @SerialName("s_out") val sOut: List<String?>? = null,
    @SerialName("w_out") val wOut: List<String?>? = null,
    @SerialName("n_in") val nIn: List<String?>? = null,
    @SerialName("e_in") val eIn: List<String?>? = null,
    @SerialName("s_in") val sIn: List<String?>? = null,
    @SerialName("w_in") val wIn: List<String?>? = null,
) {
    fun side(name: String): List<String?>? = when (name) {
        "n_out" -> nOut
        "e_out" -> eOut
        "s_out" -> sOut
        "w_out" -> wOut
        "n_in" -> nIn
        "e_in" -> eIn
        "s_in" -> sIn
        "w_in" -> wIn
        else -> null
    }
}

@Serializable
data class FeetRoomDto(
    val n: String? = null,
    val t: String? = null,
    val p: List<List<Float>>? = null,
    val r: List<Float>? = null,
    val d: List<Float>? = null,
    val i: String? = null,
    val s: Int? = null,
)

@Serializable
data class FeetZoneDto(
    val name: String = "",
    val info: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val icon: String? = null,
    val kind: String? = null,
    val small: Boolean? = null,
)
