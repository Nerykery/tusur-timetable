package ru.tusur.timetable.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.DataSourceMode
import ru.tusur.timetable.domain.model.StartScreen
import ru.tusur.timetable.domain.model.ThemeMode
import ru.tusur.timetable.notifications.NotificationHelper
import ru.tusur.timetable.notifications.ReminderScheduler
import javax.inject.Inject

data class PermissionState(
    val exactAlarms: Boolean,
    val notifications: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val reminders: ReminderScheduler,
    private val notifications: NotificationHelper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = prefs.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings.Default,
    )

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = readPermissions()
    }

    fun setTheme(mode: ThemeMode) = apply { prefs.setTheme(mode) }

    fun setStartScreen(screen: StartScreen) = apply { prefs.setStartScreen(screen) }

    fun setNotificationsEnabled(enabled: Boolean) = apply { prefs.setNotificationsEnabled(enabled) }

    fun setBeforeClassEnabled(enabled: Boolean) = apply { prefs.setBeforeClassEnabled(enabled) }

    fun setBeforeClassMinutes(minutes: Int) = apply { prefs.setBeforeClassMinutes(minutes) }

    fun setEveningDigestEnabled(enabled: Boolean) = apply { prefs.setEveningDigestEnabled(enabled) }

    fun setEveningDigestHour(hour: Int) = apply { prefs.setEveningDigestHour(hour) }

    fun setMorningDigestEnabled(enabled: Boolean) = apply { prefs.setMorningDigestEnabled(enabled) }

    fun setMorningDigestHour(hour: Int) = apply { prefs.setMorningDigestHour(hour) }

    fun setDataSourceMode(mode: DataSourceMode) = apply { prefs.setDataSourceMode(mode) }

    fun setOfficialFallbackEnabled(enabled: Boolean) =
        apply { prefs.setOfficialFallbackEnabled(enabled) }

    fun openExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun readPermissions(): PermissionState = PermissionState(
        exactAlarms = reminders.canScheduleExactAlarms(),
        notifications = notifications.canPostNotifications(),
    )

    private fun apply(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            reminders.rescheduleAll()
        }
    }
}
