package ru.tusur.timetable.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class StartScreen { SCHEDULE, MAP }

/** Источник данных: основной API расписания или api.tusur.ru. */
enum class DataSourceMode { PROXY, OFFICIAL }

data class SourceSelection(
    val key: String,
    val label: String,
) {
    val ref: SourceRef? get() = SourceRef.parse(key)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val startScreen: StartScreen = StartScreen.SCHEDULE,
    val notificationsEnabled: Boolean = true,
    val beforeClassEnabled: Boolean = true,
    /** За сколько минут напоминать: 5/10/15/20/30, по умолчанию 15. */
    val beforeClassMinutes: Int = 15,
    val eveningDigestEnabled: Boolean = false,
    val eveningDigestHour: Int = 19,
    val morningDigestEnabled: Boolean = false,
    val morningDigestHour: Int = 8,
    val dataSourceMode: DataSourceMode = DataSourceMode.PROXY,
    /** Пробовать api.tusur.ru, если основной источник недоступен (по умолчанию выключено). */
    val officialFallbackEnabled: Boolean = false,
) {
    companion object {
        val Default = AppSettings()

        val beforeClassOptions: List<Int> = listOf(5, 10, 15, 20, 30)
        val morningHours: List<Int> = (6..10).toList()
        val eveningHours: List<Int> = (18..22).toList()
    }
}
