package ru.tusur.timetable.ui

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.SearchItem
import ru.tusur.timetable.domain.model.SourceRef
import ru.tusur.timetable.domain.model.Week
import ru.tusur.timetable.domain.repository.TimetableRepository
import ru.tusur.timetable.ui.picker.PickerViewModel
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : TimetableRepository {
        val queries = mutableListOf<String>()

        override fun observeCached(ref: SourceRef, monday: LocalDate) = flowOf<Week?>(null)
        override suspend fun cachedWeek(ref: SourceRef, monday: LocalDate): Week? = null
        override suspend fun refresh(ref: SourceRef, monday: LocalDate, force: Boolean) =
            Result.failure<Week>(UnsupportedOperationException())

        override suspend fun search(query: String): Result<List<SearchItem>> {
            queries += query
            return Result.success(
                listOf(
                    SearchItem(
                        source = SourceRef.group("fit/$query", "гр. $query"),
                        label = "гр. $query",
                        hint = "fit",
                        faculty = "fit",
                        group = query,
                    ),
                ),
            )
        }

        override suspend fun recentSearches(limit: Int) = emptyList<SearchItem>()

        override suspend fun teacherLabel(teacherId: String): String? = null
    }

    private fun model(repository: FakeRepository): PickerViewModel {
        val prefs = mockk<UserPreferences>(relaxed = true)
        every { prefs.selection } returns flowOf(null)
        return PickerViewModel(repository, prefs)
    }

    private fun TestScope.settle(ms: Long = 400) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `every typed character is kept immediately`() = runTest {
        val viewModel = model(FakeRepository())

        viewModel.onQueryChange("0")
        assertEquals("0", viewModel.state.value.query)
        viewModel.onQueryChange("02")
        assertEquals("02", viewModel.state.value.query)
        viewModel.onQueryChange("023")
        assertEquals("023", viewModel.state.value.query)
        settle()
        assertEquals("023", viewModel.state.value.query)
    }

    @Test
    fun `debounce collapses rapid typing into one request`() = runTest {
        val repository = FakeRepository()
        val viewModel = model(repository)

        viewModel.onQueryChange("0")
        viewModel.onQueryChange("02")
        viewModel.onQueryChange("023")
        settle()

        assertEquals(listOf("023"), repository.queries)
        assertEquals(1, viewModel.state.value.results.size)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `short query never hits network`() = runTest {
        val repository = FakeRepository()
        val viewModel = model(repository)

        viewModel.onQueryChange("0")
        settle()

        assertTrue(repository.queries.isEmpty())
        assertTrue(viewModel.state.value.showMinLengthHint)
    }

    @Test
    fun `spaces only are treated as empty field`() = runTest {
        val repository = FakeRepository()
        val viewModel = model(repository)

        viewModel.onQueryChange("   ")
        settle()

        assertFalse(
            "Пробелы не должны выглядеть как слишком короткий запрос",
            viewModel.state.value.showMinLengthHint,
        )
        assertTrue(viewModel.state.value.results.isEmpty())
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `clearing query drops results and hint`() = runTest {
        val viewModel = model(FakeRepository())

        viewModel.onQueryChange("023")
        settle()
        assertEquals(1, viewModel.state.value.results.size)

        viewModel.onQueryChange("")
        settle()

        assertEquals("", viewModel.state.value.query)
        assertTrue(viewModel.state.value.results.isEmpty())
        assertFalse(viewModel.state.value.showMinLengthHint)
        assertFalse(viewModel.state.value.loading)
    }
}
