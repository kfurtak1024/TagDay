package dev.krfu.tagday.ui.calendar.day

import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.model.TagDisplayGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The per-type instance panels (ADR-028, ADR-031). Simple gets a count stepper, Rated and
 * Valued get reorderable lists with add-rows — three quite different panels behind one
 * entry point, which is exactly the sort of branching worth pinning down.
 *
 * The drag-reorder gesture itself (ADR-022) is *not* covered: it depends on real pointer
 * arbitration between `draggable` and the list's own scroll, which took five attempts to get
 * right on hardware and isn't something a JVM simulation should be trusted to judge. Its
 * accessibility actions (move up/down) are reachable here, but the drag is a device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class InstanceListSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private var dismissed = false
    private var incremented: Long? = null
    private var decremented: TagInstance? = null
    private var addedValue: Pair<Long, String>? = null
    private var addedRating: Pair<Long, Int?>? = null
    private var removedInstance: TagInstance? = null
    private var updatedInstance: TagInstance? = null
    private var reordered: List<TagInstance>? = null

    private val reorderLabel by lazy {
        context.getString(R.string.day_instances_sheet_reorder_content_description)
    }
    private val moveUpLabel by lazy {
        context.getString(R.string.day_instances_sheet_move_up_content_description)
    }
    private val moveDownLabel by lazy {
        context.getString(R.string.day_instances_sheet_move_down_content_description)
    }

    private fun instance(id: Long, value: String? = null, rating: Int? = null, sortOrder: Long = id) =
        TagInstance(id = id, tagId = 1, date = 0, rating = rating, value = value, createdAt = id, sortOrder = sortOrder)

    private fun setSheet(type: TagType, instances: List<TagInstance>) {
        val group = TagDisplayGroup(
            tagId = 1,
            tagName = "walk",
            color = 0x111111,
            type = type,
            instances = instances,
            summary = "walk",
        )
        compose.setContent {
            InstanceListSheet(
                group = group,
                onDismiss = { dismissed = true },
                onUpdateInstance = { updatedInstance = it },
                onRemoveInstance = { removedInstance = it },
                onAddValue = { id, v -> addedValue = id to v },
                onAddRating = { id, r -> addedRating = id to r },
                onIncrementCount = { incremented = it },
                onDecrementCount = { decremented = it },
                onReorderInstances = { reordered = it },
            )
        }
    }

    /** The custom accessibility actions on one row's drag handle, top row first. */
    private fun moveActionsOfRow(index: Int): List<CustomAccessibilityAction> =
        compose.onAllNodesWithContentDescription(reorderLabel)[index]
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()

    private fun performMove(rowIndex: Int, label: String) {
        val action = moveActionsOfRow(rowIndex).single { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    @Test
    fun simplePanel_showsTheCount_andStepsUp() {
        setSheet(TagType.SIMPLE, listOf(instance(1), instance(2)))

        compose.onNodeWithText("2").assertExists()
        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_increase_count_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(1L, incremented)
    }

    @Test
    fun simplePanel_removesTheNewestInstanceFirst() {
        // ADR-031: "newest first out", so repeated −/+ is a no-op rather than a reshuffle.
        setSheet(TagType.SIMPLE, listOf(instance(1, sortOrder = 10), instance(2, sortOrder = 20)))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_decrease_count_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(2L, decremented?.id)
    }

    @Test
    fun simplePanel_cannotStepBelowOne() {
        // Floored at 1: removing the tag from the day entirely is the capsule's ✕ (ADR-031),
        // which keeps one removal path rather than two that behave differently.
        setSheet(TagType.SIMPLE, listOf(instance(1)))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_decrease_count_content_description),
        ).assertIsNotEnabled()
        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_increase_count_content_description),
        ).assertIsEnabled()
    }

    @Test
    fun valuedPanel_addsATypedValue() {
        setSheet(TagType.VALUED, listOf(instance(1, value = "dune")))

        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("tenet")
        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_add_value_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(1L to "tenet", addedValue)
    }

    @Test
    fun valuedPanel_ignoresABlankValue() {
        setSheet(TagType.VALUED, listOf(instance(1, value = "dune")))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_add_value_content_description),
        ).performClick()
        compose.waitForIdle()

        assertNull("a blank add must not create an instance with no value", addedValue)
    }

    @Test
    fun ratedPanel_addsAnUnratedInstanceWhenNoStarsArePicked() {
        // ADR-008/ADR-028: unrated is a real state, and the add-row creates one deliberately.
        setSheet(TagType.RATED, listOf(instance(1, rating = 3)))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_add_rating_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(1L to null, addedRating)
    }

    @Test
    fun valuedPanel_removesTheInstanceItsOwnDeleteBelongsTo() {
        setSheet(TagType.VALUED, listOf(instance(1, value = "dune"), instance(2, value = "tenet")))

        compose.onAllNodesWithContentDescription(
            context.getString(R.string.day_instances_sheet_remove_content_description),
        )[1].performClick()
        compose.waitForIdle()

        assertEquals(2L, removedInstance?.id)
    }

    @Test
    fun ratedPanel_announcesTheRatingOnTheRow_andWhatEachStarWouldDo() {
        // BACKLOG F16: every star used to be labelled with its own index, so a screen reader
        // read "1 stars, 2 stars… 5 stars" and never said what the rating actually *was*.
        setSheet(TagType.RATED, listOf(instance(1, rating = 3), instance(2, rating = 5)))

        compose.onNodeWithContentDescription("Rated 3 stars").assertExists()
        compose.onNodeWithContentDescription("Rated 5 stars").assertExists()
        // Three star rows in total — one per instance, plus the add-row's — and each one's
        // fourth star offers the same action instead of restating its row's rating.
        compose.onAllNodesWithContentDescription("Rate 4 stars").assertCountEquals(3)
    }

    @Test
    fun ratedPanel_anUnratedInstanceSaysSoRatherThanReadingAsZero() {
        // "Not rated" is a state of its own, not a low rating (ADR-008). The guard producing
        // it is `rating > 0`, and a zero-star reading is exactly what dropping it sounds like.
        setSheet(TagType.RATED, listOf(instance(1, rating = null)))

        compose.onAllNodesWithContentDescription(
            context.getString(R.string.day_rating_unrated_content_description),
        ).assertCountEquals(2) // the instance row and the add-row, both sitting at zero
        compose.onNodeWithContentDescription("Rated 0 stars").assertDoesNotExist()
    }

    @Test
    fun ratedPanel_tappingAStar_writesThatRatingToThatInstance() {
        setSheet(TagType.RATED, listOf(instance(1, rating = 1), instance(2, rating = 1)))

        // The second row's fourth star: both the rating and which instance it lands on matter.
        compose.onAllNodesWithContentDescription("Rate 4 stars")[1].performClick()
        compose.waitForIdle()

        assertEquals(2L, updatedInstance?.id)
        assertEquals(4, updatedInstance?.rating)
    }

    // --- reorder accessibility actions (ADR-022) --------------------------------------

    @Test
    fun moveDown_rewritesSortOrderToTheNewPositions() {
        // Touch-drag is unavailable to a screen reader, so these discrete moves are the only
        // route to reordering — and they run the same `commit` the drop does, which is what
        // turns a display order into sequential `sortOrder`s (ADR-022, ADR-023).
        setSheet(
            TagType.VALUED,
            listOf(instance(1, value = "dune", sortOrder = 10), instance(2, value = "tenet", sortOrder = 20)),
        )

        performMove(rowIndex = 0, label = moveDownLabel)

        assertEquals(listOf(2L, 1L), reordered?.map { it.id })
        // Indices, not the original 10/20 — a later insert seeds sortOrder from a timestamp
        // and has to sort after everything a reorder has renumbered.
        assertEquals(listOf(0L, 1L), reordered?.map { it.sortOrder })
    }

    @Test
    fun moveUp_movesTheRowTowardsTheTop() {
        setSheet(
            TagType.VALUED,
            listOf(instance(1, value = "dune"), instance(2, value = "tenet"), instance(3, value = "arrival")),
        )

        performMove(rowIndex = 2, label = moveUpLabel)

        assertEquals(listOf(1L, 3L, 2L), reordered?.map { it.id })
    }

    @Test
    fun theEndsOfTheListOfferNoMoveOffIt() {
        setSheet(
            TagType.VALUED,
            listOf(instance(1, value = "dune"), instance(2, value = "tenet"), instance(3, value = "arrival")),
        )

        assertEquals(listOf(moveDownLabel), moveActionsOfRow(0).map { it.label })
        assertEquals(listOf(moveUpLabel, moveDownLabel), moveActionsOfRow(1).map { it.label })
        assertEquals(listOf(moveUpLabel), moveActionsOfRow(2).map { it.label })
    }

    @Test
    fun ratedPanel_reordersToo_notJustValued() {
        // ADR-028 has both types share one list rather than Valued having its own.
        setSheet(TagType.RATED, listOf(instance(1, rating = 2), instance(2, rating = 5)))

        performMove(rowIndex = 0, label = moveDownLabel)

        assertEquals(listOf(2L, 1L), reordered?.map { it.id })
    }

    @Test
    fun aSingleRowHasNothingToReorderAgainst() {
        setSheet(TagType.VALUED, listOf(instance(1, value = "dune")))

        assertEquals(emptyList<String>(), moveActionsOfRow(0).map { it.label })
    }

    @Test
    fun closeButton_dismissesTheSheet() {
        setSheet(TagType.SIMPLE, listOf(instance(1)))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_close_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(true, dismissed)
    }
}
