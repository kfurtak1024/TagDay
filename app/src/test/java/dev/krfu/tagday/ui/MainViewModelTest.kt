package dev.krfu.tagday.ui

import dev.krfu.tagday.data.TagDayRepository
import dev.krfu.tagday.model.AppSettings
import dev.krfu.tagday.model.GlobalTag
import dev.krfu.tagday.model.RepositoryState
import dev.krfu.tagday.model.TagEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addTagForSelectedDay_whenRepositorySucceeds_clearsInputAndError() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        viewModel.updateTagInput("workout:***")

        viewModel.addTagForSelectedDay()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.tagInput)
        assertNull(state.inputError)
        assertEquals(1, repository.addTagCalls.size)
        assertEquals("workout:***", repository.addTagCalls.single().rawInput)
        collector.cancel()
    }

    @Test
    fun addTagForSelectedDay_whenRepositoryFails_setsInputError() = runTest {
        val repository = FakeTagDayRepository(
            addTagResult = Result.failure(IllegalArgumentException("Invalid tag"))
        )
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        viewModel.updateTagInput("bad input")

        viewModel.addTagForSelectedDay()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("bad input", state.tagInput)
        assertEquals("Invalid tag", state.inputError)
        collector.cancel()
    }

    @Test
    fun updateGlobalTag_whenRenameFails_setsGlobalTagErrorAndSkipsUpdates() = runTest {
        val repository = FakeTagDayRepository(
            renameResult = Result.failure(IllegalArgumentException("Name exists"))
        )
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)

        viewModel.updateGlobalTag(
            currentName = "old-tag",
            newName = "new-tag",
            colorArgb = 0xFF123456.toInt(),
            hidden = true
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Name exists", state.globalTagError)
        assertTrue(repository.updateColorCalls.isEmpty())
        assertTrue(repository.updateHiddenCalls.isEmpty())
        collector.cancel()
    }

    @Test
    fun daySelectionNavigation_changesDateAsExpected() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        val initial = viewModel.uiState.value.selectedDate

        viewModel.selectPreviousDay()
        advanceUntilIdle()
        assertEquals(initial.minusDays(1), viewModel.uiState.value.selectedDate)

        viewModel.selectNextDay()
        advanceUntilIdle()
        assertEquals(initial, viewModel.uiState.value.selectedDate)

        viewModel.selectToday()
        advanceUntilIdle()
        assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        collector.cancel()
    }

    @Test
    fun screenSelection_changesSelectedTabAsExpected() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)

        viewModel.showWeek()
        advanceUntilIdle()
        assertEquals(TabScreen.Week, viewModel.uiState.value.selectedTab)

        viewModel.showMonth()
        advanceUntilIdle()
        assertEquals(TabScreen.Month, viewModel.uiState.value.selectedTab)

        viewModel.showYear()
        advanceUntilIdle()
        assertEquals(TabScreen.Year, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(TabScreen.GlobalTags)
        advanceUntilIdle()
        assertEquals(TabScreen.GlobalTags, viewModel.uiState.value.selectedTab)

        viewModel.showDay()
        advanceUntilIdle()
        assertEquals(TabScreen.Day, viewModel.uiState.value.selectedTab)
        collector.cancel()
    }

    @Test
    fun selectToday_whenNotOnDayTab_resetsToTodayAndDayTab() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)

        viewModel.showYear()
        viewModel.selectPreviousYear()
        advanceUntilIdle()
        assertNotEquals(TabScreen.Day, viewModel.uiState.value.selectedTab)
        assertNotEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)

        viewModel.selectToday()
        advanceUntilIdle()
        assertEquals(TabScreen.Day, viewModel.uiState.value.selectedTab)
        assertEquals(LocalDate.now(), viewModel.uiState.value.selectedDate)
        collector.cancel()
    }

    @Test
    fun periodNavigation_changesDateByExpectedAmount() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        val initial = viewModel.uiState.value.selectedDate

        viewModel.selectPreviousWeek()
        advanceUntilIdle()
        assertEquals(initial.minusWeeks(1), viewModel.uiState.value.selectedDate)

        viewModel.selectNextWeek()
        advanceUntilIdle()
        assertEquals(initial, viewModel.uiState.value.selectedDate)

        viewModel.selectNextMonth()
        advanceUntilIdle()
        assertEquals(initial.plusMonths(1), viewModel.uiState.value.selectedDate)

        viewModel.selectPreviousMonth()
        advanceUntilIdle()
        assertEquals(initial, viewModel.uiState.value.selectedDate)

        viewModel.selectNextYear()
        advanceUntilIdle()
        assertEquals(initial.plusYears(1), viewModel.uiState.value.selectedDate)

        viewModel.selectPreviousYear()
        advanceUntilIdle()
        assertEquals(initial, viewModel.uiState.value.selectedDate)
        collector.cancel()
    }

    @Test
    fun openDay_setsDateAndSwitchesToDayTab() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        val target = LocalDate.of(2026, 3, 4)

        viewModel.showMonth()
        advanceUntilIdle()
        assertEquals(TabScreen.Month, viewModel.uiState.value.selectedTab)

        viewModel.openDay(target)
        advanceUntilIdle()
        assertEquals(target, viewModel.uiState.value.selectedDate)
        assertEquals(TabScreen.Day, viewModel.uiState.value.selectedTab)
        collector.cancel()
    }

    @Test
    fun setShowHiddenTags_delegatesToRepository() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)

        viewModel.setShowHiddenTags(true)
        viewModel.setShowHiddenTags(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.setShowHiddenTagsCalls)
    }

    @Test
    fun deleteGlobalTag_delegatesToRepository() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)

        viewModel.deleteGlobalTag("vacation")
        advanceUntilIdle()

        assertEquals(listOf("vacation"), repository.deleteGlobalTagCalls)
    }

    @Test
    fun updateGlobalTag_whenRenameSucceeds_updatesColorAndHidden() = runTest {
        val repository = FakeTagDayRepository()
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)

        viewModel.updateGlobalTag(
            currentName = "old-tag",
            newName = "new-tag",
            colorArgb = 0xFF123456.toInt(),
            hidden = true
        )
        advanceUntilIdle()

        assertEquals(
            listOf(FakeTagDayRepository.UpdateColorCall("new-tag", 0xFF123456.toInt())),
            repository.updateColorCalls
        )
        assertEquals(
            listOf(FakeTagDayRepository.UpdateHiddenCall("new-tag", true)),
            repository.updateHiddenCalls
        )
        assertNull(viewModel.uiState.value.globalTagError)
        collector.cancel()
    }

    @Test
    fun updateTagInput_whenInputErrorPresent_clearsInputError() = runTest {
        val repository = FakeTagDayRepository(
            addTagResult = Result.failure(IllegalArgumentException("Invalid tag"))
        )
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)

        viewModel.updateTagInput("invalid")
        viewModel.addTagForSelectedDay()
        advanceUntilIdle()
        assertEquals("Invalid tag", viewModel.uiState.value.inputError)

        viewModel.updateTagInput("valid-tag")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.inputError)
        collector.cancel()
    }

    @Test
    fun summaries_whenHiddenTagsDisabled_excludeHiddenTagsInWeekMonthAndYear() = runTest {
        val today = LocalDate.now()
        val repoState = RepositoryState(
            globalTags = mapOf(
                "visible-tag" to GlobalTag(name = "visible-tag", colorArgb = 0xFF111111.toInt(), hidden = false),
                "hidden-tag" to GlobalTag(name = "hidden-tag", colorArgb = 0xFF222222.toInt(), hidden = true)
            ),
            entriesByDate = mapOf(
                today to listOf(
                    TagEntry(id = 1, date = today, name = "visible-tag"),
                    TagEntry(id = 2, date = today, name = "hidden-tag")
                )
            ),
            settings = AppSettings(showHiddenTags = false)
        )
        val repository = FakeTagDayRepository(state = MutableStateFlow(repoState))
        val viewModel = MainViewModel(repository)
        val collector = startCollecting(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val weekLabels = state.weekSummary.first { it.date == today }.topLabels
        val monthLabels = state.monthSummary.first { it.date == today }.topLabels
        val yearLabels = state.yearSummary.first { it.month == today.monthValue }.topLabels

        assertTrue(weekLabels.contains("visible-tag"))
        assertFalse(weekLabels.contains("hidden-tag"))
        assertTrue(monthLabels.contains("visible-tag"))
        assertFalse(monthLabels.contains("hidden-tag"))
        assertTrue(yearLabels.contains("visible-tag"))
        assertFalse(yearLabels.contains("hidden-tag"))
        collector.cancel()
    }

    private fun TestScope.startCollecting(viewModel: MainViewModel): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeTagDayRepository(
    private val addTagResult: Result<Unit> = Result.success(Unit),
    private val renameResult: Result<Unit> = Result.success(Unit),
    override val state: StateFlow<RepositoryState> = MutableStateFlow(RepositoryState())
) : TagDayRepository {
    data class AddTagCall(val date: LocalDate, val rawInput: String)
    data class UpdateColorCall(val name: String, val colorArgb: Int)
    data class UpdateHiddenCall(val name: String, val hidden: Boolean)

    val addTagCalls = mutableListOf<AddTagCall>()
    val updateColorCalls = mutableListOf<UpdateColorCall>()
    val updateHiddenCalls = mutableListOf<UpdateHiddenCall>()
    val deleteGlobalTagCalls = mutableListOf<String>()
    val setShowHiddenTagsCalls = mutableListOf<Boolean>()

    override fun palette(): List<Int> = listOf(0xFF1D4ED8.toInt())

    override suspend fun addTag(date: LocalDate, rawInput: String): Result<Unit> {
        addTagCalls += AddTagCall(date, rawInput)
        return addTagResult
    }

    override suspend fun removeEntry(entryId: Long) = Unit

    override suspend fun removeTagForDate(date: LocalDate, tagName: String) = Unit

    override suspend fun renameGlobalTag(currentName: String, newName: String): Result<Unit> {
        return renameResult
    }

    override suspend fun deleteGlobalTag(name: String) {
        deleteGlobalTagCalls += name
    }

    override suspend fun updateGlobalTagColor(name: String, colorArgb: Int) {
        updateColorCalls += UpdateColorCall(name, colorArgb)
    }

    override suspend fun setGlobalTagHidden(name: String, hidden: Boolean) {
        updateHiddenCalls += UpdateHiddenCall(name, hidden)
    }

    override suspend fun setShowHiddenTags(show: Boolean) {
        setShowHiddenTagsCalls += show
    }
}
