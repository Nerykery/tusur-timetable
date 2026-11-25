package ru.tusur.timetable.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.domain.model.DataSourceMode
import ru.tusur.timetable.domain.model.StartScreen
import ru.tusur.timetable.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissions: PermissionState,
    onThemeChange: (ThemeMode) -> Unit,
    onStartScreenChange: (StartScreen) -> Unit,
    onNotificationsEnabled: (Boolean) -> Unit,
    onBeforeEnabled: (Boolean) -> Unit,
    onBeforeMinutes: (Int) -> Unit,
    onEveningEnabled: (Boolean) -> Unit,
    onEveningHour: (Int) -> Unit,
    onMorningEnabled: (Boolean) -> Unit,
    onMorningHour: (Int) -> Unit,
    onDataSourceChange: (DataSourceMode) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { onRefreshPermissions() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permissions.notifications) {
                WarningCard(
                    title = stringResource(R.string.settings_notifications_permission),
                    text = stringResource(R.string.settings_notifications_permission_hint),
                    action = stringResource(R.string.settings_allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onOpenNotificationSettings()
                        }
                    },
                )
            }

            if (!permissions.exactAlarms) {
                WarningCard(
                    title = stringResource(R.string.settings_exact_alarm),
                    text = stringResource(R.string.settings_exact_alarm_hint),
                    action = stringResource(R.string.settings_open_settings),
                    onAction = onOpenExactAlarmSettings,
                )
            }

            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_hint),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotificationsEnabled,
                )
                HorizontalDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_before_class),
                    subtitle = stringResource(R.string.settings_before_class_hint),
                    checked = settings.beforeClassEnabled,
                    onCheckedChange = onBeforeEnabled,
                    enabled = settings.notificationsEnabled,
                )
                if (settings.beforeClassEnabled && settings.notificationsEnabled) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppSettings.beforeClassOptions.forEach { minutes ->
                            FilterChip(
                                selected = settings.beforeClassMinutes == minutes,
                                onClick = { onBeforeMinutes(minutes) },
                                label = { Text("$minutes") },
                                enabled = true,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_minutes_before, settings.beforeClassMinutes),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.settings_morning_digest),
                    subtitle = stringResource(R.string.settings_morning_digest_hint),
                    checked = settings.morningDigestEnabled,
                    onCheckedChange = onMorningEnabled,
                    enabled = settings.notificationsEnabled,
                )
                if (settings.morningDigestEnabled) {
                    HourRow(
                        label = stringResource(R.string.settings_send_at),
                        hour = settings.morningDigestHour,
                        hours = AppSettings.morningHours,
                        onHourChange = onMorningHour,
                    )
                }
                HorizontalDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_evening_digest),
                    subtitle = stringResource(R.string.settings_evening_digest_hint),
                    checked = settings.eveningDigestEnabled,
                    onCheckedChange = onEveningEnabled,
                    enabled = settings.notificationsEnabled,
                )
                if (settings.eveningDigestEnabled) {
                    HourRow(
                        label = stringResource(R.string.settings_send_at),
                        hour = settings.eveningDigestHour,
                        hours = AppSettings.eveningHours,
                        onHourChange = onEveningHour,
                    )
                }
            }

            SettingsCard {
                SectionTitle(stringResource(R.string.settings_data_source))
                Text(
                    text = stringResource(R.string.settings_data_source_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.dataSourceMode == DataSourceMode.PROXY,
                        onClick = { onDataSourceChange(DataSourceMode.PROXY) },
                        label = { Text(stringResource(R.string.settings_source_proxy)) },
                    )
                    FilterChip(
                        selected = settings.dataSourceMode == DataSourceMode.OFFICIAL,
                        onClick = { onDataSourceChange(DataSourceMode.OFFICIAL) },
                        label = { Text(stringResource(R.string.settings_source_official)) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_official_fallback),
                    subtitle = stringResource(R.string.settings_official_fallback_hint),
                    checked = settings.officialFallbackEnabled,
                    onCheckedChange = onFallbackChange,
                )
            }

            SettingsCard {
                SectionTitle(stringResource(R.string.settings_theme))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeChange(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) },
                    )
                    FilterChip(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.settings_start_screen))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.startScreen == StartScreen.SCHEDULE,
                        onClick = { onStartScreenChange(StartScreen.SCHEDULE) },
                        label = { Text(stringResource(R.string.start_screen_schedule)) },
                    )
                    FilterChip(
                        selected = settings.startScreen == StartScreen.MAP,
                        onClick = { onStartScreenChange(StartScreen.MAP) },
                        label = { Text(stringResource(R.string.start_screen_map)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.menu_about),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_about_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onOpenAbout) {
                        Text(stringResource(R.string.open))
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_footer, context.packageName),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun HourRow(
    label: String,
    hour: Int,
    hours: List<Int>,
    onHourChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(String.format(java.util.Locale.ROOT, "%02d:00", hour))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                hours.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(String.format(java.util.Locale.ROOT, "%02d:00", value)) },
                        onClick = {
                            expanded = false
                            onHourChange(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) {
                Text(action)
            }
        }
    }
}
