package dev.krfu.tagday.ui.calendar.day

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.local.entity.TagType

/**
 * What pressing "+" should do when the typed name matches a tag that **already exists**.
 *
 * Pulled out of `TagQuickEntryBar` as a pure function so ADR-034's table can be tested — the
 * bar itself is a Composable, and this project has no instrumented tests (`TESTING.md`). The
 * rule it encodes: the tag's own type decides, since type is immutable (`CLAUDE.md` § Hard
 * rules) and is therefore the only thing that can say what a `:` seed means. A seed that fits
 * is applied, a bare name opens the sheet for the types that have a first value to enter, and
 * a seed that can't apply to this type is ignored rather than blocking the submit.
 */
sealed interface QuickEntryAction {
    val tagId: Long

    /** Simple: one more of the same, which is all a Simple tag can express. */
    data class AddInstance(override val tagId: Long) : QuickEntryAction

    data class AddRating(override val tagId: Long, val rating: Int) : QuickEntryAction

    data class AddValues(override val tagId: Long, val values: List<String>) : QuickEntryAction

    /**
     * Rated or Valued with nothing seeded — the instance sheet opens on the tag so the first
     * rating/value is entered there, rather than an empty instance being guessed at. Matches
     * what creating one of these types already does (ADR-021, ADR-031).
     */
    data class OpenSheet(override val tagId: Long) : QuickEntryAction

    companion object {
        fun forExistingTag(tag: Tag, parsed: ParsedTagInput?): QuickEntryAction = when (tag.type) {
            TagType.SIMPLE -> AddInstance(tag.id)

            TagType.RATED -> when (val rating = (parsed as? ParsedTagInput.Rated)?.rating) {
                null -> OpenSheet(tag.id)
                else -> AddRating(tag.id, rating)
            }

            TagType.VALUED -> {
                val values = (parsed as? ParsedTagInput.Valued)?.values.orEmpty()
                if (values.isEmpty()) OpenSheet(tag.id) else AddValues(tag.id, values)
            }
        }
    }
}
