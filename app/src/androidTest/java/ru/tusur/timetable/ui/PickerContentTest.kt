package ru.tusur.timetable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.ui.picker.PickerContent
import ru.tusur.timetable.ui.picker.PickerUiState
import ru.tusur.timetable.ui.theme.TusurTheme

class PickerContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val groupItem = SearchItem(
        source = SourceRef.group("fit/023", "гр. 023"),
        label = "гр. 023",
        hint = "fit",
        faculty = "fit",
        group = "023",
    )

    @Test
    fun typingReportsQueryChanges() {
        var typed = ""
        rule.setContent {
            TusurTheme {
                PickerContent(
                    state = PickerUiState(),
                    saved = null,
                    recent = emptyList(),
                    onQueryChange = { typed = it },
                    onSelect = {},
                )
            }
        }

        rule.onNode(hasSetTextAction()).performTextInput("Ко")
        assertEquals("Ко", typed)
    }

    @Test
    fun shortQueryShowsHint() {
        rule.setContent {
            TusurTheme {
                PickerContent(
                    state = PickerUiState(query = "0", showMinLengthHint = true),
                    saved = null,
                    recent = emptyList(),
                    onQueryChange = {},
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("Введите минимум 2 символа.").assertIsDisplayed()
    }

    @Test
    fun resultRowIsSelectable() {
        val selected = mutableListOf<SearchItem>()
        rule.setContent {
            TusurTheme {
                PickerContent(
                    state = PickerUiState(query = "023", results = listOf(groupItem)),
                    saved = null,
                    recent = emptyList(),
                    onQueryChange = {},
                    onSelect = { selected += it },
                )
            }
        }

        rule.onNodeWithText("гр. 023").performClick()
        assertEquals(groupItem, selected.single())
    }
}
