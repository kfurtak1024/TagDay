package dev.krfu.tagday.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPaletteTest {
    @Test
    fun nextColor_onAnEmptyList_isTheFirstInThePalette() {
        assertEquals(TagPalette.colors.first(), TagPalette.nextColor(emptyList()))
    }

    @Test
    fun nextColor_skipsColorsAlreadyInUse() {
        val used = TagPalette.colors.take(3)
        assertEquals(TagPalette.colors[3], TagPalette.nextColor(used))
    }

    @Test
    fun nextColor_fillsAGapLeftByADeletedTag() {
        // The case the old `size % colors.size` got wrong: deleting from the middle left its
        // colour free, but the index kept marching on and duplicated an existing one instead.
        val used = TagPalette.colors.toMutableList().apply { removeAt(2) }
        assertEquals(TagPalette.colors[2], TagPalette.nextColor(used))
    }

    @Test
    fun nextColor_neverRepeatsWhileThePaletteHasRoom() {
        val assigned = mutableListOf<Int>()
        repeat(TagPalette.colors.size) { assigned += TagPalette.nextColor(assigned) }

        assertEquals(TagPalette.colors.size, assigned.toSet().size)
        assertEquals(TagPalette.colors.toSet(), assigned.toSet())
    }

    @Test
    fun nextColor_oncePaletteIsExhausted_stillReturnsAPaletteColor() {
        // Repeats are unavoidable past this point; the rule just has to stay in the palette.
        val used = TagPalette.colors + TagPalette.colors
        assertTrue(TagPalette.nextColor(used) in TagPalette.colors)
    }
}
