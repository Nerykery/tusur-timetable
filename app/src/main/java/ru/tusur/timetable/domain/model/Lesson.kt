package ru.tusur.timetable.domain.model

import androidx.annotation.StringRes
import ru.tusur.timetable.R

/**
 * Тип занятия определяется по подстроке в строке `kind`, как на сайте:
 * лекц -> Лекция, практ -> Практика, лаб -> Лабораторная, курсов -> Курсовая,
 * экзам/зач -> Экзамен/Зачёт, консульт -> Консультация.
 */
enum class LessonType(@StringRes val labelRes: Int) {
    LECTURE(R.string.lesson_type_lecture),
    PRACTICE(R.string.lesson_type_practice),
    LAB(R.string.lesson_type_lab),
    COURSEWORK(R.string.lesson_type_coursework),
    EXAM(R.string.lesson_type_exam),
    CONSULTATION(R.string.lesson_type_consultation),
    OTHER(R.string.lesson_type_other),
    ;

    val isExamLike: Boolean get() = this == EXAM

    companion object {
        fun fromKind(raw: String?): LessonType {
            val kind = raw?.lowercase().orEmpty()
            return when {
                kind.contains("лекц") -> LECTURE
                kind.contains("практ") -> PRACTICE
                kind.contains("лаб") -> LAB
                kind.contains("курсов") -> COURSEWORK
                kind.contains("экзам") || kind.contains("зач") -> EXAM
                kind.contains("консульт") -> CONSULTATION
                else -> OTHER
            }
        }
    }
}

/** Ссылка на человека или группу из расписания. */
data class PersonRef(
    val label: String,
    val teacherId: String? = null,
    val groupKey: String? = null,
) {
    /** Ключ расписания этого человека: teacher:<id> или group:<faculty/group>. */
    val sourceKey: String?
        get() = when {
            !teacherId.isNullOrBlank() -> "teacher:$teacherId"
            !groupKey.isNullOrBlank() -> "group:$groupKey"
            else -> null
        }

    /** Фамилия — нужна официальному API ТУСУР, который ищет преподавателя по фамилии. */
    val surname: String?
        get() = label.trim()
            .removePrefix("преп.")
            .trim()
            .split(' ')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

    val shortLabel: String
        get() {
            val parts = label.trim().split(' ').filter { it.isNotBlank() }
            if (parts.size < 2) return label
            val initials = parts.drop(1).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            return parts.first() + " " + initials.joinToString(".") + "."
        }
}

data class Lesson(
    val position: Int,
    val date: java.time.LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val pair: Int?,
    val title: String,
    val kindRaw: String?,
    val type: LessonType,
    val place: String?,
    val who: String?,
    val people: List<PersonRef> = emptyList(),
    val note: String? = null,
    val floor: String? = null,
    val mapPath: String? = null,
    val mapRoom: String? = null,
) {
    val startLabel: String get() = ru.tusur.timetable.core.time.TomskTime.formatMinutes(startMinutes)
    val endLabel: String get() = ru.tusur.timetable.core.time.TomskTime.formatMinutes(endMinutes)
    val durationMinutes: Int get() = (endMinutes - startMinutes).coerceAtLeast(0)
    val pairLabel: String? get() = pair?.let { "$it-я пара" }
    val timeRange: String get() = "$startLabel–$endLabel"

    fun phaseAt(nowMinutes: Int): LessonPhase = when {
        nowMinutes < startMinutes -> LessonPhase.Upcoming
        nowMinutes >= endMinutes -> LessonPhase.Past
        else -> {
            val total = durationMinutes.coerceAtLeast(1)
            val elapsed = nowMinutes - startMinutes
            LessonPhase.Live(
                minutesLeft = endMinutes - nowMinutes,
                progress = (elapsed.toFloat() / total).coerceIn(0f, 1f),
            )
        }
    }

    /** Есть ли аудитория корпуса ФЭТ (можно открыть схему). */
    val hasMap: Boolean get() = !mapRoom.isNullOrBlank()
}

sealed interface LessonPhase {
    data class Live(val minutesLeft: Int, val progress: Float) : LessonPhase

    data object Past : LessonPhase

    data object Upcoming : LessonPhase
}
