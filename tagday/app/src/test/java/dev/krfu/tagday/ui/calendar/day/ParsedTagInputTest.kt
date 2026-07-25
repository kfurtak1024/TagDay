package dev.krfu.tagday.ui.calendar.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedTagInputTest {
    @Test
    fun blankInput_returnsNull() {
        assertNull(ParsedTagInput.parse(""))
        assertNull(ParsedTagInput.parse("   "))
        assertNull(ParsedTagInput.parse(":***"))
    }

    @Test
    fun plainName_isAmbiguous() {
        assertEquals(ParsedTagInput.Ambiguous("yoga"), ParsedTagInput.parse("yoga"))
        assertEquals(ParsedTagInput.Ambiguous("yoga"), ParsedTagInput.parse("  yoga  "))
    }

    @Test
    fun trailingColonWithNothingAfter_isAmbiguous() {
        assertEquals(ParsedTagInput.Ambiguous("food"), ParsedTagInput.parse("food:"))
        assertEquals(ParsedTagInput.Ambiguous("food"), ParsedTagInput.parse("food:   "))
    }

    @Test
    fun starsSuffix_isRatedWithStarCount() {
        assertEquals(ParsedTagInput.Rated("food", 3), ParsedTagInput.parse("food:***"))
        assertEquals(ParsedTagInput.Rated("food", 1), ParsedTagInput.parse("food:*"))
        assertEquals(ParsedTagInput.Rated("food", 5), ParsedTagInput.parse("food:*****"))
    }

    @Test
    fun moreThanFiveStars_clampsToFive() {
        assertEquals(ParsedTagInput.Rated("food", 5), ParsedTagInput.parse("food:******"))
    }

    @Test
    fun textSuffix_isValued() {
        assertEquals(ParsedTagInput.Valued("movie", "dune"), ParsedTagInput.parse("movie:dune"))
    }

    @Test
    fun multiColonSuffix_isValuedWithFullRemainder() {
        assertEquals(ParsedTagInput.Valued("time", "10:30"), ParsedTagInput.parse("time:10:30"))
    }
}
