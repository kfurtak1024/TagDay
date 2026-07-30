package dev.krfu.tagday.ui.calendar

import dev.krfu.tagday.MainDispatcherRule
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import dev.krfu.tagday.data.repository.FakeTagInstanceRepository
import dev.krfu.tagday.data.repository.FakeTagRepository
import dev.krfu.tagday.keepSubscribed
import java.time.LocalDate
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
    ): Triple<CalendarViewModel, FakeTagRepository, FakeTagInstanceRepository> {
        val tagRepository = FakeTagRepository(tags)
        val instanceRepository = FakeTagInstanceRepository(instances, tags.associateBy { it.id })
        return Triple(CalendarViewModel(tagRepository, instanceRepository), tagRepository, instanceRepository)
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
