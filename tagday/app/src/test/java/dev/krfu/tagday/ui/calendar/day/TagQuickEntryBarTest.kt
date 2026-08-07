package dev.krfu.tagday.ui.calendar.day

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.krfu.tagday.R
import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The quick-entry bar's routing, which `QuickEntryActionTest` covers only as a pure function —
 * this checks the bar actually calls the matching callback, that the type picker appears when
 * it should, and that as-you-type sanitization (ADR-028) does what ADR-034 assumes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TagQuickEntryBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val submitLabel = context.getString(R.string.day_quick_entry_submit_content_description)

    private val walk = Tag(id = 1, name = "walk", type = TagType.SIMPLE, color = 1, createdAt = 0)
    private val film = Tag(id = 2, name = "film", type = TagType.VALUED, color = 2, createdAt = 0)
    private val mood = Tag(id = 3, name = "mood", type = TagType.RATED, color = 3, createdAt = 0)

    private class Calls {
        var addedExisting: Long? = null
        var addedRating: Pair<Long, Int>? = null
        var addedValues: Pair<Long, List<String>>? = null
        var editedExisting: Long? = null
        var created: List<Any?>? = null
        var createdForEditing: Pair<String, TagType>? = null
    }

    private fun setBar(tags: List<Tag> = listOf(walk, film, mood)): Calls {
        val calls = Calls()
        compose.setContent {
            TagQuickEntryBar(
                allTags = tags,
                onAddExistingTag = { calls.addedExisting = it },
                onAddRatingToExistingTag = { id, r -> calls.addedRating = id to r },
                onAddValuesToExistingTag = { id, v -> calls.addedValues = id to v },
                onEditExistingTag = { calls.editedExisting = it },
                onCreateTag = { n, t, r, v -> calls.created = listOf(n, t, r, v) },
                onCreateTagForEditing = { n, t -> calls.createdForEditing = n to t },
            )
        }
        return calls
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextInput(text)

    private fun submit() = compose.onNodeWithContentDescription(submitLabel).performClick()

    @Test
    fun existingValuedTag_withAValue_addsThatValue() {
        val calls = setBar()

        type("film:dune")
        submit()

        assertEquals(film.id to listOf("dune"), calls.addedValues)
        // The bug F4 fixed: this used to fall through to a plain, value-less instance.
        assertNull(calls.addedExisting)
    }

    @Test
    fun existingValuedTag_bareName_opensTheSheetInsteadOfAddingABlankInstance() {
        val calls = setBar()

        type("film")
        submit()

        assertEquals(film.id, calls.editedExisting)
        assertNull(calls.addedExisting)
        assertNull(calls.addedValues)
    }

    @Test
    fun existingRatedTag_withStars_addsThatRating() {
        val calls = setBar()

        type("mood:***")
        submit()

        assertEquals(mood.id to 3, calls.addedRating)
    }

    @Test
    fun existingSimpleTag_addsOneMoreInstance() {
        val calls = setBar()

        type("walk")
        submit()

        assertEquals(walk.id, calls.addedExisting)
        assertNull(calls.editedExisting)
    }

    @Test
    fun newName_createsASimpleTagByDefault() {
        val calls = setBar()

        type("jogging")
        submit()

        assertEquals(listOf("jogging", TagType.SIMPLE, null, emptyList<String>()), calls.created)
    }

    @Test
    fun newName_showsTheTypePicker() {
        setBar()

        type("jogging")

        // The picker only appears when the typed name would create something (ADR-029).
        compose.onNodeWithText(context.getString(R.string.tag_type_simple)).assertExists()
        compose.onNodeWithText(context.getString(R.string.tag_type_valued)).assertExists()
    }

    @Test
    fun nameMatchingAnExistingTag_hidesTheTypePicker() {
        setBar()

        type("walk")

        // Nothing to choose: the tag exists and its type is immutable (ADR-034).
        compose.onNodeWithText(context.getString(R.string.tag_type_simple)).assertDoesNotExist()
    }

    @Test
    fun nameHalfIsSanitizedAsTyped_butTheValueHalfIsLeftAlone() {
        // ADR-028: "Fast Food" becomes fast-food, while `film:Blade Runner` keeps the value's
        // capitals and space. ADR-034's dispatch depends on the name half already being normal.
        setBar()

        type("Fast Food")
        compose.onNodeWithText("fast-food").assertExists()
    }

    @Test
    fun valueHalfKeepsItsCapitalsAndSpaces() {
        val calls = setBar()

        type("film:Blade Runner")
        submit()

        assertEquals(film.id to listOf("Blade Runner"), calls.addedValues)
    }
}
