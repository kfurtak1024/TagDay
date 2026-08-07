package dev.krfu.tagday.ui.calendar.day

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
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
                onUpdateInstance = {},
                onRemoveInstance = { removedInstance = it },
                onAddValue = { id, v -> addedValue = id to v },
                onAddRating = { id, r -> addedRating = id to r },
                onIncrementCount = { incremented = it },
                onDecrementCount = { decremented = it },
                onReorderInstances = {},
            )
        }
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
    fun closeButton_dismissesTheSheet() {
        setSheet(TagType.SIMPLE, listOf(instance(1)))

        compose.onNodeWithContentDescription(
            context.getString(R.string.day_instances_sheet_close_content_description),
        ).performClick()
        compose.waitForIdle()

        assertEquals(true, dismissed)
    }
}
