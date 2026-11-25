package ru.tusur.timetable.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.DataSourceMode
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.domain.model.StartScreen
import ru.tusur.timetable.domain.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tusur_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val SOURCE_KEY = stringPreferencesKey("source_key")
        val SOURCE_LABEL = stringPreferencesKey("source_label")
        val THEME = stringPreferencesKey("theme_mode")
        val START_SCREEN = stringPreferencesKey("start_screen")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val BEFORE_ENABLED = booleanPreferencesKey("before_enabled")
        val BEFORE_MINUTES = intPreferencesKey("before_minutes")
        val EVENING_ENABLED = booleanPreferencesKey("evening_enabled")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val DATA_SOURCE = stringPreferencesKey("data_source")
        val OFFICIAL_FALLBACK = booleanPreferencesKey("official_fallback")
        val SENT_REMINDERS = stringSetPreferencesKey("sent_reminders")
        val ALARM_CODES = stringSetPreferencesKey("alarm_codes")
    }

    private val preferences: Flow<Preferences> = context.dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }

    val settings: Flow<AppSettings> = preferences.map { it.toSettings() }

    val selection: Flow<SourceSelection?> = preferences.map { prefs ->
        val key = prefs[Keys.SOURCE_KEY]
        if (key.isNullOrBlank()) null else SourceSelection(key, prefs[Keys.SOURCE_LABEL].orEmpty())
    }

    suspend fun selectionOnce(): SourceSelection? = selection.first()

    suspend fun setSelection(ref: SourceRef?, label: String? = null) {
        context.dataStore.edit { prefs ->
            if (ref == null) {
                prefs.remove(Keys.SOURCE_KEY)
                prefs.remove(Keys.SOURCE_LABEL)
            } else {
                prefs[Keys.SOURCE_KEY] = ref.key
                prefs[Keys.SOURCE_LABEL] = label ?: ref.displayLabel ?: ref.fallbackLabel
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) = editString(Keys.THEME, mode.name)

    suspend fun setStartScreen(screen: StartScreen) = editString(Keys.START_SCREEN, screen.name)

    suspend fun setNotificationsEnabled(enabled: Boolean) = editBoolean(Keys.NOTIFICATIONS, enabled)

    suspend fun setBeforeClassEnabled(enabled: Boolean) = editBoolean(Keys.BEFORE_ENABLED, enabled)

    suspend fun setBeforeClassMinutes(minutes: Int) = editInt(Keys.BEFORE_MINUTES, minutes)

    suspend fun setEveningDigestEnabled(enabled: Boolean) = editBoolean(Keys.EVENING_ENABLED, enabled)

    suspend fun setEveningDigestHour(hour: Int) = editInt(Keys.EVENING_HOUR, hour)

    suspend fun setMorningDigestEnabled(enabled: Boolean) = editBoolean(Keys.MORNING_ENABLED, enabled)

    suspend fun setMorningDigestHour(hour: Int) = editInt(Keys.MORNING_HOUR, hour)

    suspend fun setDataSourceMode(mode: DataSourceMode) = editString(Keys.DATA_SOURCE, mode.name)

    suspend fun setOfficialFallbackEnabled(enabled: Boolean) = editBoolean(Keys.OFFICIAL_FALLBACK, enabled)

    /** true — уведомление ещё не отправлялось; заодно подчищает старые записи. */
    suspend fun markReminderSent(eventKey: String): Boolean {
        val prefs = context.dataStore.data.first()
        val current = prefs[Keys.SENT_REMINDERS].orEmpty()
        if (eventKey in current) return false
        val updated = (current + eventKey).toList().takeLast(MAX_SENT_KEYS).toSet()
        context.dataStore.edit { it[Keys.SENT_REMINDERS] = updated }
        return true
    }

    suspend fun scheduledAlarmCodes(): Set<String> =
        context.dataStore.data.first()[Keys.ALARM_CODES].orEmpty()

    suspend fun setScheduledAlarmCodes(codes: Set<String>) {
        context.dataStore.edit { it[Keys.ALARM_CODES] = codes }
    }

    private suspend fun editString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun editBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun editInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        themeMode = enumOr(Keys.THEME, ThemeMode.SYSTEM),
        startScreen = enumOr(Keys.START_SCREEN, StartScreen.SCHEDULE),
        notificationsEnabled = this[Keys.NOTIFICATIONS] ?: true,
        beforeClassEnabled = this[Keys.BEFORE_ENABLED] ?: true,
        beforeClassMinutes = (this[Keys.BEFORE_MINUTES] ?: 15)
            .takeIf { it in AppSettings.beforeClassOptions } ?: 15,
        eveningDigestEnabled = this[Keys.EVENING_ENABLED] ?: false,
        eveningDigestHour = (this[Keys.EVENING_HOUR] ?: 19).coerceIn(0, 23),
        morningDigestEnabled = this[Keys.MORNING_ENABLED] ?: false,
        morningDigestHour = (this[Keys.MORNING_HOUR] ?: 8).coerceIn(0, 23),
        dataSourceMode = enumOr(Keys.DATA_SOURCE, DataSourceMode.PROXY),
        officialFallbackEnabled = this[Keys.OFFICIAL_FALLBACK] ?: false,
    )

    private inline fun <reified T : Enum<T>> Preferences.enumOr(
        key: Preferences.Key<String>,
        fallback: T,
    ): T {
        val raw = this[key] ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private companion object {
        const val MAX_SENT_KEYS = 200
    }
}
