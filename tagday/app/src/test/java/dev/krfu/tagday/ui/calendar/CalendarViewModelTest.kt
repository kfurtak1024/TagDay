package dev.krfu.tagday.ui.calendar

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.Event
import app.cash.turbine.test
import dev.krfu.tagday.MainDispatcherRule
import dev.krfu.tagday.MutableClock
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.repository.FakeTagInstanceRepository
import dev.krfu.tagday.data.repository.FakeTagRepository
import dev.krfu.tagday.keepSubscribed
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The pending-removal (ADR-019), fresh-Valued-tag (ADR-021) and reorder (ADR-022) flows are
 * real branching logic living in the ViewModel, not thin delegation, so they get tests of
 * their own — see `TESTING.md`.
 */
// `TestScope.advanceTimeBy` is still @ExperimentalCoroutinesApi in coroutines-test 1.9.0 —
// both the millis and the kotlin.time.Duration overloads, so there's no stable alternative to
// switch to. Opting in here matches `MainDispatcherRule`, which does the same for
// `UnconfinedTestDispatcher`/`setMain`.
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.now()
    private val todayEpochDay = today.toEpochDay().toInt()

    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 0x111111, createdAt = 0)
    private val movie = Tag(id = 2, name = "movie", type = TagType.VALUED, color = 0x222222, createdAt = 0)

    private fun instance(id: Long, tagId: Long, value: String? = null, sortOrder: Long = id) = TagInstance(
        id = id,
        tagId = tagId,
        date = todayEpochDay,
        value = value,
        createdAt = id,
        sortOrder = sortOrder,
    )

    private fun viewModelWith(
        instances: List<TagInstance> = emptyList(),
        tags: List<Tag> = listOf(walk, movie),
        clock: Clock = Clock.systemDefaultZone(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): Triple<CalendarViewModel, FakeTagRepository, FakeTagInstanceRepository> {
        val tagRepository = FakeTagRepository(tags)
        val instanceRepository = FakeTagInstanceRepository(instances, tags.associateBy { it.id })
        return Triple(
            CalendarViewModel(tagRepository, instanceRepository, clock, savedState),
            tagRepository,
            instanceRepository,
        )
    }

    private fun CalendarUiState.dayGroups(): List<TagDisplayGroup> =
        (periodData as CalendarPeriodData.Day).groups

    // --- navigation -------------------------------------------------------------------

    @Test
    fun stepZoom_clampsAtBothEnds() = runTest {
        val (viewModel, _, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)

        viewModel.stepZoom(-1)
        assertEquals(ZoomLevel.DAY, viewModel.uiState.value.zoomLevel)

        repeat(ZoomLevel.entries.size + 2) { viewModel.stepZoom(1) }
        assertEquals(ZoomLevel.YEAR, viewModel.uiState.value.zoomLevel)

        viewModel.stepZoom(-1)
        assertEquals(ZoomLevel.MONTH, viewModel.uiState.value.zoomLevel)
    }

    @Test
    fun stepTime_stepsByTheCurrentZoomLevelsUnit() = runTest {
        val (viewModel, _, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        val start = viewModel.uiState.value.focusedDate

        viewModel.stepTime(1)
        assertEquals(start.plusDays(1), viewModel.uiState.value.focusedDate)

        viewModel.setZoom(ZoomLevel.MONTH)
        viewModel.stepTime(-1)
        assertEquals(start.plusDays(1).minusMonths(1), viewModel.uiState.value.focusedDate)
    }

    /**
     * Every emission must be internally consistent — the period data has to belong to the
     * date and zoom level emitted alongside it. Asserting on `.value` after the fact can't
     * catch this: the *settled* state was always right, and it was the intermediate emission
     * that paired a new query with the previous query's data (BACKLOG F5, ADR-036). So this
     * records every emission and checks all of them.
     */
    @Test
    fun everyEmission_pairsTheDataWithItsOwnQuery() = runTest {
        val (viewModel, _, _) = viewModelWith(instances = listOf(instance(id = 1, tagId = walk.id)))

        viewModel.uiState.test {
            viewModel.stepTime(1)
            viewModel.setZoom(ZoomLevel.WEEK)
            viewModel.setZoom(ZoomLevel.DAY)
            viewModel.stepTime(-1)

            val states = cancelAndConsumeRemainingEvents()
                .filterIsInstance<Event.Item<CalendarUiState>>()
                .map { it.value }

            assertTrue("expected several emissions, got ${states.size}", states.size > 1)
            states.forEach { state ->
                // Day data may only appear at Day zoom, Week data at Week zoom, and so on. The
                // initial state is the one exemption: it's `stateIn`'s placeholder, not a query
                // result, and carries an empty Day payload before anything has been collected.
                if (state.isLoading) return@forEach
                val expected = when (state.zoomLevel) {
                    ZoomLevel.DAY -> CalendarPeriodData.Day::class
                    ZoomLevel.WEEK -> CalendarPeriodData.Week::class
                    ZoomLevel.MONTH, ZoomLevel.YEAR -> CalendarPeriodData.Heatmap::class
                }
                assertEquals(
                    "zoom ${state.zoomLevel} carried ${state.periodData::class.simpleName}",
                    expected,
                    state.periodData::class,
                )
                // And the Day payload must be the *focused* day's, not a neighbour's.
                val groups = (state.periodData as? CalendarPeriodData.Day)?.groups ?: return@forEach
                assertEquals(
                    "groups for ${state.focusedDate} (today is $today)",
                    if (state.focusedDate == today) 1 else 0,
                    groups.size,
                )
            }
        }
    }

    @Test
    fun today_rollsOverAtMidnight() = runTest {
        // Ten seconds to midnight UTC. Nothing about the focused date changes — it's the
        // ViewModel's notion of *today* that has to move, since that's what every zoom level's
        // today-highlight and the jump-to-today button key off (BACKLOG F6).
        val clock = MutableClock(Instant.parse("2026-08-03T23:59:50Z"))
        val (viewModel, _, _) = viewModelWith(clock = clock)
        keepSubscribed(viewModel.uiState)

        assertEquals(LocalDate.of(2026, 8, 3), viewModel.uiState.value.today)

        clock.instant = Instant.parse("2026-08-04T00:00:01Z")
        advanceTimeBy(11.seconds)

        assertEquals(LocalDate.of(2026, 8, 4), viewModel.uiState.value.today)
        // And it keeps going, rather than firing once and stopping.
        clock.instant = Instant.parse("2026-08-05T00:00:01Z")
        advanceTimeBy(1.days)
        assertEquals(LocalDate.of(2026, 8, 5), viewModel.uiState.value.today)
    }

    @Test
    fun pendingRemoval_commitsItselfWithoutTheSnackbar() = runTest {
        // The undo window belongs to the ViewModel, not the snackbar's LaunchedEffect. Leaving
        // the Calendar screen mid-window used to cancel that effect with neither Undo nor
        // commit taken, stranding the instances — hidden from Day zoom but never deleted
        // (BACKLOG F7). Nothing here touches the UI: the removal has to land on its own.
        val doomed = instance(id = 1, tagId = walk.id)
        val (viewModel, _, instanceRepository) = viewModelWith(instances = listOf(doomed))
        keepSubscribed(viewModel.uiState)

        viewModel.removeInstance(doomed, walk.name)
        assertTrue("hidden optimistically", viewModel.uiState.value.dayGroups().isEmpty())
        assertEquals("but not yet deleted", listOf(doomed), instanceRepository.instances.value)

        advanceTimeBy(5.seconds)

        assertEquals(emptyList<TagInstance>(), instanceRepository.instances.value)
        assertNull(viewModel.uiState.value.pendingRemoval)
    }

    @Test
    fun undoRemoval_cancelsTheCommitTimer() = runTest {
        val spared = instance(id = 1, tagId = walk.id)
        val (viewModel, _, instanceRepository) = viewModelWith(instances = listOf(spared))
        keepSubscribed(viewModel.uiState)

        viewModel.removeInstance(spared, walk.name)
        viewModel.undoRemoval()
        // Well past the window: the timer must be cancelled, not merely ignored.
        advanceTimeBy(30.seconds)

        assertEquals(listOf(spared), instanceRepository.instances.value)
        assertEquals(1, viewModel.uiState.value.dayGroups().size)
    }

    @Test
    fun navigationState_survivesProcessDeath() = runTest {
        // A second ViewModel built from the same SavedStateHandle stands in for the one Android
        // recreates after killing the process (ADR-035, BACKLOG F10/F12).
        val savedState = SavedStateHandle()
        val (viewModel, _, _) = viewModelWith(savedState = savedState)
        keepSubscribed(viewModel.uiState)

        viewModel.setZoom(ZoomLevel.MONTH)
        viewModel.setFocusedDate(today.minusMonths(3))
        viewModel.selectHeatmapTag(movie.id)

        val (restored, _, _) = viewModelWith(savedState = savedState)
        keepSubscribed(restored.uiState)

        assertEquals(ZoomLevel.MONTH, restored.uiState.value.zoomLevel)
        assertEquals(today.minusMonths(3), restored.uiState.value.focusedDate)
        // The heatmap tag in particular: without it, zooming out lands back on "Pick a tag".
        assertEquals(movie.id, restored.uiState.value.selectedTagId)
    }

    @Test
    fun jumpToDay_setsBothDateAndZoom() = runTest {
        val (viewModel, _, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.setZoom(ZoomLevel.YEAR)

        val target = today.minusDays(40)
        viewModel.jumpToDay(target)

        assertEquals(ZoomLevel.DAY, viewModel.uiState.value.zoomLevel)
        assertEquals(target, viewModel.uiState.value.focusedDate)
    }

    @Test
    fun setFocusedDate_movesTheDateWithoutChangingZoom() = runTest {
        // The period row's date picker: at Month zoom, picking a day means "show me that
        // month", not "drop me into Day zoom" the way tapping a grid cell does.
        val (viewModel, _, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.setZoom(ZoomLevel.MONTH)

        val target = today.minusMonths(4)
        viewModel.setFocusedDate(target)

        assertEquals(target, viewModel.uiState.value.focusedDate)
        assertEquals(ZoomLevel.MONTH, viewModel.uiState.value.zoomLevel)
    }

    @Test
    fun jumpToToday_keepsZoomLevel() = runTest {
        val (viewModel, _, _) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.setZoom(ZoomLevel.WEEK)
        viewModel.stepTime(3)

        viewModel.jumpToToday()

        assertEquals(today, viewModel.uiState.value.focusedDate)
        assertEquals(ZoomLevel.WEEK, viewModel.uiState.value.zoomLevel)
    }

    // --- day-zoom data ----------------------------------------------------------------

    @Test
    fun dayGroups_onlyIncludeTheFocusedDate() = runTest {
        val yesterdayInstance = instance(1, walk.id).copy(date = todayEpochDay - 1)
        val (viewModel, _, _) = viewModelWith(listOf(yesterdayInstance, instance(2, walk.id)))
        keepSubscribed(viewModel.uiState)

        assertEquals(listOf(2L), viewModel.uiState.value.dayGroups().single().instances.map { it.id })

        viewModel.stepTime(-1)
        assertEquals(listOf(1L), viewModel.uiState.value.dayGroups().single().instances.map { it.id })
    }

    @Test
    fun addValue_addsAnInstanceOnTheFocusedDateWithThatValue() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.stepTime(-3)

        viewModel.addValue(movie.id, "dune")

        val added = instanceRepository.instances.value.single()
        assertEquals(movie.id, added.tagId)
        assertEquals("dune", added.value)
        assertEquals(today.minusDays(3).toEpochDay().toInt(), added.date)
    }

    @Test
    fun createTagAndAdd_withSeveralValues_seedsOneInstancePerValueInOrder() = runTest {
        // Quick-entry's `film:dune,tenet,arrival` shorthand — ADR-028.
        val (viewModel, tagRepository, instanceRepository) = viewModelWith(tags = emptyList())
        keepSubscribed(viewModel.uiState)

        viewModel.createTagAndAdd("film", TagType.VALUED, values = listOf("dune", "tenet", "arrival"))

        val created = tagRepository.tags.value.single()
        assertEquals(TagType.VALUED, created.type)
        assertEquals(
            listOf("dune", "tenet", "arrival"),
            instanceRepository.instances.value.sortedBy { it.sortOrder }.map { it.value },
        )
        // Distinct sortOrders, or `ORDER BY sortOrder` would break the tie arbitrarily.
        val sortOrders = instanceRepository.instances.value.map { it.sortOrder }
        assertEquals(sortOrders.size, sortOrders.distinct().size)
    }

    @Test
    fun addRating_addsAnInstanceOnTheFocusedDateWithThatRating() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith()
        keepSubscribed(viewModel.uiState)
        viewModel.stepTime(-2)

        viewModel.addRating(walk.id, 4)

        val added = instanceRepository.instances.value.single()
        assertEquals(walk.id, added.tagId)
        assertEquals(4, added.rating)
        assertEquals(today.minusDays(2).toEpochDay().toInt(), added.date)
    }

    @Test
    fun addRating_withNoStarsPicked_addsAnUnratedInstance() = runTest {
        // A Rated instance may legitimately exist unrated and be rated later (ADR-008), so
        // the sheet's "+" doesn't need a rating to add a row.
        val (viewModel, _, instanceRepository) = viewModelWith()
        keepSubscribed(viewModel.uiState)

        viewModel.addRating(walk.id, null)

        assertNull(instanceRepository.instances.value.single().rating)
    }

    @Test
    fun createTagAndAdd_createsTheTagThenSeedsOneInstance() = runTest {
        val (viewModel, tagRepository, instanceRepository) = viewModelWith(tags = emptyList())
        keepSubscribed(viewModel.uiState)

        viewModel.createTagAndAdd("freediving", TagType.RATED, rating = 4)

        val created = tagRepository.tags.value.single()
        assertEquals("freediving", created.name)
        assertEquals(TagType.RATED, created.type)
        val added = instanceRepository.instances.value.single()
        assertEquals(created.id, added.tagId)
        assertEquals(4, added.rating)
    }

    // --- fresh tag opened for editing (ADR-021, ADR-031) ------------------------------

    @Test
    fun createTagForEditing_createsTagWithNoInstanceAndSignalsTheSheet() = runTest {
        // Both Rated and Valued: neither has anything worth showing until the first
        // rating/value is entered, so creation defers to the sheet instead of guessing.
        listOf(TagType.VALUED, TagType.RATED).forEach { type ->
            val (viewModel, tagRepository, instanceRepository) = viewModelWith(tags = emptyList())
            keepSubscribed(viewModel.uiState)

            viewModel.createTagForEditing("film", type)

            val created = tagRepository.tags.value.single()
            assertEquals(type, created.type)
            assertTrue(instanceRepository.instances.value.isEmpty())
            assertEquals(created.id, viewModel.pendingTagEdit.value)
        }
    }

    @Test
    fun consumePendingTagEdit_clearsTheSignalSoItOnlyFiresOnce() = runTest {
        val (viewModel, _, _) = viewModelWith(tags = emptyList())
        keepSubscribed(viewModel.uiState)
        viewModel.createTagForEditing("movie", TagType.VALUED)

        viewModel.consumePendingTagEdit()

        assertNull(viewModel.pendingTagEdit.value)
    }

    // --- Simple count editor (ADR-031) ------------------------------------------------

    @Test
    fun addInstance_addsOneMoreOfTheSameTagOnTheFocusedDay() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)

        viewModel.addInstance(walk.id)

        assertEquals(2, viewModel.uiState.value.dayGroups().single().instances.size)
        assertEquals(2, instanceRepository.instances.value.size)
        assertEquals(todayEpochDay, instanceRepository.instances.value.last().date)
    }

    @Test
    fun removeInstanceImmediately_deletesWithoutAnUndoWindow() = runTest {
        // Unlike removeInstance, nothing is left pending: the count editor's "-" is undone by
        // pressing "+", so a snackbar would be noise (and would say "removed", which is wrong).
        val (viewModel, _, instanceRepository) = viewModelWith(
            listOf(instance(1, walk.id), instance(2, walk.id)),
        )
        keepSubscribed(viewModel.uiState)

        viewModel.removeInstanceImmediately(instance(2, walk.id))

        assertEquals(listOf(1L), instanceRepository.instances.value.map { it.id })
        assertNull(viewModel.uiState.value.pendingRemoval)
        assertEquals(1, viewModel.uiState.value.dayGroups().single().instances.size)
    }

    // --- reorder (ADR-022) ------------------------------------------------------------

    @Test
    fun reorderInstances_writesTheGivenInstancesThroughToTheRepository() = runTest {
        val first = instance(1, movie.id, value = "dune", sortOrder = 0)
        val second = instance(2, movie.id, value = "terminator", sortOrder = 1)
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(first, second))
        keepSubscribed(viewModel.uiState)

        viewModel.reorderInstances(listOf(second.copy(sortOrder = 0), first.copy(sortOrder = 1)))

        assertEquals(1, instanceRepository.reorderCalls.size)
        // Display order follows sortOrder (ADR-023), so the group now leads with terminator.
        assertEquals(
            listOf("terminator", "dune"),
            viewModel.uiState.value.dayGroups().single().instances.map { it.value },
        )
    }

    // --- delay-delete undo (ADR-019) --------------------------------------------------

    @Test
    fun removeGroup_hidesItImmediatelyButDoesNotDeleteYet() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)
        val group = viewModel.uiState.value.dayGroups().single()

        viewModel.removeGroup(group)

        assertTrue(viewModel.uiState.value.dayGroups().isEmpty())
        assertEquals(group.tagName, viewModel.uiState.value.pendingRemoval?.tagName)
        assertEquals(1, instanceRepository.instances.value.size)
    }

    @Test
    fun undoRemoval_bringsTheGroupBackAndDeletesNothing() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)
        viewModel.removeGroup(viewModel.uiState.value.dayGroups().single())

        viewModel.undoRemoval()

        assertEquals(1, viewModel.uiState.value.dayGroups().size)
        assertNull(viewModel.uiState.value.pendingRemoval)
        assertEquals(1, instanceRepository.instances.value.size)
    }

    @Test
    fun commitPendingRemoval_actuallyDeletes() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)
        viewModel.removeGroup(viewModel.uiState.value.dayGroups().single())

        viewModel.commitPendingRemoval()

        assertTrue(instanceRepository.instances.value.isEmpty())
        assertNull(viewModel.uiState.value.pendingRemoval)
    }

    @Test
    fun commitPendingRemoval_withNothingPending_isANoOp() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)

        viewModel.commitPendingRemoval()

        assertEquals(1, instanceRepository.instances.value.size)
    }

    @Test
    fun removeInstance_removesOnlyThatInstanceFromTheGroup() = runTest {
        val (viewModel, _, instanceRepository) = viewModelWith(
            listOf(instance(1, movie.id, value = "dune"), instance(2, movie.id, value = "terminator")),
        )
        keepSubscribed(viewModel.uiState)

        viewModel.removeInstance(instance(1, movie.id, value = "dune"), movie.name)
        assertEquals(
            listOf("terminator"),
            viewModel.uiState.value.dayGroups().single().instances.map { it.value },
        )

        viewModel.commitPendingRemoval()
        assertEquals(listOf(2L), instanceRepository.instances.value.map { it.id })
    }

    @Test
    fun secondRemoval_commitsTheFirstOneRatherThanStackingOrLosingIt() = runTest {
        // Only one removal is ever pending (see beginPendingRemoval) — the previous one has
        // to be flushed to the repository, not silently dropped, or its instances would
        // stay hidden while never actually being deleted.
        val (viewModel, _, instanceRepository) = viewModelWith(
            listOf(instance(1, walk.id), instance(2, movie.id, value = "dune")),
        )
        keepSubscribed(viewModel.uiState)
        val groups = viewModel.uiState.value.dayGroups()
        val movieGroup = groups.single { it.tagId == movie.id }
        val walkGroup = groups.single { it.tagId == walk.id }

        viewModel.removeGroup(movieGroup)
        viewModel.removeGroup(walkGroup)

        // movie's removal committed when walk's started; walk's is still pending.
        assertEquals(listOf(1L), instanceRepository.instances.value.map { it.id })
        assertEquals(walk.name, viewModel.uiState.value.pendingRemoval?.tagName)
        assertTrue(viewModel.uiState.value.dayGroups().isEmpty())

        viewModel.commitPendingRemoval()
        assertTrue(instanceRepository.instances.value.isEmpty())
    }

    @Test
    fun pendingRemoval_isNotAppliedToWeekZoomData() = runTest {
        // Known gap, asserted so it's a decision rather than a surprise: the optimistic
        // filter only covers Day-zoom groups (withPendingRemovalApplied), so zooming out
        // inside the undo window still shows the not-yet-deleted instance.
        val (viewModel, _, _) = viewModelWith(listOf(instance(1, walk.id)))
        keepSubscribed(viewModel.uiState)
        viewModel.removeGroup(viewModel.uiState.value.dayGroups().single())

        viewModel.setZoom(ZoomLevel.WEEK)

        val weekGroups = (viewModel.uiState.value.periodData as CalendarPeriodData.Week).groupsByDate
        assertEquals(1, weekGroups.getValue(todayEpochDay).size)
    }
}
