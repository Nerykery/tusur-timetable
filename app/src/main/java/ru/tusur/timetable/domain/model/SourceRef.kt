package ru.tusur.timetable.domain.model

/**
 * Ссылка на источник расписания.
 *
 * `id` для группы — строка из поиска сайта: «023» или «fit/023» (факультет/группа).
 * `id` для преподавателя — id из поиска сайта («35612»).
 * Ключ (`key`) — то, что хранится в настройках, SavedState и deep links.
 */
data class SourceRef(
    val type: Type,
    val id: String,
    val displayLabel: String? = null,
) {
    enum class Type { GROUP, TEACHER }

    val key: String
        get() = when (type) {
            Type.GROUP -> "group:$id"
            Type.TEACHER -> "teacher:$id"
        }

    val isTeacher: Boolean get() = type == Type.TEACHER
    val isGroup: Boolean get() = type == Type.GROUP

    /** Параметр `group` для /api/week — «fit/023». */
    val groupQuery: String? get() = if (type == Type.GROUP) id else null

    /** Параметр `teacher` для /api/week — «35612». */
    val teacherQuery: String? get() = if (type == Type.TEACHER) id else null

    /** Группа для официального API ТУСУР: без факультета — «023». */
    val officialGroupId: String?
        get() = groupQuery?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /** Фамилия для официального API: /lessons/lecturer?name=<Фамилия>. */
    val officialTeacherName: String?
        get() = if (type == Type.TEACHER) {
            displayLabel?.trim()?.removePrefix("преп.")?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    /**
     * Страница расписания на сайте ТУСУР. Для группы путь известен из поля `source`
     * ответа API; для преподавателя сайт отдаёт только общую страницу.
     */
    val webUrl: String
        get() = when (type) {
            Type.GROUP -> {
                val faculty = id.substringBeforeLast('/', "")
                val group = id.substringAfterLast('/')
                if (faculty.isNotEmpty()) {
                    "$OFFICIAL_SITE/faculties/$faculty/groups/$group"
                } else {
                    OFFICIAL_SITE
                }
            }

            Type.TEACHER -> OFFICIAL_SITE
        }

    /** Человеческое имя для строки выбора. */
    val fallbackLabel: String
        get() = displayLabel?.takeIf { it.isNotBlank() }
            ?: when (type) {
                Type.GROUP -> "гр. ${id.substringAfterLast('/')}"
                Type.TEACHER -> "преподаватель"
            }

    fun withLabel(label: String?): SourceRef = copy(displayLabel = label?.takeIf { it.isNotBlank() })

    companion object {
        const val OFFICIAL_SITE = "https://timetable.tusur.ru"

        fun group(id: String, label: String? = null): SourceRef = SourceRef(Type.GROUP, id.trim(), label)

        fun teacher(id: String, label: String? = null): SourceRef = SourceRef(Type.TEACHER, id.trim(), label)

        fun parse(rawKey: String?): SourceRef? {
            val value = rawKey?.trim().orEmpty()
            if (value.isEmpty()) return null
            return when {
                value.startsWith(GROUP_PREFIX) ->
                    value.removePrefix(GROUP_PREFIX).trim().takeIf { it.isNotEmpty() }?.let(::group)

                value.startsWith(TEACHER_PREFIX) ->
                    value.removePrefix(TEACHER_PREFIX).trim().takeIf { it.isNotEmpty() }?.let(::teacher)

                else -> null
            }
        }

        private const val GROUP_PREFIX = "group:"
        private const val TEACHER_PREFIX = "teacher:"
    }
}
