package dev.krfu.tagday.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNameTest {

    // --- sanitize: what typing is allowed to produce ------------------------------------

    @Test
    fun sanitize_lowercasesLetters() {
        assertEquals("walk", TagName.sanitize("WALK"))
        assertEquals("fast-food", TagName.sanitize("Fast-Food"))
    }

    @Test
    fun sanitize_rewritesWhitespaceAndUnderscoresAsSeparators() {
        // '-' is *the* separator for these names, so two typed words mean a separator
        // between them rather than one run-together word.
        assertEquals("fast-food", TagName.sanitize("fast food"))
        assertEquals("fast-food", TagName.sanitize("Fast_Food"))
        assertEquals("a-b-c", TagName.sanitize("a b c"))
    }

    @Test
    fun sanitize_dropsEverythingThatIsNeitherALetterNorASeparator() {
        assertEquals("movie", TagName.sanitize("mov:ie"))
        assertEquals("walk", TagName.sanitize("walk2"))
        assertEquals("walk", TagName.sanitize("w!a@l#k"))
        // Accented letters are dropped rather than transliterated — "letters" here means
        // a-z, and guessing at an ASCII fold would be a different feature.
        assertEquals("caf", TagName.sanitize("café"))
    }

    @Test
    fun sanitize_collapsesRunsOfSeparators() {
        assertEquals("fast-food", TagName.sanitize("fast--food"))
        assertEquals("fast-food", TagName.sanitize("fast----food"))
        // Mixed separator kinds collapse together too.
        assertEquals("fast-food", TagName.sanitize("fast - _ food"))
    }

    @Test
    fun sanitize_dropsLeadingSeparators() {
        assertEquals("walk", TagName.sanitize("-walk"))
        assertEquals("walk", TagName.sanitize("---walk"))
        assertEquals("walk", TagName.sanitize("   walk"))
    }

    @Test
    fun sanitize_keepsATrailingSeparator_soAHyphenatedNameCanBeTyped() {
        // Typing "fast-food" has to pass through "fast-"; stripping it here would make the
        // separator impossible to enter. isValid is what rejects it at save time.
        assertEquals("fast-", TagName.sanitize("fast-"))
    }

    @Test
    fun sanitize_isIdempotent() {
        val once = TagName.sanitize("--Fast__Food 99--")
        assertEquals(once, TagName.sanitize(once))
        assertEquals("fast-food-", once)
    }

    @Test
    fun sanitize_emptyAndSeparatorOnlyInputCollapseToEmpty() {
        assertEquals("", TagName.sanitize(""))
        assertEquals("", TagName.sanitize("-"))
        assertEquals("", TagName.sanitize("123"))
    }

    // --- isValid: what may be saved -----------------------------------------------------

    @Test
    fun isValid_acceptsLettersAndSingleSeparators() {
        assertTrue(TagName.isValid("walk"))
        assertTrue(TagName.isValid("fast-food"))
        assertTrue(TagName.isValid("playing-game-two"))
        assertTrue(TagName.isValid("a"))
    }

    @Test
    fun isValid_rejectsEmptyOrSeparatorEdges() {
        assertFalse(TagName.isValid(""))
        assertFalse(TagName.isValid("-"))
        assertFalse(TagName.isValid("-walk"))
        assertFalse(TagName.isValid("walk-"))
    }

    @Test
    fun isValid_rejectsDoubledSeparators() {
        assertFalse(TagName.isValid("fast--food"))
    }

    @Test
    fun isValid_rejectsAnythingButLowercaseLettersAndSeparators() {
        assertFalse(TagName.isValid("Walk"))
        assertFalse(TagName.isValid("walk2"))
        assertFalse(TagName.isValid("walk fast"))
        assertFalse(TagName.isValid("walk_fast"))
        assertFalse(TagName.isValid("café"))
    }

    @Test
    fun isValid_acceptsAnythingSanitizeProduces_exceptTrailingSeparators() {
        // The contract between the two halves: sanitize never emits something isValid would
        // reject for any reason other than being incomplete (empty, or ending in '-').
        val inputs = listOf(
            "WALK", "Fast--Food", "-x-", "a b c", "walk 2", "mov:ie", "café", "---", "",
            "playing_game", "Fast-Food-Two", "   ", "fast - _ food", "9",
        )
        inputs.forEach { raw ->
            val sanitized = TagName.sanitize(raw)
            val incomplete = sanitized.isEmpty() || sanitized.endsWith("-")
            assertEquals(
                "sanitize(\"$raw\") = \"$sanitized\"",
                !incomplete,
                TagName.isValid(sanitized),
            )
        }
    }
}
