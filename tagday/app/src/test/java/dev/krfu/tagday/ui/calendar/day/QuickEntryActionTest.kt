package dev.krfu.tagday.ui.calendar.day

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-034's table, case by case. Before it, every one of these collapsed to "add one instance
 * with no rating and no value", which discarded the typed seed and, for Valued, produced an
 * instance the capsule couldn't show at all (BACKLOG F4).
 */
class QuickEntryActionTest {
    private fun tag(type: TagType) = Tag(id = 7, name = "x", type = type, color = 0, createdAt = 0)

    private fun action(type: TagType, typed: String) =
        QuickEntryAction.forExistingTag(tag(type), ParsedTagInput.parse(typed))

    @Test
    fun simple_addsAnInstance() {
        assertEquals(QuickEntryAction.AddInstance(7), action(TagType.SIMPLE, "walk"))
    }

    @Test
    fun rated_withStars_addsThatRating() {
        assertEquals(QuickEntryAction.AddRating(7, 3), action(TagType.RATED, "mood:***"))
    }

    @Test
    fun rated_bare_opensTheSheet() {
        assertEquals(QuickEntryAction.OpenSheet(7), action(TagType.RATED, "mood"))
    }

    @Test
    fun valued_withValue_addsThatValue() {
        assertEquals(QuickEntryAction.AddValues(7, listOf("dune")), action(TagType.VALUED, "film:dune"))
    }

    @Test
    fun valued_withSeveralValues_addsEachInOrder() {
        assertEquals(
            QuickEntryAction.AddValues(7, listOf("dune", "tenet")),
            action(TagType.VALUED, "film:dune,tenet"),
        )
    }

    @Test
    fun valued_bare_opensTheSheet() {
        // The case that used to add an invisible instance and make the capsule read `film: []`.
        assertEquals(QuickEntryAction.OpenSheet(7), action(TagType.VALUED, "film"))
    }

    @Test
    fun seedThatCannotApplyToThisType_isIgnoredRatherThanBlocking() {
        // Type is immutable, so a Simple tag can't take stars and a Rated one can't take a
        // value — the tag's type wins and the submit still does the sensible thing.
        assertEquals(QuickEntryAction.AddInstance(7), action(TagType.SIMPLE, "walk:***"))
        assertEquals(QuickEntryAction.OpenSheet(7), action(TagType.RATED, "mood:happy"))
        assertEquals(QuickEntryAction.OpenSheet(7), action(TagType.VALUED, "film:***"))
    }

    @Test
    fun suggestionTap_behavesLikeABareName() {
        // A tapped suggestion carries no seed at all, so it must land on the same branch a
        // bare name does rather than adding a blank instance.
        listOf(TagType.SIMPLE to QuickEntryAction.AddInstance(7), TagType.RATED to QuickEntryAction.OpenSheet(7))
            .forEach { (type, expected) ->
                assertEquals(expected, QuickEntryAction.forExistingTag(tag(type), null))
            }
    }
}
