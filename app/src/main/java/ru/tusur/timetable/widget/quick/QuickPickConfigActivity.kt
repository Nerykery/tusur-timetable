package ru.tusur.timetable.widget.quick

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.tusur.timetable.R
import ru.tusur.timetable.data.db.WidgetDao
import ru.tusur.timetable.data.db.WidgetPrefsEntity
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceSelection
import ru.tusur.timetable.ui.picker.PickerContent
import ru.tusur.timetable.ui.picker.PickerUiState
import ru.tusur.timetable.ui.picker.PickerViewModel
import ru.tusur.timetable.ui.theme.TusurTheme
import ru.tusur.timetable.widget.WidgetDisplayMode
import javax.inject.Inject

@AndroidEntryPoint
class QuickPickConfigActivity : ComponentActivity() {

    @Inject
    lateinit var widgetDao: WidgetDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Пока пользователь не сохранил настройку, добавление виджета считается отменённым.
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        setContent {
            TusurTheme {
                val viewModel: PickerViewModel = hiltViewModel()
                val pickerState by viewModel.state.collectAsStateWithLifecycle()
                val saved by viewModel.saved.collectAsStateWithLifecycle()
                val recent by viewModel.recent.collectAsStateWithLifecycle()

                QuickPickConfigScreen(
                    pickerState = pickerState,
                    saved = saved,
                    recent = recent,
                    onQueryChange = viewModel::onQueryChange,
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onSave = { item, mode -> saveConfig(appWidgetId, item, mode) },
                )
            }
        }
    }

    private fun saveConfig(appWidgetId: Int, item: SearchItem?, mode: WidgetDisplayMode) {
        if (item == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@QuickPickConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                widgetDao.upsert(
                    WidgetPrefsEntity(
                        widgetId = glanceId.toString(),
                        sourceKey = item.source.key,
                        label = item.label,
                        mode = mode.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                QuickPickWidget().update(this@QuickPickConfigActivity, glanceId)
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
fun QuickPickConfigScreen(
    pickerState: PickerUiState,
    saved: SourceSelection?,
    recent: List<SearchItem>,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (SearchItem?, WidgetDisplayMode) -> Unit,
) {
    var selected by remember { mutableStateOf<SearchItem?>(null) }
    var mode by remember { mutableStateOf(WidgetDisplayMode.NEXT) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PickerContent(
                    state = pickerState,
                    saved = saved,
                    recent = recent,
                    onQueryChange = onQueryChange,
                    onSelect = { selected = it },
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.widget_config_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == WidgetDisplayMode.NEXT,
                            onClick = { mode = WidgetDisplayMode.NEXT },
                            label = { Text(stringResource(R.string.widget_config_mode_next)) },
                        )
                        FilterChip(
                            selected = mode == WidgetDisplayMode.TODAY,
                            onClick = { mode = WidgetDisplayMode.TODAY },
                            label = { Text(stringResource(R.string.widget_config_mode_today)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = selected?.label
                            ?: stringResource(R.string.widget_config_nothing_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.widget_config_cancel))
                        }
                        Button(
                            onClick = { onSave(selected, mode) },
                            enabled = selected != null,
                        ) {
                            Text(stringResource(R.string.widget_config_save))
                        }
                    }
                }
            }
        }
    }
}
