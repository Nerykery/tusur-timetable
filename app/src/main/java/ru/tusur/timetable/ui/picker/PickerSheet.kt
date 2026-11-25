package ru.tusur.timetable.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tusur.timetable.R
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    state: PickerUiState,
    saved: SourceSelection?,
    recent: List<SearchItem>,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchItem) -> Unit,
    onSelectSaved: (SourceSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PickerContent(
            state = state,
            saved = saved,
            recent = recent,
            onQueryChange = onQueryChange,
            onSelect = onSelect,
            onSelectSaved = onSelectSaved,
        )
    }
}

@Composable
fun PickerContent(
    state: PickerUiState,
    saved: SourceSelection?,
    recent: List<SearchItem>,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchItem) -> Unit,
    onSelectSaved: (SourceSelection) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.picker_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.picker_hint)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.picker_clear),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = MaterialTheme.shapes.large,
        )

        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        if (saved != null && state.query.length < 2) {
            SectionLabel(stringResource(R.string.picker_saved))
            PickerRow(
                title = saved.label.ifBlank { saved.key },
                subtitle = stringResource(R.string.picker_current),
                isGroup = saved.key.startsWith("group:"),
                selected = true,
                onClick = {
                    // Сохранённое расписание открываем всегда, даже если его нет в «недавних».
                    onSelectSaved(saved)
                },
            )
        }

        if (state.query.length < MIN_QUERY) {
            if (recent.isNotEmpty()) {
                SectionLabel(stringResource(R.string.picker_recent))
                recent.forEach { item ->
                    PickerRow(
                        title = item.label,
                        subtitle = item.hint,
                        isGroup = item.isGroup,
                        selected = saved?.key == item.source.key,
                        onClick = { onSelect(item) },
                    )
                }
            }
            Text(
                text = if (state.showMinLengthHint) {
                    stringResource(R.string.picker_min_chars)
                } else {
                    stringResource(R.string.picker_start_typing)
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        if (state.error != null) {
            Text(
                text = stringResource(R.string.picker_error),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return
        }

        if (!state.loading && state.results.isEmpty()) {
            Text(
                text = stringResource(R.string.picker_no_results),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.results, key = { "${it.source.key}" }) { item ->
                PickerRow(
                    title = item.label,
                    subtitle = item.hint.ifBlank {
                        if (item.isGroup) {
                            stringResource(R.string.search_type_group)
                        } else {
                            stringResource(R.string.search_type_teacher)
                        }
                    },
                    isGroup = item.isGroup,
                    selected = saved?.key == item.source.key,
                    onClick = {
                        keyboard?.hide()
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    isGroup: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isGroup) Icons.Default.List else Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.picker_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private const val MIN_QUERY = 2
